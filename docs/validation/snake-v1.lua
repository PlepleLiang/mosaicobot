local display = require('display')
local delay = require('delay')
local system = require('system')

local screen = display.open()
local info = screen:info()
local W = info.width
local H = info.height

local STATUS_H = 40
local PLAY_X = 0
local PLAY_Y = STATUS_H
local PLAY_W = W
local PLAY_H = H - STATUS_H
local CELL = 20
local GRID_W = math.floor(PLAY_W / CELL)
local GRID_H = math.floor(PLAY_H / CELL)
local PLAY_W2 = GRID_W * CELL
local PLAY_H2 = GRID_H * CELL
local OFFSET_X = math.floor((PLAY_W - PLAY_W2) / 2)
local OFFSET_Y = PLAY_Y + math.floor((PLAY_H - PLAY_H2) / 2)

local BG = '#111713'
local STATUS_BG = '#0c110e'
local SNAKE_HEAD = '#c0e8b1'
local SNAKE_BODY = '#8fbf85'
local FOOD = '#e88f8f'
local TEXT = '#ffffff'
local BTN = '#3a5a40'
local BTN_TEXT = '#ffffff'
local EXIT_BTN = '#8f3a3a'
local SUB_TEXT = '#a0b8a0'
local OVER_TEXT = '#ff8080'

local DIR_UP = 1
local DIR_RIGHT = 2
local DIR_DOWN = 3
local DIR_LEFT = 4
local DIR_X = {[1]=0,[2]=1,[3]=0,[4]=-1}
local DIR_Y = {[1]=-1,[2]=0,[3]=1,[4]=0}

local STATE_READY = 0
local STATE_PLAY = 1
local STATE_PAUSE = 2
local STATE_OVER = 3

local snake = {}
local dir = DIR_RIGHT
local pendingDir = DIR_RIGHT
local food = nil
local score = 0
local state = STATE_READY
local moveInterval = 130
local lastMove = 0
local prevIds = {}
local running = true

math.randomseed(system.millis())

local function gridToPixel(cx, cy)
  return OFFSET_X + cx * CELL, OFFSET_Y + cy * CELL
end

local function isFree(x, y)
  for i = 1, #snake do
    local s = snake[i]
    if s.x == x and s.y == y then
      return false
    end
  end
  return true
end

