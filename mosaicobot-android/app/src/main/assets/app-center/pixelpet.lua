local food,joy,rest,age,heart=70,75,false,0,0
local function update(p,tap,dt)
  age=age+dt;heart=math.max(0,heart-dt)
  food=clamp(food-dt*0.25,0,100)
  joy=clamp(joy+(rest and dt*0.6 or -dt*0.12),0,100)
  if tap then
    if hit(p,112,134,256,200) then joy=clamp(joy+12,0,100);heart=1.5
    elseif hit(p,16,414,140,52) then food=clamp(food+18,0,100);heart=1.5
    elseif hit(p,170,414,140,52) then rest=not rest
    elseif hit(p,324,414,140,52) then food=70;joy=75;rest=false;age=0;heart=0 end
  end
end
local function draw()
  txt(24,76,rest and "RESTING..." or "HELLO, LITTLE HUMAN",18,muted)
  local y=156+(rest and 0 or math.floor(math.sin(age*2)*5))
  local c="#d8bde9"
  rect(150,y+12,180,124,c);rect(164,y-30,36,50,c);rect(278,y-30,36,50,c)
  rect(165,y+128,40,20,c);rect(275,y+128,40,20,c)
  if rest or math.floor(age*2)%9==0 then
    rect(180,y+53,22,5,bg);rect(278,y+53,22,5,bg)
  else rect(184,y+47,12,18,bg);rect(280,y+47,12,18,bg) end
  rect(169,y+77,24,7,orange);rect(288,y+77,24,7,orange)
  if food<20 then rect(226,y+90,28,5,bg);rect(222,y+94,5,8,bg);rect(254,y+94,5,8,bg)
  else rect(227,y+93,28,6,bg) end
  if heart>0 then
    rect(368,132,12,12,orange);rect(388,132,12,12,orange);rect(364,144,40,12,orange);rect(374,156,20,12,orange)
  end
  txt(25,339,"FOOD",16,muted);rect(100,342,120,10,panel);rect(100,342,math.max(1,food*1.2),10,green)
  txt(249,339,"JOY",16,muted);rect(308,342,144,10,panel);rect(308,342,math.max(1,joy*1.44),10,c)
  txt(89,380,"Tap your pal to say hello",18,muted)
  button(16,414,140,52,"FEED",green);button(170,414,140,52,rest and "WAKE" or "REST");button(324,414,140,52,"RESET")
end
run("POCKET PAL",update,draw)
