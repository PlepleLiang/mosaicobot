local colors={"#bdd9a0","#efa77b","#accfe7","#d8bde9"}
local cells,pen,confirm={},1,false
local function update(p,tap)
  if confirm then
    if tap and hit(p,82,256,146,54) then cells={};confirm=false
    elseif tap and hit(p,252,256,146,54) then confirm=false end
    return
  end
  if hit(p,80,72,320,320) then
    local x,y=math.floor((p.x-80)/20),math.floor((p.y-72)/20)
    cells[y*16+x]=pen>0 and pen or nil
  end
  if tap then
    for i=1,4 do if hit(p,12+(i-1)*55,416,50,52) then pen=i end end
    if hit(p,244,416,104,52) then pen=0 elseif hit(p,360,416,108,52) then confirm=true end
  end
end
local function draw()
  for y=0,15 do for x=0,15 do
    local value=cells[y*16+x]
    rect(80+x*20,72+y*20,19,19,value and colors[value] or panel)
  end end
  for i=1,4 do
    button(12+(i-1)*55,416,50,52,pen==i and "+" or "",colors[i])
  end
  button(244,416,104,52,pen==0 and "ERASING" or "ERASE");button(360,416,108,52,"CLEAR")
  if confirm then
    round(40,168,400,164,14,"#32463e")
    txt(92,188,"CLEAR YOUR DRAWING?",22,ink)
    txt(100,225,"This cannot be undone.",18,muted)
    button(82,256,146,54,"YES",orange);button(252,256,146,54,"KEEP")
  end
end
run("PIXEL STUDIO",update,draw)
