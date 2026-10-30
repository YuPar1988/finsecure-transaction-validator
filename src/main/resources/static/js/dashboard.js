(function () {
  'use strict';

  var root = document.getElementById('dashboard');
  if (!root) return;

  var endpoint = root.dataset.endpoint + '?window=' + encodeURIComponent(root.dataset.window);
  var logUrl = root.dataset.logUrl;
  var zone = root.dataset.zone || undefined;
  var tooltip = document.getElementById('tooltip');
  var chartBox = document.getElementById('timeline-chart');
  var tableBox = document.getElementById('timeline-table');
  var last = null;

  var timeFmt = new Intl.DateTimeFormat('en-GB', {
    timeZone: zone, day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  });

  function setText(id, value) {
    var node = document.getElementById(id);
    if (node) node.textContent = value;
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function pillClass(code) {
    return code >= 500 ? 'critical' : code >= 400 ? 'warning' : 'good';
  }

  function render(stats) {
    last = stats;
    var f = FsCharts.format;
    setText('t-total', f(stats.totalRequests));
    setText('t-alltime', f(stats.allTimeRequests));
    setText('t-success', stats.successRate);
    setText('t-avg', stats.averageMs);
    setText('t-max', stats.maxMs);
    setText('t-5xx', stats.serverErrors);
    setText('t-4xx', stats.clientErrors);
    setText('t-approval', stats.approvalRate);
    setText('t-approved', stats.approved);
    setText('t-rejected', stats.rejected);

    var decided = stats.approved + stats.rejected;
    document.getElementById('split-approved').style.width = decided ? (stats.approved * 100 / decided) + '%' : '0';
    document.getElementById('split-rejected').style.width = decided ? (stats.rejected * 100 / decided) + '%' : '0';

    FsCharts.columnChart(chartBox, stats.timelineLabels, stats.timelineCounts,
      { tooltip: tooltip, unit: 'requests', label: 'API requests over time' });
    FsCharts.dataTable(tableBox, stats.timelineLabels, stats.timelineCounts, 'Requests');

    var rows = (stats.recent || []).map(function (r) {
      return '<tr>' +
        '<td><a href="' + logUrl + r.id + '">' + escapeHtml(timeFmt.format(new Date(r.createdAt))) + '</a></td>' +
        '<td class="mono">' + escapeHtml(r.method) + '</td>' +
        '<td class="mono">' + escapeHtml(r.path) + '</td>' +
        '<td><span class="pill ' + pillClass(r.statusCode) + '">' + r.statusCode + '</span></td>' +
        '<td>' + escapeHtml(r.outcome) + '</td>' +
        '<td class="mono">' + escapeHtml(r.transactionId) + '</td>' +
        '<td class="num">' + r.durationMs + '</td>' +
        '<td class="mono">' + escapeHtml(r.podName) + '</td>' +
        '</tr>';
    }).join('');
    if (rows) document.getElementById('recent-rows').innerHTML = rows;

    setText('refreshed', 'Updated ' + new Date().toLocaleTimeString('en-GB') + ' · auto-refresh every 15 seconds');
  }

  function load() {
    fetch(endpoint, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
      .then(function (res) {
        if (res.redirected || res.status === 401 || res.status === 403) {
          window.location.reload();
          return null;
        }
        return res.json();
      })
      .then(function (data) { if (data) render(data); })
      .catch(function () { setText('refreshed', 'Refresh failed, retrying...'); });
  }

  document.getElementById('timeline-toggle').addEventListener('click', function (e) {
    e.preventDefault();
    var showTable = tableBox.hidden;
    tableBox.hidden = !showTable;
    chartBox.hidden = showTable;
    e.target.textContent = showTable ? 'Show as chart' : 'Show as table';
  });

  var resizeTimer;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () { if (last) render(last); }, 150);
  });

  load();
  setInterval(load, 15000);
})();
