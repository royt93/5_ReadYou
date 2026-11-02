package com.mckimquyen.reader.domain.mirror

/**
 * The single static page the browser receives. Article text never appears in it: the page fetches JSON and puts every
 * value into the DOM with `textContent`, so a hostile article cannot run script. It loads nothing from outside and the
 * server pairs it with a strict Content-Security-Policy. The wording is fixed English because a desktop browser does
 * not follow the app's language setting.
 */
object MirrorHtml {
    const val PAGE: String = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Second Screen</title>
<style>
:root{color-scheme:light dark;--bg:#fbfcfe;--fg:#1a1c1e;--muted:#5d6670}
@media (prefers-color-scheme:dark){:root{--bg:#111416;--fg:#e2e3e6;--muted:#a3abb4}}
body{margin:0;background:var(--bg);color:var(--fg);font:18px/1.7 system-ui,sans-serif}
main{max-width:46rem;margin:0 auto;padding:2rem 1.25rem 4rem}
h1{font-size:1.9rem;line-height:1.25;margin:0 0 .5rem}
.meta{color:var(--muted);font-size:.95rem;margin-bottom:2rem}
p{margin:0 0 1.1rem}
#status{color:var(--muted);font-size:.9rem;margin-top:2.5rem}
</style></head><body><main>
<h1 id="title">Waiting for an article…</h1>
<div class="meta" id="meta"></div>
<div id="body"></div>
<div id="status"></div>
</main><script>
(function () {
  var base = location.pathname;
  var titleEl = document.getElementById('title');
  var metaEl = document.getElementById('meta');
  var bodyEl = document.getElementById('body');
  var statusEl = document.getElementById('status');
  var lastVersion = -1;
  function show(a) {
    if (!a || a.version === lastVersion) return;
    lastVersion = a.version;
    titleEl.textContent = a.title || 'Waiting for an article…';
    document.title = a.title || 'Second Screen';
    var parts = [];
    if (a.source) parts.push(a.source);
    if (a.date) parts.push(new Date(a.date).toLocaleDateString());
    metaEl.textContent = parts.join(' · ');
    bodyEl.textContent = '';
    (a.paragraphs || []).forEach(function (text) {
      var p = document.createElement('p');
      p.textContent = text;
      bodyEl.appendChild(p);
    });
    statusEl.textContent = a.truncated ? 'This article was shortened.' : '';
    window.scrollTo(0, 0);
  }
  fetch(base + '/state').then(function (r) { return r.json(); }).then(show).catch(function () {});
  var es = new EventSource(base + '/events');
  es.addEventListener('article', function (e) { try { show(JSON.parse(e.data)); } catch (x) {} });
  es.addEventListener('ended', function () { statusEl.textContent = 'Session ended'; es.close(); });
})();
</script></body></html>"""
}
