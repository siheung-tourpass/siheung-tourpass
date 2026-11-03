(() => {
  document.querySelectorAll('.admin-card-browser').forEach(browser => {
    const cards = [...browser.querySelectorAll('.admin-place-card')];
    const search = browser.querySelector('[data-admin-search]');
    const filters = [...browser.querySelectorAll('button[data-admin-kind]')];
    const more = browser.querySelector('.admin-browser-more');
    let kind = 'all', limit = 12;
    const render = () => {
      const needle = search.value.trim().toLocaleLowerCase();
      const matches = cards.filter(card => (kind === 'all' || card.dataset.adminKind === kind) && card.dataset.search.toLocaleLowerCase().includes(needle));
      const visible = new Set(matches.slice(0,limit));
      cards.forEach(card => { card.hidden = !visible.has(card); });
      browser.querySelector('.admin-browser-count').textContent = `${matches.length}개 중 ${visible.size}개 표시`;
      browser.querySelector('.admin-browser-empty').hidden = matches.length > 0;
      more.hidden = matches.length <= limit;
      filters.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.adminKind === kind)));
    };
    browser.querySelector('.admin-browser-tools').hidden = false;
    search.addEventListener('input', () => { limit = 12; render(); });
    filters.forEach(button => button.addEventListener('click', () => { kind = button.dataset.adminKind; limit = 12; render(); }));
    more.addEventListener('click', () => {
      const shown = new Set(cards.filter(card => !card.hidden));
      limit += 12; render();
      cards.find(card => !card.hidden && !shown.has(card))?.querySelector('a').focus();
    });
    render();
  });
})();

(() => {
  const form = document.querySelector('#goods-form');
  if (!form) return;
  const base = form.elements.baseUrl;
  if (!base.value) base.value = form.action.replace(/\/admin\/goods$/, '');
  form.addEventListener('invalid', event => {
    const details = event.target.closest('details');
    if (details) details.open = true;
  }, true);
  const print = document.querySelector('#goods-print');
  if (!print) return;
  form.addEventListener('input', () => {
    print.disabled = true;
    document.querySelector('.goods-dirty').hidden = false;
  });
})();
