const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

// Use a disposable database: this check confirms a trip without purchasing a pass.
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 360, height: 784 }, reducedMotion: 'reduce' });
    const root = process.argv[2] || 'http://localhost:8080/siheung-tourpass';
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(root + '/login');
    if (await page.locator('.kakao-login').isDisabled()) {
      assert(await page.locator('.login-notice').isVisible());
    }
    await page.getByLabel('아이디', { exact: true }).fill('visitor');
    await page.getByLabel('비밀번호', { exact: true }).fill('DemoPass!2026');
    await Promise.all([page.waitForURL(root + '/'), page.getByRole('button', { name: '로그인', exact: true }).click()]);
    await page.goto(root + '/merchants');
    const start = page.locator('a[href*="/journeys/new?productId="]').first();
    assert(await start.count(), 'Seed an available product with benefit places');
    const productId = new URL(await start.getAttribute('href'), root).searchParams.get('productId');
    await start.click();
    await page.waitForSelector('.is-deck-mode');
    await page.locator('#card-search').fill('검색결과없는문자열-ux-check');
    assert(await page.locator('#builder-empty').isVisible());
    await page.locator('#builder-empty button').click();
    await page.waitForFunction(() => document.querySelector('#builder-empty').hidden);
    assert.equal(await page.locator('#card-search').inputValue(), '');
    assert(Number(await page.locator('#sheet-count').textContent().then(text => text.replace(/\D/g, ''))) > 0, 'Reset must preserve selected cards');
    await page.locator('#builder-next').click();
    assert(await page.locator('#purchase-journey').isVisible());
    assert(await page.locator('#confirm-journey').isVisible());
    assert(await page.locator('#confirm-journey').evaluate(element => element.classList.contains('secondary')));
    for (const [width, height] of [[360, 784], [1920, 1080]]) {
      await page.setViewportSize({ width, height });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      for (const id of ['purchase-journey', 'confirm-journey', 'save-journey']) {
        const button = page.locator('#' + id);
        assert(await button.evaluate(element => element.scrollWidth <= element.clientWidth), id + ' must fit');
      }
    }
    await page.locator('[name=name]').fill('구매 없이 확정 UX 검사');
    await page.locator('#confirm-journey').click();
    await page.waitForURL(/\/journeys\/detail\?id=/);
    assert(await page.locator('.journey-business-card h1').textContent().then(text => text.includes('구매 없이 확정')));
    assert.equal(await page.locator('.trip-pass-qr').count(), 0);
    // A missing trip keeps the visitor in the trip flow instead of sending them home.
    const response = await page.goto(root + '/journeys/detail?id=999999999');
    assert.equal(response.status(), 404);
    const recovery = page.locator('.recovery-panel a.button:not(.secondary)');
    assert.equal(await recovery.getAttribute('href'), new URL(root + '/journeys').pathname);
    await recovery.click();
    await page.waitForURL(root + '/journeys');
    const profile = await (await page.request.get(root + '/profile')).text();
    const csrf = profile.match(/name="csrf" value="([^"]+)"/)[1];
    const issue = await page.request.post(root + '/passes/issue', { form: { csrf, productId } });
    assert([200, 409].includes(issue.status()));
    await page.goto(root + '/passes/checkout?productId=' + productId);
    assert.equal(await page.locator('.checkout-pay form').count(), 0, 'An overlapping pass cannot be purchased');
    await page.locator('.checkout-pay a.button').click();
    await page.waitForURL(root + '/passes');
    const forbidden = await page.goto(root + '/admin');
    assert.equal(forbidden.status(), 403);
    assert.equal(await recovery.getAttribute('href'), new URL(root + '/explore').pathname);
    const missing = await page.goto(root + '/ux-missing-page');
    assert.equal(missing.status(), 404);
    assert(await recovery.isVisible());
    assert.deepEqual(errors, []);
    console.log('PASS: empty search recovery, optional purchase, responsive actions, contextual errors and checkout conflict recovery');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
