/* Browser-only showcase regression. No AI, Lua, phone or real device access.
 * Run: node docs/tests/showcase.cjs [URL] (or SHOWCASE_URL).
 * PLAYWRIGHT_PATH and BROWSER_PATH override the local Playwright/Edge paths.
 */
const {chromium}=require(process.env.PLAYWRIGHT_PATH || 'playwright');
const assert=require('node:assert/strict');
const path=require('node:path'),os=require('node:os'),fs=require('node:fs');
const {pathToFileURL}=require('node:url');
(async()=>{
  const url=new URL(process.argv[2]||process.env.SHOWCASE_URL||pathToFileURL(path.join(__dirname,'../site/index.html')).href);
  const screenshots=fs.mkdtempSync(path.join(os.tmpdir(),'mosaicobot-showcase-'));
  const browser=await chromium.launch({executablePath:process.env.BROWSER_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1050},hasTouch:true,reducedMotion:'reduce'});
  const page=await context.newPage(),errors=[],external=[],failedRequests=[];
  page.setDefaultTimeout(10000);
  page.on('pageerror',e=>errors.push(e.message));
  page.on('requestfailed',r=>failedRequests.push(r.url()));
  page.on('response',r=>{if(r.status()>=400)failedRequests.push(r.status()+' '+r.url());});
  await page.route(/^https?:/,route=>{
    if(url.protocol!=='file:'&&new URL(route.request().url()).origin===url.origin)return route.continue();
    external.push(route.request().url());return route.abort();
  });
  await page.addInitScript(()=>{
    const probe=window.__showcase={events:[],screen:null};
    let scenes;
    Object.defineProperty(window,'MosaicoScenes',{configurable:true,get:()=>scenes,set:value=>{
      scenes=value;
      const input=value.Screen.prototype.input,notify=value.Screen.prototype.notify;
      value.Screen.prototype.notify=function(...args){probe.screen=this;return notify.apply(this,args);};
      value.Screen.prototype.input=function(key,down){
        const before=this.bullets.length,result=input.call(this,key,down);
        probe.events.push({scene:this.work.scene,key,down,before,after:this.bullets.length,inputs:[...this.inputs]});
        return result;
      };
    }});
  });
  let checks=0;
  const check=(condition,label)=>{assert.ok(condition,label);console.log('PASS '+(++checks)+' '+label);};
  const phase=expected=>page.waitForFunction(p=>document.querySelector('#device-stage').dataset.phase===p,expected);
  const creation=expected=>page.waitForFunction(p=>document.querySelector('#workshop').dataset.creation===p,expected);
  const connection=expected=>page.waitForFunction(p=>document.querySelector('#workshop').dataset.connection===p,expected);
  const shot=name=>page.screenshot({path:path.join(screenshots,name+'.png'),fullPage:true});
  const pick=id=>page.locator('[data-scene="'+id+'"]').click();
  const action=key=>page.locator('.touch-controls [data-input="'+key+'"]');
  const neutral=()=>page.evaluate(()=>window.__showcase.screen.inputs.size===0&&!document.querySelector('[data-input][data-held=true]'));
  const saved=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('mosaicobot.showcase.works.v1')||'[]'));
  const confirm=async value=>{
    await page.evaluate(()=>{__showcase.closed=false;document.querySelector('#confirm-dialog').addEventListener('close',()=>{__showcase.closed=true;},{once:true});});
    await page.locator('#confirm-dialog button[value="'+value+'"]').click();
    await page.waitForFunction(()=>__showcase.closed);
  };
  try{
    await page.goto(url.href);
    await page.waitForFunction(()=>window.__showcase?.screen&&document.querySelectorAll('.scene-card').length===9);
    check(await page.locator('#works-count').innerText()==='0','fresh context starts with no saved works');
    // 1. All nine scenes share the visible lifecycle; snake also reaches a real game-over.
    const ids=['snake','tank','flight','racer','pixelpet','lightboard','focus','signature','expression'];
    for(const id of ids){
      await pick(id);await phase('idle');
      check(await page.locator('#device-stage').getAttribute('data-scene')===id&&await page.locator('#scene-pause').isDisabled(),id+' loads idle');
      await page.locator('#scene-start').click();await phase('running');
      check(await page.locator('#scene-start').isDisabled()&&await page.locator('#scene-pause').isEnabled(),id+' starts');
      await page.locator('#scene-pause').click();await phase('paused');
      const frozen=await page.evaluate(()=>JSON.stringify({age:__showcase.screen.age,remaining:__showcase.screen.remaining}));
      await page.waitForTimeout(100);
      check(await neutral()&&await page.evaluate(()=>JSON.stringify({age:__showcase.screen.age,remaining:__showcase.screen.remaining}))===frozen,id+' pause freezes time and clears input');
      await page.locator('#scene-start').click();await phase('running');
      await page.locator('#scene-retry').click();await phase('running');
      check(await page.evaluate(()=>__showcase.screen.score===0&&__showcase.screen.age<1),id+' retry resets run');
      await page.locator('#scene-exit').click();await phase('exited');
      check(await neutral(),id+' exit clears input');
      await page.locator('#scene-start').click();await phase('running');
      check(await page.locator('#device-stage').getAttribute('data-phase')==='running',id+' restarts after exit');
    }
    await pick('snake');await page.locator('#scene-start').click();await phase('ended');
    check(await neutral()&&(await page.locator('#scene-status').innerText()).includes('已结束'),'snake naturally ends at wall');
    await page.locator('#scene-retry').click();await phase('running');
    check(await page.evaluate(()=>__showcase.screen.score===0),'retry after game-over resets score');
    await pick('focus');await page.locator('#scene-start').click();
    const remaining=await page.evaluate(()=>__showcase.screen.remaining);
    await page.waitForFunction(before=>__showcase.screen.remaining<before-.2,remaining);
    check(await page.evaluate(()=>__showcase.screen.remaining<300),'focus uses elapsed browser time');
    await pick('expression');await page.locator('#scene-start').click();
    await shot('01-lifecycle');
    // 2. Generation, save, connection failure/retry, cancel/confirm and immutable versions.
    await page.locator('#intent').fill('做一个橙色慢速坦克');await page.locator('#generate').click();
    await creation('generating');
    check(await page.locator('#generate').isDisabled()&&await page.locator('#intent').isDisabled(),'generation locks form');
    await creation('generated');
    check(await page.locator('#draft-title').innerText()==='坦克守线'&&await page.locator('#work-speed').inputValue()==='1'&&await page.locator('#deploy-work').isDisabled(),'generated draft has parsed parameters and cannot deploy unsaved');
    check(await page.locator('#device-stage').getAttribute('data-scene')==='expression','generation does not replace running screen');
    await page.locator('#save-work').click();await creation('saved');
    const version1=(await saved())[0];
    check(version1.scene==='tank'&&version1.version===1&&await page.locator('#save-work').isDisabled(),'save persists V1 without deployment');
    await page.locator('#deploy-work').click();
    check(!await page.locator('#confirm-dialog').evaluate(node=>node.open)&&await page.locator('#connect-device').evaluate(node=>node===document.activeElement),'disconnected deployment blocked with connect focus');
    await page.locator('#fail-connection').click();await connection('connecting');await connection('failed');
    check((await saved()).length===1&&await page.locator('#connect-device').innerText()==='重试连接','failed connection preserves saved version');
    await page.locator('#connect-device').click();await connection('connected');
    await page.locator('#deploy-work').click();
    check(await page.locator('#confirm-dialog').evaluate(node=>node.open)&&await page.locator('#device-stage').getAttribute('data-phase')==='running','deployment modal does not pause independent screen');
    await confirm('cancel');
    check(await page.locator('#device-stage').getAttribute('data-scene')==='expression'&&await page.locator('#deploy-work').evaluate(node=>node===document.activeElement),'cancel preserves run and restores trigger focus');
    await page.locator('#deploy-work').click();await confirm('confirm');await creation('deployed');
    check(await page.evaluate(()=>__showcase.screen.work.id===JSON.parse(localStorage.getItem('mosaicobot.showcase.works.v1'))[0].id)&&await page.locator('#device-stage').getAttribute('data-phase')==='running','only confirmed deploy runs saved snapshot');
    // 3. Confirmed deployment must restore keyboard focus to the square screen.
    check(await page.locator('#mosaico-screen').evaluate(node=>node===document.activeElement),'confirmed deployment restores square-screen focus');
    await shot('02-confirmed-deployment');
    await page.locator('#modify-work').click();await creation('modified');
    await page.locator('#work-color').selectOption('#8ecaff');await page.locator('#work-speed').selectOption('3');
    check(await page.locator('#draft-version').innerText()==='V02'&&await page.evaluate(()=>__showcase.screen.work.version===1&&__showcase.screen.work.color==='#ff6b35'),'editing V2 leaves running V1 unchanged');
    await page.locator('#save-work').click();
    check((await saved()).length===2&&JSON.stringify((await saved())[1])===JSON.stringify(version1),'saving V2 preserves original V1 snapshot');
    await page.locator('#disconnect-device').click();await connection('disconnected');
    check(await page.locator('#device-stage').getAttribute('data-phase')==='running'&&await neutral()&&(await saved()).length===2,'disconnect clears input but keeps independent run and versions');
    await page.locator('#connect-device').click();await connection('connected');
    await page.locator('#deploy-work').click();await confirm('confirm');
    check(await page.evaluate(()=>__showcase.screen.work.version===2&&__showcase.screen.work.color==='#8ecaff'),'reconnect permits explicit V2 deployment');
    await page.locator('#scene-pause').click();
    // 4. Unsupported requests never invent a draft or overwrite saved versions.
    const versions=JSON.stringify(await saved());
    for(const input of ['做一个天气预报','做一个贪食蛇和坦克','做一个专注61分钟','做一个蓝色粉色飞机','做一个快速像素宠']){
      await page.locator('#intent').fill(input);await page.locator('#generate').click();await creation('unsupported');
      check((await page.locator('#creation-status').innerText()).includes('暂不支持')&&JSON.stringify(await saved())===versions&&await page.locator('#draft-version').innerText()==='V02','unknown/invalid input is explicit and non-destructive: '+input);
    }
    // 5. Reload restores versions, not connection or running state.
    await page.reload();await page.waitForFunction(()=>window.__showcase?.screen);
    check(JSON.stringify(await saved())===versions&&await page.locator('.work-item').count()===2,'saved versions survive reload');
    check(await page.locator('#device-stage').getAttribute('data-scene')==='expression'&&await page.locator('#device-stage').getAttribute('data-phase')==='idle'&&await page.locator('#workshop').getAttribute('data-connection')==='disconnected','reload does not silently reconnect or deploy');
    await page.locator('.work-item').last().locator('button').click();
    check(await page.locator('#draft-version').innerText()==='V01'&&await page.locator('#work-color').inputValue()==='#ff6b35','stored V1 reopens with original parameters');
    await shot('03-reloaded-versions');
    // 6. Four viewport widths, three physical forms, both pendant mounts: no clipping.
    for(const width of [375,768,1024,1440]){
      await page.setViewportSize({width,height:width===375?844:1050});
      for(const form of ['back','game','pendant']){
        await page.locator('button[data-form="'+form+'"]').click();
        for(const mount of form==='pendant'?['bag','phone']:['bag']){
          if(form==='pendant')await page.locator('button[data-mount="'+mount+'"]').click();
          const layout=await page.evaluate(()=>{
            const stage=document.querySelector('#device-stage'),root=stage.getBoundingClientRect();
            const visible=node=>!!node.getClientRects().length&&getComputedStyle(node).display!=='none';
            const objects=[...stage.querySelectorAll('.phone-shell,.mosaico-shell,.backpack,.lanyard,[data-input]')].filter(visible);
            const clipped=objects.filter(node=>{const r=node.getBoundingClientRect();return r.left<root.left-1||r.right>root.right+1||r.top<root.top-1||r.bottom>root.bottom+1;}).map(node=>node.className||node.dataset.input);
            const overflow=[...document.querySelectorAll('.exhibit-panel,.studio-grid,.creation-panel,.works-panel,.scene-library,.screen-console')].filter(node=>node.scrollWidth>node.clientWidth+1).map(node=>node.className);
            const blocked=[...stage.querySelectorAll('[data-input]')].filter(visible).filter(node=>{const r=node.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return r.width<44||r.height<44||!node.contains(hit);}).map(node=>node.dataset.input);
            return {clipped,overflow,blocked,pageFits:document.documentElement.scrollWidth<=innerWidth+1};
          });
          check(layout.pageFits&&!layout.clipped.length&&!layout.overflow.length&&!layout.blocked.length,'layout '+width+' '+form+'/'+mount+' '+JSON.stringify(layout));
          await shot('layout-'+width+'-'+form+(form==='pendant'?'-'+mount:''));
        }
      }
    }
    // 7. Short keyboard, accessibility-click and real touchscreen actions fire synchronously.
    await page.setViewportSize({width:1440,height:1050});await page.locator('button[data-form="game"]').click();
    const fired=()=>page.evaluate(()=>__showcase.events.some(e=>e.down&&e.key==='action'&&e.after===e.before+1));
    const resetEvents=()=>page.evaluate(()=>{__showcase.events.length=0;});
    for(const id of ['tank','flight']){
      await pick(id);await page.locator('#scene-start').click();await page.locator('#mosaico-screen').focus();
      await resetEvents();await page.keyboard.press('Space');
      check(await fired()&&await neutral(),id+' short Space fires immediately and releases');
      await resetEvents();await action('action').focus();await page.keyboard.press('Enter');
      check(await fired()&&await neutral(),id+' keyboard button activation fires and releases');
      await resetEvents();await page.locator('.phone-controller [data-input="action"]').tap();
      check(await fired()&&await neutral(),id+' short phone-controller touch fires and releases');
      await resetEvents();await action('action').tap();
      check(await fired()&&await neutral(),id+' short square-console touch fires and releases');
    }
    await page.locator('#mosaico-screen').focus();await page.keyboard.down('ArrowLeft');
    check(await page.evaluate(()=>__showcase.screen.inputs.has('left')),'keyboard direction held');
    await page.locator('button[data-form="back"]').click();await page.keyboard.up('ArrowLeft');
    check(await neutral(),'form change clears held input');
    await page.locator('#mosaico-screen').focus();await page.keyboard.down('ArrowRight');
    await page.evaluate(()=>window.dispatchEvent(new Event('blur')));await phase('paused');await page.keyboard.up('ArrowRight');
    check(await neutral(),'window blur clears input and pauses');
    await page.locator('#scene-start').click();await page.locator('#mosaico-screen').focus();await page.keyboard.press('p');await phase('paused');
    check(await neutral(),'P pauses and clears input');await page.keyboard.press('p');await phase('running');
    const cdp=await context.newCDPSession(page);
    await action('left').scrollIntoViewIfNeeded();const left=await action('left').boundingBox();
    await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x:left.x+left.width/2,y:left.y+left.height/2}]});
    check(await page.evaluate(()=>__showcase.screen.inputs.has('left')),'real touch holds direction');
    await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});
    check(await neutral(),'touchcancel releases input');
    await page.locator('#mosaico-screen').focus();await page.keyboard.down('ArrowLeft');
    await page.locator('#discard-work').click();
    check(await neutral()&&await page.locator('#device-stage').getAttribute('data-phase')==='running','modal clears input without pausing');
    await page.keyboard.up('ArrowLeft');await page.keyboard.press('Escape');
    await page.waitForFunction(()=>!document.querySelector('#confirm-dialog').open);
    check(await page.locator('#draft-panel').isVisible()&&await neutral(),'Escape cancels dialog without discarding version');
    await page.locator('#intent').fill(' ');await resetEvents();await page.keyboard.press('Space');
    check(await page.evaluate(()=>__showcase.events.length===0),'typing in intent never controls game');
    // 8. Pet responses are visible both in live status and the accessible screen label.
    await pick('pixelpet');await page.locator('#scene-start').click();
    for(const [key,message] of [['action','吃饱了，谢谢你'],['up','一起玩，真开心'],['down','休息一下，充满电']]){
      await action(key).tap();
      check((await page.locator('#scene-status').innerText()).includes(message)&&(await page.locator('#mosaico-screen').getAttribute('aria-label')).includes(message),'pet response text: '+message);
    }
    await shot('04-pet-text');
    // 9. Compare actual normal/hover styles and readable contrast on dark selected controls.
    await page.locator('#scene-pause').click();await phase('paused');
    for(const selector of ['#scene-start','button[data-form="back"]','.touch-controls [data-input="action"]']){
      const button=page.locator(selector);await button.scrollIntoViewIfNeeded();await page.mouse.move(0,0);
      const name=selector.includes('scene-start')?'primary':selector.includes('data-form')?'selected':'action';
      await shot('normal-'+name);
      const normal=await button.evaluate(node=>({background:getComputedStyle(node).backgroundColor,color:getComputedStyle(node).color}));
      await button.hover();
      const hover=await button.evaluate(node=>{
        const style=getComputedStyle(node);
        const luminance=color=>{const values=color.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;});return values[0]*.2126+values[1]*.7152+values[2]*.0722;};
        const bg=luminance(style.backgroundColor),fg=luminance(style.color);
        return {background:style.backgroundColor,color:style.color,contrast:(Math.max(bg,fg)+.05)/(Math.min(bg,fg)+.05)};
      });
      check(hover.background!==normal.background&&hover.contrast>=4.5,'hover changes background with readable text '+selector+' '+JSON.stringify({normal,hover}));
      await shot('hover-'+(selector.includes('scene-start')?'primary':selector.includes('data-form')?'selected':'action'));
    }
    check(errors.length===0,'no JavaScript errors '+JSON.stringify(errors));
    check(external.length===0,'no external requests '+JSON.stringify(external));
    check(failedRequests.length===0,'no failed assets '+JSON.stringify(failedRequests));
    console.log(JSON.stringify({checks,scenarios:9,url:url.href,errors,external,failedRequests,screenshots}));
  }catch(error){
    await shot('failure').catch(()=>{});
    console.error(JSON.stringify({checks,errors,external,failedRequests,screenshots}));throw error;
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
