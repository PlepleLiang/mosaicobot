/* 09: universal landscape input prototype. No game, network or device execution. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id), dialog=get('game-mode'), root=get('gp-controller');
  const names={up:'↑',down:'↓',left:'←',right:'→',a:'A',b:'B',x:'X',y:'Y',l1:'L1',l2:'L2',r1:'R1',r2:'R2',l3:'L3',r3:'R3',start:'START',select:'SELECT'};
  const spoken={up:'十字键 上',down:'十字键 下',left:'十字键 左',right:'十字键 右'};
  const keyboard={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',KeyW:'up',KeyS:'down',KeyA:'left',KeyD:'right',KeyJ:'a',KeyK:'b',KeyU:'x',KeyI:'y'};
  let status='ready', origin=null, timer=null, returnStatus='menu', sessionValid=false;
  const held=new Map(), pulseTimers=new Set();
  const axes={left:{x:0,y:0,pointer:null,keys:new Set()},right:{x:0,y:0,pointer:null,keys:new Set()}};
  const portrait=()=>matchMedia('(max-width:760px) and (orientation:portrait), (max-width:639px)').matches;
  const enabled=()=>dialog.open&&sessionValid&&status==='ready'&&!portrait();
  const key=k=>'<button class="gp-key" data-key="'+k+'" aria-label="'+(spoken[k]||names[k])+'" aria-pressed="false" '+(!enabled()?'disabled':'')+'>'+names[k]+'</button>';
  const btn=(action,label,primary=false)=>'<button data-gp-action="'+action+'" class="'+(primary?'gp-primary':'')+'">'+label+'</button>';
  function stick(side,label,click){return '<div class="gp-stick-group '+side+'"><div class="gp-stick" data-stick="'+side+'" tabindex="'+(enabled()?'0':'-1')+'" role="group" aria-disabled="'+!enabled()+'" aria-label="'+label+'，拖动控制；聚焦后方向键控制，空格回中"><span class="gp-stick-knob">'+label+'</span></div>'+key(click).replace('class="gp-key"','class="gp-key gp-click"')+'</div>';}
  function render(){
    clearInput();
    const connected=!['offline','scanning','found','lost','reboot'].includes(status);
    root.dataset.status=status;
    root.innerHTML='<header class="gp-top"><span class="gp-device"><i></i>我的 Mosaico · '+(connected?'已连接（演示）':'待连接')+'</span><div class="gp-brand">MOSAICO / GAMEPAD<small>横屏通用手柄 · 交互提案</small></div><button id="gp-menu">'+icon('grid')+'手柄菜单</button></header>'+ 
      '<div class="gp-shoulders"><div>'+key('l2')+key('l1')+'</div><span>看向 Mosaico，在设备上选游戏</span><div>'+key('r1')+key('r2')+'</div></div>'+ 
      '<div class="gp-controls">'+stick('left','LS','l3')+'<div class="gp-dpad" role="group" aria-label="十字方向键">'+['up','left','right','down'].map(key).join('')+'<span class="gp-dpad-center" aria-hidden="true">D-PAD</span></div><span class="gp-center-mark" aria-hidden="true">'+icon('game')+'</span><div class="gp-face" role="group" aria-label="标准动作键">'+['y','x','b','a'].map(key).join('')+'</div>'+stick('right','RS','r3')+'<div class="gp-meta"><div>'+key('select')+key('start')+'</div><small>按键含义由游戏决定 · 不等于系统暂停</small></div></div>';
    get('gp-menu').onclick=()=>openState('menu');
    bindControls();
    if(status!=='ready')renderCover();
    get('gp-observer-title').textContent=status==='ready'?'控制通道就绪 · 游戏状态未读取':'手机输入已释放 · 不代表游戏暂停';
    update();
  }
  function renderCover(){
    const content={
      menu:['CONTROLLER / MENU','控制在这里，游戏在那里。','打开菜单只释放按键与摇杆，不发送 Start，也不默认暂停游戏。',btn('resume','继续控制',true)+btn('help','按键说明')+btn('exit','退出手柄')],
      help:['STANDARD INPUT','一套输入，不绑定玩法。','ABXY、Start、Select 都是原始游戏按键。L1 / R1 对应经典 L / R；LS / RS 为双轴摇杆，L3 / R3 是独立按下。',btn('menu','返回手柄菜单',true)],
      exit:['LEAVE CONTROLLER','退出手机手柄？','会释放全部输入并断开控制会话，恢复 App 原方向。游戏可能继续运行，请先在设备游戏菜单中暂停或保存；不会停止、删除或重新部署游戏。',btn('close','确认退出',true)+btn('return','留在手柄')],
      offline:['CONNECTION','先找到你的 Mosaico。','同一局域网下自动扫描，也可手动重扫或输入地址。网页不扫描真实网络；设备和游戏未就绪前，按键保持禁用。',btn('scan','手动重扫 · 演示',true)+btn('address','手动地址')+btn('exit','退出手柄')],
      scanning:['DISCOVERY','正在查找设备…','原生版沿用 App 的网络变化扫描与历史设备连接。当前只播放扫描过程，不访问局域网。','<button disabled>扫描中 · 演示</button>'+btn('offline','取消扫描')],
      found:['DEVICE FOUND','发现一台 Mosaico','正在自动检查手柄与游戏是否可用，无需设备端确认。','<button disabled>检查中 · 演示</button>'+btn('exit','退出手柄')],
      address:['MANUAL CONNECTION','连接指定地址','实际版本复用现有连接设置。本页不接收真实设备地址，点击下面继续预览自动能力检查。',btn('reconnect','模拟地址校验通过',true)+btn('offline','返回')],
      waiting_game:['WAITING FOR GAME','等待游戏运行。','请先运行支持手柄的 Lua 游戏。游戏可用后自动接入，无需编号或设备确认；不会自动启动或部署作品。',btn('game-started','模拟游戏已运行',true)+btn('exit','返回选择游戏')],
      unsupported:['CAPABILITY CHECK','这台固件还不认识手机手柄。','需要接入通用输入协议，不是能联网就能遥控。原有游戏和实体手柄不受影响；这里不会升级或替换固件。',btn('exit','返回应用',true)],
      busy:['CONTROL OWNER','另一台手机正在控制。','自动等待通道释放，不抢占已有控制者。原生 App 定时重查，释放后自动接入。',btn('reconnect','模拟占用已释放',true)+btn('exit','退出手柄')],
      lost:['CONNECTION LOST','连接断开，按键已释放。','手机已停止发送。设备端需超时归零，是否暂停由运行环境决定；目前无法确认游戏状态。',btn('reconnect','重新连接 · 演示',true)+btn('exit','退出手柄')],
      background:['INPUT SUSPENDED','欢迎回来，先松开双手。','退后台时已清空手机输入。恢复控制前重新核对会话，不续播旧按键，也不把游戏当作已暂停。',btn('reconnect','核对控制会话',true)+btn('exit','退出手柄')],
      reconnecting:['CHECK SESSION','正在核对设备与控制会话…','演示自动检查通道和游戏、同步全零状态，收到回执后启用手柄；无需授权。','<button disabled>核对中 · 演示</button>'],
      reboot:['SESSION EXPIRED','设备重启，旧会话已失效。','不重发旧按键，不自动启动上一次游戏。自动重新检查，游戏未运行则等待；无需重新授权。',btn('reconnect','重新检查',true)+btn('exit','返回应用')],
      orientation:['LANDSCAPE ONLY','保持横屏，再继续。','方向变化已将输入归零。转回横屏后确认恢复，不把竖屏页面缩成小手柄。',btn('resume','恢复控制',true)+btn('exit','退出手柄')]
    }[status];
    const layer=document.createElement('div');layer.className='gp-cover';
    layer.innerHTML='<section class="gp-card" role="dialog" aria-modal="true" aria-labelledby="gp-card-title"><span class="gp-eyebrow">'+content[0]+'</span><h3 id="gp-card-title">'+content[1]+'</h3><p>'+content[2]+'</p>'+(status==='help'?'<div class="gp-help"><span>十字键：数字方向<br>摇杆：方向 + 推动幅度</span><span>ABXY：游戏自行定义<br>Start / Select：原样发送</span><span>按住、组合、多指可用<br>松手、失焦立即归零</span><span>需要兼容运行环境<br>不保证所有旧游戏免适配</span></div>':'')+'<div class="gp-card-actions">'+content[3]+'</div></section>';
    [...root.children].forEach(c=>c.inert=true);root.append(layer);
    layer.querySelectorAll('[data-gp-action]').forEach(b=>b.onclick=()=>act(b.dataset.gpAction));
    layer.querySelector('button:not(:disabled)')?.focus({preventScroll:true});
  }
  function openState(next){
    clearTimeout(timer);
    if(next==='exit'&&status!=='exit')returnStatus=status==='ready'?'menu':status;
    if(['offline','scanning','found','address','waiting_game','unsupported','busy','lost','reboot','background','reconnecting'].includes(next))sessionValid=false;
    if(next==='ready')sessionValid=true;
    status=next;render();
    if(next==='found')timer=setTimeout(()=>act('reconnect'),400);
  }
  function act(action){
    if(action==='close'){close();return;}
    if(action==='return'){openState(returnStatus);return;}
    if(action==='resume'){if(!sessionValid){openState('offline');return;}status='ready';render();get('gp-menu').focus({preventScroll:true});return;}
    if(action==='scan'){openState('scanning');timer=setTimeout(()=>openState('found'),650);return;}
    if(action==='reconnect'||action==='game-started'){openState('reconnecting');timer=setTimeout(()=>openState('ready'),600);return;}
    openState(action);
  }
  function enter(){
    origin=document.activeElement;status=state.connected?'reconnecting':'scanning';sessionValid=false;returnStatus='menu';
    dialog.showModal();document.body.style.overflow='hidden';render();
    if(status==='scanning')timer=setTimeout(()=>openState('offline'),650);
    else timer=setTimeout(()=>openState('ready'),600);
    if(portrait())get('gp-portrait-exit').focus({preventScroll:true});else if(status==='ready')get('gp-menu').focus({preventScroll:true});
  }
  function close(){clearTimeout(timer);clearInput();dialog.close();document.body.style.overflow='';origin?.focus({preventScroll:true});}
  function clearInput(){held.clear();pulseTimers.forEach(clearTimeout);pulseTimers.clear();Object.values(axes).forEach(a=>{a.x=a.y=0;a.pointer=null;a.keys.clear();});update();}
  function press(source,k){if(!enabled()||held.has(source))return;held.set(source,k);update();}
  function release(source){held.delete(source);update();}
  function update(){
    const keys=new Set(held.values());
    root.querySelectorAll('[data-key]').forEach(b=>b.setAttribute('aria-pressed',String(keys.has(b.dataset.key))));
    root.querySelectorAll('[data-stick]').forEach(s=>{const a=axes[s.dataset.stick];s.dataset.x=a.x.toFixed(2);s.dataset.y=a.y.toFixed(2);s.querySelector('.gp-stick-knob').style.transform='translate('+a.x*28+'px,'+a.y*28+'px)';});
    const fmt=n=>(Math.abs(n)<.005?0:n).toFixed(2);
    get('gp-observer').innerHTML='LX '+fmt(axes.left.x)+' · LY '+fmt(axes.left.y)+' / RX '+fmt(axes.right.x)+' · RY '+fmt(axes.right.y)+'<br>BUTTONS / '+(keys.size?[...keys].map(k=>names[k]).join(' + '):'全部释放');
  }
  function bindControls(){
    root.querySelectorAll('[data-key]').forEach(b=>{
      b.onpointerdown=e=>{if(!enabled()||e.button!==0)return;e.preventDefault();b.setPointerCapture(e.pointerId);press('p'+e.pointerId,b.dataset.key);};
      ['pointerup','pointercancel','lostpointercapture'].forEach(t=>b.addEventListener(t,e=>release('p'+e.pointerId)));
      b.oncontextmenu=e=>e.preventDefault();
      b.onclick=e=>{if(e.detail!==0||!enabled())return;const id='assistive-'+b.dataset.key;press(id,b.dataset.key);const t=setTimeout(()=>{release(id);pulseTimers.delete(t);},120);pulseTimers.add(t);};
    });
    root.querySelectorAll('[data-stick]').forEach(s=>{
      const a=axes[s.dataset.stick];
      const move=e=>{if(!enabled()||a.pointer!==e.pointerId)return;const r=s.getBoundingClientRect(),radius=r.width*.36;let x=(e.clientX-r.left-r.width/2)/radius,y=(e.clientY-r.top-r.height/2)/radius;const length=Math.hypot(x,y);if(length<.08)x=y=0;else if(length>1){x/=length;y/=length;}a.x=x;a.y=y;update();};
      s.onpointerdown=e=>{if(!enabled()||e.button!==0||a.pointer!==null)return;e.preventDefault();a.keys.clear();a.pointer=e.pointerId;s.setPointerCapture(e.pointerId);move(e);};
      s.onpointermove=move;
      const end=e=>{if(a.pointer!==e.pointerId)return;a.x=a.y=0;a.pointer=null;update();};
      ['pointerup','pointercancel','lostpointercapture'].forEach(t=>s.addEventListener(t,end));
      s.oncontextmenu=e=>e.preventDefault();
      s.onblur=()=>{a.keys.clear();if(a.pointer===null){a.x=a.y=0;update();}};
    });
  }
  function axisKeys(a){let x=Number(a.keys.has('ArrowRight'))-Number(a.keys.has('ArrowLeft')),y=Number(a.keys.has('ArrowDown'))-Number(a.keys.has('ArrowUp'));const n=Math.max(1,Math.hypot(x,y));a.x=x/n;a.y=y/n;update();}
  dialog.addEventListener('keydown',e=>{
    if(!enabled()||!root.contains(document.activeElement))return;
    const s=document.activeElement.closest('[data-stick]');
    if(s&&['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code)){
      e.preventDefault();const a=axes[s.dataset.stick];if(a.pointer!==null)return;if(e.code==='Space'){a.keys.clear();}else a.keys.add(e.code);axisKeys(a);return;
    }
    if(keyboard[e.code]){e.preventDefault();if(!e.repeat)press('k'+e.code,keyboard[e.code]);}
  });
  document.addEventListener('keyup',e=>{release('k'+e.code);Object.values(axes).forEach(a=>{if(a.keys.delete(e.code))axisKeys(a);});});
  dialog.addEventListener('cancel',e=>{e.preventDefault();if(portrait())close();else if(status==='exit')act('return');else openState('exit');});
  dialog.addEventListener('focusout',e=>{if(root.contains(e.target)&&!root.contains(e.relatedTarget)){clearInput();}});
  const suspend=()=>{if(dialog.open&&status==='ready')openState('background');};
  window.addEventListener('blur',suspend);
  document.addEventListener('visibilitychange',()=>{if(document.hidden)suspend();});
  matchMedia('(max-width:760px) and (orientation:portrait), (max-width:639px)').addEventListener('change',e=>{
    if(!dialog.open)return;clearInput();
    if(e.matches){if(status==='ready')status='orientation';render();get('gp-portrait-exit').focus({preventScroll:true});}
    else {render();if(status==='ready')get('gp-menu').focus({preventScroll:true});}
  });
  get('gp-scenario').onchange=e=>{
    const next=e.target.value;openState(next==='offline'?'scanning':next);
    if(next==='offline')timer=setTimeout(()=>openState('offline'),650);
  };
  get('gp-close-review').onclick=()=>{if(status==='ready')openState('exit');else close();};
  get('gp-portrait-exit').onclick=close;
  get('review-game').onclick=enter;get('browse-games').onclick=enter;
  if(new URLSearchParams(location.search).get('view')==='game')enter();
})();
