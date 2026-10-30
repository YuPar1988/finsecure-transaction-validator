(function (global) {
  'use strict';

  var SVG_NS = 'http://www.w3.org/2000/svg';

  function el(name, attrs) {
    var node = document.createElementNS(SVG_NS, name);
    Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, attrs[k]); });
    return node;
  }

  function niceMax(value) {
    if (value <= 4) return 4;
    var exp = Math.pow(10, Math.floor(Math.log10(value)));
    var f = value / exp;
    var nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
    return nice * exp;
  }

  // Bar with a 4px rounded data-end and a square base on the baseline.
  function barPath(x, y, w, h, r) {
    if (h <= 0) return '';
    r = Math.min(r, w / 2, h);
    return 'M' + x + ',' + (y + h) +
      'V' + (y + r) +
      'Q' + x + ',' + y + ' ' + (x + r) + ',' + y +
      'H' + (x + w - r) +
      'Q' + (x + w) + ',' + y + ' ' + (x + w) + ',' + (y + r) +
      'V' + (y + h) + 'Z';
  }

  function format(n) {
    return Number(n).toLocaleString('en-US');
  }

  /**
   * Renders a single-series column chart into container.
   * options: { height, unit, tooltip (element) }
   */
  function columnChart(container, labels, values, options) {
    options = options || {};
    container.innerHTML = '';

    var width = Math.max(container.clientWidth, 320);
    var height = options.height || 230;
    var pad = { top: 12, right: 8, bottom: 26, left: 40 };
    var plotW = width - pad.left - pad.right;
    var plotH = height - pad.top - pad.bottom;
    var max = niceMax(Math.max.apply(null, values.concat([0])));
    var unit = options.unit || 'requests';

    var svg = el('svg', { viewBox: '0 0 ' + width + ' ' + height, role: 'img',
      'aria-label': (options.label || 'Column chart') });

    var ticks = 4;
    for (var t = 0; t <= ticks; t++) {
      var v = max * t / ticks;
      var y = pad.top + plotH - (v / max) * plotH;
      if (t > 0) {
        svg.appendChild(el('line', { x1: pad.left, x2: width - pad.right, y1: y, y2: y, 'class': 'grid-line' }));
      }
      var label = el('text', { x: pad.left - 8, y: y + 4, 'text-anchor': 'end', 'class': 'tick' });
      label.textContent = Number.isInteger(v) ? format(v) : v.toFixed(1);
      svg.appendChild(label);
    }
    svg.appendChild(el('line', { x1: pad.left, x2: width - pad.right,
      y1: pad.top + plotH, y2: pad.top + plotH, 'class': 'baseline' }));

    var n = values.length;
    var slot = plotW / Math.max(n, 1);
    var gap = 2;
    var barW = Math.max(Math.min(slot - gap, 28), 2);
    var labelEvery = Math.ceil(n / Math.max(Math.floor(plotW / 54), 1));
    var tooltip = options.tooltip;
    var bars = [];

    values.forEach(function (value, i) {
      var cx = pad.left + slot * i + slot / 2;
      var h = (value / max) * plotH;
      var bar = el('path', { d: barPath(cx - barW / 2, pad.top + plotH - h, barW, h, 4), 'class': 'bar' });
      svg.appendChild(bar);
      bars.push(bar);

      if (i % labelEvery === 0) {
        var x = el('text', { x: cx, y: height - 8, 'text-anchor': 'middle', 'class': 'tick' });
        x.textContent = labels[i];
        svg.appendChild(x);
      }

      var hit = el('rect', { x: pad.left + slot * i, y: pad.top, width: slot, height: plotH, 'class': 'hit' });
      hit.addEventListener('mouseenter', function () {
        bars.forEach(function (b, j) { b.classList.toggle('dim', j !== i); });
        if (tooltip) {
          var box = container.getBoundingClientRect();
          var scale = box.width / width;
          tooltip.innerHTML = '<strong>' + format(value) + ' ' + unit + '</strong>' + labels[i];
          tooltip.style.left = (box.left + window.scrollX + cx * scale) + 'px';
          tooltip.style.top = (box.top + window.scrollY + (pad.top + plotH - h) * scale) + 'px';
          tooltip.style.display = 'block';
        }
      });
      hit.addEventListener('mouseleave', function () {
        bars.forEach(function (b) { b.classList.remove('dim'); });
        if (tooltip) tooltip.style.display = 'none';
      });
      svg.appendChild(hit);
    });

    container.appendChild(svg);
  }

  function dataTable(container, labels, values, header) {
    var rows = labels.map(function (l, i) {
      return '<tr><td>' + l + '</td><td class="num">' + format(values[i]) + '</td></tr>';
    }).join('');
    container.innerHTML = '<table><thead><tr><th>Interval</th><th class="num">' + header +
      '</th></tr></thead><tbody>' + rows + '</tbody></table>';
  }

  global.FsCharts = { columnChart: columnChart, dataTable: dataTable, format: format };
})(window);
