// سرور کوچک کدهای فعال‌سازی یک‌بارمصرف. بدون وابستگی؛ نیاز به Node 22.5 یا بالاتر.
// اجرا:  ADMIN_KEY=یک-کلید-طولانی PORT=8080 node server.js
'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');

const PORT = Number(process.env.PORT || 8080);
const ADMIN_KEY = process.env.ADMIN_KEY || '';
const DB_PATH = process.env.DB_PATH || 'codes.db';
if (ADMIN_KEY.length < 12) {
  console.error('ADMIN_KEY باید حداقل ۱۲ کاراکتر باشد.');
  process.exit(1);
}

const db = new DatabaseSync(DB_PATH);
db.exec(`
  CREATE TABLE IF NOT EXISTS codes (
    code TEXT PRIMARY KEY,
    label TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    used_at INTEGER,
    device_id TEXT,
    revoked INTEGER NOT NULL DEFAULT 0
  )`);

db.exec('CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)');
const qMetaGet = db.prepare('SELECT v FROM meta WHERE k = ?');
const qMetaSet = db.prepare('INSERT INTO meta (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v');

const qInsert = db.prepare('INSERT INTO codes (code, label, created_at) VALUES (?, ?, ?)');
const qGet = db.prepare('SELECT * FROM codes WHERE code = ?');
// مصرف اتمیک: فقط اگر هنوز مصرف نشده و باطل نشده باشد
const qUse = db.prepare('UPDATE codes SET used_at = ?, device_id = ? WHERE code = ? AND used_at IS NULL AND revoked = 0');
const qList = db.prepare('SELECT * FROM codes ORDER BY created_at DESC, rowid DESC LIMIT 1000');
const qRevoke = db.prepare('UPDATE codes SET revoked = 1 WHERE code = ?');
const qDelete = db.prepare('DELETE FROM codes WHERE code = ? AND used_at IS NULL');

function newCode() {
  for (;;) {
    const c = String(crypto.randomInt(0, 1_000_000_000_000)).padStart(12, '0');
    if (!qGet.get(c)) return c;
  }
}

// کد تست ادمین: چندبارمصرف، مصرف نمی‌شود و برای تست اپ است. با TEST_CODE (۱۲ رقم) قابل تنظیم است؛
// اگر تنظیم نشده باشد اولین بار خودکار ساخته و در دیتابیس ذخیره می‌شود (در لاگ و پنل /admin دیده می‌شود).
function testCode() {
  if (/^\d{12}$/.test(process.env.TEST_CODE || '')) return process.env.TEST_CODE;
  const row = qMetaGet.get('test_code');
  if (row) return row.v;
  const c = newCode();
  qMetaSet.run('test_code', c);
  return c;
}
function regenTestCode() { const c = newCode(); qMetaSet.run('test_code', c); return c; }

// محدودیت تلاش برای جلوگیری از حدس زدن کد (۱۰ تلاش در ۱۰ دقیقه برای هر IP)
const attempts = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const arr = (attempts.get(ip) || []).filter((t) => now - t < 600_000);
  arr.push(now);
  attempts.set(ip, arr);
  if (attempts.size > 10_000) attempts.clear();
  return arr.length > 10;
}

function adminOk(req) {
  const h = req.headers.authorization || '';
  const given = Buffer.from(h.startsWith('Bearer ') ? h.slice(7) : '');
  const want = Buffer.from(ADMIN_KEY);
  return given.length === want.length && crypto.timingSafeEqual(given, want);
}

function send(res, status, body, type = 'application/json; charset=utf-8') {
  res.writeHead(status, { 'content-type': type, 'cache-control': 'no-store' });
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 10_000) { reject(new Error('big')); req.destroy(); } });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch (e) { reject(e); } });
    req.on('error', reject);
  });
}

