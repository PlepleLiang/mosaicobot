/* App Center design prototype. Local metadata only; no cloud, Lua execution or hardware calls. */
"use strict";
const CENTER_STORE = "mosaicobot.center.v1";
const centerPresets = [
  {id:"seed-tank",type:"tank",name:"坦克大战",english:"TANK / DEFENSE",category:"游戏",icon:"game",color:"#bdd9a0",description:"守住基地，再推进一格。",about:"一块方屏，一场小小的阵地战。穿过砖墙、躲开敌方炮弹，守住身后的基地。",controls:"左侧触屏方向区移动，右侧点击开火；暂停菜单可重开或退出。",features:["可破坏砖墙","逐波来袭","基地守卫"]},
  {id:"seed-flight",type:"flight",name:"飞机大战",english:"ORBIT / STRIKER",category:"游戏",icon:"game",color:"#abcde8",description:"指尖领航，穿越星际弹幕。",about:"把 Mosaico 变成一台口袋街机。拖动战机闪避，自动射击，在密集弹幕间找到自己的航线。",controls:"拖动战机移动，自动发射；点击右上角暂停，结束后重开。",features:["单指操控","自动射击","递进关卡"]},
  {id:"seed-race",type:"racer",name:"午夜赛车",english:"MIDNIGHT / RUN",category:"游戏",icon:"game",color:"#f1b480",description:"三条车道，把夜色甩在身后。",about:"没有复杂的按键，只有速度与反应。切换车道躲开车流，在不断加速的公路上挑战自己的距离。",controls:"点击屏幕左侧或右侧切换车道；暂停区独立，不使用 IMU。",features:["三车道躲避","距离计分","渐进加速"]},
  {id:"seed-pet",type:"pixelpet",name:"口袋像素宠",english:"POCKET / PAL",category:"趣玩",icon:"spark",color:"#dfc5ee",description:"在桌角，养一点小小的快乐。",about:"一只住在方屏里的像素伙伴。点一点喂食，摸一摸互动，让工作间隙多一点回应。",controls:"触摸宠物互动，底部按钮喂食或休息；本轮拟定为单次会话状态，不承诺后台养成。",features:["触摸回应","表情变化","离线陪伴"]},
  {id:"seed-draw",type:"lightboard",name:"光点画板",english:"PIXEL / STUDIO",category:"趣玩",icon:"grid",color:"#efd37f",description:"画一颗心，送给这块小屏幕。",about:"用手指点亮 16 × 16 的像素网格。切换颜色、擦除、清空，给 Mosaico 换一张自己画的表情。",controls:"点击或滑动涂色，底部切换画笔和橡皮；清空二次确认，退出不保存画布。",features:["像素涂鸦","四色画笔","一键展示"]},
  {id:"seed-time",type:"quiettime",name:"一刻专注",english:"A MOMENT / OFFLINE",category:"工具",icon:"clock",color:"#bdcbaa",description:"让时间可见，让消息远一点。",about:"把一段安静的时间放在眼前。15 / 25 / 45 分钟三档倒计时，到点用屏幕提示，不申请声音权限。",controls:"选择时长后开始，点击暂停或继续；长任务也可重置和退出。",features:["三档时长","进度可视","无声提醒"]},
];
const centerState = {filter:"全部",query:"",detail:null,scroll:0,published:[]};
try {
  const entries = JSON.parse(localStorage.getItem(CENTER_STORE) || "[]");
  if (Array.isArray(entries)) centerState.published = entries.filter(a => a && typeof a.id === "string" && typeof a.name === "string" && typeof a.description === "string" && a.work && typeof a.work.id === "string" && Number.isInteger(a.work.version) && a.work.params && Array.isArray(a.work.thread));
} catch { /* Storage is optional in file/private mode. */ }
function saveCenter() {
  try { localStorage.setItem(CENTER_STORE, JSON.stringify(centerState.published)); return true; }
  catch { return false; }
}
function resetAppCenter() {
  centerState.published=[]; centerState.detail=null; centerState.filter="全部"; centerState.query="";
  saveCenter();
}
function centerEntries() { return [...centerPresets, ...centerState.published.filter(a => catalog[a.type])]; }
function centerEntry(id) { return centerEntries().find(a => a.id===id); }
function centerArt(type) {
  const scenes = {
    tank:'<g fill="#344334"><path d="M32 46h70v22H32zm0 24h22v40H32zm158-22h94v22h-94zm72 24h22v42h-22zM32 152h64v18H32zm160 0h92v18h-92z"/><path d="M132 84h22v26h-22zm46 0h22v26h-22z"/></g><g fill="#bdd9a0"><path d="M129 132h10v40h-10zm38 0h10v40h-10zM143 138h20v27h-20zm7-25h6v31h-6z"/></g><g fill="#eea277"><path d="M218 32h7v30h-7zm28 0h7v30h-7zm-17 4h13v23h-13zm4 15h5v25h-5z"/></g><path d="M153 100v-8m0-8v-8" stroke="#e7e9b5" stroke-width="3"/><path d="M155 177h14v12h-14z" fill="#f4d793"/>',
    flight:'<g fill="#3b5361"><rect x="38" y="30" width="3" height="3"/><rect x="90" y="85" width="3" height="3"/><rect x="275" y="97" width="3" height="3"/><rect x="250" y="26" width="3" height="3"/><rect x="55" y="171" width="3" height="3"/></g><path d="m160 116-24 42h17v15h14v-15h17z" fill="#acd3ed"/><path d="m158 176 2 11 2-11" fill="#f5b57d"/><g fill="#de916e"><path d="m85 42 15 15-15 8-15-8zm145 25 15 15-15 8-15-8zm-62-36 15 15-15 8-15-8z"/></g><g stroke="#b4e4cf" stroke-width="3"><path d="M157 101v-9m6-6v-9M86 87v8m145 12v8m-62-45v8"/></g>',
    racer:'<path d="M80 0h160v200H80z" fill="#293036"/><path d="M80 0v200M240 0v200" stroke="#8d7760" stroke-width="3"/><path d="M133 0v200M187 0v200" stroke="#72736b" stroke-width="2" stroke-dasharray="17 17"/><rect x="146" y="129" width="28" height="49" rx="6" fill="#eeac76"/><path d="M150 143h20v13h-20z" fill="#323a3e"/><rect x="204" y="36" width="26" height="45" rx="5" fill="#a8becb"/><path d="M208 50h18v12h-18z" fill="#323a3e"/><g fill="#556657"><path d="M38 10h17v38H38zm230 69h17v35h-17zM37 140h18v41H37z"/></g>',
    pixelpet:'<path d="M61 171h200" stroke="#494654" stroke-width="2"/><g fill="#d8bce9"><path d="M121 76V51h21v24h38V51h21v26h12v62h-12v19h-79v-19h-12V76z"/></g><path d="M133 98h8v11h-8zm49 0h8v11h-8zm-31 27h21v6h-21z" fill="#333142"/><path d="M128 119h13m42 0h13" stroke="#e89596" stroke-width="5"/><path d="M240 43h6v-6h12v6h6v12l-12 12-12-12z" fill="#eea28c"/><g fill="#5e506b"><path d="M64 67h5v5h-5zm180 68h4v4h-4z"/></g>',
    lightboard:'<g stroke="#344138" stroke-width="1"><path d="M80 28h160M80 44h160M80 60h160M80 76h160M80 92h160M80 108h160M80 124h160M80 140h160M80 156h160M80 172h160M80 28v144M96 28v144M112 28v144M128 28v144M144 28v144M160 28v144M176 28v144M192 28v144M208 28v144M224 28v144M240 28v144"/></g><path d="M112 60h32v16h32V60h32v48h-16v16h-16v16h-32v-16h-16v-16h-16z" fill="#e6b87f"/><path d="M114 62h12v12h-12z" fill="#f8dfb1"/><g fill="#b3c99b"><rect x="50" y="62" width="12" height="12"/><rect x="50" y="83" width="12" height="12"/></g><rect x="50" y="104" width="12" height="12" fill="#e6b87f"/>',
    quiettime:'<circle cx="160" cy="101" r="68" fill="none" stroke="#354036" stroke-width="5"/><path d="M160 33a68 68 0 0 1 53 110" fill="none" stroke="#becdaa" stroke-width="5" stroke-linecap="round"/><text x="160" y="110" fill="#e7eadc" text-anchor="middle" font-family="Consolas,monospace" font-size="36">24:59</text><text x="160" y="133" fill="#a2b496" text-anchor="middle" font-family="Consolas,monospace" font-size="8" letter-spacing="2">ONE THING AT A TIME</text>',
  };
  return '<svg class="center-art" viewBox="0 0 320 200" aria-hidden="true"><rect width="320" height="200" fill="'+(type==='pixelpet'?'#26232f':type==='flight'?'#1d2b35':'#202a26')+'"/>'+ (scenes[type] || scenes.lightboard) +'</svg>';
}
function openAppCenter() { centerState.detail=null; showTab("works"); setAppGroup("center"); }
function openCenterDetail(id) {
  centerState.scroll=$("works-panel").scrollTop; centerState.detail=id;
  renderBuiltinApps(); $("works-panel").scrollTop=0; $("center-back").focus({preventScroll:true});
}
function centerBack() {
  const id=centerState.detail; centerState.detail=null; renderBuiltinApps();
  $("works-panel").scrollTop=centerState.scroll;
  document.querySelector('[data-center-detail="'+CSS.escape(id)+'"]')?.focus({preventScroll:true});
}
function renderAppCenter() {
  const app=centerEntry(centerState.detail);
  if(app) { renderCenterDetail(app); return; }
  centerState.detail=null;
  $("app-center").innerHTML = '<div class="center-topline"><span class="local-chip"><i></i>本地模拟 · 仅本机可见</span><button class="text-button" id="center-upload">'+icon("upload")+'上传作品</button></div>'+
    '<button class="center-feature" data-center-detail="seed-tank"><span><small>BUILT TO BE REMIXED</small><strong>别人的灵感，<br>你的新起点。</strong><em>从一场坦克大战开始 '+icon("arrow")+'</em></span>'+centerArt("tank")+'</button>'+
    '<label class="center-search">'+icon("search")+'<input id="center-search" type="search" aria-label="搜索应用名称或玩法" placeholder="搜索一个新玩法" maxlength="80" value="'+escapeHTML(centerState.query)+'"></label>'+
    '<div class="center-filters" role="group" aria-label="应用中心分类">'+["全部","游戏","工具","趣玩","我的上传"].map(f=>'<button data-center-filter="'+f+'" aria-pressed="'+(centerState.filter===f)+'">'+f+'</button>').join('')+'</div>'+
    '<div class="center-section-title"><h3 id="center-list-title">灵感货架</h3><span id="center-count" role="status"></span></div><div id="center-grid" class="center-grid"></div>'+
    '<p class="center-endnote">先带走一个想法，再把它变成你的。<br>预置封面为设计示意，Lua 包待确认后实现。</p>';
  $("center-upload").onclick=()=>showCenterUpload();
  $("center-search").oninput=e=>{centerState.query=e.target.value;renderCenterGrid();};
  renderCenterGrid();
}
function renderCenterGrid() {
  const q=centerState.query.trim().toLowerCase();
  const entries=centerEntries().filter(a=>(centerState.filter==="全部" || (centerState.filter==="我的上传"?!!a.work:a.category===centerState.filter)) && (a.name+' '+a.description).toLowerCase().includes(q));
  $("center-count").textContent=entries.length+" 个应用";
  $("center-list-title").textContent=centerState.filter==="我的上传"?"我的上传":"灵感货架";
  $("center-grid").innerHTML=entries.length?entries.map(a=>'<article class="center-card"><button class="center-card-link" data-center-detail="'+escapeHTML(a.id)+'">'+centerArt(a.type)+'<span class="center-card-body"><span class="center-card-kicker">'+escapeHTML(a.category)+' / '+(a.work?'本机上传':'预置方案')+'</span><strong>'+escapeHTML(a.name)+'</strong><span class="center-card-desc">'+escapeHTML(a.description)+'</span></span></button><div class="center-card-bottom"><span>'+(a.work?'我 · 本机':'Mosaico Lab')+'</span><button data-center-get="'+escapeHTML(a.id)+'" aria-label="'+(centerImported(a)?'查看':'获取')+escapeHTML(a.name)+'">'+(centerImported(a)?'已添加':'获取 +')+'</button></div></article>').join(''):
    '<div class="center-empty">'+icon("grid")+'<h3>'+(q?'没找到这个玩法':'还没有上传作品')+'</h3><p>'+(q?'换个关键词，或清除筛选再看看。':'从我的创作中选一个版本，让灵感有个新位置。')+'</p><button class="secondary-button" id="center-empty-action">'+(q?'清除筛选':'选择作品上传')+'</button></div>';
  $("center-empty-action")?.addEventListener('click',()=>{if(q){centerState.query="";centerState.filter="全部";renderAppCenter();}else showCenterUpload();});
}
function centerImported(a) { return state.works.find(w=>w.centerId===a.id); }
function renderCenterDetail(a) {
  const imported=centerImported(a);
  $("app-center").innerHTML='<div class="center-detail-nav"><button class="text-button" id="center-back">'+icon("back")+'应用中心</button><span class="local-chip">本地预览</span></div><div class="center-detail-art">'+centerArt(a.type)+'<span>界面示意 · 非设备实况</span></div>'+
    '<div class="center-detail-heading"><p class="mini-label">'+escapeHTML(a.english || 'MADE BY YOU')+'</p><h2>'+escapeHTML(a.name)+'</h2><p>'+escapeHTML(a.description)+'</p></div>'+
    '<div class="center-author"><span class="center-avatar">'+(a.work?'我':'M')+'</span><span><b>'+(a.work?'我 · 本机上传':'Mosaico Lab · 预置方案')+'</b><small>v'+(a.work?.version || 1)+' · '+escapeHTML(a.category)+' · 无云端同步</small></span></div>'+
    '<div class="center-specs"><span><b>480 × 480</b>方形屏幕</span><span><b>触摸交互</b>无需外设</span><span><b>本地副本</b>可继续创作</span></div>'+
    '<div class="center-detail-copy"><h3>这一小块屏幕，可以做什么</h3><p>'+escapeHTML(a.about || a.description)+'</p><div class="center-tags">'+(a.features||['我的作品','版本快照']).map(f=>'<span>'+escapeHTML(f)+'</span>').join('')+'</div><h3>怎么玩</h3><p>'+escapeHTML(a.controls||'添加到我的创作后查看作品详情。具体操作以作者说明为准。')+'</p></div>'+
    '<div class="center-boundary"><b>当前是本地设计演示</b><p>这里只保存作品元数据与模拟版本，不上传到服务器。预置游戏尚未打包为可运行 Lua，不代表已通过设备验证。</p></div>'+
    '<div class="center-detail-actions"><button class="primary-button full-width" id="center-add">'+icon(imported?'check':'plus')+(imported?'已添加 · 查看我的创作':'添加到我的创作')+'</button><p>添加不会部署或启动 Mosaico。运行时仍需单独确认。</p>'+(a.work?'<button class="text-button center-remove" id="center-remove">移除这条本机上传</button>':'')+'</div>';
  $("center-back").onclick=centerBack;
  $("center-add").onclick=()=>acquireCenterApp(a);
  $("center-remove")?.addEventListener('click',()=>removeCenterUpload(a));
}
function acquireCenterApp(a) {
  if(activeJob()) {toast("先完成或取消当前任务，再添加作品。");return;}
  if(centerImported(a)){centerState.detail=null;setAppGroup('mine');renderWorks();return;}
  const work=a.work?JSON.parse(JSON.stringify(a.work)):{type:a.type,version:1,params:{color:a.color},thread:[]};
  work.id='center-work-'+Date.now(); work.installed=false; work.previous=null; work.centerId=a.id; work.title=a.name;
  work.thread=[{role:'agent',text:'已从应用中心添加「'+a.name+'」的独立副本（本地模拟）。没有部署或运行设备。'+(catalog[a.type].centerOnly?'本轮为玩法方案；Lua 与 Agent 改编在确认设计后实现。':'可以继续创作，原上传版本不受修改影响。')}];
  state.works.unshift(work); persist();renderWorks();renderAppCenter();
  toast('已添加到「我的创作」（本地模拟），未启动设备。');
}
function showCenterPlan(work) {
  openModal('预置 Lua 应用 · 待实现','<p>「'+escapeHTML(catalog[work.type].name)+'」当前是应用中心的交互方案。本轮仅供确认页面与流程，尚未生成或验证可运行 Lua，不会用别的游戏代替运行。</p><p>确认设计后，再实现离线预置 Lua、源码校验、Agent 改编和设备运行。</p><button class="primary-button full-width" id="center-plan-done">知道了</button>','DESIGN ONLY / NO EXECUTION');
  modalAction('center-plan-done',closeModal);
}
function showCenterUpload() {
  if(!state.works.length) {
    openModal('把你的作品放上货架','<p>还没有可以上传的本地作品。可以先去创作，或从应用中心获取一个预置方案来体验上传流程。</p><p>本阶段仅保存在本机，不会公开分享。</p><div class="modal-actions"><button class="secondary-button" id="upload-create">去创作</button><button class="primary-button" id="upload-browse">浏览预置应用</button></div>','APP CENTER / LOCAL');
    modalAction('upload-create',()=>{closeModal();showTab('create');});modalAction('upload-browse',()=>{closeModal();openAppCenter();});return;
  }
  const options=state.works.map(w=>'<option value="'+escapeHTML(w.id)+'">'+escapeHTML(w.title||catalog[w.type].name)+' · v'+w.version+'</option>').join('');
  openModal('上传到应用中心',
    '<p class="upload-subtitle">只在这台手机上架，不会公开到网络。</p><form id="center-upload-form" class="center-upload-form">'+
    '<div class="upload-form-scroll"><div class="upload-section-heading"><span>选择作品版本</span><button type="button" class="text-button" id="upload-source" aria-expanded="false" aria-controls="upload-source-preview">'+icon('code')+'查看源码</button></div>'+
    '<div class="upload-work-card"><span id="upload-art" aria-hidden="true"></span><label class="upload-work-select"><select id="upload-work" aria-label="选择作品版本" required>'+options+'</select><span id="upload-version"></span></label></div>'+
    '<div id="upload-source-preview" class="upload-source-preview" hidden><p>网页仅展示版本参数；真实 Lua 请在 Android 中查看。</p><pre id="upload-source-code"></pre></div>'+
    '<label class="upload-field"><span class="upload-field-heading"><span>应用名称</span><small id="upload-name-count"></small></span><input id="upload-name" maxlength="24" required aria-describedby="upload-name-help"><span id="upload-name-help" class="upload-field-help">最多 24 字符 / 48 字节，约 16 个汉字</span></label>'+
    '<label class="upload-field"><span class="upload-field-heading"><span>一句话介绍</span><small id="upload-description-count">0 / 80</small></span><textarea id="upload-description" maxlength="80" rows="2" required placeholder="说说它能做什么、怎么玩"></textarea></label>'+
    '<fieldset class="upload-category"><legend>应用分类</legend><div>'+['游戏','工具','趣玩'].map(c=>'<label><input type="radio" name="upload-category" value="'+c+'"><span>'+c+'</span></label>').join('')+'</div></fieldset>'+
    '<p class="upload-hint">保存独立版本快照，后续修改不覆盖。<br>不附带对话、设备地址或模型配置。</p></div>'+
    '<div class="upload-footer"><label class="upload-consent"><input id="upload-consent" type="checkbox" required><span>我有权分享，且已检查源码不含密钥、个人信息等私密内容。</span></label><p id="upload-error" class="upload-error" role="alert"></p><div class="upload-actions"><button type="button" class="secondary-button" id="upload-cancel">取消</button><button class="primary-button" type="submit" disabled>'+icon('upload')+'确认上传 · 仅本机</button></div></div></form>','APP CENTER / LOCAL');
  const updateCounts=()=>{
    const name=$("upload-name").value, bytes=new TextEncoder().encode(name.trim()).length;
    $("upload-name-count").textContent=name.length+' / 24';$("upload-description-count").textContent=$("upload-description").value.length+' / 80';
    $("upload-name").setCustomValidity(bytes>48?'名称超过 48 字节，请缩短后上传。':'');
    $("upload-name").setAttribute('aria-invalid',String(bytes>48));
    $("upload-name-help").textContent=bytes>48?'名称超过 48 字节，请缩短后上传。':'最多 24 字符 / 48 字节，约 16 个汉字';
    document.querySelector('.center-upload-form button[type="submit"]').disabled=!$("upload-consent").checked||!name.trim()||bytes>48||!$("upload-description").value.trim();
  };
  const fill=()=>{
    const w=state.works.find(w=>w.id===$("upload-work").value), a=centerPresets.find(a=>a.type===w.type);
    $("upload-name").value=(w.title||catalog[w.type].name).slice(0,24);$("upload-description").value=(a?.description||catalog[w.type].description).slice(0,80);
    const category=a?.category||(w.type==='snake'?'游戏':'工具');document.querySelectorAll('[name="upload-category"]').forEach(input=>input.checked=input.value===category);
    $("upload-version").textContent=catalog[w.type].capability+' · 版本快照';$("upload-art").innerHTML=centerArt(a?.type||'lightboard');
    $("upload-source-code").textContent=JSON.stringify(w.params,null,2);$("upload-source-preview").hidden=true;$("upload-source").setAttribute('aria-expanded','false');
    $("upload-consent").checked=false;$("upload-error").textContent='';updateCounts();
  };
  fill();$("upload-work").onchange=fill;$("upload-name").oninput=updateCounts;$("upload-description").oninput=updateCounts;$("upload-consent").onchange=updateCounts;
  $("upload-source").onclick=()=>{const preview=$("upload-source-preview");preview.hidden=!preview.hidden;$("upload-source").setAttribute('aria-expanded',String(!preview.hidden));};
  modalAction('upload-cancel',closeModal);
  $("center-upload-form").onsubmit=e=>{
    e.preventDefault();const w=state.works.find(w=>w.id===$("upload-work").value);const name=$("upload-name").value.trim(), description=$("upload-description").value.trim();
    if(!w||!name||!description){$("upload-error").textContent='请填写名称和介绍，不能只有空格。';return;}
    const source=w.id+':'+w.version+':'+JSON.stringify(w.params);
    const existing=centerState.published.find(a=>a.source===source);
    if(existing){closeModal();showTab('works');setAppGroup('center');openCenterDetail(existing.id);toast('这个版本已上传，已打开现有条目。');return;}
    const preset=centerPresets.find(a=>a.type===w.type);
    const entry={id:'local-'+Date.now(),source,name,description,category:document.querySelector('[name="upload-category"]:checked').value,type:w.type,about:description,controls:preset?.controls,features:['本机上传','版本快照'],work:{id:w.id,type:w.type,version:w.version,params:{...w.params},thread:[]}};
    centerState.published.unshift(entry);const saved=saveCenter();closeModal();centerState.detail=null;centerState.query='';centerState.filter='我的上传';showTab('works');setAppGroup('center');
    toast(saved?'已上传到本机应用中心；没有公开到网络。':'本次会话已上架；浏览器不允许保存，关闭后将丢失。');
  };
}
function removeCenterUpload(a) {
  openModal('移除这条本机上传？','<p>只从本机应用中心移除「'+escapeHTML(a.name)+'」。已经添加到「我的创作」的副本和设备应用不受影响。</p><div class="modal-actions"><button id="center-keep" class="secondary-button">保留</button><button id="center-remove-confirm" class="danger-button">确认移除</button></div>');
  modalAction('center-keep',closeModal);modalAction('center-remove-confirm',()=>{centerState.published=centerState.published.filter(x=>x.id!==a.id);saveCenter();closeModal();centerState.detail=null;renderBuiltinApps();toast('已移除本机上传，创作副本仍保留。');});
}
function initAppCenter() {
  $("browse-center").onclick=openAppCenter;$("review-center").onclick=()=>{openAppCenter();$("prototype").scrollIntoView({block:'start'});};$("library-upload").onclick=()=>showCenterUpload();
  document.addEventListener('click',e=>{
    const detail=e.target.closest('[data-center-detail]');if(detail)openCenterDetail(detail.dataset.centerDetail);
    const get=e.target.closest('[data-center-get]');if(get){const a=centerEntry(get.dataset.centerGet);if(a)acquireCenterApp(a);}
    const filter=e.target.closest('[data-center-filter]');if(filter){centerState.filter=filter.dataset.centerFilter;document.querySelectorAll('[data-center-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===filter)));renderCenterGrid();}
  });
  if(new URLSearchParams(location.search).get('view')==='center')openAppCenter();
}
