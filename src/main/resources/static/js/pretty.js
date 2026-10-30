(function () {
  'use strict';
  document.querySelectorAll('pre.code').forEach(function (pre) {
    var text = pre.textContent.trim();
    if (!text || (text[0] !== '{' && text[0] !== '[')) return;
    try {
      pre.textContent = JSON.stringify(JSON.parse(text), null, 2);
    } catch (e) {
      // leave non-JSON or truncated bodies as they are
    }
  });
})();
