(() => {
  const rail = document.querySelector('.record-cards');
  if (!rail) return;
  const cards = [...rail.children];
  const previous = document.querySelector('#record-previous');
  const next = document.querySelector('#record-next');
  const position = document.querySelector('#record-position');
  let activeCard = cards[Math.max(0, cards.findIndex(card => `#${card.id}` === location.hash))];
  const go = CardScroll.bind(rail, {
    cards: () => cards, current: () => activeCard, previous, next,
    update(card, index, total) {
      activeCard = card;
      position.textContent = `${index + 1} / ${total}`;
    }
  });
  rail.classList.add('is-record-carousel');
  document.querySelector('.record-navigation').hidden = false;
  go(activeCard);
})();