const toDigits = (s) => String(s).replace(/[۰-۹]/g, (d) => d.charCodeAt(0) - 0x06f0).replace(/[٠-٩]/g, (d) => d.charCodeAt(0) - 0x0660);

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() || req.socket.remoteAddress || '';

    if (req.method === 'POST' && url.pathname === '/api/activate') {
      if (rateLimited(ip)) return send(res, 429, { error: 'too_many' });
      const body = await readJson(req);
      const code = toDigits(body.code || '').trim();
      const device = String(body.deviceId || '').slice(0, 100);
      if (!/^\d{12}$/.test(code) || !device) return send(res, 400, { error: 'bad_request' });
      const row = qGet.get(code);
      if (code === testCode()) return send(res, 200, { ok: true, test: true });
      if (!row || row.revoked) return send(res, 404, { error: 'invalid' });
      if (qUse.run(Date.now(), device, code).changes === 1) return send(res, 200, { ok: true });
      // قبلاً مصرف شده: اگر همان دستگاه دوباره بزند (مثلاً پاسخ قبلی به دستگاه نرسیده) پذیرفته می‌شود
      if (row.used_at && row.device_id === device) return send(res, 200, { ok: true });
      return send(res, 409, { error: 'used' });
    }

    if (url.pathname === '/admin' && req.method === 'GET') return send(res, 200, ADMIN_HTML, 'text/html; charset=utf-8');

    if (url.pathname.startsWith('/admin/')) {
      if (!adminOk(req)) return send(res, 401, { error: 'unauthorized' });
      if (req.method === 'GET' && url.pathname === '/admin/codes') return send(res, 200, { codes: qList.all(), testCode: testCode(), testCodeFromEnv: /^\d{12}$/.test(process.env.TEST_CODE || '') });
      if (req.method === 'POST' && url.pathname === '/admin/test-code/regenerate') {
        if (/^\d{12}$/.test(process.env.TEST_CODE || '')) return send(res, 409, { error: 'env_controlled' });
        return send(res, 200, { testCode: regenTestCode() });
      }
      if (req.method === 'POST' && url.pathname === '/admin/codes') {
        const body = await readJson(req);
        const n = Math.min(Math.max(parseInt(body.count, 10) || 1, 1), 200);
        const label = String(body.label || '').slice(0, 100);
        const out = [];
        for (let i = 0; i < n; i++) { const c = newCode(); qInsert.run(c, label, Date.now()); out.push(c); }
        return send(res, 200, { codes: out });
      }
      const m = url.pathname.match(/^\/admin\/codes\/(\d{12})(\/revoke)?$/);
      if (m && req.method === 'POST' && m[2]) { qRevoke.run(m[1]); return send(res, 200, { ok: true }); }
      if (m && req.method === 'DELETE' && !m[2]) { qDelete.run(m[1]); return send(res, 200, { ok: true }); }
    }
    send(res, 404, { error: 'not_found' });
  } catch (e) {
    send(res, 400, { error: 'bad_request' });
  }
});

const ADMIN_HTML = `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>کدهای فعال‌سازی</title>
<style>body{font-family:system-ui,Tahoma;max-width:720px;margin:24px auto;padding:0 16px}
input,button{font:inherit;padding:8px;margin:4px 0}table{width:100%;border-collapse:collapse}
td,th{border-bottom:1px solid #ddd;padding:6px;text-align:right}code{direction:ltr;unicode-bidi:embed}</style></head><body>
<h2>کدهای فعال‌سازی یک‌بارمصرف</h2>
<div><input id="key" type="password" placeholder="کلید ادمین"> <button onclick="load()">ورود</button></div>
<div><input id="label" placeholder="برچسب (مثلاً نام کاربر)"> <input id="count" type="number" min="1" max="200" value="1" style="width:5em">
<button onclick="gen()">ساخت کد</button></div><pre id="new"></pre>
<p id="tc"></p>
<table id="t"></table>
<script>
const H=()=>({authorization:'Bearer '+key.value,'content-type':'application/json'});
const fmt=t=>t?new Date(t).toLocaleString('fa-IR'):'—';
async function load(){const r=await fetch('/admin/codes',{headers:H()});if(!r.ok){t.innerHTML='<tr><td>کلید اشتباه است</td></tr>';return}
const {codes,testCode,testCodeFromEnv}=await r.json();tc.innerHTML='کد تست ادمین (چندبارمصرف، مصرف نمی‌شود): <code>'+testCode+'</code> '+(testCodeFromEnv?'':'<button onclick="regen()">ساخت کد تست جدید</button>');t.innerHTML='<tr><th>کد</th><th>برچسب</th><th>وضعیت</th><th>زمان مصرف</th><th></th></tr>'+codes.map(c=>
'<tr><td><code>'+c.code+'</code></td><td>'+esc(c.label)+'</td><td>'+(c.revoked?'باطل':c.used_at?'مصرف شد':'آزاد')+'</td><td>'+fmt(c.used_at)+'</td><td>'+
(c.revoked?'':'<button onclick="rev(\\''+c.code+'\\')">ابطال</button>')+'</td></tr>').join('')}
const esc=s=>s.replace(/[&<>"]/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[m]));
async function gen(){const r=await fetch('/admin/codes',{method:'POST',headers:H(),body:JSON.stringify({count:+count.value,label:label.value})});
const j=await r.json();document.getElementById('new').textContent=(j.codes||[]).join('\\n');load()}
async function regen(){if(!confirm('کد تست قبلی از کار می‌افتد. ادامه؟'))return;await fetch('/admin/test-code/regenerate',{method:'POST',headers:H()});load()}
async function rev(c){if(!confirm('ابطال شود؟'))return;await fetch('/admin/codes/'+c+'/revoke',{method:'POST',headers:H()});load()}
</script></body></html>`;

server.listen(PORT, () => {
  console.log('listening on :' + PORT);
  console.log('کد تست ادمین (چندبارمصرف): ' + testCode());
});