local function spawnFood()
  local free = {}
  for y = 0, GRID_H - 1 do
    for x = 0, GRID_W - 1 do
      if isFree(x, y) then
        free[#free + 1] = {x = x, y = y}
      end
    end
  end
  if #free == 0 then
    food = nil
    return false
  end
  local idx = math.random(#free)
  food = free[idx]
  return true
end

local function resetGame()
  snake = {}
  local sx = math.floor(GRID_W / 2)
  local sy = math.floor(GRID_H / 2)
  snake[1] = {x = sx, y = sy}
  snake[2] = {x = sx - 1, y = sy}
  snake[3] = {x = sx - 2, y = sy}
  dir = DIR_RIGHT
  pendingDir = DIR_RIGHT
  score = 0
  state = STATE_READY
  spawnFood()
  lastMove = system.millis()
end

local function trySetDir(nd)
  if nd == dir then return end
  if (nd == DIR_UP and dir == DIR_DOWN) or (nd == DIR_DOWN and dir == DIR_UP) or
     (nd == DIR_LEFT and dir == DIR_RIGHT) or (nd == DIR_RIGHT and dir == DIR_LEFT) then
    return
  end
  pendingDir = nd
end

local function inRect(x, y, rx, ry, rw, rh)
  return x >= rx and x < rx + rw and y >= ry and y < ry + rh
end

local BTN_H = 30
local BTN_Y = 5
local EXIT_W = 70
local EXIT_X = W - EXIT_W - 10
local PAUSE_W = 90
local PAUSE_X = math.floor(W / 2 - PAUSE_W / 2)

local function directionFromPoint(x, y)
  if y < PLAY_Y then return nil end
  local dx = x - math.floor(PLAY_W / 2)
  local dy = y - (PLAY_Y + math.floor(PLAY_H / 2))
  if math.abs(dx) > math.abs(dy) then
    if dx < 0 then return DIR_LEFT else return DIR_RIGHT end
  else
    if dy < 0 then return DIR_UP else return DIR_DOWN end
  end
end

local function handleTouch(x, y)
  if inRect(x, y, EXIT_X, BTN_Y, EXIT_W, BTN_H) then
    running = false
    return
  end
  if state == STATE_PLAY or state == STATE_PAUSE then
    if inRect(x, y, PAUSE_X, BTN_Y, PAUSE_W, BTN_H) then
      if state == STATE_PLAY then
        state = STATE_PAUSE
      else
        state = STATE_PLAY
        lastMove = system.millis()
      end
      return
    end
  end
  if state == STATE_READY then
    state = STATE_PLAY
    lastMove = system.millis()
    local nd = directionFromPoint(x, y)
    if nd then trySetDir(nd) end
  elseif state == STATE_OVER then
    resetGame()
  elseif state == STATE_PLAY then
    local nd = directionFromPoint(x, y)
    if nd then trySetDir(nd) end
  elseif state == STATE_PAUSE then
    if y >= PLAY_Y then
      state = STATE_PLAY
      lastMove = system.millis()
    end
  end
end

local function step()
  dir = pendingDir
  local head = snake[1]
  local nx = head.x + DIR_X[dir]
  local ny = head.y + DIR_Y[dir]
  if nx < 0 or nx >= GRID_W or ny < 0 or ny >= GRID_H then
    state = STATE_OVER
    return
  end
  for i = 1, #snake do
    local s = snake[i]
    if s.x == nx and s.y == ny then
      state = STATE_OVER
      return
    end
  end
  local ate = false
  if food and nx == food.x and ny == food.y then
    ate = true
    score = score + 1
  end
  table.insert(snake, 1, {x = nx, y = ny})
  if not ate then
    table.remove(snake)
  end
  if ate then
    if not spawnFood() then
      state = STATE_OVER
    end
  end
end

local function drawCell(cx, cy, color)
  local px, py = gridToPixel(cx, cy)
  screen:fill_rect(px, py, CELL, CELL, color)
end

local function drawCentered(y, msg, fs, color)
  local w = screen:measure_text(msg, {font_size = fs})
  screen:text(math.floor((W - w) / 2), y, msg, {font_size = fs, color = color})
end

local function draw()
  screen:begin({clear = BG})
  screen:fill_rect(0, 0, W, STATUS_H, STATUS_BG)
  screen:text(10, 8, 'SCORE ' .. score, {font_size = 20, color = TEXT})
  if state == STATE_PLAY or state == STATE_PAUSE then
    local label = 'PAUSE'
    if state == STATE_PAUSE then label = 'RESUME' end
    screen:fill_round_rect(PAUSE_X, BTN_Y, PAUSE_W, BTN_H, 6, BTN)
    local w = screen:measure_text(label, {font_size = 18})
    screen:text(PAUSE_X + math.floor((PAUSE_W - w) / 2), BTN_Y + 5, label, {font_size = 18, color = BTN_TEXT})
  end
  screen:fill_round_rect(EXIT_X, BTN_Y, EXIT_W, BTN_H, 6, EXIT_BTN)
  local ew = screen:measure_text('EXIT', {font_size = 18})
  screen:text(EXIT_X + math.floor((EXIT_W - ew) / 2), BTN_Y + 5, 'EXIT', {font_size = 18, color = BTN_TEXT})

  screen:stroke_rect(OFFSET_X, OFFSET_Y, PLAY_W2, PLAY_H2, '#3a5a40')
  if food then
    drawCell(food.x, food.y, FOOD)
  end
  for i = 1, #snake do
    local c = SNAKE_BODY
    if i == 1 then c = SNAKE_HEAD end
    drawCell(snake[i].x, snake[i].y, c)
  end

  if state == STATE_READY then
    drawCentered(math.floor(H / 2 - 40), 'SNAKE', 36, TEXT)
    drawCentered(math.floor(H / 2 + 10), 'TAP TO START', 20, SUB_TEXT)
  elseif state == STATE_PAUSE then
    drawCentered(math.floor(H / 2 - 20), 'PAUSED', 36, TEXT)
  elseif state == STATE_OVER then
    drawCentered(math.floor(H / 2 - 50), 'GAME OVER', 36, OVER_TEXT)
    drawCentered(math.floor(H / 2 + 0), 'SCORE ' .. score, 24, TEXT)
    drawCentered(math.floor(H / 2 + 40), 'TAP TO RESTART', 18, SUB_TEXT)
  end
  screen:present()
end

local function pollTouches()
  if not info.touch_available then return end
  local points = screen:touch().points
  local currentIds = {}
  for i = 1, #points do
    local p = points[i]
    currentIds[p.id] = true
    if not prevIds[p.id] then
      handleTouch(p.x, p.y)
    end
  end
  prevIds = currentIds
end

local ok, err = xpcall(function()
  resetGame()
  while running do
    pollTouches()
    local now = system.millis()
    if state == STATE_PLAY and now - lastMove >= moveInterval then
      lastMove = now
      step()
    end
    draw()
    delay.delay_ms(33)
  end
end, function(e)
  return tostring(e)
end)

screen:close()
if not ok then
  error(err)
end
