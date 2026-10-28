// Anti-flash: applies the theme before paint (localStorage -> prefers-color-scheme).
// It lives in an external file so the production CSP does not need an inline-script hash.
(function () {
  try {
    var stored = localStorage.getItem('claseya.theme');
    var dark = stored ? stored === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.classList.toggle('dark', dark);
  } catch (e) {}
})();
