local mode,wave,score="ready",1,0
local px,py,dir=6,8,1
local walls,enemies,shots={},{},{}
local moveT,fireT,enemyT,shotT=0,0,0,0
local dirs={{0,-1},{1,0},{0,1},{-1,0}}
math.randomseed(system.millis())
local function key(x,y) return y*14+x end
local function occupied(x,y)
  if x<0 or x>13 or y<0 or y>9 or (x==6 and y==9) or walls[key(x,y)] then return true end
  for _,e in ipairs(enemies) do if e.x==x and e.y==y then return true end end
  return false
end
local function spawn()
  enemies={};shots={};px=6;py=8;dir=1
  local cols={1,3,5,8,10,12}
  for i=1,math.min(2+wave,6) do enemies[#enemies+1]={x=cols[i],y=0,d=3,wait=i*0.17} end
end
local function reset()
  mode="run";wave=1;score=0;walls={};moveT=0;fireT=0;enemyT=0;shotT=0
  for y=2,6,2 do for x=1,12 do if x%3~=0 then walls[key(x,y)]=true end end end
  walls[key(5,9)]=true;walls[key(7,9)]=true;spawn()
end
local function fire(x,y,d,friend)
  if #shots<30 then shots[#shots+1]={x=x,y=y,d=d,friend=friend} end
end
local function update(p,tap,dt)
  if mode~="run" then if (tap and hit(p,136,266,208,50)) or padPressed("a") then reset() end return end
  moveT=moveT-dt;fireT=fireT-dt;enemyT=enemyT+dt;shotT=shotT+dt
  local nd=nil
  if hit(p,12,406,54,56) then nd=4 elseif hit(p,74,406,54,56) then nd=1
  elseif hit(p,136,406,54,56) then nd=3 elseif hit(p,198,406,54,56) then nd=2 end
  if padHeld("up") or padAxis("ly") < -0.35 then nd=1
  elseif padHeld("right") or padAxis("lx") > 0.35 then nd=2
  elseif padHeld("down") or padAxis("ly") > 0.35 then nd=3
  elseif padHeld("left") or padAxis("lx") < -0.35 then nd=4 end
  if nd then
    dir=nd
    if moveT<=0 then
      local nx,ny=px+dirs[dir][1],py+dirs[dir][2]
      if not occupied(nx,ny) then px=nx;py=ny end
      moveT=0.15
    end
  end
  if (hit(p,316,406,150,56) or padHeld("a") or padHeld("r1")) and fireT<=0 then fire(px,py,dir,true);fireT=0.28 end
  for _,e in ipairs(enemies) do
    e.wait=e.wait-dt
    if e.wait<=0 then fire(e.x,e.y,e.d,false);e.wait=math.max(0.65,1.8-wave*0.12) end
  end
  if enemyT>=0.45 then
    enemyT=0
    for _,e in ipairs(enemies) do
      if math.random(4)==1 then e.d=math.random(4) end
      local nx,ny=e.x+dirs[e.d][1],e.y+dirs[e.d][2]
      if not occupied(nx,ny) and not (nx==px and ny==py) then e.x=nx;e.y=ny else e.d=math.random(4) end
    end
  end
  if shotT>=0.08 then
    shotT=0
    for i=#shots,1,-1 do
      local b=shots[i];b.x=b.x+dirs[b.d][1];b.y=b.y+dirs[b.d][2]
      local gone=b.x<0 or b.x>13 or b.y<0 or b.y>9
      if not gone and walls[key(b.x,b.y)] then walls[key(b.x,b.y)]=nil;gone=true
      elseif b.x==6 and b.y==9 then mode="over";gone=true
      elseif b.friend then
        for j=#enemies,1,-1 do local e=enemies[j]
          if b.x==e.x and b.y==e.y then table.remove(enemies,j);score=score+100;gone=true;break end
        end
      elseif b.x==px and b.y==py then mode="over";gone=true end
      if gone then table.remove(shots,i) end
    end
  end
  if #enemies==0 and mode=="run" then wave=wave+1;spawn() end
end
local function tank(x,y,d,c)
  local sx,sy=44+x*28,92+y*28
  rect(sx+2,sy+3,5,23,c);rect(sx+21,sy+3,5,23,c);rect(sx+9,sy+7,10,14,c)
  rect(sx+12+dirs[d][1]*8,sy+12+dirs[d][2]*8,4,4,c)
end
local function draw()
  txt(20,66,"WAVE "..wave.."   SCORE "..score,16,muted)
  round(38,86,404,292,8,panel)
  for k in pairs(walls) do rect(44+(k%14)*28+2,92+math.floor(k/14)*28+2,24,24,"#766447") end
  rect(44+6*28+3,92+9*28+3,22,22,"#edcb80")
  tank(px,py,dir,green)
  for _,e in ipairs(enemies) do tank(e.x,e.y,e.d,orange) end
  for _,b in ipairs(shots) do rect(44+b.x*28+11,92+b.y*28+11,6,6,b.friend and green or orange) end
  button(12,406,54,56,"L");button(74,406,54,56,"U");button(136,406,54,56,"D");button(198,406,54,56,"R")
  button(316,406,150,56,"FIRE",green)
  if mode~="run" then overlay(mode=="ready" and "TANK DEFENSE" or "BASE / TANK LOST","Move, fire, protect your base.") end
end
run("TANK DEFENSE",update,draw)
