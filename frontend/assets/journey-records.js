(() => {
  const rail = document.querySelector('.record-cards');
  if (!rail) return;
  const cards = [...rail.children];
  const previous = document.querySelector('#record-previous');
  const next = document.querySelector('#record-next');
  const position = document.querySelector('#record-position');
  let frame;
  let index = Math.max(0, cards.findIndex(card => `#${card.id}` === location.hash));
  function update() {
    previous.disabled = index === 0;
    next.disabled = index === cards.length - 1;
    position.textContent = `${index + 1} / ${cards.length}`;
  }
  function go(to, behavior = 'smooth') {
    index = Math.max(0, Math.min(cards.length - 1, to));
    CardScroll.scrollTo(rail, cards[index], behavior);
    update();
  }
  previous.addEventListener('click', () => go(index - 1));
  next.addEventListener('click', () => go(index + 1));
  rail.addEventListener('keydown', event => {
    if (event.target.closest('input, select, textarea, button, a')) return;
    if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
    event.preventDefault();
    go(index + (event.key === 'ArrowLeft' ? -1 : 1));
  });
  rail.addEventListener('scroll', () => {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = null;
      index = cards.indexOf(CardScroll.nearest(rail, cards));
      update();
    });
  }, { passive: true });
  rail.addEventListener('focusin', event => {
    const card = event.target.closest('.journey-record-card');
    const to = cards.indexOf(card);
    if (to >= 0 && to !== index) go(to, 'instant');
  });
  window.addEventListener('resize', () => go(index, 'instant'));
  rail.classList.add('is-record-carousel');
  document.querySelector('.record-navigation').hidden = false;
  go(index, 'instant');
})();
