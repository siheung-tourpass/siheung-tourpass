const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
async function checkCenteredPopup(page, popup) {
 const viewport=page.viewportSize();
 for(const [width,height] of [[320,900],[375,900],[414,900],[768,900],[1280,900],[375,400]]){
  await page.setViewportSize({width,height});
  const box=await popup.boundingBox();assert(box);
  assert(Math.abs(box.x+box.width/2-width/2)<1,JSON.stringify({width,height,box}));
  assert(Math.abs(box.y+box.height/2-height/2)<1,JSON.stringify({width,height,box}));
  assert(box.x>=15&&box.x+box.width<=width-15);
  assert(box.y>=23&&box.y+box.height<=height-23);
  assert(await popup.evaluate(el=>el.scrollWidth<=el.clientWidth));
 }
 await page.setViewportSize(viewport);
}
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
 await page.goto(root+'/');
 const homePhoto=page.locator('.home-photo'),homeCredit=page.locator('#home-photo-credit');
 assert((await homePhoto.boundingBox()).y>=(await page.locator('.home-heading').boundingBox()).y+(await page.locator('.home-heading').boundingBox()).height);
 assert.equal(await homeCredit.isVisible(),false);
 await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).click();
 await checkCenteredPopup(page,homeCredit);
 assert.equal(await homeCredit.locator('a').count(),2);
 await page.keyboard.press('Escape');assert(await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).evaluate(el=>el===document.activeElement));
 await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).click();await homeCredit.getByRole('button',{name:'닫기',exact:true}).click();assert.equal(await homeCredit.isVisible(),false);
 await page.goto(root+'/explore');await page.waitForSelector('.is-deck-mode');
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 for(const width of [320,375,414,768,1280]){
  await page.setViewportSize({width,height:900});
  for(const kind of ['event','dining','place']){
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
 await page.goto(root+'/journeys');
 const picker=page.locator('#journey-start-picker');assert.equal(await picker.isVisible(),false);
 await page.getByRole('button',{name:'새 여행 만들기',exact:true}).click();
 assert.equal(await picker.locator('.journey-template').count(),4);
 await checkCenteredPopup(page,picker);
 await picker.getByRole('link',{name:'빈 구성으로 시작',exact:true}).click();await page.waitForSelector('.is-deck-mode');await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 const keys=[];
 for(const kind of ['event','dining','place']){
  await page.locator(`[data-card-type=${kind}]`).click();const card=page.locator('.builder-card.is-current');keys.push(await card.getAttribute('data-card-key'));await card.locator('.card-select-toggle').click({position:{x:80,y:100}});
 }
 await page.locator('#open-deck').click();assert.equal(await page.locator('#selected-places li').count(),3);
 await checkCenteredPopup(page,page.locator('#deck-summary'));
 assert.deepEqual(await page.locator('#selected-places li').first().locator('button').allTextContents(),['↑','↓','×']);
 for (const control of await page.locator('.selection-controls button').all()) {
  const box = await control.boundingBox(); assert.equal(box.width,44); assert.equal(box.height,44); assert(await control.getAttribute('aria-label'));
 }
 const firstControls=page.locator('#selected-places li').first().locator('.selection-controls');
 await firstControls.evaluate(el=>el.append(el.firstElementChild));
 assert.equal(await firstControls.locator('[data-action="위로"]').isDisabled(),true);
 assert.equal(await firstControls.locator('[data-action="아래로"]').isDisabled(),false);
 await page.locator('#selected-place-template').evaluate(el=>{
  const controls=el.content.querySelector('.selection-controls');controls.append(controls.firstElementChild);
 });
 await firstControls.locator('[data-action="아래로"]').click();
 assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'아래로');
 assert.equal(await firstControls.locator('[data-action="위로"]').isDisabled(),true);
 assert.match(await firstControls.locator('[data-action="빼기"]').getAttribute('aria-label'),/선택 해제$/);
 await page.locator('#selected-places li').nth(1).locator('[data-action="위로"]').click();
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
 assert.deepEqual(await page.locator('#selected-places input[name=cardKey]').evaluateAll(inputs=>inputs.map(input=>input.value)),order);
 await page.locator('#open-deck').click();
 await page.locator('input[name=name]').fill('통합 카드 브라우저 검증');await page.locator('#save-journey').click();await page.waitForLoadState();
 const id=new URL(page.url()).searchParams.get('id');assert(id);
 try{
  await page.waitForSelector('.is-deck-mode');
  assert.equal(await page.locator('.card-select-toggle[aria-pressed=true]').count(),3);
  r=await page.request.get(root+'/api/v1/me/journeys/'+id);let saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order);
  let expectedCover=null;
  for(const key of order){
   const photo=page.locator(`[data-card-key="${key}"] .card-background`);
   if(await photo.count()){expectedCover=await photo.getAttribute('src');break;}
  }
  await page.goto(root+'/journeys');
  const pass=page.locator('.journey-business-card').filter({has:page.locator(`a[href$="edit?id=${id}"]`)});
  assert.equal(await pass.locator('.pass-cover-image').count(),expectedCover?1:0);
  if(expectedCover)assert.equal(await pass.locator('.pass-cover-image').getAttribute('src'),expectedCover);
  assert.equal(await pass.locator('.button').count(),0);
  if(await pass.locator('.pass-cover-credit').count()){
   const before=await pass.boundingBox();
   await pass.locator('.pass-cover-trigger').click();assert.equal(new URL(page.url()).pathname.endsWith('/journeys'),true);
   assert(await pass.locator('.pass-cover-credit .pass-cover-popover').evaluate(el=>el.matches(':popover-open')));
   assert.deepEqual(await pass.boundingBox(),before);
   await checkCenteredPopup(page,pass.locator('.pass-cover-credit .pass-cover-popover'));
   await page.keyboard.press('Escape');assert(await pass.locator('.pass-cover-trigger').evaluate(el=>el===document.activeElement));
  }
  const deleteTrigger=pass.locator('.journey-ticket-delete');
  const deletePopover=pass.locator('.journey-delete-popover');
  const beforeDelete=await pass.boundingBox();
  await deleteTrigger.click();assert.equal(new URL(page.url()).pathname.endsWith('/journeys'),true);
  assert(await deletePopover.evaluate(el=>el.matches(':popover-open')));
  assert.deepEqual(await pass.boundingBox(),beforeDelete);
  assert.equal(await deletePopover.getByRole('button',{name:'이 패스 삭제',exact:true}).evaluate(el=>getComputedStyle(el).color),'rgb(255, 255, 255)');
  await checkCenteredPopup(page,deletePopover);
  await page.keyboard.press('Escape');assert(await deleteTrigger.evaluate(el=>el===document.activeElement));
  await deleteTrigger.click();await deletePopover.getByRole('button',{name:'닫기'}).click();
  assert(!await deletePopover.evaluate(el=>el.matches(':popover-open')));
  await deleteTrigger.click();await page.mouse.click(2,2);
  assert(!await deletePopover.evaluate(el=>el.matches(':popover-open')));
  const passBox=await pass.boundingBox();await page.mouse.click(passBox.x+passBox.width-12,passBox.y+passBox.height/2);
  await page.waitForURL(root+'/journeys/edit?id='+id);await page.waitForSelector('.is-deck-mode');
  await page.locator('#open-deck').click();assert.deepEqual(await page.locator('#selected-places li').evaluateAll(items=>items.map(x=>x.dataset.selectedId)),order);
  await page.locator('#selected-places li').first().getByRole('button',{name:/선택 해제/}).click();assert.equal(await page.locator('#selected-places li').count(),2);
  await page.locator('#save-journey').click();await page.waitForLoadState();
  r=await page.request.get(root+'/api/v1/me/journeys/'+id);saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order.slice(1));
  const beforeStatus=saved.places;
  r=await page.request.patch(root+'/api/v1/me/journeys/'+id,{headers:{'X-CSRF-Token':session.csrfToken},data:{status:'CONFIRMED'}});assert.equal(r.status(),200);
  for(const [current,next,label] of [['CONFIRMED','ARCHIVED','사용 완료 · 아카이브'],['ARCHIVED','CONFIRMED','제작 확정 목록으로 복원'],['CONFIRMED','PLANNING','제작 중으로 보내기']]){
   await page.goto(root+'/journeys?status='+current);
   const statusCard=page.locator('.journey-card').filter({has:page.locator(`a[href$="detail?id=${id}"]`)});
   await statusCard.locator('.journey-ticket-action').click();
   await checkCenteredPopup(page,statusCard.locator('section[id^=pass-status-]'));
   await statusCard.locator('section[id^=pass-status-]').getByRole('button',{name:label,exact:true}).click();
   await page.waitForURL(root+'/journeys?status='+next);
   r=await page.request.get(root+'/api/v1/me/journeys/'+id);const changed=await r.json();
   assert.equal(changed.status,next);assert.deepEqual(changed.places,beforeStatus);
  }
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
 await browser.close();console.log('PASS: all three card kinds share click/keyboard/flip/selection UX at five widths; mixed order, composition cover, save/reload, removal and no-JavaScript saving persist.');
})().catch(e=>{console.error(e);process.exit(1)});
