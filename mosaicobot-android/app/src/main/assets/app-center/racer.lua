local mode,lane,distance="ready",2,0
local traffic={}
local timer,road=0,0
local stickLane=0
math.randomseed(system.millis())
local function reset() mode="run";lane=2;distance=0;traffic={};timer=0.8;road=0 end
local function update(p,tap,dt)
  if mode~="run" then if (tap and hit(p,136,266,208,50)) or padPressed("a") then reset() end return end
  if tap and p then lane=clamp(lane+(p.x<240 and -1 or 1),1,3) end
  for _,event in ipairs(padState.events) do
    local left,right=event.pressed.left,event.pressed.right
    if left~=right then lane=clamp(lane+(left and -1 or 1),1,3) end
  end
  local axis=padAxis("lx")
  local side=axis < -0.5 and -1 or (axis > 0.5 and 1 or 0)
  if side~=0 and side~=stickLane then lane=clamp(lane+side,1,3) end
  stickLane=side
  local speed=130+math.min(distance*0.10,150)
  distance=distance+speed*dt/10;road=(road+speed*dt)%56;timer=timer-dt
  if timer<=0 then traffic[#traffic+1]={lane=math.random(3),y=62};timer=1.0 end
  for i=#traffic,1,-1 do local c=traffic[i];c.y=c.y+speed*dt
    if c.lane==lane and c.y+48>324 and c.y<378 then mode="over" end
    if c.y>410 then table.remove(traffic,i) end
  end
end
local function car(x,y,c)
  round(x-21,y,42,54,7,c);rect(x-14,y+12,28,17,"#27363d")
  rect(x-16,y+44,8,4,orange);rect(x+8,y+44,8,4,orange)
end
local function draw()
  rect(102,60,276,350,"#293136")
  rect(96,60,4,350,orange);rect(380,60,4,350,orange)
  for i=0,6 do local y=60+(i*56+road)%336
    rect(193,y,3,22,"#8b9288");rect(285,y,3,22,"#8b9288")
  end
  for _,c in ipairs(traffic) do car(56+c.lane*92,c.y,"#a6c4ce") end
  car(56+lane*92,324,orange)
  rect(0,60,480,30,bg);txt(20,65,"DISTANCE "..math.floor(distance).." m",18,green)
  button(16,420,216,48,"< LEFT");button(248,420,216,48,"RIGHT >")
  if mode~="run" then overlay(mode=="ready" and "MIDNIGHT RUN" or "RACE OVER","Tap left / right to change lane.") end
end
run("MIDNIGHT RUN",update,draw)
