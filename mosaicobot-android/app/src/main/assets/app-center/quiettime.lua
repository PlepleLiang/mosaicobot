local minutes,remaining,started=25,1500,false
local presets={15,25,45}
local function update(p,tap,dt)
  if started then remaining=math.max(0,remaining-dt) end
  if tap then
    for i,n in ipairs(presets) do
      if not started and hit(p,24+(i-1)*152,338,128,50) then minutes=n;remaining=n*60 end
    end
    if hit(p,24,414,208,52) then
      if remaining<=0 then remaining=minutes*60 end
      started=true
    elseif hit(p,248,414,208,52) then remaining=minutes*60;started=false end
  end
end
local function draw()
  local left=math.ceil(remaining)
  local label=string.format("%02d:%02d",math.floor(left/60),left%60)
  local progress=clamp(1-remaining/(minutes*60),0,1)
  screen:stroke_circle(240,194,108,"#46594c")
  for i=0,59 do
    local a=i*math.pi/30-math.pi/2
    local x,y=240+math.cos(a)*106,194+math.sin(a)*106
    rect(x-2,y-2,4,4,i/60<=progress and green or "#46594c")
  end
  txt(151,166,label,54,ink)
  txt(145,234,remaining<=0 and "TIME FOR A BREAK" or "ONE THING AT A TIME",16,green)
  for i,n in ipairs(presets) do button(24+(i-1)*152,338,128,50,n.." MIN",minutes==n and green or nil) end
  button(24,414,208,52,remaining<=0 and "AGAIN" or started and "IN PROGRESS" or "START",green)
  button(248,414,208,52,"RESET")
end
run("A MOMENT OFFLINE",update,draw)
