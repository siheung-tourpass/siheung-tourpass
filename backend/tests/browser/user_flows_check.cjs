const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = process.argv[2] || 'http://localhost:8080/siheung-tourpass';
const output = process.argv[3] || '.runtime/user-flows';

// Use a fresh disposable DB with tourism-sql --replace-demo and tourpass-sql data.
// This flow buys, redeems and archives a trip; those records intentionally remain.
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const errors = [];
  async function pageFor(loginId) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    if (loginId) {
      await page.goto(root + '/login');
      await page.locator('[name=loginId]').fill(loginId);
      await page.locator('[name=password]').fill('DemoPass!2026');
      await Promise.all([page.waitForURL(url => !url.pathname.endsWith('/login')), page.getByRole('button', { name: '로그인', exact: true }).click()]);
    }
    return page;
  }
  async function capture(page, name, target = true) {
    await page.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].filter(image => image.complete).map(image => image.decode().catch(() => {})));
    });
    if (target !== false) {
      await page.evaluate(selector => {
        const element = typeof selector === 'string' ? document.querySelector(selector) : null;
        scrollTo({ top: element ? element.getBoundingClientRect().top + scrollY - 90 : 0, behavior: 'instant' });
      }, target);
    }
    const png = await page.screenshot({ path: path.join(output, name + '.png'), fullPage: false, animations: 'disabled',
      mask: [page.locator('.code'), page.locator('.trip-pass-qr'), page.locator('input[name=code]')], maskColor: '#dce7e2' });
    assert.equal(png.readUInt32BE(16), page.viewportSize().width, name + ' width');
    assert.equal(png.readUInt32BE(20), page.viewportSize().height, name + ' height');
  }
  try {
    const visitor = await pageFor();
    await visitor.goto(root + '/'); await capture(visitor, '01-landing', false);
    await visitor.goto(root + '/explore'); await visitor.waitForSelector('.is-deck-mode');
    await visitor.locator('#card-search').fill('NO_MATCH_FLOW_REVIEW');
    assert(await visitor.locator('#builder-empty').isVisible());
    await capture(visitor, '02-empty-search', false);
    await visitor.locator('#builder-empty button').click();
    assert.equal(await visitor.locator('#card-search').inputValue(), '');
    await visitor.goto(root + '/login'); await capture(visitor, '03-login');
    await visitor.locator('[name=loginId]').fill('visitor');
    await visitor.locator('[name=password]').fill('DemoPass!2026');
    await Promise.all([visitor.waitForURL(root + '/'), visitor.getByRole('button', { name: '로그인', exact: true }).click()]);
    await visitor.goto(root + '/merchants'); await capture(visitor, '04-products');
    await visitor.getByRole('link', { name: '이 상품으로 여행 시작', exact: true }).first().click();
    await visitor.waitForSelector('.is-deck-mode'); await capture(visitor, '05-builder', '#builder-browse-section');
    await visitor.locator('#deck-sheet-handle').click(); await capture(visitor, '06-selected-map', false);
    await visitor.keyboard.press('Escape');
    await visitor.locator('#builder-next').click();
    await visitor.locator('[name=name]').fill('오이도에서 보내는 하루');
    await capture(visitor, '07-review');
    await capture(visitor, '07b-review-actions', '#journey-details');
    await visitor.locator('#purchase-journey').click(); await visitor.waitForURL(/\/passes\/checkout/);
    assert(await visitor.locator('.checkout-benefit-terms').count() > 0);
    assert.match(await visitor.locator('.checkout-benefit-terms').first().textContent(), /본인 부담/);
    await capture(visitor, '08-checkout');
    await capture(visitor, '08b-checkout-payment', '.checkout-pay');
    await visitor.locator('.checkout-pay button').click(); await visitor.waitForURL(/\/journeys\/detail\?id=/);
    const journeyUrl = visitor.url();
    const code = (await visitor.locator('.code').textContent()).trim();
    await capture(visitor, '09-pass-code', '.trip-pass');
    const staff = await pageFor('staff-a');
    await staff.goto(root + '/merchant/redeem');
    // The issued benefits API supplies the merchant used by the visible staff form.
    const passList = await (await visitor.request.get(root + '/api/v1/me/passes', {
      headers: { Authorization: 'Basic ' + Buffer.from('visitor:DemoPass!2026').toString('base64') }
    })).json();
    assert(passList.items.length > 0, 'Purchase must persist');
    const pass = await (await visitor.request.get(root + '/api/v1/me/passes/' + passList.items[0].id, {
      headers: { Authorization: 'Basic ' + Buffer.from('visitor:DemoPass!2026').toString('base64') }
    })).json();
    const merchantId = String(pass.benefits[0].merchantId);
    await staff.locator('[name=merchantId]').selectOption(merchantId);
    await staff.locator('[name=code]').fill(code); await capture(staff, '10-staff-lookup');
    await staff.getByRole('button', { name: '코드 조회', exact: true }).click();
    assert(await staff.getByRole('button', { name: '혜택 사용 처리', exact: true }).first().isEnabled());
    await capture(staff, '11-staff-confirm');
    await staff.getByRole('button', { name: '혜택 사용 처리', exact: true }).first().click();
    await staff.waitForURL(/\/merchant\/history/);
    assert(await staff.getByRole('link', { name: '다음 이용권 코드 조회', exact: true }).isVisible());
    await staff.locator('[name=merchantId]').selectOption(merchantId);
    await staff.getByRole('button', { name: '조회', exact: true }).click();
    assert(await staff.locator('.destination-card').count() > 0, 'History must show the merchant that redeemed the benefit');
    await capture(staff, '12-staff-history');
    await visitor.goto(root + '/feedback/history');
    await visitor.getByRole('link', { name: '피드백 작성·수정', exact: true }).first().click();
    assert.equal(await visitor.locator('.header-back').getAttribute('href'), new URL(root + '/feedback/history').pathname);
    await visitor.locator('[name=rating]').selectOption('5');
    await visitor.locator('[name=comment]').fill('장소 안내를 확인하고 편하게 이용했어요.');
    await capture(visitor, '13-feedback', 'main form');
    await visitor.getByRole('button', { name: '피드백 저장', exact: true }).click();
    await visitor.goto(journeyUrl);
    const finish = visitor.getByRole('button', { name: '여행 마치고 기록 남기기', exact: true });
    await finish.click();
    const popup = visitor.locator('#journey-finish-confirm');
    assert(await popup.evaluate(element => element.matches(':popover-open')));
    await capture(visitor, '14-finish-confirm', false);
    for (const [width, height] of [[320, 568], [1280, 900]]) {
      await visitor.setViewportSize({ width, height });
      const box = await popup.boundingBox();
      assert(box.x >= 0 && box.y >= 0 && box.x + box.width <= width && box.y + box.height <= height);
      assert(await visitor.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    }
    await visitor.keyboard.press('Escape');
    assert(await finish.evaluate(element => element === document.activeElement));
    await visitor.reload();
    assert(await finish.isVisible(), 'Closing the confirmation must keep the trip in progress');
    await visitor.setViewportSize({ width: 390, height: 844 });
    await finish.click();
    await visitor.getByRole('button', { name: '여행 완료하고 기록하기', exact: true }).click();
    await visitor.waitForSelector('.trip-story');
    const edit = visitor.locator('.trip-stop-edit').first(); await edit.locator('summary').click();
    const date = edit.locator('[name=visitedOn]');
    const today = await date.getAttribute('max'); assert.match(today, /^\d{4}-\d{2}-\d{2}$/);
    await date.fill('2099-01-01'); assert(await date.evaluate(element => element.validity.rangeOverflow));
    await date.fill(''); await edit.locator('label.star').last().click();
    assert(await date.inputValue() <= today);
    await edit.locator('[name=note]').fill('바다를 보며 걸었던 하루. 다음에는 노을도 보고 싶어요.');
    await edit.getByRole('button', { name: '기록 저장', exact: true }).click();
    await visitor.waitForURL(/#record-/);
    for (const width of [320, 390, 1280]) {
      await visitor.setViewportSize({ width, height: 844 });
      assert(await visitor.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      assert(await visitor.locator('.record-form .visually-hidden').first().evaluate(element => element.getBoundingClientRect().width <= 1));
    }
    await visitor.setViewportSize({ width: 390, height: 844 });
    await visitor.reload();
    await capture(visitor, '15-travel-record', '.trip-stop-edit');
    await visitor.locator('.trip-stop-edit summary').first().click();
    await capture(visitor, '15b-record-saved', '.trip-story');
    await visitor.getByRole('link', { name: '포토카드 보기', exact: true }).click();
    assert(await visitor.getByRole('link', { name: '여행 기록으로 돌아가기 →', exact: true }).isVisible());
    await capture(visitor, '16-photocard-empty');
    const admin = await pageFor('admin'); await admin.goto(root + '/admin'); await capture(admin, '17-admin-catalog');
    await admin.locator('.admin-place-card:visible .admin-card-select').first().click();
    await admin.getByRole('link', { name: '카드 출력', exact: true }).click(); await capture(admin, '18-admin-print-settings', '.goods-settings');
    await admin.getByRole('button', { name: '미리보기 만들기', exact: true }).click();
    assert(await admin.locator('.goods-card').count() > 0); await capture(admin, '19-admin-print-preview', '.goods-preview');
    await admin.setViewportSize({ width: 1280, height: 900 }); await capture(admin, '20-admin-desktop', '.goods-workbench');
    await visitor.goto(root + '/passes/checkout?productId=' + pass.productId);
    assert.equal(await visitor.locator('.checkout-pay form').count(), 0);
    await capture(visitor, '21-checkout-conflict', '.checkout-pay');
    await visitor.goto(root + '/journeys/detail?id=999999999');
    assert(await visitor.locator('.recovery-panel a.button').first().isVisible());
    await capture(visitor, '22-missing-trip');
    assert.deepEqual(errors, []);
    console.log('PASS: purchase → redemption → feedback → completion confirmation → record; screenshots: ' + output);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
