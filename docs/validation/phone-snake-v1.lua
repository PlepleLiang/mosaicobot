local display = require('display')
local delay = require('delay')
local system = require('system')

local screen = display.open()
local info = screen:info()
local SW = math.floor(info.width)
local SH = math.floor(info.height)
math.randomseed(system.millis())

local HEADER = 40
local CELL = 20
local COLS = math.floor(SW / CELL)
local ROWS = math.floor((SH - HEADER) / CELL)
local GRID_W = COLS * CELL
local GRID_H = ROWS * CELL
local OFFX = math.floor((SW - GRID_W) / 2)
local OFFY = HEADER + math.floor((SH - HEADER - GRID_H) / 2)
local EXIT_X = SW - 70
local EXIT_Y = 8
local EXIT_W = 60
local EXIT_H = 28
local STEP = 120

local state = 'ready'
local snake = {}
local dir = {x=1, y=0}
local food = {x=0, y=0}
local score = 0
local win = false
local lastUpdate = 0
local exitRequested = false
local active = {}

local function spawnFood()
  food.x = 0
  food.y = 0
  if #snake >= COLS * ROWS then
    food = nil
    return
  end
  local occupied = {}
  for i = 1, #snake do
    local s = snake[i]
    occupied[s.y * COLS + s.x] = true
  end
  local free = {}
  for y = 0, ROWS - 1 do
    for x = 0, COLS - 1 do
      if not occupied[y * COLS + x] then
        free[#free + 1] = {x=x, y=y}
      end
    end
  end
  if #free == 0 then
    food = nil
    return
  end
  local p = free[math.random(1, #free)]
  food.x = p.x
  food.y = p.y
end

local function reset()
  local sx = math.floor(COLS / 2)
  local sy = math.floor(ROWS / 2)
  snake = {{x=sx, y=sy}, {x=sx-1, y=sy}, {x=sx-2, y=sy}}
  dir = {x=1, y=0}
  state = 'ready'
  score = 0
  win = false
  spawnFood()
end

local function setDir(nx, ny)
  if state == 'playing' then
    if nx == -dir.x and ny == -dir.y then return end
    dir.x = nx
    dir.y = ny
  elseif state == 'ready' then
    if nx == -dir.x and ny == -dir.y then return end
    dir.x = nx
    dir.y = ny
    state = 'playing'
    lastUpdate = system.millis()
  end
end

local function update()
  local head = snake[1]
  local nh = {x=head.x + dir.x, y=head.y + dir.y}
  if nh.x < 0 or nh.x >= COLS or nh.y < 0 or nh.y >= ROWS then
    state = 'over'
    win = false
    return
  end
  local eating = food ~= nil and nh.x == food.x and nh.y == food.y
  local limit = #snake
  if not eating then limit = #snake - 1 end
  for i = 1, limit do
    if snake[i].x == nh.x and snake[i].y == nh.y then
      state = 'over'
      win = false
      return
    end
  end
  table.insert(snake, 1, nh)
  if eating then
    score = score + 1
    if #snake >= COLS * ROWS then
      state = 'over'
      win = true
    else
      spawnFood()
    end
  else
    table.remove(snake)
  end
end

local function handleTouch()
  if not info.touch_available then return end
  local pts = screen:touch().points
  local seen = {}
  for i = 1, #pts do
    local t = pts[i]
    seen[t.id] = true
    local a = active[t.id]
    if not a then
      if t.x >= EXIT_X and t.x <= EXIT_X + EXIT_W and t.y >= EXIT_Y and t.y <= EXIT_Y + EXIT_H then
        active[t.id] = {sx=t.x, sy=t.y, used=true, ex=true}
        exitRequested = true
      else
        active[t.id] = {sx=t.x, sy=t.y, used=false, ex=false}
        if state == 'over' then reset() end
      end
    else
      if not a.used and not a.ex then
        local dx = t.x - a.sx
        local dy = t.y - a.sy
        if math.abs(dx) >= 20 or math.abs(dy) >= 20 then
          local nx, ny
          if math.abs(dx) > math.abs(dy) then
            nx = 1
            if dx < 0 then nx = -1 end
            ny = 0
          else
            nx = 0
            ny = 1
            if dy < 0 then ny = -1 end
          end
          setDir(nx, ny)
          a.used = true
        end
      end
    end
  end
  for id, a in pairs(active) do
    if not seen[id] then active[id] = nil end
  end
end

local function draw()
  screen:begin({clear='#111713'})
  screen:fill_rect(0, 0, SW, HEADER, '#0b120d')
  screen:text(10, 10, 'SCORE ' .. score, {font_size=20, color='#c0e8b1'})
  screen:fill_rect(EXIT_X, EXIT_Y, EXIT_W, EXIT_H, '#22331f')
  screen:stroke_rect(EXIT_X, EXIT_Y, EXIT_W, EXIT_H, '#c0e8b1')
  screen:text(EXIT_X + 12, EXIT_Y + 6, 'EXIT', {font_size=16, color='#c0e8b1'})
  screen:stroke_rect(OFFX, OFFY, GRID_W, GRID_H, '#3c5a41')
  for x = 1, COLS - 1 do
    local gx = OFFX + x * CELL
    screen:line(gx, OFFY, gx, OFFY + GRID_H - 1, '#18251a')
  end
  for y = 1, ROWS - 1 do
    local gy = OFFY + y * CELL
    screen:line(OFFX, gy, OFFX + GRID_W - 1, gy, '#18251a')
  end
  for i = 1, #snake do
    local s = snake[i]
    screen:fill_rect(OFFX + s.x * CELL + 1, OFFY + s.y * CELL + 1, CELL - 2, CELL - 2, '#c0e8b1')
  end
  if food ~= nil then
    local cx = OFFX + food.x * CELL + math.floor(CELL / 2)
    local cy = OFFY + food.y * CELL + math.floor(CELL / 2)
    screen:fill_circle(cx, cy, 7, '#e86f6f')
  end
  if state == 'ready' then
    local pw = 240
    local ph = 90
    local px = OFFX + math.floor((GRID_W - pw) / 2)
    local py = OFFY + math.floor((GRID_H - ph) / 2)
    screen:fill_rect(px, py, pw, ph, '#0b120d')
    screen:stroke_rect(px, py, pw, ph, '#c0e8b1')
    screen:text(px + 20, py + 16, 'SNAKE', {font_size=28, color='#c0e8b1'})
    screen:text(px + 20, py + 50, 'SWIPE TO START', {font_size=16, color='#ffffff'})
  elseif state == 'over' then
    local pw = 260
    local ph = 110
    local px = OFFX + math.floor((GRID_W - pw) / 2)
    local py = OFFY + math.floor((GRID_H - ph) / 2)
    screen:fill_rect(px, py, pw, ph, '#0b120d')
    screen:stroke_rect(px, py, pw, ph, '#e86f6f')
    if win then
      screen:text(px + 30, py + 18, 'YOU WIN', {font_size=28, color='#c0e8b1'})
    else
      screen:text(px + 30, py + 18, 'GAME OVER', {font_size=28, color='#e86f6f'})
    end
    screen:text(px + 30, py + 56, 'SCORE ' .. score, {font_size=18, color='#ffffff'})
    screen:text(px + 30, py + 80, 'TAP TO RESTART', {font_size=14, color='#c0e8b1'})
  end
  screen:present()
end

local function main()
  reset()
  while true do
    local now = system.millis()
    if state == 'playing' and now - lastUpdate >= STEP then
      lastUpdate = now
      update()
    end
    handleTouch()
    if exitRequested then break end
    draw()
    delay.delay_ms(33)
  end
end

local ok, err = xpcall(main, function(e) return tostring(e) end)
screen:close()
if not ok then error(err) end
