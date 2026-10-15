'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const { catalog, Screen, games } = window.MosaicoScenes;
  const scenes = new Map(catalog.map(item=>[item.id,item]));
  const colors = ['#ff6b35','#c0e8b1','#8ecaff','#f6a9c7'];
  const state = { form:'back', mount:'bag', connection:'disconnected', creation:'empty', draft:null, works:[], storage:true, busy:false };
  const storageKey = 'mosaicobot.showcase.works.v1';
  let serial = 0, connectionTimer = 0, confirmation = null, restoreFocus = null;
  const isWork = w => w && scenes.has(w.scene) && colors.includes(w.color) && [1,2,3].includes(w.speed) && Number.isInteger(w.minutes) && w.minutes>=1 && w.minutes<=60 && typeof w.text==='string' && [...w.text].length<=24 && Number.isInteger(w.version) && w.version>0 && w.version<=9999 && typeof w.id==='string' && /^[a-z0-9-]{1,80}$/.test(w.id) && typeof w.savedAt==='string' && !Number.isNaN(Date.parse(w.savedAt));
  function storageNotice(message) { $('storage-status').textContent=message; }
  try {
    const stored=localStorage.getItem(storageKey);
    if(stored) { const parsed=JSON.parse(stored); if(!Array.isArray(parsed)||parsed.length>50||!parsed.every(isWork)) throw Error('invalid'); state.works=parsed.map(w=>({...w})); }
    localStorage.setItem('mosaicobot.showcase.probe','1'); localStorage.removeItem('mosaicobot.showcase.probe');
    storageNotice('版本保存在此浏览器；不上传云端。最多保留 50 个版本。');
  } catch { state.storage=false; storageNotice('本地存储不可用或数据无效。本次会话仍可保存和部署，关闭页面后不保留。'); }
  function persist() {
    if(!state.storage) return;
    try { localStorage.setItem(storageKey,JSON.stringify(state.works)); }
    catch { state.storage=false; storageNotice('本地保存失败（可能空间不足）。版本已保留在本次会话，关闭页面后不保留。'); }
  }
  function feedback(text, phase=state.creation) {
    state.creation=phase; $('creation-status').textContent=text; $('workshop').dataset.creation=phase;
    const stage={generating:0,generated:0,modified:0,saved:1,deployed:3}[phase] ?? -1;
    document.querySelectorAll('[data-step]').forEach((node,i)=>{node.dataset.active=String(i<=stage);});
  }
  function sceneChanged(info) {
    const scene=scenes.get(info.scene); $('scene-title').textContent=scene.name; $('scene-help').textContent=scene.help;
    const label={idle:'待开始',running:'运行中',paused:'已暂停',ended:'已结束',exited:'已退出'}[info.phase];
    const detail=games.has(info.scene)?` · 得分 ${info.score}`:info.scene==='focus'?` · 剩余 ${Math.ceil(info.remaining)} 秒`:info.scene==='pixelpet'?` · ${info.petMessage} · 饱腹 ${info.pet.food} / 开心 ${info.pet.joy} / 精力 ${info.pet.energy}`:info.scene==='expression'?` · ${['开心','惊讶','眨眼'][info.mood]}`:'';
    $('scene-status').textContent=`${label}${detail} · 本地模拟`;
    $('device-stage').dataset.scene=info.scene; $('device-stage').dataset.phase=info.phase;
    $('mosaico-screen').setAttribute('aria-label',`${scene.name}，${label}${detail}，本地模拟方屏`);
    $('scene-start').disabled=info.phase==='running'; $('scene-start').textContent=info.phase==='paused'?'继续':'开始';
    $('scene-pause').disabled=info.phase!=='running'; $('tool-controls').hidden=info.scene!=='lightboard';
    document.querySelectorAll('[data-scene]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.scene===info.scene)));
  }
  const screen = new Screen($('mosaico-screen'),sceneChanged);
  function defaultWork(scene) { return {scene,color:colors[0],speed:2,minutes:5,text:'今天也有光'}; }
  function showScene(id, start=false) { screen.load(defaultWork(id)); if(start) screen.start(); $('mosaico-screen').focus({preventScroll:true}); $('exhibit').scrollIntoView({behavior:'auto',block:'start'}); }
  const forms = {
    back:['贴近日常，也表达自己。','手机背面的独立方屏。表情、签名、光点与专注，让背面也有自己的语言。'],
    game:['手机负责控制，方屏负责游戏。','横屏手机是控制器，Mosaico 是并列游戏屏。操作同一份游戏状态，不是手机投屏。'],
    pendant:['挂在身边，带一点自己的世界。','手绳连接手机或背包的挂点。像素宠、光点画板或专注时刻，随你一起出发。']
  };
  document.querySelectorAll('[data-form]').forEach(button=>{ if(button.tagName!=='BUTTON') return; button.addEventListener('click',()=>{
    state.form=button.dataset.form; screen.clearInput(); $('device-stage').dataset.form=state.form;
    document.querySelectorAll('button[data-form]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
    $('form-title').textContent=forms[state.form][0]; $('form-description').textContent=forms[state.form][1]; $('mount-choice').hidden=state.form!=='pendant';
  }); });
  document.querySelectorAll('button[data-mount]').forEach(button=>button.addEventListener('click',()=>{
    state.mount=button.dataset.mount; $('device-stage').dataset.mount=state.mount;
    document.querySelectorAll('button[data-mount]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
  }));
  catalog.forEach((scene,i)=>{
    const button=document.createElement('button'); button.className='scene-card'; button.dataset.scene=scene.id; button.setAttribute('aria-pressed',String(scene.id==='expression'));
    const icon=document.createElement('span'); icon.className='scene-icon'; icon.setAttribute('aria-hidden','true');
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'); svg.setAttribute('viewBox','0 0 48 48');
    const path=document.createElementNS(svg.namespaceURI,'path'); path.setAttribute('d',scene.icon);svg.append(path);icon.append(svg);
    const description=document.createElement('span'),number=document.createElement('span'),name=document.createElement('strong'),summary=document.createElement('span');
    number.className='scene-number';number.textContent=`${String(i+1).padStart(2,'0')} / ${scene.id.toUpperCase()}`;
    name.textContent=scene.name;summary.className='scene-summary';summary.textContent=scene.summary;description.append(number,name,summary);button.append(icon,description);
    button.addEventListener('click',()=>showScene(scene.id));$('scene-library').append(button);
  });
  $('quick-play').addEventListener('click',()=>{document.querySelector('button[data-form="game"]').click();showScene('snake',true);});
  $('scene-start').addEventListener('click',()=>screen.start()); $('scene-pause').addEventListener('click',()=>screen.pause());
  $('scene-retry').addEventListener('click',()=>screen.retry()); $('scene-exit').addEventListener('click',()=>screen.exit());
  const heldPointers=new Map(), heldKeys=new Map();
  function clearInputs() { heldPointers.clear(); heldKeys.clear(); screen.clearInput(); document.querySelectorAll('[data-input]').forEach(b=>b.removeAttribute('data-held')); }
  function releasePointer(e) {
    const key=heldPointers.get(e.pointerId); heldPointers.delete(e.pointerId);
    if(key && ![...heldPointers.values()].includes(key) && ![...heldKeys.values()].includes(key)) screen.input(key,false);
    e.currentTarget?.removeAttribute('data-held');
  }
  document.querySelectorAll('[data-input]').forEach(button=>{
    button.addEventListener('pointerdown',e=>{e.preventDefault();button.setPointerCapture(e.pointerId);heldPointers.set(e.pointerId,button.dataset.input);button.dataset.held='true';screen.input(button.dataset.input,true);});
    ['pointerup','pointercancel','lostpointercapture'].forEach(event=>button.addEventListener(event,releasePointer));
    button.addEventListener('click',e=>{if(e.detail===0){screen.input(button.dataset.input,true);screen.input(button.dataset.input,false);}});
  });
  const keyMap={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',w:'up',a:'left',s:'down',d:'right',' ':'action'};
  function gameContext() { const node=document.activeElement; return node===$('mosaico-screen') || node?.closest('.screen-console,.phone-controller'); }
  document.addEventListener('keydown',e=>{
    if(!gameContext() || $('confirm-dialog').open || e.ctrlKey || e.altKey || e.metaKey || e.target.matches('input,select,textarea')) return;
    const key=keyMap[e.key] || keyMap[e.key.toLowerCase()];
    if(key){e.preventDefault();if(!e.repeat){heldKeys.set(e.code,key);screen.input(key,true);}}
    if(e.key.toLowerCase()==='p' && !e.repeat){e.preventDefault();screen.phase==='running'?screen.pause():screen.phase==='paused'&&screen.start();clearInputs();}
  });
  document.addEventListener('keyup',e=>{const key=heldKeys.get(e.code);heldKeys.delete(e.code);if(key && ![...heldKeys.values()].includes(key) && ![...heldPointers.values()].includes(key)) screen.input(key,false);});
  window.addEventListener('blur',()=>{clearInputs();screen.pause();});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){clearInputs();screen.pause();}});
  $('paint-color').addEventListener('input',e=>{screen.paintColor=e.target.value;screen.eraser=false;$('paint-eraser').setAttribute('aria-pressed','false');});
  $('paint-eraser').addEventListener('click',()=>{screen.eraser=!screen.eraser;$('paint-eraser').setAttribute('aria-pressed',String(screen.eraser));});
  function confirmAction(title,description,action) {
    clearInputs();confirmation=action;restoreFocus=document.activeElement;
    $('dialog-title').textContent=title;$('dialog-description').textContent=description;$('confirm-dialog').returnValue='';$('confirm-dialog').showModal();
  }
  $('confirm-dialog').addEventListener('close',()=>{
    const action=confirmation;confirmation=null;if($('confirm-dialog').returnValue==='confirm') action?.();
    if(restoreFocus?.isConnected) restoreFocus.focus({ preventScroll:true });
  });
  $('paint-clear').addEventListener('click',()=>confirmAction('清空光点画板？','当前画面将被清空。此操作只影响本次网页画板。',()=>screen.clearBoard()));
  function connection(next) {
    state.connection=next;$('workshop').dataset.connection=next;
    $('connection-status').textContent={disconnected:'模拟设备未连接',connecting:'正在模拟连接…',connected:'模拟设备已连接',failed:'模拟连接失败，请重试'}[next];
    $('connect-device').textContent=next==='failed'?'重试连接':next==='connected'?'已连接':'模拟连接';
    $('connect-device').disabled=next==='connecting'||next==='connected';$('fail-connection').disabled=next==='connecting';$('disconnect-device').disabled=next!=='connected';
  }
  function connect(fail=false) { clearTimeout(connectionTimer);connection('connecting');connectionTimer=setTimeout(()=>connection(fail?'failed':'connected'),650); }
  $('connect-device').addEventListener('click',()=>connect());$('fail-connection').addEventListener('click',()=>connect(true));
  $('disconnect-device').addEventListener('click',()=>{clearTimeout(connectionTimer);clearInputs();connection('disconnected');feedback('模拟连接已断开。作品保留，方屏继续独立运行；重新连接后可部署。');});
  const aliases=[['snake',/贪食蛇|snake/gi],['tank',/坦克(?:守线)?|tank/gi],['flight',/像素飞行|飞机|飞行|flight/gi],['racer',/口袋赛车|赛车|racer/gi],['pixelpet',/像素宠|pixelpet/gi],['lightboard',/光点画板|像素画板|画板|lightboard/gi],['focus',/专注(?:时刻|计时器)?|focus/gi],['signature',/个性签名|签名|signature/gi],['expression',/像素表情|表情|expression/gi]];
  function parseIntent(input) {
    let rest=input.trim(); const found=[];
    for(const [id,pattern] of aliases) rest=rest.replace(pattern,()=>{found.push(id);return '';});
    if(found.length!==1) return null;
    const work=defaultWork(found[0]);
    const chosenColors=[];for(const [name,color] of [['橙色',colors[0]],['绿色',colors[1]],['蓝色',colors[2]],['粉色',colors[3]]]) rest=rest.replaceAll(name,()=>{chosenColors.push(color);return '';});
    if(chosenColors.length>1)return null;if(chosenColors.length)work.color=chosenColors[0];
    const chosenSpeeds=[];for(const [name,speed] of [['慢速',1],['中速',2],['快速',3]])rest=rest.replaceAll(name,()=>{chosenSpeeds.push(speed);return '';});
    if(chosenSpeeds.length>1 || chosenSpeeds.length&&!games.has(work.scene))return null;if(chosenSpeeds.length)work.speed=chosenSpeeds[0];
    const durations=[];rest=rest.replace(/(\d+)\s*分钟/g,(_,n)=>{durations.push(Number(n));return '';});
    if(durations.length>1 || durations.length&&(work.scene!=='focus'||durations[0]<1||durations[0]>60))return null;if(durations.length)work.minutes=durations[0];
    rest=rest.replace(/做一个|做个|生成一个|生成|制作一个|制作|给我一个|一个|请|的|游戏|计时器|工具|应用|[\s，。！？、,.!?]/g,'');
    return rest?null:work;
  }
  function newDraft(work) {
    const version=Math.max(0,...state.works.filter(w=>w.scene===work.scene).map(w=>w.version))+1;
    if(version>9999){feedback('此模板版本已达到上限。请尝试其他模板。');return;}
    state.draft={...work,id:`work-${Date.now().toString(36)}-${(++serial).toString(36)}`,version,savedAt:''};renderDraft();
  }
  function renderDraft() {
    const work=state.draft;$('draft-panel').hidden=!work;if(!work)return;
    $('draft-title').textContent=scenes.get(work.scene).name;$('draft-version').textContent=`V${String(work.version).padStart(2,'0')}`;
    $('work-color').value=work.color;$('work-speed').value=work.speed;$('work-minutes').value=work.minutes;$('work-text').value=work.text;
    $('work-color').disabled=false;$('work-speed').disabled=!games.has(work.scene);$('work-minutes').disabled=work.scene!=='focus';$('work-text').disabled=work.scene!=='signature';
    const saved=Boolean(work.savedAt);$('save-work').disabled=saved;$('deploy-work').disabled=!saved;$('modify-work').disabled=!saved;
    for(const id of ['work-color','work-speed','work-minutes','work-text'])if(saved)$(id).disabled=true;
  }
  document.querySelectorAll('[data-prompt]').forEach(b=>b.addEventListener('click',()=>{if(state.busy)return;$('intent').value=b.dataset.prompt;$('intent').focus();}));
  $('create-form').addEventListener('submit',async e=>{
    e.preventDefault();if(state.busy)return;
    if(state.draft&&!state.draft.savedAt){feedback('先保存或放弃当前草稿，再生成下一件作品。');return;}
    const work=parseIntent($('intent').value);
    if(!work){feedback('暂不支持这个输入。请使用已列出的九种模板和参数；这里不会生成任意应用。','unsupported');return;}
    state.busy=true;$('generate').disabled=true;$('intent').disabled=true;$('draft-panel').hidden=true;
    feedback('1 / 3 · 正在匹配本地模板…','generating');
    await new Promise(resolve=>setTimeout(resolve,350));feedback('2 / 3 · 配置支持的颜色与参数…','generating');
    await new Promise(resolve=>setTimeout(resolve,350));feedback('3 / 3 · 草稿已生成，等待保存。尚未部署。','generated');
    newDraft(work);state.busy=false;$('generate').disabled=false;$('intent').disabled=false;
  });
  function readParameters() {
    const work=state.draft;if(!work||work.savedAt)return false;
    const minutes=Number($('work-minutes').value),text=$('work-text').value.trim();
    if(work.scene==='focus'&&(!Number.isInteger(minutes)||minutes<1||minutes>60)){feedback('专注时长仅支持 1–60 整数分钟。');$('work-minutes').focus();return false;}
    if(work.scene==='signature'&&(!text||[...text].length>24)){feedback('请填写 1–24 字的签名。');$('work-text').focus();return false;}
    const color=$('work-color').value,speed=Number($('work-speed').value);
    if(!colors.includes(color)||![1,2,3].includes(speed)){feedback('参数不支持，请选择列表中的颜色和速度。');return false;}
    work.color=color;if(games.has(work.scene))work.speed=speed;if(work.scene==='focus')work.minutes=minutes;if(work.scene==='signature')work.text=text;return true;
  }
  $('save-work').addEventListener('click',()=>{
    if(state.busy||!readParameters())return;
    if(state.works.length>=50){feedback('已达到 50 个版本的本地容量。本页不会自动删除旧版本；仍可运行已保存作品。');return;}
    state.draft.savedAt=new Date().toISOString();state.works.unshift({...state.draft});persist();renderWorks();renderDraft();
    feedback(`${scenes.get(state.draft.scene).name} V${state.draft.version} 已保存${state.storage?'到此浏览器':'到本次会话'}。等待你确认部署。`,'saved');
  });
  $('modify-work').addEventListener('click',()=>{
    if(state.busy||!state.draft?.savedAt)return;const work={...state.draft};newDraft(work);
    feedback('已创建新的可编辑草稿。修改参数后保存，再次确认部署；正在运行的旧版本不受影响。','modified');
  });
  $('discard-work').addEventListener('click',()=>{
    if(state.busy)return;confirmAction('放弃当前草稿？','未保存的修改不会保留。已保存版本和正在运行的作品不受影响。',()=>{state.draft=null;renderDraft();feedback('草稿已放弃。可以开始新的创作。','empty');});
  });
  function requestDeploy(work) {
    if(state.busy||!work?.savedAt)return;
    if(state.connection!=='connected'){feedback('尚未模拟连接。请先点击「模拟连接」或重试，再确认部署；版本已保留。');$('connect-device').focus();return;}
    const snapshot={...work};
    confirmAction('确认部署这个版本？',`${scenes.get(work.scene).name} · V${work.version}。这会替换方屏当前的模拟场景，不执行 Lua，不连接真实硬件。`,()=>{
      if(state.connection!=='connected'){feedback('连接已断开，部署未执行。请重新连接后重试。');return;}
      state.draft=snapshot;renderDraft();screen.load(snapshot);screen.start();clearInputs();
      feedback(`${scenes.get(work.scene).name} V${work.version} 已确认部署，方屏运行中（本地模拟回执）。可在作品列表再次打开或修改新版本。`,'deployed');
      $('exhibit').scrollIntoView({behavior:'auto',block:'start'});
      restoreFocus=$('mosaico-screen');
    });
  }
  $('deploy-work').addEventListener('click',()=>requestDeploy(state.draft));
  function renderWorks() {
    const list=$('works-list');list.replaceChildren();$('works-count').textContent=state.works.length;
    if(!state.works.length){const empty=document.createElement('p');empty.className='empty-works';empty.textContent='这里将留下你做过的版本。从一个小想法开始。';list.append(empty);return;}
    state.works.forEach(work=>{
      const row=document.createElement('div');row.className='work-item';row.dataset.work=work.id;
      const info=document.createElement('div'),title=document.createElement('strong'),detail=document.createElement('p'),open=document.createElement('button');
      title.textContent=`${scenes.get(work.scene).name} · V${work.version}`;detail.textContent=`${new Date(work.savedAt).toLocaleDateString('zh-CN')} · ${state.storage?'浏览器保存':'会话保存'}`;
      open.textContent='打开版本';open.setAttribute('aria-label',`打开${scenes.get(work.scene).name}版本 ${work.version}`);
      open.addEventListener('click',()=>{
        if(state.busy){feedback('正在生成，请等待当前任务完成。');return;}
        if(state.draft&&!state.draft.savedAt){feedback('先保存或放弃当前草稿，再打开其他版本。');return;}
        state.draft={...work};renderDraft();feedback(`已打开 V${work.version}。确认部署才会替换方屏，也可以修改为新版本。`,'saved');
        $('draft-panel').scrollIntoView({behavior:'auto',block:'center'});$('deploy-work').focus({preventScroll:true});
      });info.append(title,detail);row.append(info,open);list.append(row);
    });
  }
  renderWorks();connection('disconnected');$('tool-controls').hidden=true;
})();
