'use strict';
(() => {
  const catalog = [
    { id:'snake', name:'贪食蛇', summary:'转个弯，吃掉下一颗光点。', help:'方向键或触屏转向，吃到光点加分；撞墙或自己结束。', icon:'M8 12h20v8H16v8h20v8H8M36 8v4m-2-2h4' },
    { id:'tank', name:'坦克守线', summary:'移动、开火，守住底线。', help:'左右移动，动作 / 空格发射；击中坦克加分，敌人越过底线或碰撞结束。', icon:'M12 20h24v14H12zM24 20V8m-16 28h32M16 26h16' },
    { id:'flight', name:'像素飞行', summary:'躲开障碍，继续向前。', help:'四向移动躲避来袭障碍，动作 / 空格射击；碰撞结束，生存与命中加分。', icon:'M24 6v34m0-24L8 30h32L24 16M16 40l8-6 8 6' },
    { id:'racer', name:'口袋赛车', summary:'三条车道，一路向前。', help:'左右切换车道躲避车辆；动作切换加速，加速时障碍更快。碰撞结束。', icon:'M18 10h12v28H18zM18 18h12M10 6v8m0 8v8m0 8v4M38 6v8m0 8v8m0 8v4' },
    { id:'pixelpet', name:'像素宠', summary:'喂食、玩耍，回应一个小伙伴。', help:'动作喂食，向上玩耍，向下休息；状态只在本页运行时变化，无后台养成。', icon:'M12 18v-8h8v8h8v-8h8v26H12zM18 26h2m8 0h2M20 32h8' },
    { id:'lightboard', name:'光点画板', summary:'把手边的念头，画成像素。', help:'开始后点击或拖动方屏作画。方向键移动光标，动作绘制；可换色、擦除和确认清空。', icon:'M10 10h28v28H10zM18 10v28m10-28v28M10 18h28M10 28h28' },
    { id:'focus', name:'专注时刻', summary:'把接下来的几分钟，留给自己。', help:'开始真实网页倒计时；暂停保留剩余时间。分钟数可在创作台修改，隐藏页面自动暂停。', icon:'M16 8h16M24 8v6M34 16l4-4M24 24v8M10 28a14 14 0 1 0 28 0 14 14 0 1 0-28 0' },
    { id:'signature', name:'个性签名', summary:'今天的你，写在背面。', help:'开始展示签名，动作切换边框样式。文字与颜色可在创作台生成新版本。', icon:'M8 34l6-18 6 12 6-18 6 18 8-10M8 40h32' },
    { id:'expression', name:'像素表情', summary:'不说话，也能表达。', help:'开始后用动作或左右按钮切换开心、惊讶与眨眼表情。', icon:'M8 8h32v32H8zM16 18v6m16-6v6M16 30h16' }
  ];
  const games = new Set(['snake','tank','flight','racer']);
  class Screen {
    constructor(canvas, change, animate=true) {
      this.canvas = canvas; this.ctx = canvas.getContext('2d'); this.change = change;
      this.inputs = new Set(); this.phase = 'idle'; this.work = { scene:'expression', color:'#ff6b35', speed:2, minutes:5, text:'今天也有光' };
      this.motion = matchMedia('(prefers-reduced-motion: reduce)');
      this.eraser = false; this.paintColor = '#ff6b35'; this.load(this.work);
      this.last = 0; this.frame = this.frame.bind(this); if(animate) requestAnimationFrame(this.frame);
      canvas.addEventListener('pointerdown', e => {
        if (this.work.scene !== 'lightboard' || this.phase !== 'running') return;
        canvas.focus({preventScroll:true}); canvas.setPointerCapture(e.pointerId);
        this.painting = e.pointerId; this.paintAt(e);
      });
      canvas.addEventListener('pointermove', e => { if (this.painting === e.pointerId) this.paintAt(e); });
      for (const event of ['pointerup','pointercancel','lostpointercapture']) canvas.addEventListener(event, () => { this.painting = null; });
    }
    load(work) {
      this.clearInput(); this.work = { ...work }; this.phase = 'idle'; this.score = 0; this.age = 0; this.tick = 0; this.spawn = 0;
      this.snake = [[7,10],[6,10],[5,10]]; this.direction = 'right'; this.nextDirection = 'right'; this.food = [13,10];
      this.player = {x:240,y:390}; this.enemies = []; this.bullets = []; this.lane = 1; this.boost = false;
      this.mood = 0; this.pet = {food:65,joy:65,energy:65}; this.petMessage = '你好，新朋友';
      this.remaining = work.minutes * 60; this.board = new Map(); this.cursor = [7,7]; this.borderStyle = 0;
      this.notify(); this.draw();
    }
    notify() { this.change?.({scene:this.work.scene,phase:this.phase,score:this.score,remaining:this.remaining,pet:{...this.pet},petMessage:this.petMessage,mood:this.mood}); }
    start() { if (this.phase === 'ended' || this.phase === 'exited') this.load(this.work); this.phase = 'running'; this.last = 0; this.notify(); this.draw(); }
    pause() { if (this.phase === 'running') { this.phase = 'paused'; this.clearInput(); this.notify(); this.draw(); } }
    retry() { this.load(this.work); this.start(); }
    exit() { this.phase = 'exited'; this.clearInput(); this.notify(); this.draw(); }
    end() { this.phase = 'ended'; this.clearInput(); this.notify(); this.draw(); }
    clearInput() { this.inputs.clear(); this.painting = null; }
    input(key, down) {
      if (!down) { this.inputs.delete(key); return; }
      if (this.phase !== 'running' || this.inputs.has(key)) return;
      this.inputs.add(key);
      const scene = this.work.scene;
      if (['tank','flight'].includes(scene) && key==='action') { this.tick=0; this.bullets.push({x:this.player.x,y:this.player.y-20}); }
      if (scene === 'snake' && ['up','down','left','right'].includes(key)) {
        const opposite = {up:'down',down:'up',left:'right',right:'left'};
        if (key !== opposite[this.direction]) this.nextDirection = key;
      }
      if (scene === 'racer') { if (key === 'left') this.lane = Math.max(0,this.lane-1); if (key === 'right') this.lane = Math.min(2,this.lane+1); if (key === 'action') this.boost = !this.boost; }
      if (scene === 'expression' && ['action','left','right'].includes(key)) this.mood = (this.mood + (key === 'left' ? 2 : 1)) % 3;
      if (scene === 'signature' && key === 'action') this.borderStyle = (this.borderStyle+1)%3;
      if (scene === 'pixelpet') {
        if (key === 'action') { this.pet.food = Math.min(100,this.pet.food+15); this.petMessage = '吃饱了，谢谢你'; }
        if (key === 'up') { this.pet.joy = Math.min(100,this.pet.joy+15); this.pet.energy = Math.max(0,this.pet.energy-5); this.petMessage = '一起玩，真开心'; }
        if (key === 'down') { this.pet.energy = Math.min(100,this.pet.energy+15); this.petMessage = '休息一下，充满电'; }
      }
      if (scene === 'lightboard') {
        const delta = {left:[-1,0],right:[1,0],up:[0,-1],down:[0,1]}[key];
        if (delta) this.cursor = this.cursor.map((v,i)=>Math.max(0,Math.min(15,v+delta[i])));
        if (key === 'action') this.paintCell(...this.cursor);
      }
      this.notify(); this.draw();
    }
    paintAt(e) {
      const box = this.canvas.getBoundingClientRect();
      const x = Math.floor((e.clientX-box.left)/box.width*16), y = Math.floor((e.clientY-box.top)/box.height*16);
      if (x>=0 && x<16 && y>=0 && y<16) { this.cursor=[x,y]; this.paintCell(x,y); this.draw(); }
    }
    paintCell(x,y) { const key = `${x},${y}`; if (this.eraser) this.board.delete(key); else this.board.set(key,this.paintColor); }
    clearBoard() { this.board.clear(); this.draw(); }
    frame(time) {
      const elapsed = this.last ? (time-this.last)/1000 : 0;
      const dt = this.work.scene === 'focus' ? elapsed : Math.min(elapsed,.1); this.last=time;
      if (this.phase === 'running') { this.update(dt); this.draw(); }
      requestAnimationFrame(this.frame);
    }
    update(dt) {
      this.age += dt; this.tick += dt; this.spawn += dt;
      const scene = this.work.scene, speed = this.work.speed;
      if (scene === 'focus') {
        const before = Math.ceil(this.remaining); this.remaining = Math.max(0,this.remaining-dt);
        if (Math.ceil(this.remaining)!==before) this.notify();
        if (!this.remaining) this.end(); return;
      }
      if (scene === 'pixelpet') {
        if (this.tick > 6) { this.tick=0; this.pet.food=Math.max(0,this.pet.food-1); this.pet.energy=Math.max(0,this.pet.energy-1); this.notify(); } return;
      }
      if (!games.has(scene)) return;
      if (scene === 'snake') {
        if (this.tick < .28/speed) return; this.tick=0; this.direction=this.nextDirection;
        const movement={up:[0,-1],down:[0,1],left:[-1,0],right:[1,0]}[this.direction];
        const head=[this.snake[0][0]+movement[0],this.snake[0][1]+movement[1]];
        const ate=head[0]===this.food[0] && head[1]===this.food[1];
        const body=ate ? this.snake : this.snake.slice(0,-1);
        if (head.some(v=>v<0 || v>=20) || body.some(p=>p[0]===head[0] && p[1]===head[1])) { this.end(); return; }
        this.snake.unshift(head);
        if (ate) {
          this.score+=10; const free=[];
          for(let x=0;x<20;x++) for(let y=0;y<20;y++) if(!this.snake.some(p=>p[0]===x && p[1]===y)) free.push([x,y]);
          if (!free.length) { this.end(); return; } this.food=free[Math.floor(Math.random()*free.length)]; this.notify();
        } else this.snake.pop(); return;
      }
      if (scene !== 'racer') {
        const amount=dt*185; if(this.inputs.has('left')) this.player.x-=amount; if(this.inputs.has('right')) this.player.x+=amount;
        if(scene==='flight') { if(this.inputs.has('up')) this.player.y-=amount; if(this.inputs.has('down')) this.player.y+=amount; }
        this.player.x=Math.max(28,Math.min(452,this.player.x)); this.player.y=Math.max(65,Math.min(438,this.player.y));
        if(this.inputs.has('action') && this.tick>.2) { this.tick=0; this.bullets.push({x:this.player.x,y:this.player.y-20}); }
      } else this.player.x=120+this.lane*120;
      const boost=scene==='racer' && this.boost ? 1.6 : 1;
      if(this.spawn>Math.max(.45,1.5-speed*.2)/boost) {
        this.spawn=0; this.enemies.push({x:scene==='racer'?120+Math.floor(Math.random()*3)*120:35+Math.random()*410,y:25});
      }
      for(const enemy of this.enemies) enemy.y+=dt*(65+speed*28)*boost;
      for(const bullet of this.bullets) bullet.y-=dt*310;
      for(const bullet of this.bullets) for(const enemy of this.enemies) if(!enemy.hit && !bullet.hit && Math.abs(bullet.x-enemy.x)<23 && Math.abs(bullet.y-enemy.y)<25) { enemy.hit=true; bullet.hit=true; this.score+=10; this.notify(); }
      const oldScore=this.score; this.score=Math.max(this.score,Math.floor(this.age)*(scene==='racer'?2:1)); if(this.score!==oldScore) this.notify();
      if(this.enemies.some(e=>!e.hit && Math.abs(e.x-this.player.x)<32 && Math.abs(e.y-this.player.y)<36)) { this.end(); return; }
      if(scene==='tank' && this.enemies.some(e=>!e.hit && e.y>448)) { this.end(); return; }
      this.enemies=this.enemies.filter(e=>!e.hit && e.y<510); this.bullets=this.bullets.filter(b=>!b.hit && b.y>0);
    }
    rect(x,y,w,h,color=this.work.color) { this.ctx.fillStyle=color; this.ctx.fillRect(x,y,w,h); }
    text(text,x,y,size=22,color='#e6eedf',align='center') { const c=this.ctx; c.fillStyle=color; c.textAlign=align; c.font=`600 ${size}px "Microsoft YaHei", sans-serif`; c.fillText(String(text),x,y); }
    pixel(pattern,x,y,unit,color=this.work.color) { pattern.forEach((row,j)=>[...row].forEach((bit,i)=>{if(bit==='1') this.rect(x+i*unit,y+j*unit,unit,unit,color);})); }
    glow(color, blur, draw) { const c=this.ctx;c.save();c.shadowColor=color;c.shadowBlur=blur;draw();c.restore(); }
    ring(x,y,r,color,width=1,start=0,end=Math.PI*2) { const c=this.ctx;c.strokeStyle=color;c.lineWidth=width;c.beginPath();c.arc(x,y,r,start,end);c.stroke(); }
    label(value,x,y,size=12,color='#92a6a2',align='left') { const c=this.ctx;c.fillStyle=color;c.textAlign=align;c.font=`${size}px Consolas, monospace`;c.fillText(value,x,y); }
    atmosphere(scene,t) {
      const c=this.ctx,colors=scene==='focus'?['#20322c','#0d1616']:scene==='pixelpet'?['#253047','#10131f']:['#18273b','#090e19'];
      const bg=c.createRadialGradient(240,200,20,240,240,350);bg.addColorStop(0,colors[0]);bg.addColorStop(1,colors[1]);c.fillStyle=bg;c.fillRect(0,0,480,480);
      for(let i=0;i<65;i++){const x=(i*137+19)%480,y=(i*83+31+t*(scene==='flight'?28:3))%480;this.rect(x,y,i%7===0?2:1,i%7===0?2:1,i%3===0?'#7aaba96b':'#8190bf40');}
      if(['snake','tank','flight','racer','signature','lightboard'].includes(scene)){
        for(let n=0;n<=480;n+=24){this.rect(n,0,1,480,'#89b4d010');this.rect(0,n,480,1,'#89b4d010');}
      }
    }
    draw() {
      if(!this.ctx) return;
      const c=this.ctx, scene=this.work.scene,accent=this.work.color,t=this.motion.matches?0:this.age;
      this.atmosphere(scene,t);
      if(scene==='snake') {
        for(let n=0;n<20;n++){this.rect(n*24,0,1,480,'#1b251c');this.rect(0,n*24,480,1,'#1b251c');}
        this.snake.forEach((p,i)=>this.glow(accent,12,()=>{
          this.rect(p[0]*24+2,p[1]*24+2,20,20,i===0?'#eff8e8':accent);
          this.rect(p[0]*24+4,p[1]*24+4,16,3,'#ffffff48');
        }));
        const head=this.snake[0];this.rect(head[0]*24+14,head[1]*24+6,3,3,'#172b24');this.rect(head[0]*24+14,head[1]*24+15,3,3,'#172b24');
        this.glow('#ff6b35',22,()=>this.rect(this.food[0]*24+4,this.food[1]*24+4,16,16,'#ff976a'));
        this.ring(this.food[0]*24+12,this.food[1]*24+12,17+Math.sin(t*3)*2,'#ff976a55');
      } else if(['tank','flight','racer'].includes(scene)) {
        if(scene==='racer') {
          this.rect(55,0,370,480,'#111d2ac9');
          this.glow(accent,14,()=>{this.rect(55,0,3,480,accent);this.rect(420,0,3,480,accent);});
          for(let y=-70;y<480;y+=70){this.rect(178,y+t*100%70,2,35,'#b6cfcc70');this.rect(298,y+t*100%70,2,35,'#b6cfcc70');}
          for(let i=0;i<12;i++){this.rect(25,(i*51+t*150)%480,2,20,'#ff976a80');this.rect(451,(i*51+t*150)%480,2,20,'#ff976a80');}
        } else if(scene==='flight') {
          c.strokeStyle='#5bacd52c';c.lineWidth=1;
          for(let x=-240;x<=720;x+=80){c.beginPath();c.moveTo(240+(x-240)*.22,135);c.lineTo(x,480);c.stroke();}
          for(let i=0;i<9;i++){const y=145+((i*38+t*35)%335);this.rect(0,y,480,1,'#5bacd51f');}
          this.ring(370,120,40,'#83bdff22');this.ring(370,120,48,'#83bdff14');
          this.label('SECTOR / 07',450,65,11,'#8ecaff','right');
        } else {
          for(let y=72;y<480;y+=70)this.rect(0,y,480,2,'#75917b28');
          for(let i=0;i<7;i++){this.rect((i*127)%480,100+i*46,28,15,'#243c3a');this.rect((i*127)%480+3,103+i*46,22,2,'#4c6b58');}
          this.rect(0,448,480,2,'#ff976a80');this.label('DEFENSE LINE',450,467,11,'#ff976a','right');
        }
        const pattern=scene==='tank'?['00100','00100','11111','10101','11111','10001']:scene==='flight'?['0001000','0001000','0011100','1111111','0111110','0101010']:['01110','11011','11111','11011','01110'];
        if(scene!=='tank'){
          const tail=c.createLinearGradient(0,this.player.y+15,0,this.player.y+95);tail.addColorStop(0,accent+'a0');tail.addColorStop(1,accent+'00');c.fillStyle=tail;c.fillRect(this.player.x-7,this.player.y+18,14,65);
          this.glow('#a9e7ff',18,()=>this.rect(this.player.x-4,this.player.y+22,8,12+Math.sin(t*16)*4,'#e0f5ff'));
        }
        this.glow(accent,18,()=>this.pixel(pattern,this.player.x-(scene==='flight'?28:25),this.player.y-25,scene==='flight'?8:10));
        this.rect(this.player.x-4,this.player.y-12,8,14,'#e5f5ff');
        if(scene==='tank'){this.rect(this.player.x-23,this.player.y+20,8,4,'#91a7a0');this.rect(this.player.x+15,this.player.y+20,8,4,'#91a7a0');}
        this.enemies.forEach(e=>this.glow('#e47451',12,()=>{this.pixel(['0110','1111','1001','1111'],e.x-20,e.y-20,10,'#e47451');this.rect(e.x-4,e.y-12,8,7,'#ffd5b1');}));
        this.bullets.forEach(b=>this.glow('#b9efff',18,()=>{this.rect(b.x-2,b.y,4,18,'#e5faff');this.rect(b.x-1,b.y+18,2,12,'#8ecaff70');}));
        if(scene==='racer') this.label(this.boost?'BOOST / ON':'NIGHT DRIVE / CRUISE',240,450,15,accent,'center');
      } else if(scene==='lightboard') {
        for(const [position,color] of this.board){const [x,y]=position.split(',').map(Number);this.glow(color,12,()=>this.rect(x*30+2,y*30+2,26,26,color));}
        for(let n=0;n<16;n++){this.rect(n*30,0,1,480,'#7ea1a32b');this.rect(0,n*30,480,1,'#7ea1a32b');}
        for(let x=0;x<16;x++)for(let y=0;y<16;y++)this.rect(x*30+14,y*30+14,2,2,'#9fc4c040');
        c.strokeStyle='#fff9e7';c.lineWidth=2;c.strokeRect(this.cursor[0]*30+2,this.cursor[1]*30+2,26,26);
      } else if(scene==='focus') {
        this.label('A LITTLE FOCUS',30,43,12,'#b7c5b0');this.label('MOSAICO',450,43,12,accent,'right');
        this.ring(240,229,165,'#8fba9d18');this.ring(240,229,130,'#8fba9d20');
        for(let i=0;i<60;i++){const a=i*Math.PI/30-Math.PI/2,r=i%5===0?151:155;c.strokeStyle=i%5===0?'#c3d7bd70':'#c3d7bd24';c.lineWidth=1;c.beginPath();c.moveTo(240+Math.cos(a)*r,229+Math.sin(a)*r);c.lineTo(240+Math.cos(a)*160,229+Math.sin(a)*160);c.stroke();}
        const progress=this.remaining/(this.work.minutes*60),angle=-Math.PI/2+Math.PI*2*progress;
        this.ring(240,229,141,'#30483c',3);
        this.glow(accent,15,()=>this.ring(240,229,141,accent,5,-Math.PI/2,angle));
        this.glow(accent,22,()=>this.ring(240+Math.cos(angle)*141,229+Math.sin(angle)*141,5,'#f5ffe9',5));
        this.label('BREATHE / BE HERE',240,177,12,'#a5bba6','center');
        const time=Math.ceil(this.remaining);c.font='300 72px Georgia, serif';c.fillStyle='#f3f4df';c.textAlign='center';c.fillText(`${String(Math.floor(time/60)).padStart(2,'0')}:${String(time%60).padStart(2,'0')}`,240,253);
        this.label(`${this.work.minutes} MIN · ONE SMALL MOMENT`,240,284,11,accent,'center');
        this.text(this.remaining?'给自己一点专注':'这一刻，完成了',240,420,20,'#c9d9c4');
        this.rect(220,443,40,2,accent);
      } else if(scene==='signature') {
        this.label('PERSONAL / EDITION 01',30,40,12,accent);this.label('M / YOU',450,40,12,'#b4c9cb','right');
        this.glow(accent,35,()=>this.ring(240,240,152+Math.sin(t)*5,accent+'70',1));this.ring(240,240,179,'#8ecaff25');
        c.strokeStyle=accent;c.lineWidth=2;c.setLineDash(this.borderStyle===1?[15,12]:[]);c.strokeRect(35+this.borderStyle*8,65,410-this.borderStyle*16,330);c.setLineDash([]);
        for(const [x,y] of [[35,65],[445,65],[35,395],[445,395]]){this.rect(x-4,y-4,8,8,accent);}
        const chars=[...this.work.text],lines=[];for(let i=0;i<chars.length;i+=6)lines.push(chars.slice(i,i+6).join(''));
        lines.forEach((line,i)=>this.glow(accent,15,()=>this.text(line,240+Math.sin(t+i)*3,230+i*49-(lines.length-1)*24,38,accent)));
        this.label('MAKE A LITTLE STATEMENT.',240,366,12,'#c0cdd0','center');this.label('NOT A PHONE. A PERSONAL UNIVERSE.',240,445,10,'#81999c','center');
      } else {
        const pet=scene==='pixelpet';
        this.label(pet?'POCKET COMPANION':'HELLO / LITTLE WORLD',30,40,12,accent);this.label(pet?'M / 08':'M / 09',450,40,12,'#b4c9cb','right');
        this.glow(accent,28,()=>this.ring(240,225,135,accent+'50',2));this.ring(240,225,151,accent+'20');
        if(pet){
          const bob=Math.sin(t*2.5)*4,x=144,y=120+bob;
          c.save();c.translate(240,327);c.scale(1,.2);this.ring(0,0,86,'#8ecaff25',18);c.restore();
          this.glow(accent,20,()=>this.pixel(['11000011','11100111','11111111','11111111','11111111','11111111','01111110','01000010'],x,y,24));
          this.pixel(['10000001','11000011'],x+12,y+12,24,'#ffe4e6');
          this.rect(x+24,y+72,144,48,'#ffefdf');
          const resting=this.petMessage==='休息一下，充满电'||this.pet.energy<25;
          this.rect(x+42,y+79,12,resting?5:20,'#283345');this.rect(x+138,y+79,12,resting?5:20,'#283345');
          this.rect(x+21,y+104,22,10,'#f39bb7');this.rect(x+149,y+104,22,10,'#f39bb7');
          this.rect(x+78,y+113,36,7,'#614357');this.rect(x+90,y+107,12,6,'#614357');
          this.rect(x+66,y+143,60,14,'#ffe4e6');this.rect(x+83,y+146,26,6,'#d889a5');
          for(let i=0;i<6;i++){const px=65+(i*71)%360,py=100+(i*43+t*8)%200;this.glow(accent,8,()=>{this.rect(px,py,9,3,accent+'90');this.rect(px+3,py-3,3,9,accent+'90');});}
          this.text(this.petMessage,240,380,22,'#f9e7e7');
          [['饱腹',this.pet.food],['开心',this.pet.joy],['精力',this.pet.energy]].forEach(([name,value],i)=>{const bx=43+i*137;this.text(`${name} ${Math.round(value)}`,bx+60,414,14,'#bbc4d6');this.rect(bx,429,120,4,'#323d53');this.glow(accent,8,()=>this.rect(bx,429,value*1.2,4,accent));});
        } else {
          this.glow(accent,24,()=>{
            this.rect(115,151,42,this.mood===2?12:65,accent);this.rect(323,151,42,65,accent);
            if(this.mood===1){this.ring(240,282,30,accent,14);}else{this.rect(163,285,154,14,accent);this.rect(148,270,15,15,accent);this.rect(317,270,15,15,accent);}
          });
          this.rect(91,246,37,12,accent+'55');this.rect(352,246,37,12,accent+'55');
          this.glow(accent,12,()=>{this.rect(376,109,16,3,accent);this.rect(382,103,3,16,accent);});
          this.text(['今天也有光','哇，新的可能','给你一个 wink'][this.mood],240,407,24,'#ecede1');this.label(['HAPPY TO SEE YOU','OH! THE POSSIBILITIES','A WINK, JUST FOR YOU'][this.mood],240,437,11,accent,'center');
        }
      }
      if(games.has(scene)){this.rect(0,0,480,45,'#0c1422d9');this.label(`SCORE / ${String(this.score).padStart(3,'0')}`,20,29,15,'#d4e7c5');this.label(scene.toUpperCase(),460,29,12,accent,'right');}
      if(['paused','ended','exited'].includes(this.phase)) {
        this.rect(0,165,480,140,'#111713ed');this.text({paused:'已暂停',ended:scene==='focus'?'专注完成':'这一局，结束了',exited:'已退出'}[this.phase],240,225,35);
        this.text(this.phase==='paused'?'点击开始继续':this.phase==='exited'?'点击开始重新体验':'点击重试，再来一次',240,268,18,'#c0ccb6');
      }
    }
  }
  window.MosaicoScenes = { catalog, Screen, games };
})();
