(() => {
  const button = document.querySelector('[data-photo-print]');
  if (!button) return;
  const status = document.querySelector('[data-photo-print-status]');
  button.hidden = false;
  button.addEventListener('click', async () => {
    button.disabled = true;
    status.textContent = '사진을 준비하고 있습니다.';
    try {
      await Promise.all([...document.querySelectorAll('.goods-photo img')].map(image => image.decode()));
      await document.fonts.ready;
      status.textContent = '';
      window.print();
    } catch { status.textContent = '사진을 불러오지 못했습니다. 새로 고친 뒤 다시 시도해 주세요.'; }
    finally { button.disabled = false; }
  });
})();
