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
    constructor(canvas, change) {
      this.canvas = canvas; this.ctx = canvas.getContext('2d'); this.change = change;
      this.inputs = new Set(); this.phase = 'idle'; this.work = { scene:'expression', color:'#ff6b35', speed:2, minutes:5, text:'今天也有光' };
      this.eraser = false; this.paintColor = '#ff6b35'; this.load(this.work);
      this.last = 0; this.frame = this.frame.bind(this); requestAnimationFrame(this.frame);
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
    draw() {
      if(!this.ctx) return;
      const c=this.ctx, scene=this.work.scene; this.rect(0,0,480,480,'#111713');
      if(scene==='snake') {
        for(let n=0;n<20;n++){this.rect(n*24,0,1,480,'#1b251c');this.rect(0,n*24,480,1,'#1b251c');}
        this.snake.forEach((p,i)=>this.rect(p[0]*24+2,p[1]*24+2,20,20,i===0?'#eff8e8':this.work.color));
        this.rect(this.food[0]*24+4,this.food[1]*24+4,16,16,'#ff6b35');
      } else if(['tank','flight','racer'].includes(scene)) {
        if(scene==='racer') {
          this.rect(55,0,3,480,'#64735a');this.rect(420,0,3,480,'#64735a');
          for(let y=-40;y<480;y+=70){this.rect(178,y+this.age*100%70,3,35,'#64735a');this.rect(298,y+this.age*100%70,3,35,'#64735a');}
        } else for(let i=0;i<20;i++) this.rect((i*113)%480,(i*67+this.age*30)%480,2,2,'#4a5b43');
        const pattern=scene==='tank'?['00100','11111','10101','11111','10001']:scene==='flight'?['00100','00100','11111','01110','01010']:['01110','11011','11111','11011','01110'];
        this.pixel(pattern,this.player.x-25,this.player.y-25,10);
        this.enemies.forEach(e=>this.pixel(['0110','1111','1001','1111'],e.x-20,e.y-20,10,'#e47451'));
        this.bullets.forEach(b=>this.rect(b.x-3,b.y,6,15,'#e5f0d6'));
        if(scene==='racer') this.text(this.boost?'BOOST ON':'CRUISE',240,450,15,this.work.color);
      } else if(scene==='lightboard') {
        for(const [position,color] of this.board){const [x,y]=position.split(',').map(Number);this.rect(x*30,y*30,30,30,color);}
        for(let n=0;n<16;n++){this.rect(n*30,0,1,480,'#293325');this.rect(0,n*30,480,1,'#293325');}
        c.strokeStyle='#fff9e7';c.lineWidth=2;c.strokeRect(this.cursor[0]*30+2,this.cursor[1]*30+2,26,26);
      } else if(scene==='focus') {
        c.strokeStyle='#283228';c.lineWidth=10;c.beginPath();c.arc(240,228,135,0,Math.PI*2);c.stroke();
        c.strokeStyle=this.work.color;c.beginPath();c.arc(240,228,135,-Math.PI/2,-Math.PI/2+Math.PI*2*this.remaining/(this.work.minutes*60));c.stroke();
        const time=Math.ceil(this.remaining);this.text(`${String(Math.floor(time/60)).padStart(2,'0')}:${String(time%60).padStart(2,'0')}`,240,245,62);
        this.text(this.remaining?'给自己一点专注':'这一刻，完成了',240,405,22,this.work.color);
      } else if(scene==='signature') {
        c.strokeStyle=this.work.color;c.lineWidth=5;c.setLineDash(this.borderStyle===1?[15,12]:[]);c.strokeRect(35+this.borderStyle*8,65,410-this.borderStyle*16,330);c.setLineDash([]);
        const chars=[...this.work.text];const lines=[];for(let i=0;i<chars.length;i+=8) lines.push(chars.slice(i,i+8).join(''));
        lines.forEach((line,i)=>this.text(line,240,225+i*45-(lines.length-1)*22,32,this.work.color));this.text('M / MY LITTLE STATEMENT',240,355,13,'#819476');
      } else {
        const pet=scene==='pixelpet';
        if(pet) this.pixel(['11000011','11100111','11111111','10111101','11111111','11011011','01111110','01000010'],120,110,30);
        else {
          this.rect(95,135,45,this.mood===2?16:75);this.rect(340,135,45,75);
          if(this.mood===1){this.rect(205,265,70,75);}else{this.rect(140,290,200,22);this.rect(118,266,22,24);this.rect(340,266,22,24);}
        }
        if(pet){this.text(this.petMessage,240,385,22);this.text(`饱腹 ${Math.round(this.pet.food)} · 开心 ${Math.round(this.pet.joy)} · 精力 ${Math.round(this.pet.energy)}`,240,425,16,this.work.color);}
        else this.text(['今天也有光','哇，新的可能','给你一个 wink'][this.mood],240,400,24,this.work.color);
      }
      if(games.has(scene)) this.text(`SCORE ${this.score}`,20,32,17,'#d4e7c5','left');
      if(['paused','ended','exited'].includes(this.phase)) {
        this.rect(0,165,480,140,'#111713ed');this.text({paused:'已暂停',ended:scene==='focus'?'专注完成':'这一局，结束了',exited:'已退出'}[this.phase],240,225,35);
        this.text(this.phase==='paused'?'点击开始继续':this.phase==='exited'?'点击开始重新体验':'点击重试，再来一次',240,268,18,'#c0ccb6');
      }
    }
  }
  window.MosaicoScenes = { catalog, Screen, games };
})();
