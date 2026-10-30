(function () {
  'use strict';

  var root = document.getElementById('tester');
  if (!root) return;

  var body = document.getElementById('body');
  var out = document.getElementById('response');
  var meta = document.getElementById('meta');
  var logLink = document.getElementById('log-link');
  var lastApprovedId = null;

  // Account numbers below pass the Luhn checksum.
  var ACCOUNTS = ['4111111111111111', '5500005555555559', '1234567897', '4012888888881881', '378282246310005'];
  var MERCHANTS = ['MER1001', 'MER2040', 'SHOP77', 'CAFE09', 'GROCER12'];
  var CURRENCIES = ['SGD', 'USD', 'EUR', 'GBP', 'MYR'];

  function txnId() {
    return 'TXN-' + Date.now().toString(36).toUpperCase() + '-' + Math.random().toString(36).slice(2, 7).toUpperCase();
  }

  function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
  }

  function base() {
    return {
      transactionId: txnId(),
      accountNumber: pick(ACCOUNTS),
      merchantId: pick(MERCHANTS),
      amount: Math.round((Math.random() * 900 + 5) * 100) / 100,
      currency: pick(CURRENCIES),
      country: 'SG'
    };
  }

  var samples = {
    valid: function () { return base(); },
    highRisk: function () { var t = base(); t.amount = 7500.00; return t; },
    overLimit: function () { var t = base(); t.amount = 25000.00; return t; },
    currency: function () { var t = base(); t.currency = 'XYZ'; return t; },
    blocked: function () { var t = base(); t.country = 'KP'; return t; },
    badAccount: function () { var t = base(); t.accountNumber = '1234567890'; return t; },
    duplicate: function () { var t = base(); if (lastApprovedId) t.transactionId = lastApprovedId; return t; }
  };

  function invalid() {
    var options = [samples.overLimit, samples.currency, samples.blocked, samples.badAccount];
    return pick(options)();
  }

  function show(sample) {
    if (sample === 'malformed') {
      body.value = '{ "transactionId": "TXN-BROKEN", "amount": ';
      return;
    }
    body.value = JSON.stringify(samples[sample](), null, 2);
  }

  function post(payload) {
    return fetch(root.dataset.validate, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: payload
    });
  }

  function display(res, started) {
    var ms = Math.round(performance.now() - started);
    var requestId = res.headers.get('X-Request-Id');
    return res.text().then(function (text) {
      meta.textContent = 'HTTP ' + res.status + ' · ' + ms + ' ms';
      try {
        var json = JSON.parse(text);
        out.textContent = JSON.stringify(json, null, 2);
        if (json.status === 'APPROVED') lastApprovedId = json.transactionId;
      } catch (e) {
        out.textContent = text || '(empty)';
      }
      logLink.innerHTML = '';
      if (requestId) {
        var a = document.createElement('a');
        a.href = root.dataset.logUrl + encodeURIComponent(requestId);
        a.textContent = 'View stored log entry (' + requestId.slice(0, 8) + ')';
        logLink.appendChild(a);
      }
    });
  }

  document.querySelectorAll('[data-sample]').forEach(function (btn) {
    btn.addEventListener('click', function () { show(btn.dataset.sample); });
  });

  document.getElementById('send').addEventListener('click', function () {
    var started = performance.now();
    post(body.value).then(function (res) { return display(res, started); })
      .catch(function (e) { meta.textContent = 'Request failed: ' + e.message; });
  });

  document.getElementById('health').addEventListener('click', function () {
    var started = performance.now();
    fetch(root.dataset.health, { headers: { 'Accept': 'application/json' } })
      .then(function (res) { return display(res, started); })
      .catch(function (e) { meta.textContent = 'Request failed: ' + e.message; });
  });

  document.getElementById('batch').addEventListener('click', function () {
    var count = Math.min(Math.max(parseInt(document.getElementById('count').value, 10) || 1, 1), 500);
    var share = parseFloat(document.getElementById('mix').value);
    var status = document.getElementById('batch-status');
    var done = 0, approved = 0, rejected = 0, failed = 0;
    var btn = this;
    btn.disabled = true;

    function next() {
      if (done >= count) {
        status.textContent = 'Finished: ' + approved + ' approved, ' + rejected + ' rejected, ' + failed + ' errors.';
        btn.disabled = false;
        return;
      }
      var txn = Math.random() < share ? invalid() : samples.valid();
      post(JSON.stringify(txn)).then(function (res) {
        if (res.status === 200) approved++; else if (res.status === 422) rejected++; else failed++;
      }).catch(function () { failed++; }).then(function () {
        done++;
        status.textContent = 'Sent ' + done + ' / ' + count;
        next();
      });
    }
    next();
  });

  show('valid');
})();
