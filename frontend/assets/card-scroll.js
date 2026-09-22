const CardScroll = {
  scrollTo(rail, card, behavior = 'instant') {
    const reduced = document.documentElement.dataset.reduceMotion === 'true' || matchMedia('(prefers-reduced-motion: reduce)').matches;
    rail.scrollTo({ left: card.offsetLeft - (rail.clientWidth - card.offsetWidth) / 2, behavior: reduced ? 'instant' : behavior });
  },
  nearest(rail, cards) {
    const center = rail.scrollLeft + rail.clientWidth / 2;
    const distance = card => Math.abs(card.offsetLeft + card.offsetWidth / 2 - center);
    return cards.reduce((nearest, card) => !nearest || distance(card) < distance(nearest) ? card : nearest, null);
  }
};
