local mode,score,lives="ready",0,3
local px,py=240,334
local enemies,shots,hostile={},{},{}
local spawnT,fireT,hitT,age=0,0,0,0
math.randomseed(system.millis())
local function reset()
  mode="run";score=0;lives=3;px=240;py=334
  enemies={};shots={};hostile={};spawnT=0;fireT=0;hitT=0;age=0
end
local function damage()
  if hitT<=0 then lives=lives-1;hitT=1.3;if lives<=0 then mode="over" end end
end
local function update(p,tap,dt)
  if mode~="run" then if (tap and hit(p,136,266,208,50)) or padPressed("a") then reset() end return end
  age=age+dt;spawnT=spawnT-dt;fireT=fireT-dt;hitT=hitT-dt
  if p then px=clamp(p.x,32,448);py=clamp(p.y,150,388) end
  local dx=padAxis("lx")+(padHeld("right") and 1 or 0)-(padHeld("left") and 1 or 0)
  local dy=padAxis("ly")+(padHeld("down") and 1 or 0)-(padHeld("up") and 1 or 0)
  local length=math.max(1,math.sqrt(dx*dx+dy*dy))
  px=clamp(px+dx/length*260*dt,32,448);py=clamp(py+dy/length*260*dt,150,388)
  if fireT<=0 and #shots<24 then shots[#shots+1]={x=px,y=py-24};fireT=0.2 end
  if spawnT<=0 and #enemies<10 then
    enemies[#enemies+1]={x=math.random(36,444),y=66,fire=0.5};spawnT=math.max(0.32,0.9-age*0.004)
  end
  for i=#shots,1,-1 do local b=shots[i];b.y=b.y-310*dt
    local gone=b.y<62
    for j=#enemies,1,-1 do local e=enemies[j]
      if math.abs(b.x-e.x)<20 and math.abs(b.y-e.y)<20 then table.remove(enemies,j);score=score+10;gone=true;break end
    end
    if gone then table.remove(shots,i) end
  end
  for i=#enemies,1,-1 do local e=enemies[i];e.y=e.y+(48+math.min(age,80))*dt;e.fire=e.fire-dt
    if e.fire<=0 and #hostile<24 then hostile[#hostile+1]={x=e.x,y=e.y+15};e.fire=1.4 end
    local contact=math.abs(e.x-px)<28 and math.abs(e.y-py)<28
    if contact then damage() end
    if e.y>410 or contact then table.remove(enemies,i) end
  end
  for i=#hostile,1,-1 do local b=hostile[i];b.y=b.y+158*dt
    local contact=math.abs(b.x-px)<18 and math.abs(b.y-py)<23
    if contact then damage() end
    if b.y>414 or contact then table.remove(hostile,i) end
  end
end
local function draw()
  for i=1,28 do rect((i*73)%456+10,70+(i*41+age*28)%336,2,3,"#36515b") end
  txt(20,66,"SCORE "..score.."  SHIELD "..lives,16,muted)
  for _,e in ipairs(enemies) do rect(e.x-16,e.y-6,32,12,orange);rect(e.x-5,e.y-13,10,25,orange) end
  for _,b in ipairs(shots) do rect(b.x-2,b.y,4,10,green) end
  for _,b in ipairs(hostile) do rect(b.x-3,b.y,6,9,orange) end
  if hitT<=0 or math.floor(hitT*12)%2==0 then
    rect(px-5,py-22,10,36,"#accfe7");rect(px-21,py-2,42,10,"#accfe7");rect(px-3,py+17,6,10,orange)
  end
  txt(79,444,"DRAG TO MOVE / AUTO FIRE",18,muted)
  if mode~="run" then overlay(mode=="ready" and "ORBIT STRIKER" or "MISSION OVER","Drag, dodge, keep flying.") end
end
run("ORBIT STRIKER",update,draw)
