/* Local HTML flow check only; never accesses a phone, model or Mosaico. */
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const os=require('node:os');
const {pathToFileURL}=require('node:url');
(async()=>{
  const browser=await chromium.launch({executablePath:process.env.BROWSER_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1050}});
  const page=await context.newPage();
  const errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route(/^https?:/,route=>{external.push(route.request().url());return route.abort();});
  let checks=0;
  const check=(condition,label)=>{assert.ok(condition,label);checks++;console.log('PASS '+label);};
  try{
    await page.goto(pathToFileURL(path.join(__dirname,'../index.html')).href+'?view=center');
    check(await page.locator('.center-card').count()===6,'six preset concepts');
    await page.locator('.phone').screenshot({path:path.join(os.tmpdir(),'mosaicobot-center.png')});
    await page.locator('#center-upload').click();
    check(await page.locator('#upload-browse').isVisible(),'empty upload offers discovery without creating fake personal works');
    await page.locator('#upload-browse').click();
    await page.locator('[data-center-filter="游戏"]').click();
    check(await page.locator('.center-card').count()===3,'game category');
    await page.locator('#center-search').fill('nothing-matches');
    check(await page.locator('.center-empty').isVisible(),'search empty state');
    await page.locator('#center-empty-action').click();
    await page.locator('[data-center-detail="seed-tank"]').last().click();
    await page.locator('.phone').screenshot({path:path.join(os.tmpdir(),'mosaicobot-center-detail.png')});
    check(await page.locator('.center-detail-heading h2').innerText()==='坦克大战','detail route');
    await page.locator('#center-add').click();
    check(await page.evaluate(()=>state.works.length===1&&state.running===null),'get saves one independent copy without execution');
    await page.locator('#center-add').click();
    check(await page.evaluate(()=>state.works.length===1&&state.appGroup==='mine'),'repeated get returns to library without duplicates');
    await page.locator('[data-work-action="run"]').click();
    check((await page.locator('#modal-content').innerText()).includes('尚未生成或验证可运行 Lua'),'prototype cannot pretend to execute unimplemented Lua');
    await page.locator('#center-plan-done').click();
    await page.locator('#library-upload').click();
    await page.locator('#upload-name').fill('我的坦克');
    check(await page.locator('.center-upload-form button[type="submit"]').isDisabled(),'upload requires rights/privacy confirmation');
    await page.locator('#upload-consent').check();
    await page.locator('#modal').screenshot({path:path.join(os.tmpdir(),'mosaicobot-center-upload.png')});
    await page.locator('.center-upload-form button[type="submit"]').click();
    check(await page.locator('.center-card').count()===1,'published entry appears in my uploads');
    check(await page.evaluate(()=>centerState.published.length===1&&centerState.published[0].work.thread.length===0),'upload snapshot excludes conversations');
    await page.reload();
    await page.locator('[data-center-filter="我的上传"]').click();
    check(await page.locator('.center-card').count()===1,'local upload survives reload');
    await page.locator('#center-upload').click();await page.locator('#upload-consent').check();await page.locator('.center-upload-form button[type="submit"]').click();
    check(await page.evaluate(()=>centerState.published.length===1),'same version upload deduplicated');
    await page.locator('#center-add').click();
    check(await page.evaluate(()=>state.works.length===2&&state.works[0].id!==state.works[1].id),'uploaded snapshot can be acquired as independent copy');
    await page.locator('#center-remove').click();await page.locator('#center-remove-confirm').click();
    check(await page.evaluate(()=>centerState.published.length===0&&state.works.length===2),'remove upload keeps acquired personal copies');
    for(const width of [375,390]){
      await page.setViewportSize({width,height:844});await page.locator('[data-app-group="center"]').click();
      check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&document.getElementById('works-panel').scrollWidth<=document.getElementById('works-panel').clientWidth),'no overflow at '+width);
    }
    await page.locator('[data-app-group="device"]').click();
    check(await page.locator('.builtin-tile').count()===14,'original device directory remains');
    check(errors.length===0,'no JavaScript errors');check(external.length===0,'no external requests');
    console.log(JSON.stringify({checks,errors,external,screenshots:os.tmpdir()}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
