(() => {
  const form = document.querySelector('#journey-builder');
  if (!form) return;
  const filters = document.querySelector('#journey-filters');
  const browse = form.querySelector('#browse-cards');
  const cards = [...browse.children];
  const byId = new Map(cards.map(card => [card.dataset.cardKey, card]));
  const controls = new Map(cards.map(card => [card, {
    name: card.querySelector('h3').textContent,
    checkbox: card.querySelector('input[type=checkbox]'),
    label: card.querySelector('.place-pick span'),
    toggle: card.querySelector('.card-select-toggle'),
    reason: card.querySelector('.result-reason'),
    front: card.querySelector('.card-front'),
    back: card.querySelector('.card-back'),
    details: card.querySelector('.card-detail-content'),
    flipFront: card.querySelector('[data-flip=front]'),
    flipBack: card.querySelector('[data-flip=back]')
  }]));
  let selected = readSelection((form.dataset.selectionOrder || '').split(','));
  const list = form.querySelector('#selected-places');
  const selectionTemplate = form.querySelector('#selected-place-template');
  const bar = form.querySelector('.builder-selection-bar');
  const status = document.querySelector('#builder-status');
  const search = document.querySelector('#card-search');
  const date = document.querySelector('#card-date');
  const typeButtons = [...form.querySelectorAll('[data-card-type]')];
  const searchText = new Map(cards.map(card => [card, card.textContent.toLocaleLowerCase('ko')]));
  const stage = form.querySelector('#deck-stage');
  const dialog = form.querySelector('#deck-summary');
  const openDeck = form.querySelector('#open-deck');
  const previous = form.querySelector('#deck-previous');
  const next = form.querySelector('#deck-next');
  const feedback = form.querySelector('#deck-feedback');
  let filteredCards = [];
  let activeCard;
  let drag;
  let suppressClick = false;
  let kind = 'all';
  let request;

  function setStatus(state, text) {
    status.dataset.state = state;
    status.textContent = text;
  }

  function filterCards() {
    const region = filters.elements.regionId.value;
    const term = search.value.trim().toLocaleLowerCase('ko');
    filteredCards = [...browse.children].filter(card => {
      const wrongRegion = card.dataset.cardKind !== 'event' && card.dataset.savedOnly !== 'true' && region && card.dataset.regionId !== region;
      const wrongDate = card.dataset.cardKind === 'event' && card.dataset.savedOnly !== 'true' && date.value &&
        (date.value < card.dataset.startDate || date.value > card.dataset.endDate);
      card.hidden = !!(wrongRegion || wrongDate || (kind !== 'all' && kind !== card.dataset.cardKind) || !searchText.get(card).includes(term));
      return !card.hidden;
    });
    stage.hidden = filteredCards.length === 0;
    activate(filteredCards.includes(activeCard) ? activeCard : filteredCards[0]);
    form.querySelector('#builder-empty').hidden = filteredCards.length > 0;
    form.querySelector('#card-count').textContent = `${filteredCards.length}장 찾음`;
  }

  function updateActiveCard(card, index, total) {
    if (card !== activeCard) {
      if (activeCard) {
        activeCard.classList.remove('is-current');
        activeCard.removeAttribute('aria-current');
        setFlipped(activeCard, false);
      }
      activeCard = card;
      if (card) {
        card.classList.add('is-current');
        card.setAttribute('aria-current', 'true');
      }
    }
    form.querySelector('#deck-position').textContent = activeCard ? `${index + 1} / ${total}` : '0 / 0';
    updateCardDepth();
    stage.setAttribute('aria-label', activeCard ? `${controls.get(activeCard).name} · 좌우로 밀어 탐색` : '여행 카드 덱');
  }

  function updateCardDepth() {
    const center = browse.scrollLeft + browse.clientWidth / 2;
    filteredCards.forEach(card => {
      const distance = Math.max(-1, Math.min(1, (card.offsetLeft + card.offsetWidth / 2 - center) / card.offsetWidth));
      card.style.setProperty('--card-yaw', `${-distance * 16}deg`);
      card.style.setProperty('--card-depth', `${-Math.abs(distance) * 64}px`);
      card.style.setProperty('--card-scale', String(1 - Math.abs(distance) * .06));
    });
  }

  const activate = CardScroll.bind(browse, {
    cards: () => filteredCards, current: () => activeCard, update: updateActiveCard,
    previous, next, keyboard: stage
  });

  browse.addEventListener('wheel', event => {
    if (event.target.closest('.card-back') || Math.abs(event.deltaX) >= Math.abs(event.deltaY) || event.shiftKey) return;
    if ((browse.scrollLeft <= 1 && event.deltaY < 0) || (browse.scrollLeft >= browse.scrollWidth - browse.clientWidth - 1 && event.deltaY > 0)) return;
    event.preventDefault();
    browse.scrollBy({ left: event.deltaY, behavior: 'instant' });
  }, { passive: false });
  browse.addEventListener('pointerdown', event => {
    if (event.pointerType === 'touch' || event.button !== 0 || event.target.closest('input, button:not(.card-select-toggle), a, summary, label, .card-back')) return;
    suppressClick = false;
    drag = { x: event.clientX, scroll: browse.scrollLeft, moved: false };
  });
  browse.addEventListener('pointermove', event => {
    if (!drag) return;
    if (!drag.moved && Math.abs(event.clientX - drag.x) < 6) return;
    drag.moved = true;
    browse.setPointerCapture(event.pointerId);
    browse.classList.add('is-dragging');
    event.preventDefault();
    browse.scrollLeft = drag.scroll + drag.x - event.clientX;
  });
  function endDrag() {
    if (!drag) return;
    suppressClick = drag.moved;
    drag = null;
    browse.classList.remove('is-dragging');
    activate(CardScroll.nearest(browse, filteredCards), 'smooth');
  }
  browse.addEventListener('pointerup', endDrag);
  browse.addEventListener('pointercancel', endDrag);
  browse.addEventListener('click', event => {
    if (!suppressClick) return;
    suppressClick = false;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);
  function openSummary() {
    if (!dialog.open) dialog.showModal();
    openDeck.setAttribute('aria-expanded', 'true');
  }
  openDeck.addEventListener('click', openSummary);
  form.querySelector('#close-deck').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => openDeck.setAttribute('aria-expanded', 'false'));
  form.addEventListener('invalid', openSummary, true);
  form.addEventListener('submit', event => {
    if (!dialog.open) { event.preventDefault(); openSummary(); }
  });
  search.addEventListener('keydown', event => {
    if (event.key === 'Enter') { event.preventDefault(); filters.closest('details').open = false; stage.focus({ preventScroll: true }); }
  });

  search.addEventListener('input', filterCards);
  date.addEventListener('input', filterCards);
  typeButtons.forEach(control => control.addEventListener('click', () => {
    kind = control.dataset.cardType;
    typeButtons.forEach(item => item.setAttribute('aria-pressed', String(item === control)));
    filterCards();
  }));
  form.querySelector('#shuffle-cards').addEventListener('click', () => {
    const shuffled = [...cards];
    for (let index = shuffled.length - 1; index > 0; index--) {
      const other = Math.floor(Math.random() * (index + 1));
      [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
    }
    browse.append(...shuffled);
    activate(null);
    filterCards();
    setStatus('success', '카드를 섞었어요. 선택한 관광지와 여행 순서는 그대로예요.');
  });

  function renderSelection(focus) {
    list.replaceChildren();
    const ids = [...selected];
    ids.forEach((id, index) => {
      const name = controls.get(byId.get(id)).name;
      const item = selectionTemplate.content.firstElementChild.cloneNode(true);
      item.dataset.selectedId = id;
      item.querySelector('span').textContent = `${index + 1}. ${name}`;
      item.querySelector('input').value = id;
      item.querySelectorAll('button').forEach(button => {
        const action = button.dataset.action;
        button.setAttribute('aria-label', `${name} ${action === '빼기' ? '선택 해제' : action}`);
        button.disabled = action === '위로' && index === 0 || action === '아래로' && index === ids.length - 1;
      });
      list.append(item);
    });
    cards.forEach(card => {
      const checked = selected.has(card.dataset.cardKey);
      const { checkbox, label, toggle } = controls.get(card);
      checkbox.checked = checked;
      card.classList.toggle('is-selected', checked);
      label.textContent = checked ? '✓ 내 여행에 담김' : '여행에 담기';
      toggle.setAttribute('aria-pressed', String(checked));
    });
    form.querySelector('#selection-count').textContent = `${selected.size}장`;
    form.querySelector('#bar-count').textContent = `${selected.size}장 선택`;
    form.querySelector('#selection-empty').hidden = selected.size > 0;
    const save = form.querySelector('#confirm-journey');
    if (save) save.disabled = selected.size === 0;
    if (focus?.id) {
      const item = [...list.children].find(item => item.dataset.selectedId === focus.id);
      const controls = [...item.querySelectorAll('button')];
      (controls.find(control => control.dataset.action === focus.text && !control.disabled) || controls.find(control => !control.disabled)).focus();
    }
  }

  list.addEventListener('click', event => {
    const control = event.target.closest('button');
    if (!control) return;
    const id = control.closest('li').dataset.selectedId;
    const ids = [...selected];
    const index = ids.indexOf(id);
    if (control.dataset.action === '빼기') {
      selected.delete(id);
      renderSelection({ id: ids[index + 1] || ids[index - 1], text: '빼기' });
      if (!selected.size) {
        const card = byId.get(id);
        (dialog.open || card.hidden ? form.querySelector('#selection-summary') : controls.get(card).checkbox).focus();
      }
    } else {
      const to = index + (control.dataset.action === '위로' ? -1 : 1);
      [ids[index], ids[to]] = [ids[to], ids[index]];
      selected = new Set(ids);
      renderSelection({ id, text: control.dataset.action });
    }
  });

  function readSelection(order = selected) {
    return new Set([...order, ...byId.keys()].filter(id => controls.get(byId.get(id))?.checkbox.checked));
  }
  function selectCard(card, checked) {
    const id = card.dataset.cardKey;
    if (checked) selected.add(id);
    else selected.delete(id);
    renderSelection();
    feedback.textContent = `${controls.get(card).name} ${checked ? '여행에 담았어요' : '여행에서 뺐어요'} · 총 ${selected.size}장`;
  }
  browse.addEventListener('change', event => {
    if (event.target.matches('input[type=checkbox]')) selectCard(event.target.closest('.builder-card'), event.target.checked);
  });

  function showResults(items) {
    const results = new Map(items.map(item => [`place:${item.place.id}`, item]));
    cards.forEach(card => {
      const match = results.get(card.dataset.cardKey);
      const { reason } = controls.get(card);
      if (!reason) return;
      reason.textContent = match ? `찾은 이유: ${match.explanation}` : '';
      reason.hidden = !match;
      card.classList.toggle('is-match', !!match);
    });
    const ranked = [...results.keys()].map(id => byId.get(id)).filter(Boolean);
    browse.append(...ranked, ...cards.filter(card => !results.has(card.dataset.cardKey)));
    activate(null);
    filterCards();
  }

  async function recommend() {
    request?.abort();
    const current = new AbortController();
    request = current;
    showResults([]);
    setStatus('loading', '취향에 맞는 카드를 찾고 있어요…');
    filters.setAttribute('aria-busy', 'true');
    try {
      const query = new URLSearchParams([...new FormData(filters)].filter(([, value]) => value !== ''));
      const response = await fetch(`${filters.dataset.recommendationsUrl}?${query}`, { signal: current.signal });
      if (!response.ok) throw new Error('recommendations');
      const data = await response.json();
      if (request !== current) return;
      showResults(data.items);
      setStatus('success', data.items.length ? '취향에 맞는 관광지 카드 최대 3장을 강조했어요. 검색·종류 조건으로 더 좁혀 보세요.' : '이 지역에 취향으로 찾을 관광지가 없습니다. 지역을 바꾸거나 다른 종류의 카드를 살펴보세요.');
    } catch (error) {
      if (error.name === 'AbortError') return;
      setStatus('error', '취향 찾기를 불러오지 못했어요. 맞는 카드 찾기를 다시 누르거나 보드에서 직접 골라 주세요.');
    } finally {
      if (request === current) filters.removeAttribute('aria-busy');
    }
  }

  filters.elements.regionId.addEventListener('change', recommend);
  filters.addEventListener('submit', event => { event.preventDefault(); recommend(); filters.closest('details').open = false; });
  filters.addEventListener('reset', () => { setTimeout(recommend, 0); });
  window.addEventListener('pageshow', () => {
    selected = readSelection();
    renderSelection();
    filterCards();
  });
  function setFlipped(card, flipped) {
    const { front, back, details } = controls.get(card);
    if (!front || !back) return;
    card.classList.toggle('is-flipped', flipped);
    front.inert = flipped;
    back.inert = !flipped;
    front.setAttribute('aria-hidden', String(flipped));
    back.setAttribute('aria-hidden', String(!flipped));
    if (!flipped) details.scrollTop = 0;
  }
  document.querySelector('#builder-filters').disabled = false;
  cards.forEach(card => {
    const { back, checkbox } = controls.get(card);
    checkbox.removeAttribute('name');
    back.open = true;
    back.querySelector('summary').hidden = true;
    card.querySelectorAll('[data-flip], .card-select-toggle').forEach(control => { control.hidden = false; });
    setFlipped(card, false);
  });
  browse.addEventListener('click', event => {
    const control = event.target.closest('[data-flip], .card-select-toggle');
    if (!control) return;
    const card = control.closest('.builder-card');
    if (control.dataset.flip) {
      const flipped = control.dataset.flip === 'back';
      setFlipped(card, flipped);
      controls.get(card)[flipped ? 'flipFront' : 'flipBack'].focus({ preventScroll: true });
    } else {
      selectCard(card, !selected.has(card.dataset.cardKey));
    }
  });
  document.body.classList.add('is-deck-mode');
  dialog.close();
  form.querySelector('#close-deck').hidden = false;
  form.querySelector('.deck-navigation').hidden = false;
  bar.hidden = false;
  form.querySelector('.card-tools').hidden = false;
  renderSelection();
  recommend();
})();
