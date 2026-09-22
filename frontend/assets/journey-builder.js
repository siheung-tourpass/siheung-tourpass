(() => {
  const form = document.querySelector('#journey-builder');
  if (!form) return;
  const filters = document.querySelector('#journey-filters');
  const browse = form.querySelector('#browse-cards');
  const cards = [...browse.children];
  const byId = new Map(cards.map(card => [card.dataset.cardKey, card]));
  let selected = new Set();
  const list = form.querySelector('#selected-places');
  const inputs = form.querySelector('#selected-inputs');
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
  let scrollFrame;
  let kind = 'all';
  let request;

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

  function updateActiveCard(card) {
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
    const index = filteredCards.indexOf(activeCard);
    form.querySelector('#deck-position').textContent = activeCard ? `${index + 1} / ${filteredCards.length}` : '0 / 0';
    previous.disabled = !activeCard || index === 0;
    next.disabled = !activeCard || index === filteredCards.length - 1;
    updateCardDepth();
    stage.setAttribute('aria-label', activeCard ? `${activeCard.querySelector('h3').textContent} · 좌우로 밀어 탐색` : '여행 카드 덱');
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

  function scrollToActive(behavior = 'instant') {
    if (!activeCard) return;
    CardScroll.scrollTo(browse, activeCard, behavior);
    updateCardDepth();
  }

  function activate(card, behavior = 'instant') {
    updateActiveCard(card);
    scrollToActive(behavior);
  }

  function advance(offset) {
    const index = filteredCards.indexOf(activeCard);
    activate(filteredCards[Math.max(0, Math.min(filteredCards.length - 1, index + offset))], 'smooth');
  }

  browse.addEventListener('scroll', () => {
    if (scrollFrame) return;
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = null;
      updateActiveCard(CardScroll.nearest(browse, filteredCards));
    });
  }, { passive: true });
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
  window.addEventListener('resize', () => requestAnimationFrame(() => scrollToActive()));
  previous.addEventListener('click', () => advance(-1));
  next.addEventListener('click', () => advance(1));
  stage.addEventListener('keydown', event => {
    if (event.target.closest('input, button, a, summary, details')) return;
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    advance(event.key === 'ArrowLeft' ? -1 : 1);
  });
  browse.addEventListener('focusin', event => {
    const card = event.target.closest('.builder-card');
    if (!card || card === activeCard) return;
    activate(card);
  });
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
    updateActiveCard(null);
    filterCards();
    status.dataset.state = 'success';
    status.textContent = '카드를 섞었어요. 선택한 관광지와 여행 순서는 그대로예요.';
  });

  function selectionButton(icon, action, label, onClick) {
    const control = document.createElement('button');
    control.type = 'button';
    control.className = 'secondary';
    control.setAttribute('aria-label', label);
    control.addEventListener('click', onClick);
    const glyph = document.createElement('span');
    glyph.textContent = icon;
    glyph.setAttribute('aria-hidden', 'true');
    control.append(glyph);
    control.dataset.action = action;
    control.title = action;
    return control;
  }

  function renderSelection(focus) {
    list.replaceChildren();
    inputs.replaceChildren();
    const ids = [...selected];
    ids.forEach((id, index) => {
      const name = byId.get(id).querySelector('h3').textContent;
      const item = document.createElement('li');
      const title = document.createElement('span');
      title.textContent = `${index + 1}. ${name}`;
      item.append(title);
      const controls = document.createElement('div');
      controls.className = 'selection-controls';
      for (const [text, icon, offset] of [['위로', '↑', -1], ['아래로', '↓', 1]]) {
        const move = selectionButton(icon, text, `${name} ${text}`, () => {
          const reordered = [...selected];
          const to = index + offset;
          [reordered[index], reordered[to]] = [reordered[to], reordered[index]];
          selected = new Set(reordered);
          renderSelection({ id, text });
        });
        move.disabled = index + offset < 0 || index + offset >= ids.length;
        controls.append(move);
      }
      const remove = selectionButton('×', '빼기', `${name} 선택 해제`, () => {
        selected.delete(id);
        renderSelection({ id: ids[index + 1] || ids[index - 1], text: '빼기' });
        if (!selected.size) {
          const card = byId.get(id);
          (dialog.open || card.hidden ? form.querySelector('#selection-summary') : card.querySelector('input[type=checkbox]')).focus();
        }
      });
      controls.append(remove);
      item.dataset.selectedId = id;
      item.append(controls);
      list.append(item);
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'cardKey';
      input.value = id;
      inputs.append(input);
    });
    cards.forEach(card => {
      const checked = selected.has(card.dataset.cardKey);
      card.querySelector('input[type=checkbox]').checked = checked;
      card.classList.toggle('is-selected', checked);
      card.querySelector('.place-pick span').textContent = checked ? '✓ 내 여행에 담김' : '여행에 담기';
      card.querySelector('.card-select-toggle')?.setAttribute('aria-pressed', String(checked));
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

  function syncSelection(card) {
    const id = card.dataset.cardKey;
    if (card.querySelector('input[type=checkbox]').checked) selected.add(id);
    else selected.delete(id);
  }

  cards.forEach(card => {
    const checkbox = card.querySelector('input[type=checkbox]');
    checkbox.removeAttribute('name');
    syncSelection(card);
  });
  browse.addEventListener('change', event => {
    if (!event.target.matches('input[type=checkbox]')) return;
    const card = event.target.closest('.builder-card');
    syncSelection(card);
    renderSelection();
    feedback.textContent = `${card.querySelector('h3').textContent} ${event.target.checked ? '여행에 담았어요' : '여행에서 뺐어요'} · 총 ${selected.size}장`;
  });

  // Restore the saved mixed-card order rather than the catalog's type grouping.
  selected = new Set([...(form.dataset.selectionOrder || '').split(','), ...selected].filter(key => selected.has(key)));

  function showResults(items) {
    cards.forEach(card => {
      const match = items.find(item => `place:${item.place.id}` === card.dataset.cardKey);
      const reason = card.querySelector('.result-reason');
      if (!reason) return;
      reason.textContent = match ? `찾은 이유: ${match.explanation}` : '';
      reason.hidden = !match;
      card.classList.toggle('is-match', !!match);
    });
    const ranked = items.map(item => byId.get(`place:${item.place.id}`)).filter(Boolean);
    browse.append(...ranked, ...cards.filter(card => !card.classList.contains('is-match')));
    updateActiveCard(null);
    filterCards();
  }

  async function recommend() {
    request?.abort();
    const current = new AbortController();
    request = current;
    showResults([]);
    status.dataset.state = 'loading';
    status.textContent = '취향에 맞는 카드를 찾고 있어요…';
    filters.setAttribute('aria-busy', 'true');
    try {
      const query = new URLSearchParams([...new FormData(filters)].filter(([, value]) => value !== ''));
      const response = await fetch(`${filters.dataset.recommendationsUrl}?${query}`, { signal: current.signal });
      if (!response.ok) throw new Error('recommendations');
      const data = await response.json();
      if (request !== current) return;
      showResults(data.items);
      status.dataset.state = 'success';
      status.textContent = data.items.length ? '취향에 맞는 관광지 카드 최대 3장을 강조했어요. 검색·종류 조건으로 더 좁혀 보세요.' : '이 지역에 취향으로 찾을 관광지가 없습니다. 지역을 바꾸거나 다른 종류의 카드를 살펴보세요.';
    } catch (error) {
      if (error.name === 'AbortError') return;
      status.dataset.state = 'error';
      status.textContent = '취향 찾기를 불러오지 못했어요. 맞는 카드 찾기를 다시 누르거나 보드에서 직접 골라 주세요.';
    } finally {
      if (request === current) filters.removeAttribute('aria-busy');
    }
  }

  filters.elements.regionId.addEventListener('change', recommend);
  filters.addEventListener('submit', event => { event.preventDefault(); recommend(); filters.closest('details').open = false; });
  filters.addEventListener('reset', () => { setTimeout(recommend, 0); });
  window.addEventListener('pageshow', () => {
    cards.forEach(syncSelection);
    renderSelection();
    filterCards();
  });
  function setFlipped(card, flipped) {
    const front = card.querySelector('.card-front');
    const back = card.querySelector('.card-back');
    if (!front || !back) return;
    card.classList.toggle('is-flipped', flipped);
    front.inert = flipped;
    back.inert = !flipped;
    front.setAttribute('aria-hidden', String(flipped));
    back.setAttribute('aria-hidden', String(!flipped));
    if (!flipped) back.querySelector('.card-detail-content').scrollTop = 0;
  }
  document.querySelector('#builder-filters').disabled = false;
  cards.forEach(card => {
    const back = card.querySelector('.card-back');
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
      card.querySelector(`[data-flip=${flipped ? 'front' : 'back'}]`).focus({ preventScroll: true });
    } else {
      const checkbox = card.querySelector('input[type=checkbox]');
      checkbox.checked = !selected.has(card.dataset.cardKey);
      checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    }
  });
  document.body.classList.add('is-deck-mode');
  dialog.close();
  form.querySelector('#close-deck').hidden = false;
  form.querySelector('.deck-navigation').hidden = false;
  bar.hidden = false;
  form.querySelector('.card-tools').hidden = false;
  renderSelection();
  filterCards();
  recommend();
})();
