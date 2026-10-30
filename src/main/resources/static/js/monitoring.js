(function () {
  'use strict';

  var root = document.getElementById('monitoring');
  if (!root) return;

  function setText(id, value) {
    var node = document.getElementById(id);
    if (node) node.textContent = value;
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function render(d) {
    var pill = document.getElementById('m-status-pill');
    pill.textContent = 'Application ' + d.status;
    pill.className = 'pill ' + (d.status === 'UP' ? 'good' : 'critical');

    setText('m-uptime', d.uptime);
    setText('m-pod', d.pod);
    setText('m-heap', d.heapUsedMb);
    setText('m-heap-pct', d.heapPercent);
    setText('m-heap-max', d.heapMaxMb);
    setText('m-cpu', d.processCpuPercent);
    setText('m-sys-cpu', d.systemCpuPercent);
    setText('m-threads', d.liveThreads);
    setText('m-db', d.dbActiveConnections);
    setText('m-db-idle', d.dbIdleConnections);
    setText('m-approved', d.approvedTotal);
    setText('m-rejected', d.rejectedTotal);
    setText('m-login-fail', d.loginFailures);

    document.getElementById('m-components').innerHTML = (d.components || []).map(function (c) {
      return '<tr><td class="mono">' + esc(c.name) + '</td><td><span class="pill ' +
        (c.status === 'UP' ? 'good' : 'critical') + '">' + esc(c.status) + '</span></td></tr>';
    }).join('');

    var rows = (d.endpoints || []).map(function (e) {
      return '<tr><td class="mono">' + esc(e.method) + '</td><td class="mono">' + esc(e.uri) + '</td><td>' +
        esc(e.status) + '</td><td class="num">' + e.count + '</td><td class="num">' + e.avgMs +
        '</td><td class="num">' + e.maxMs + '</td></tr>';
    }).join('');
    document.getElementById('m-endpoints').innerHTML = rows ||
      '<tr><td colspan="6" class="empty">No API traffic on this pod yet.</td></tr>';

    setText('m-refreshed', 'Updated ' + new Date().toLocaleTimeString('en-GB') + ' · auto-refresh every 10 seconds');
  }

  function load() {
    fetch(root.dataset.endpoint, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
      .then(function (res) {
        if (res.redirected || res.status === 401 || res.status === 403) {
          window.location.reload();
          return null;
        }
        return res.json();
      })
      .then(function (d) { if (d) render(d); })
      .catch(function () { setText('m-refreshed', 'Refresh failed, retrying...'); });
  }

  setInterval(load, 10000);
})();
