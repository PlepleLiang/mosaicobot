/* Focused local design checks. No model, ROM, Lua or device access. */
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path'),os=require('node:os');
const {pathToFileURL}=require('node:url');
(async()=>{
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1050},hasTouch:true});
  const page=await context.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route(/^https?:/,r=>{external.push(r.request().url());return r.abort();});
  let n=0;const check=(v,m)=>{assert.ok(v,m);console.log('PASS '+(++n)+' '+m);};
  const action=a=>page.locator('[data-gp-action="'+a+'"]');
  const scenario=s=>page.locator('#gp-scenario').selectOption(s);
  const key=k=>page.locator('[data-key="'+k+'"]');
  const neutral=async()=>await page.locator('.gp-key[aria-pressed="true"]').count()===0&&await page.locator('[data-stick][data-x="0.00"][data-y="0.00"]').count()===2;
  const fits=async()=>page.evaluate(()=>{const root=document.querySelector('#gp-controller').getBoundingClientRect();return [...document.querySelectorAll('.gp-key,.gp-stick,#gp-menu')].every(b=>{const r=b.getBoundingClientRect();return r.width>=48&&r.height>=48&&r.left>=root.left&&r.right<=root.right&&r.top>=root.top&&r.bottom<=root.bottom;});});
  try{
    await page.goto(pathToFileURL(path.join(__dirname,'../index.html')).href+'?view=game');
    await page.waitForFunction(()=>document.querySelector('#gp-controller').dataset.status==='ready');
    const baseline=await page.evaluate(()=>JSON.stringify({works:state.works,running:state.running,tab:state.tab}));
    check(await page.locator('#game-mode').isVisible()&&await page.locator('#gp-controller').getAttribute('data-status')==='ready','entry opens universal controller directly');
    check(await page.locator('.gp-key').count()===16&&await page.locator('.gp-key:disabled').count()===0,'all 16 standard buttons enabled independent of game');
    check(await page.locator('[data-game-pick],[data-game-preview],#game-device').count()===0,'old game-specific routes and renderer removed');
    const bounds=await page.locator('#gp-controller').boundingBox();check(bounds.width>bounds.height*1.7&&await fits(),'landscape layout and minimum touch sizes');
    await page.screenshot({path:path.join(os.tmpdir(),'mosaicobot-universal-review.png')});
    const start=await key('start').boundingBox();await page.mouse.move(start.x+24,start.y+24);await page.mouse.down();
    check(await key('start').getAttribute('aria-pressed')==='true'&&await page.locator('.gp-cover').count()===0,'Start is held raw input, never opens pause');await page.mouse.up();
    const ls=await page.locator('[data-stick="left"]').boundingBox(),rs=await page.locator('[data-stick="right"]').boundingBox(),a=await key('a').boundingBox();
    const cdp=await context.newCDPSession(page);
    await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:1,x:ls.x+ls.width*.75,y:ls.y+ls.height/2},{id:2,x:rs.x+rs.width/2,y:rs.y+rs.height*.25},{id:3,x:a.x+24,y:a.y+24}]});
    check(Number(await page.locator('[data-stick="left"]').getAttribute('data-x'))>.5&&Number(await page.locator('[data-stick="right"]').getAttribute('data-y'))<-.5&&await key('a').getAttribute('aria-pressed')==='true','two analog sticks and A accept simultaneous touches');
    await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});check(await neutral(),'cancelled touch releases every button and recenters both axes');
    await page.locator('#gp-menu').focus();await page.keyboard.down('ArrowLeft');await page.keyboard.down('KeyJ');check(await key('left').getAttribute('aria-pressed')==='true'&&await key('a').getAttribute('aria-pressed')==='true','keyboard movement and action coexist');
    await page.locator('#gp-menu').click();await page.keyboard.up('ArrowLeft');await page.keyboard.up('KeyJ');
    check(await neutral()&&(await page.locator('.gp-card').innerText()).includes('不默认暂停'),'controller menu clears input without claiming game pause');await action('resume').click();
    await page.locator('[data-stick="right"]').focus();await page.keyboard.down('ArrowRight');check(await page.locator('[data-stick="right"]').getAttribute('data-x')==='1.00','keyboard alternative controls analog stick');await page.keyboard.up('ArrowRight');check(await neutral(),'keyboard axis releases to center');
    await scenario('lost');check(await page.locator('.gp-key:disabled').count()===16&&await neutral(),'disconnect disables and clears all input');await action('reconnect').click();await page.waitForFunction(()=>document.querySelector('#gp-controller').dataset.status==='ready');
    check(await page.locator('.gp-key:disabled').count()===0&&await neutral(),'reconnect automatically enables neutral controller without approval');
    await scenario('unsupported');await page.keyboard.press('Escape');await action('return').click();check(await page.locator('#gp-controller').getAttribute('data-status')==='unsupported'&&await page.locator('.gp-key:disabled').count()===16,'exit cancellation cannot bypass capability check');
    await scenario('waiting_game');check(await page.locator('#gp-device-confirm').count()===0&&await page.locator('.gp-key:disabled').count()===16,'waiting for game without physical confirmation');await action('game-started').click();await page.waitForFunction(()=>document.querySelector('#gp-controller').dataset.status==='ready');
    await scenario('offline');await action('scan').waitFor();await action('scan').click();await page.waitForFunction(()=>document.querySelector('#gp-controller').dataset.status==='ready');check(await page.locator('.gp-key:disabled').count()===0,'manual rescan automatically checks and connects');
    await scenario('ready');
    for(const [w,h] of [[844,390],[740,360],[667,375]]){
      await page.setViewportSize({width:w,height:h});check(await fits(),'all controls fit landscape '+w+'x'+h);
      if(w===844)await page.screenshot({path:path.join(os.tmpdir(),'mosaicobot-universal-landscape.png')});
    }
    await page.setViewportSize({width:600,height:360});check(await page.locator('.gp-rotate').isVisible()&&await neutral(),'too-narrow landscape window offers safe space prompt');
    await page.setViewportSize({width:375,height:812});check(await page.locator('.gp-rotate').isVisible()&&!(await page.locator('.gp-landscape-frame').isVisible())&&await neutral(),'portrait prompts rotation rather than shrinking gamepad');
    await page.setViewportSize({width:844,height:390});check(await action('resume').isVisible(),'rotation back requires explicit resume');await action('resume').click();
    await page.locator('#gp-menu').click();await action('exit').click();await action('close').click();
    check(!(await page.locator('#game-mode').isVisible())&&await page.evaluate(()=>JSON.stringify({works:state.works,running:state.running,tab:state.tab}))===baseline,'exit preserves workshop route, works and device run state');
    check(errors.length===0&&external.length===0,'no JS errors or external requests '+JSON.stringify({errors,external}));
    console.log('TOTAL '+n+' universal controller checks');
  }finally{await browser.close();}
})();
