(() => {
  const key = 'tourpass-reduce-motion';
  const checkbox = document.querySelector('#reduce-motion');
  try {
    document.documentElement.dataset.reduceMotion = String(localStorage.getItem(key) === 'true');
  } catch (_) { /* Browser storage can be unavailable. */ }
  if (!checkbox) return;
  checkbox.checked = document.documentElement.dataset.reduceMotion === 'true';
  checkbox.addEventListener('change', () => {
    document.documentElement.dataset.reduceMotion = String(checkbox.checked);
    const status = document.querySelector('#preference-status');
    try {
      localStorage.setItem(key, String(checkbox.checked));
      status.textContent = '화면 설정을 저장했습니다.';
    } catch (_) {
      status.textContent = '이 브라우저에는 저장할 수 없어 현재 화면에만 적용합니다.';
    }
  });
})();
