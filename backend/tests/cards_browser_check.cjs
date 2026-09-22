const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
async function login(request, root) {
 const session=await (await request.get(root+'/api/v1/session')).json();
 const response=await request.post(root+'/api/v1/session',{headers:{'X-CSRF-Token':session.csrfToken},data:{loginId:'visitor',password:'DemoPass!2026'}});
 assert.equal(response.status(),200);
 return response.json();
}
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 const page=await browser.newPage({viewport:{width:1280,height:900}});
 const root=process.argv[2] || 'http://localhost:8080/siheung-tourpass';
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(root+'/explore');await page.waitForSelector('.is-deck-mode');
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 for(const width of [320,375,414,768,1280]){
  await page.setViewportSize({width,height:900});
  for(const kind of ['event','merchant','place']){
   await page.locator(`[data-card-type=${kind}]`).click();await page.waitForTimeout(150);
   const card=page.locator('.builder-card.is-current');
   const select=card.locator('.card-select-toggle');
   await select.click({position:{x:80,y:100}});
   assert.equal(await select.getAttribute('aria-pressed'),'true');
   assert(await card.evaluate(el=>el.classList.contains('is-selected')));
   await card.locator('.card-front .card-flip').click();await page.waitForTimeout(330);
   assert(await card.evaluate(el=>el.classList.contains('is-flipped')));
   const button=await card.locator('.card-back .card-flip').boundingBox();const bounds=await card.boundingBox();
   assert(button.y>bounds.y+bounds.height*.75,JSON.stringify({width,kind,button,bounds}));
   await card.locator('.card-back .card-flip').click();await page.waitForTimeout(330);
   await select.focus();await page.keyboard.press('Space');assert.equal(await select.getAttribute('aria-pressed'),'false');
  }
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }
 await page.locator('[data-card-type=place]').click();
 const active=page.locator('.builder-card.is-current');
 await active.locator('.card-front .card-flip').click();
 await page.locator('[data-card-type=event]').click();
 assert.equal(await page.locator('.builder-card.is-flipped').count(),0);
 assert.equal(await page.locator('.builder-card[aria-current=true]').count(),1);
 const session=await login(page.request,root);let r;
 await page.goto(root+'/journeys/new');await page.waitForSelector('.is-deck-mode');await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 const keys=[];
 for(const kind of ['event','merchant','place']){
  await page.locator(`[data-card-type=${kind}]`).click();const card=page.locator('.builder-card.is-current');keys.push(await card.getAttribute('data-card-key'));await card.locator('.card-select-toggle').click({position:{x:80,y:100}});
 }
 await page.locator('#open-deck').click();assert.equal(await page.locator('#selected-places li').count(),3);
 assert.deepEqual(await page.locator('#selected-places li').first().locator('button').allTextContents(),['↑','↓','×']);
 for (const control of await page.locator('.selection-controls button').all()) {
  const box = await control.boundingBox(); assert.equal(box.width,44); assert.equal(box.height,44); assert(await control.getAttribute('aria-label'));
 }
 await page.locator('#selected-places li').last().getByRole('button',{name:/위로/}).click();
 assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'위로');
 const order=[keys[0],keys[2],keys[1]];
 await page.locator('#close-deck').click();
 await page.evaluate(()=>window.dispatchEvent(new Event('pageshow')));
 await page.locator('.builder-filter-panel > summary').click();
 await page.locator('#card-search').fill('no matching cards xyz');
 assert.equal(await page.locator('.builder-card:visible').count(),0);
 assert.equal(await page.locator('#builder-empty').isVisible(),true);
 await page.locator('#card-search').fill('');
 await page.locator('#shuffle-cards').click();
 assert.deepEqual(await page.locator('#selected-inputs input').evaluateAll(inputs=>inputs.map(input=>input.value)),order);
 await page.locator('#open-deck').click();
 await page.locator('input[name=name]').fill('통합 카드 브라우저 검증');await page.locator('#save-journey').click();await page.waitForLoadState();
 const id=new URL(page.url()).searchParams.get('id');assert(id);
 try{
  await page.waitForSelector('.is-deck-mode');
  assert.equal(await page.locator('.card-select-toggle[aria-pressed=true]').count(),3);
  r=await page.request.get(root+'/api/v1/me/journeys/'+id);let saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order);
  await page.locator('#open-deck').click();assert.deepEqual(await page.locator('#selected-places li').evaluateAll(items=>items.map(x=>x.dataset.selectedId)),order);
  await page.locator('#selected-places li').first().getByRole('button',{name:/선택 해제/}).click();assert.equal(await page.locator('#selected-places li').count(),2);
  await page.locator('#save-journey').click();await page.waitForLoadState();
  r=await page.request.get(root+'/api/v1/me/journeys/'+id);saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order.slice(1));
 }finally{r=await page.request.delete(root+'/api/v1/me/journeys/'+id,{headers:{'X-CSRF-Token':session.csrfToken},data:{}});assert.equal(r.status(),204);}
 // The server-rendered cards remain usable without JavaScript.
 const plainContext=await browser.newContext({javaScriptEnabled:false});
 const plain=await plainContext.newPage();
 const plainSession=await login(plain.request,root);
 await plain.goto(root+'/journeys/new');
 assert.equal(await plain.locator('.card-select-toggle:visible').count(),0);
 const pick=plain.locator('.builder-card[data-card-kind=place] input[type=checkbox]').first();
 await pick.check();const plainKey=await pick.getAttribute('value');
 await plain.locator('input[name=name]').fill('스크립트 없이 저장');
 await plain.locator('#save-journey').click();await plain.waitForLoadState();
 const plainId=new URL(plain.url()).searchParams.get('id');assert(plainId);
 try {
  assert.equal(await plain.locator(`input[value="${plainKey}"]`).isChecked(),true);
  r=await plain.request.get(root+'/api/v1/me/journeys/'+plainId);const saved=await r.json();
  assert.deepEqual(saved.places.map(item=>item.cardKey),[plainKey]);
 } finally {
  r=await plain.request.delete(root+'/api/v1/me/journeys/'+plainId,{headers:{'X-CSRF-Token':plainSession.csrfToken},data:{}});assert.equal(r.status(),204);
 }
 await plainContext.close();
 assert.deepEqual(errors,[]);
 await browser.close();console.log('PASS: all three card kinds share click/keyboard/flip/selection UX at five widths; mixed order, save/reload, removal and no-JavaScript saving persist.');
})().catch(e=>{console.error(e);process.exit(1)});
