const CardScroll = {
  scrollTo(rail, card, behavior = 'instant') {
    const reduced = document.documentElement.dataset.reduceMotion === 'true' || matchMedia('(prefers-reduced-motion: reduce)').matches;
    rail.scrollTo({ left: card.offsetLeft - (rail.clientWidth - card.offsetWidth) / 2, behavior: reduced ? 'instant' : behavior });
  },
  bind(rail, { cards, current, update, previous, next, keyboard = rail }) {
    let frame;
    const select = card => {
      const items = cards(), index = items.indexOf(card);
      previous.disabled = !card || index === 0;
      next.disabled = !card || index === items.length - 1;
      update(card, index, items.length);
    };
    const go = (card, behavior = 'instant') => {
      select(card);
      if (card) this.scrollTo(rail, card, behavior);
    };
    const advance = offset => {
      const items = cards();
      go(items[Math.max(0, Math.min(items.length - 1, items.indexOf(current()) + offset))], 'smooth');
    };
    previous.addEventListener('click', () => advance(-1));
    next.addEventListener('click', () => advance(1));
    keyboard.addEventListener('keydown', event => {
      if (event.target.closest('input, select, textarea, button, a, summary, details') || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      event.preventDefault();
      advance(event.key === 'ArrowLeft' ? -1 : 1);
    });
    rail.addEventListener('scroll', () => {
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = null;
        select(this.nearest(rail, cards()));
      });
    }, { passive: true });
    rail.addEventListener('focusin', event => {
      const card = cards().find(item => item.contains(event.target));
      if (card && card !== current()) go(card);
    });
    window.addEventListener('resize', () => requestAnimationFrame(() => go(current())));
    return go;
  },
  nearest(rail, cards) {
    const center = rail.scrollLeft + rail.clientWidth / 2;
    const distance = card => Math.abs(card.offsetLeft + card.offsetWidth / 2 - center);
    return cards.reduce((nearest, card) => !nearest || distance(card) < distance(nearest) ? card : nearest, null);
  }
};
