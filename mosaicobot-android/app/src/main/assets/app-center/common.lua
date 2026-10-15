-- MosaicoBot offline presets v1. Shared source is inlined into every standalone Work.
local display = require("display")
local delay = require("delay")
local system = require("system")
local screen
local bg, panel, ink, muted = "#10191a", "#1f2c2b", "#edf1e5", "#a7b9ac"
local green, orange = "#bdd9a0", "#efa77b"
local function rect(x,y,w,h,c) screen:fill_rect(math.floor(x),math.floor(y),math.floor(w),math.floor(h),c) end
local function round(x,y,w,h,r,c) screen:fill_round_rect(math.floor(x),math.floor(y),math.floor(w),math.floor(h),r,c) end
local function txt(x,y,s,size,c) screen:text(math.floor(x),math.floor(y),s,{font_size=size or 18,color=c or ink}) end
local function hit(p,x,y,w,h) return p and p.x>=x and p.x<x+w and p.y>=y and p.y<y+h end
local function clamp(n,a,b) return math.max(a,math.min(b,n)) end
local function button(x,y,w,h,label,c)
  round(x,y,w,h,8,c or panel)
  local tw,th=screen:measure_text(label,{font_size=18})
  txt(x+(w-tw)/2,y+(h-th)/2,label,18,c and bg or ink)
end
local function overlay(title,subtitle)
  round(40,148,400,188,16,"#243632")
  txt(65,170,title,28,green)
  txt(65,214,subtitle,16,ink)
  button(136,266,208,50,"START / RETRY",green)
end
local function run(title,update,draw)
  local ok,err=pcall(function()
    screen=display.open()
    local info=screen:info()
    if info.width~=480 or info.height~=480 then error("480x480 screen required") end
    local alive,paused,down=true,false,false
    local last=system.millis()
    while alive do
      local now=system.millis()
      local dt=clamp(now-last,0,100)/1000
      last=now
      local p=nil
      if info.touch_available then p=screen:touch().points[1] end
      local tap=p~=nil and not down
      down=p~=nil
      local header=p and p.y<58
      if tap and hit(p,390,6,84,48) then alive=false
      elseif tap and hit(p,300,6,84,48) then paused=not paused end
      if alive then
        if header then p=nil;tap=false end
        if not paused then update(p,tap,dt,now) end
        screen:begin({clear=bg})
        draw()
        rect(0,0,480,58,bg)
        txt(16,21,title,19,green)
        button(300,6,84,48,paused and "PLAY" or "PAUSE")
        button(390,6,84,48,"EXIT")
        if paused then
          round(80,174,320,112,14,panel)
          txt(176,193,"PAUSED",26,green)
          txt(115,238,"Tap PLAY to continue",18,ink)
        end
        screen:present()
      end
      delay.delay_ms(34)
    end
  end)
  if screen then screen:close() end
  if not ok then error(err) end
end
