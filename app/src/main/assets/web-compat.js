(() => {
  if (window.__lumirSessionObserver) return;
  window.__lumirSessionObserver = true;

  let previousTheme;
  const syncTheme = () => {
    const theme = document.documentElement.dataset.theme;
    if (theme !== 'light' && theme !== 'dark') return;
    if (theme === previousTheme) return;
    previousTheme = theme;
    window.Lumir.setTheme(theme === 'dark');
  };
  new MutationObserver(syncTheme).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  });
  syncTheme();

  const originalFetch = window.fetch.bind(window);
  window.fetch = async (...args) => {
    const response = await originalFetch(...args);
    try {
      const input = args[0];
      const url = new URL(input && typeof input === 'object' && 'url' in input ? input.url : input, location.href);
      if (url.origin === location.origin && response.ok) {
        if (['/api/logout', '/api/v1/auth/logout'].includes(url.pathname)) {
          window.Lumir.onSignedOut();
        } else if (['/api/session', '/api/v1/auth/session'].includes(url.pathname)) {
          const session = await response.clone().json();
          if (!session.authenticated) window.Lumir.onSignedOut();
        }
      }
    } catch (_) {}
    return response;
  };

  const originalOpen = window.open.bind(window);
  window.open = (url, target, features) => {
    try {
      const external = new URL(String(url || ''), location.href);
      if (external.origin !== location.origin && /^https?:$/.test(external.protocol)) {
        window.Lumir.copyLink(external.href);
        return null;
      }
    } catch (_) {}
    return originalOpen(url, target, features);
  };
})();
