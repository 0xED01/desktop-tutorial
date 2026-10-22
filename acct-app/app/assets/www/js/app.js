(function () {
'use strict';

// ====================== 基础数据 ======================
var CH = {
  c1:'会计概述', c2:'会计基础', c3:'流动资产', c4:'非流动资产', c5:'负债', c6:'所有者权益',
  c7:'收入、费用和利润', c8:'财务报告', c9:'产品成本核算', c10:'政府会计基础',
  j1:'总论', j2:'会计法律制度', j3:'支付结算法律制度', j4:'税法概述及货物和劳务税',
  j5:'所得税法律制度', j6:'财产和行为税法律制度', j7:'税收征收管理法律制度', j8:'劳动合同与社会保险法律制度',
  j0:'经济法高频数字'
};
var SUBJ = {
  sw:{name:'初级会计实务', chs:['c1','c2','c3','c4','c5','c6','c7','c8','c9','c10']},
  jj:{name:'经济法基础', chs:['j1','j2','j3','j4','j5','j6','j7','j8']}
};
var TYPE = {s:'单选题', m:'多选题', j:'判断题'};
var KEYS = 'ABCDEFGH';
var ACCOUNTS = window.ACCOUNTS, ENTRIES = window.ENTRIES, QUESTIONS = window.QUESTIONS, KNOWLEDGE = window.KNOWLEDGE;

function hash(s) { var h = 5381; for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0; return (h >>> 0).toString(36); }
var QMAP = {}, EMAP = {}, AMAP = {};
QUESTIONS.forEach(function (q) { q.id = q.c + '_' + hash(q.q + (q.o ? q.o.join('|') : '')); QMAP[q.id] = q; });
ENTRIES.forEach(function (e, i) { e.idx = i; EMAP[e.id] = e; });
ACCOUNTS.forEach(function (a) { AMAP[a.n] = a; });
function subjOf(c) { return c.charAt(0) === 'j' ? 'jj' : 'sw'; }
function qsOf(c) { return QUESTIONS.filter(function (q) { return q.c === c; }); }

// ====================== 存储 ======================
var STORE_KEY = 'kj_state_v1';
var S = (function () {
  var x = {};
  try { x = JSON.parse(localStorage.getItem(STORE_KEY) || '{}') || {}; } catch (e) { x = {}; }
  var d = {qa:{}, wrong:{}, fav:{}, ent:{}, pos:{}, exams:[], days:{}, recent:[], examDate:'', theme:'auto', autoRemove:true, subj:'sw', lastEntry:'', lastCh:''};
  for (var k in d) if (!(k in x)) x[k] = d[k];
  return x;
})();
function save() { try { localStorage.setItem(STORE_KEY, JSON.stringify(S)); } catch (e) {} }
function today() { var d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
function pad(n) { return n < 10 ? '0' + n : '' + n; }
function bumpDay() { var t = today(); S.days[t] = (S.days[t] || 0) + 1; }
function streak() {
  var n = 0, d = new Date();
  if (!S.days[today()]) d.setDate(d.getDate() - 1);
  for (;;) { var k = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); if (S.days[k]) { n++; d.setDate(d.getDate() - 1); } else break; }
  return n;
}
// 安卓 WebView 不一定跟随系统深色模式，由原生层告知系统当前是否为深色
function applyTheme() {
  var t = S.theme;
  if (t === 'auto' && window.Native) { try { t = Native.isDark() ? 'dark' : 'light'; } catch (e) {} }
  if (t === 'auto') {
    document.documentElement.removeAttribute('data-theme');
    t = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } else document.documentElement.setAttribute('data-theme', t);
  if (window.Native) { try { Native.theme(t); } catch (e) {} }
}
window.__applyTheme = applyTheme;
applyTheme();

// ====================== 工具函数 ======================
function $(s, el) { return (el || document).querySelector(s); }
function $$(s, el) { return Array.prototype.slice.call((el || document).querySelectorAll(s)); }
function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]; }); }
function fmt(n, d) {
  if (n === '' || n == null || isNaN(n)) return '—';
  var dd = d == null ? 2 : d;
  var v = Math.round(n * Math.pow(10, dd)) / Math.pow(10, dd);
  var neg = v < 0; v = Math.abs(v);
  var parts = v.toFixed(dd).split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  var s = parts[1] && /[1-9]/.test(parts[1]) ? parts[0] + '.' + parts[1].replace(/0+$/, '') : parts[0];
  return (neg ? '-' : '') + s;
}
function num(v) { if (v == null) return NaN; var s = String(v).replace(/[,，\s]/g, ''); if (s === '') return NaN; return Number(s); }
function shuffle(a) { a = a.slice(); for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
function toast(msg) {
  var t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toast._t); toast._t = setTimeout(function () { t.hidden = true; }, 1800);
}
var IC = {
  chev:'<svg class="chev" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg>',
  gear:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
  grid:'<svg viewBox="0 0 24 24"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg>',
  star:'<svg viewBox="0 0 24 24"><path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/></svg>',
  starOn:'<svg viewBox="0 0 24 24" style="fill:#E8A317;stroke:#E8A317"><path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z"/></svg>',
  search:'<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>',
  close:'<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  book:'<svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M12 4v16M5 9h14"/></svg>',
  shuffle:'<svg viewBox="0 0 24 24"><path d="M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/></svg>',
  list:'<svg viewBox="0 0 24 24"><path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01"/></svg>',
  clock:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  x:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6M15 9l-6 6"/></svg>',
  table:'<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 10v10"/></svg>',
  calc:'<svg viewBox="0 0 24 24"><rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 7h8M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"/></svg>',
  bulb:'<svg viewBox="0 0 24 24"><path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2V16h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z"/></svg>',
  pen:'<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M14 6l4 4"/></svg>'
};

// ====================== 路由 ======================
var view = $('#view'), titleEl = $('#title'), backBtn = $('#backBtn'), actions = $('#topActions'), topbar = $('#topbar');
var ROOTS = {'#/home':1, '#/entries':1, '#/quiz':1, '#/know':1, '#/tools':1};
var stack = [];
var cleanup = null;
var ROUTES = [
  [/^#\/home$/, viewHome, 'home'],
  [/^#\/entries$/, viewEntries, 'entries'],
  [/^#\/entry\/(\w+)$/, viewEntry, 'entries'],
  [/^#\/quiz$/, viewQuiz, 'quiz'],
  [/^#\/run\/(\w+)(?:\/(\w+))?$/, viewRun, 'quiz'],
  [/^#\/result$/, viewResult, 'quiz'],
  [/^#\/know$/, viewKnow, 'know'],
  [/^#\/know\/(\w+)$/, viewKnowCh, 'know'],
  [/^#\/tools$/, viewTools, 'tools'],
  [/^#\/tools\/(\w+)$/, viewTool, 'tools'],
  [/^#\/me$/, viewMe, 'home']
];
function go(path, replace) {
  if (ROOTS[path]) stack = [path];
  else if (replace && stack.length) stack[stack.length - 1] = path;
  else stack.push(path);
  try { history.replaceState(null, '', path); } catch (e) { location.hash = path; }
  render();
}
function back() {
  if (stack.length > 1) { stack.pop(); var p = stack[stack.length - 1]; try { history.replaceState(null, '', p); } catch (e) {} render(); return true; }
  var cur = stack[0] || '#/home';
  if (cur !== '#/home') { go('#/home'); return true; }
  return false;
}
var guard = null; // 离开页面前的确认（考试中）
var lastBackAt = 0;
window.appBack = function () {
  if (!$('#sheet').hidden) { closeSheet(); return true; }
  if (guard) { var g = guard; g(function () { guard = null; back(); }); return true; }
  if (back()) return true;
  var now = Date.now();
  if (now - lastBackAt < 2000) return false;
  lastBackAt = now; toast('再按一次退出'); return true;
};
backBtn.onclick = function () { window.appBack(); };
document.addEventListener('click', function (ev) {
  var a = ev.target.closest('a[href^="#/"]');
  if (!a) return;
  ev.preventDefault();
  var href = a.getAttribute('href');
  if (guard && href !== location.hash) { var g = guard; g(function () { guard = null; go(href); }); return; }
  go(href);
});
function setHeader(title, opt) {
  opt = opt || {};
  titleEl.textContent = title;
  var isRoot = stack.length <= 1 && ROOTS[stack[0]];
  backBtn.hidden = !!isRoot;
  topbar.classList.toggle('is-sub', !isRoot);
  actions.innerHTML = opt.actions || '';
  document.body.classList.toggle('no-tab', !!opt.noTab);
}
function render() {
  if (cleanup) { try { cleanup(); } catch (e) {} cleanup = null; }
  guard = null;
  closeSheet();
  var h = stack[stack.length - 1] || '#/home';
  for (var i = 0; i < ROUTES.length; i++) {
    var m = h.match(ROUTES[i][0]);
    if (m) {
      $$('#tabbar a').forEach(function (a) { a.classList.toggle('on', a.getAttribute('data-tab') === ROUTES[i][2]); });
      view.scrollTop = 0;
      ROUTES[i][1](m);
      return;
    }
  }
  go('#/home');
}

// ====================== 弹层 ======================
function openSheet(title, body, opt) {
  opt = opt || {};
  var sh = $('#sheet');
  sh.innerHTML = '<div class="panel' + (opt.full ? ' full' : '') + '"><div class="ph"><h3>' + esc(title) + '</h3><button class="icon-btn" data-close>' + IC.close + '</button></div><div class="pb">' + body + '</div></div>';
  sh.hidden = false;
  sh.onclick = function (e) { if (e.target === sh || e.target.closest('[data-close]')) closeSheet(); };
  return sh;
}
function closeSheet() { var sh = $('#sheet'); sh.hidden = true; sh.innerHTML = ''; }
function confirmBox(msg, okText, onOk) {
  var sh = openSheet('提示', '<p style="margin:4px 2px 18px;font-size:15px">' + esc(msg) + '</p><div class="row"><button class="btn plain grow" data-close>取消</button><button class="btn grow" id="cfOk">' + esc(okText || '确定') + '</button></div>');
  $('#cfOk', sh).onclick = function () { closeSheet(); onOk(); };
}

// ====================== 统计 ======================
function qStats(list) {
  var done = 0, r = 0, t = 0;
  list.forEach(function (q) { var a = S.qa[q.id]; if (a) { done++; r += a.r; t += a.r + a.w; } });
  return {done:done, total:list.length, acc: t ? Math.round(r / t * 100) : null};
}
function entStats() { var ok = 0, done = 0; ENTRIES.forEach(function (e) { var s = S.ent[e.id]; if (s) { done++; if (s.best) ok++; } }); return {ok:ok, done:done, total:ENTRIES.length}; }
function record(q, correct, inWrongMode) {
  var a = S.qa[q.id] || (S.qa[q.id] = {r:0, w:0});
  if (correct) a.r++; else a.w++;
  a.l = correct ? 1 : 0;
  if (!correct) S.wrong[q.id] = Date.now();
  else if (inWrongMode && S.autoRemove) delete S.wrong[q.id];
  bumpDay(); save();
}

// ====================== 首页 ======================
function viewHome() {
  setHeader('初级会计实操', {actions:'<button class="icon-btn" id="toMe" aria-label="我的">' + IC.gear + '</button>'});
  $('#toMe').onclick = function () { go('#/me'); };
  var all = qStats(QUESTIONS), es = entStats();
  var wrongN = Object.keys(S.wrong).length, favN = Object.keys(S.fav).length;
  var totalR = 0, totalT = 0; for (var k in S.qa) { totalR += S.qa[k].r; totalT += S.qa[k].r + S.qa[k].w; }
  var acc = totalT ? Math.round(totalR / totalT * 100) + '%' : '—';
  var hero;
  if (S.examDate) {
    var diff = Math.ceil((new Date(S.examDate + 'T00:00:00') - new Date(today() + 'T00:00:00')) / 86400000);
    hero = diff >= 0 ? '<div class="h-sub">距离考试还有</div><div class="h-big">' + diff + '<small>天</small></div><div class="h-sub">' + S.examDate + ' · 每天进步一点点</div>'
                     : '<div class="h-sub">考试日期已过</div><div class="h-big">加油<small>下一站中级</small></div><div class="h-sub">可在设置中修改考试日期</div>';
  } else hero = '<div class="h-sub">初级会计职称备考</div><div class="h-big" style="font-size:26px">分录实操 + 刷题</div><div class="h-sub">设置考试日期，开启倒计时</div>';
  var tip = dailyTip();
  var cont = '';
  if (S.lastEntry && EMAP[S.lastEntry]) cont += '<a class="item" href="#/entry/' + S.lastEntry + '"><div class="grow"><div style="font-weight:600">继续分录：' + esc(EMAP[S.lastEntry].t) + '</div><div class="small muted">' + CH[EMAP[S.lastEntry].c] + '</div></div>' + IC.chev + '</a>';
  if (S.lastCh && CH[S.lastCh]) { var st = qStats(qsOf(S.lastCh)); cont += '<a class="item" href="#/run/ch/' + S.lastCh + '"><div class="grow"><div style="font-weight:600">继续练习：' + CH[S.lastCh] + '</div><div class="small muted">已做 ' + st.done + '/' + st.total + '</div></div>' + IC.chev + '</a>'; }
  view.innerHTML =
    '<div class="hero"><button class="h-set" id="setDate">' + (S.examDate ? '修改日期' : '设置日期') + '</button>' + hero +
    '<div class="stats"><div><b>' + all.done + '</b><span>已练题目</span></div><div><b>' + acc + '</b><span>正确率</span></div><div><b>' + es.ok + '<small style="font-size:12px;font-weight:500">/' + es.total + '</small></b><span>分录掌握</span></div><div><b>' + streak() + '</b><span>连续天数</span></div></div></div>' +
    '<div class="grid">' +
      gi('#/entries', IC.book, '分录实操') + gi('#/entry/random', IC.shuffle, '随机分录') + gi('#/quiz', IC.list, '章节练习') + gi('#/run/exam/' + S.subj, IC.clock, '模拟考试') +
      gi('#/run/wrong', IC.x, '错题本' + (wrongN ? '·' + wrongN : '')) + gi('#/run/fav', IC.star, '收藏' + (favN ? '·' + favN : '')) + gi('#/tools/accounts', IC.table, '科目表') + gi('#/tools', IC.calc, '计算工具') +
    '</div>' +
    (cont ? '<div class="sec-title">继续学习</div><div class="list">' + cont + '</div>' : '') +
    '<div class="sec-title">每日一记 <a href="#/know/' + tip.c + '">' + CH[tip.c] + ' ›</a></div>' +
    '<div class="card tipcard"><h4>' + esc(tip.h) + '</h4><div class="kb">' + tip.b + '</div></div>' +
    '<div class="sec-title">今日</div><div class="card"><div class="row"><div class="grow">今日已练 <b>' + (S.days[today()] || 0) + '</b> 次</div><a class="btn sm ghost" href="#/run/rand/' + S.subj + '">随机 20 题</a></div></div>';
  $('#setDate').onclick = pickExamDate;
}
function gi(href, icon, label) { return '<a class="gi" href="' + href + '"><span class="ic">' + icon + '</span>' + label + '</a>'; }
function dailyTip() {
  var flat = []; KNOWLEDGE.forEach(function (k) { k.items.forEach(function (it) { flat.push({c:k.c, h:it.h, b:it.b}); }); });
  var d = new Date(); var n = Math.floor((d - new Date(2024, 0, 1)) / 86400000);
  return flat[n % flat.length];
}
function pickExamDate() {
  var sh = openSheet('考试日期', '<p class="small sub" style="margin-top:0">设置后首页将显示考试倒计时。</p><input type="date" class="inp" id="dIn" value="' + (S.examDate || '') + '"><div class="row" style="margin-top:14px"><button class="btn plain grow" id="dClr">清除</button><button class="btn grow" id="dOk">保存</button></div>');
  $('#dOk', sh).onclick = function () { S.examDate = $('#dIn', sh).value; save(); closeSheet(); render(); };
  $('#dClr', sh).onclick = function () { S.examDate = ''; save(); closeSheet(); render(); };
}

// ====================== 分录列表 ======================
var entFilter = 'all';
function viewEntries() {
  setHeader('分录实操');
  var es = entStats();
  var chs = []; ENTRIES.forEach(function (e) { if (chs.indexOf(e.c) < 0) chs.push(e.c); });
  var chips = '<button class="chip' + (entFilter === 'all' ? ' on' : '') + '" data-f="all">全部</button>' +
    '<button class="chip' + (entFilter === 'todo' ? ' on' : '') + '" data-f="todo">未掌握</button>' +
    '<button class="chip' + (entFilter === 'bad' ? ' on' : '') + '" data-f="bad">做错过</button>' +
    chs.map(function (c) { return '<button class="chip' + (entFilter === c ? ' on' : '') + '" data-f="' + c + '">' + CH[c] + '</button>'; }).join('');
  var list = ENTRIES.filter(function (e) {
    var s = S.ent[e.id];
    if (entFilter === 'all') return true;
    if (entFilter === 'todo') return !s || !s.best;
    if (entFilter === 'bad') return s && s.wrong;
    return e.c === entFilter;
  });
  var html = '<div class="card"><div class="row"><div class="grow"><div style="font-weight:700;font-size:17px">已掌握 ' + es.ok + ' / ' + es.total + '</div><div class="small muted">选择科目、填写金额，系统自动判分</div></div>' +
    '<a class="btn sm" href="#/entry/random">随机一题</a></div><div class="bar" style="margin-top:12px"><i style="width:' + (es.ok / es.total * 100) + '%"></i></div></div>' +
    '<div class="chips" id="entChips">' + chips + '</div>';
  if (!list.length) html += '<div class="empty">这里还没有题目</div>';
  else html += '<div class="list">' + list.map(function (e) {
    var s = S.ent[e.id], st = s ? (s.best ? '<span class="st ok">✓</span>' : '<span class="st bad">!</span>') : '<span class="st">' + (e.idx + 1) + '</span>';
    return '<a class="item ent-item" href="#/entry/' + e.id + '">' + st + '<div class="grow"><div class="t">' + esc(e.t) + '</div><div class="d">' + CH[e.c] + ' · ' + esc(e.q) + '</div></div>' + IC.chev + '</a>';
  }).join('') + '</div>';
  view.innerHTML = html;
  $$('#entChips .chip').forEach(function (b) { b.onclick = function () { entFilter = b.getAttribute('data-f'); viewEntries(); }; });
}

// ====================== 分录练习 ======================
var drafts = {};
function newDraft(e) { return {step:0, lines:e.s.map(function () { return [{s:'借', a:'', m:''}, {s:'贷', a:'', m:''}]; }), checked:null, hint:{}, shown:false}; }
function viewEntry(m) {
  var id = m[1];
  if (id === 'random') {
    var pool = ENTRIES.filter(function (e) { var s = S.ent[e.id]; return !s || !s.best; });
    if (!pool.length) pool = ENTRIES;
    var pick = pool[Math.floor(Math.random() * pool.length)];
    go('#/entry/' + pick.id, true); return;
  }
  var e = EMAP[id]; if (!e) { go('#/entries'); return; }
  S.lastEntry = id; save();
  var d = drafts[id] || (drafts[id] = newDraft(e));
  setHeader((e.idx + 1) + '. ' + e.t, {noTab:true});
  renderEntry(e, d);
}
function sumSide(lines, side) { var t = 0; lines.forEach(function (l) { if (l.s === side) { var v = num(l.m); if (!isNaN(v)) t += v; } }); return t; }
function aggregate(lines) {
  var m = {};
  lines.forEach(function (l) { var k = l[0] + '|' + l[1]; m[k] = (m[k] || 0) + l[2]; });
  return m;
}
function checkEntry(e, d) {
  var allOk = true;
  var res = e.s.map(function (st, i) {
    var ans = aggregate(st.a);
    var user = d.lines[i].filter(function (l) { return l.a || l.m !== ''; });
    var userAgg = aggregate(user.map(function (l) { return [l.s, l.a, num(l.m)]; }));
    var marks = d.lines[i].map(function (l) {
      if (!l.a && l.m === '') return null;
      var k = l.s + '|' + l.a;
      return (k in ans) && Math.abs(ans[k] - (userAgg[k] || 0)) < 0.006;
    });
    var ok = true;
    for (var k in ans) if (!(k in userAgg) || Math.abs(ans[k] - userAgg[k]) >= 0.006) ok = false;
    for (var k2 in userAgg) if (!(k2 in ans)) ok = false;
    if (!ok) allOk = false;
    return {ok:ok, marks:marks};
  });
  return {ok:allOk, steps:res};
}
function jeHtml(e) {
  return e.s.map(function (st) {
    var h = st.l ? '<div class="sl">' + esc(st.l) + '</div>' : '';
    var dr = st.a.filter(function (l) { return l[0] === '借'; }), cr = st.a.filter(function (l) { return l[0] === '贷'; });
    dr.forEach(function (l, i) { h += '<div class="jl"><span>' + (i === 0 ? '借：' : '<span style="visibility:hidden">借：</span>') + esc(l[1]) + '</span><span>' + fmt(l[2]) + '</span></div>'; });
    cr.forEach(function (l, i) { h += '<div class="jl c"><span>' + (i === 0 ? '贷：' : '<span style="visibility:hidden">贷：</span>') + esc(l[1]) + '</span><span>' + fmt(l[2]) + '</span></div>'; });
    return h;
  }).join('');
}
function renderEntry(e, d) {
  var multi = e.s.length > 1;
  var c = d.checked;
  var stepsHtml = multi ? '<div class="steps">' + e.s.map(function (st, i) {
    var cls = i === d.step ? ' on' : '';
    if (c) cls += c.steps[i].ok ? ' ok' : ' bad';
    return '<button data-step="' + i + '" class="' + cls + '">' + '①②③④⑤⑥'.charAt(i) + ' ' + esc(st.l || '分录' + (i + 1)) + '</button>';
  }).join('') + '</div>' : '';
  var lines = d.lines[d.step];
  var marks = c ? c.steps[d.step].marks : null;
  var ro = c ? ' disabled' : '';
  var rows = lines.map(function (l, i) {
    var mk = marks && marks[i] != null ? (marks[i] ? ' ok' : ' bad') : '';
    return '<div class="ln' + (l.s === '贷' ? ' cr' : '') + mk + '" data-i="' + i + '">' +
      '<button class="side ' + (l.s === '借' ? 'dr' : 'cr') + '" data-act="side"' + ro + '>' + l.s + '</button>' +
      '<button class="accbtn' + (l.a ? '' : ' ph') + '" data-act="acc"' + ro + '>' + (l.a ? esc(l.a) : '选择会计科目') + '</button>' +
      '<input class="amt" inputmode="decimal" placeholder="金额" value="' + esc(l.m) + '" data-act="amt"' + ro + '>' +
      (c ? '' : '<button class="del" data-act="del" aria-label="删除">×</button>') + '</div>';
  }).join('');
  var dr = sumSide(lines, '借'), cr = sumSide(lines, '贷'), diff = Math.round((dr - cr) * 100) / 100;
  var hint = d.hint[d.step] ? '<div class="explain" style="margin-top:10px">提示：本步共 ' + e.s[d.step].a.length + ' 行分录，涉及科目：' + e.s[d.step].a.map(function (l) { return l[0] + ' ' + esc(l[1]); }).join('，') + '</div>' : '';
  var result = '';
  if (c || d.shown) {
    result = (c ? '<div class="result ' + (c.ok ? 'ok' : 'bad') + '">' + (c.ok ? '✓ 回答正确，分录完全正确！' : '✗ 有错误，标红的行不正确，请对照答案') + '</div>' : '') +
      '<div class="card"><div class="sec-title" style="margin-top:0">参考分录</div><div class="je">' + jeHtml(e) + '</div><div class="explain">' + esc(e.e) + '</div></div>';
  }
  var prev = e.idx > 0 ? ENTRIES[e.idx - 1].id : null, next = e.idx < ENTRIES.length - 1 ? ENTRIES[e.idx + 1].id : null;
  var bar = c ? '<button class="btn plain" id="redo">修改重做</button>' + (next ? '<button class="btn" id="next">下一题</button>' : '<button class="btn" id="toList">返回列表</button>')
              : '<button class="btn plain" id="hint">提示</button><button class="btn plain" id="show">答案</button><button class="btn" id="submit">提交</button>';
  view.innerHTML =
    '<div class="card"><div class="row" style="margin-bottom:8px"><span class="tag">' + CH[e.c] + '</span>' + (multi ? '<span class="tag gray">' + e.s.length + ' 笔分录</span>' : '') + '<span class="grow"></span><span class="small muted">' + (e.idx + 1) + ' / ' + ENTRIES.length + '</span></div>' +
    '<div class="ent-q">' + esc(e.q) + '</div></div>' + stepsHtml +
    '<div class="lines" id="lines">' + rows + '</div>' +
    (c ? '' : '<div class="addrow"><button class="a-dr" id="addDr">＋ 借方</button><button class="a-cr" id="addCr">＋ 贷方</button></div>') +
    '<div class="balance"><span>借方合计 ' + fmt(dr) + '</span><span>贷方合计 ' + fmt(cr) + '</span><span id="diff" class="' + (diff === 0 ? (dr ? 'ok' : '') : 'bad') + '">' + (diff === 0 ? (dr ? '借贷平衡' : '') : '差额 ' + fmt(diff)) + '</span></div>' +
    hint + result +
    '<div class="row small" style="justify-content:space-between;margin-top:14px">' + (prev ? '<a href="#/entry/' + prev + '" data-rep style="color:var(--pri);text-decoration:none">‹ 上一题</a>' : '<span></span>') + (next ? '<a href="#/entry/' + next + '" data-rep style="color:var(--pri);text-decoration:none">下一题 ›</a>' : '') + '</div>' +
    '<div class="actionbar">' + bar + '</div>';

  $$('.steps button').forEach(function (b) { b.onclick = function () { d.step = +b.getAttribute('data-step'); renderEntry(e, d); }; });
  $$('a[data-rep]').forEach(function (a) { a.onclick = function (ev) { ev.preventDefault(); ev.stopPropagation(); go(a.getAttribute('href'), true); }; });
  var box = $('#lines');
  box.onclick = function (ev) {
    var b = ev.target.closest('[data-act]'); if (!b || b.disabled) return;
    var i = +b.closest('.ln').getAttribute('data-i'), act = b.getAttribute('data-act');
    if (act === 'side') { lines[i].s = lines[i].s === '借' ? '贷' : '借'; renderEntry(e, d); }
    else if (act === 'del') { lines.splice(i, 1); renderEntry(e, d); }
    else if (act === 'acc') pickAccount(function (name) { lines[i].a = name; renderEntry(e, d); });
  };
  box.oninput = function (ev) {
    if (ev.target.getAttribute('data-act') !== 'amt') return;
    var i = +ev.target.closest('.ln').getAttribute('data-i');
    lines[i].m = ev.target.value;
    var dr2 = sumSide(lines, '借'), cr2 = sumSide(lines, '贷'), df = Math.round((dr2 - cr2) * 100) / 100;
    var bal = $('.balance').children;
    bal[0].textContent = '借方合计 ' + fmt(dr2); bal[1].textContent = '贷方合计 ' + fmt(cr2);
    bal[2].className = df === 0 ? (dr2 ? 'ok' : '') : 'bad'; bal[2].textContent = df === 0 ? (dr2 ? '借贷平衡' : '') : '差额 ' + fmt(df);
  };
  function add(side) {
    var at = side === '借' ? (function () { var k = 0; lines.forEach(function (l, j) { if (l.s === '借') k = j + 1; }); return k; })() : lines.length;
    lines.splice(at, 0, {s:side, a:'', m:''}); renderEntry(e, d);
  }
  if ($('#addDr')) { $('#addDr').onclick = function () { add('借'); }; $('#addCr').onclick = function () { add('贷'); }; }
  if ($('#hint')) $('#hint').onclick = function () { d.hint[d.step] = true; renderEntry(e, d); };
  if ($('#show')) $('#show').onclick = function () { d.shown = true; renderEntry(e, d); setTimeout(function () { var r = $('.je'); if (r) r.scrollIntoView({behavior:'smooth', block:'center'}); }, 30); };
  if ($('#submit')) $('#submit').onclick = function () {
    var empty = d.lines.some(function (ls) { return !ls.some(function (l) { return l.a && l.m !== ''; }); });
    if (empty) { toast(multi ? '每一步分录都需要填写' : '请先填写分录'); return; }
    var bad = null;
    d.lines.forEach(function (ls) { ls.forEach(function (l) { if ((l.a && l.m === '') || (!l.a && l.m !== '')) bad = '有行未选科目或未填金额'; else if (l.m !== '' && isNaN(num(l.m))) bad = '金额格式不正确'; }); });
    if (bad) { toast(bad); return; }
    d.checked = checkEntry(e, d);
    var s = S.ent[e.id] || (S.ent[e.id] = {n:0});
    s.n++; s.last = d.checked.ok ? 1 : 0;
    if (d.checked.ok && !d.shown) s.best = true;
    if (!d.checked.ok) s.wrong = true;
    bumpDay(); save();
    if (multi && !d.checked.ok) { for (var i = 0; i < d.checked.steps.length; i++) if (!d.checked.steps[i].ok) { d.step = i; break; } }
    renderEntry(e, d);
    setTimeout(function () { var r = $('.result'); if (r) r.scrollIntoView({behavior:'smooth', block:'start'}); }, 30);
  };
  if ($('#redo')) $('#redo').onclick = function () { d.checked = null; renderEntry(e, d); };
  if ($('#next')) $('#next').onclick = function () { go('#/entry/' + next, true); };
  if ($('#toList')) $('#toList').onclick = function () { back(); };
}

// ====================== 科目选择器 ======================
var GROUPS = ['资产', '负债', '所有者权益', '成本', '损益', '政府会计'];
function pickAccount(cb) {
  var grp = '常用';
  var sh = openSheet('选择会计科目', '<div style="position:sticky;top:0;background:var(--bg);padding-bottom:4px;z-index:1"><input class="inp" id="accQ" placeholder="搜索科目，如：应交税费、折旧"><div class="chips" id="accG" style="padding-top:10px"></div></div><div class="acc-list" id="accL"></div>', {full:true});
  var q = $('#accQ', sh), gEl = $('#accG', sh), lEl = $('#accL', sh);
  function chips() { gEl.innerHTML = ['常用'].concat(GROUPS).map(function (g) { return '<button class="chip' + (g === grp ? ' on' : '') + '" data-g="' + g + '">' + g + '</button>'; }).join(''); }
  function row(a) { return '<button class="ai' + (a.p ? ' sub' : '') + '" data-n="' + esc(a.n) + '"><span class="cd">' + (a.code || '') + '</span><span class="grow">' + esc(a.n) + '</span></button>'; }
  function list() {
    var kw = q.value.trim(), out = '';
    if (kw) {
      var r = ACCOUNTS.filter(function (a) { return a.n.indexOf(kw) >= 0 || (a.code && a.code.indexOf(kw) === 0); });
      out = r.length ? r.map(row).join('') : '<div class="empty">没有找到相关科目</div>';
    } else if (grp === '常用') {
      var rec = S.recent.filter(function (n) { return AMAP[n]; });
      out = (rec.length ? '<div class="grp">最近使用</div>' + rec.map(function (n) { return row(AMAP[n]); }).join('') : '') +
        '<div class="grp">高频科目</div>' + ['银行存款','库存现金','原材料','库存商品','应收账款','应付账款','应交税费—应交增值税（进项税额）','应交税费—应交增值税（销项税额）','主营业务收入','主营业务成本','管理费用','销售费用','财务费用','营业外收入','营业外支出','待处理财产损溢','其他应收款','固定资产','累计折旧','应付职工薪酬'].map(function (n) { return row(AMAP[n]); }).join('');
    } else {
      out = ACCOUNTS.filter(function (a) { return a.g === grp; }).map(row).join('');
    }
    lEl.innerHTML = out;
  }
  chips(); list();
  gEl.onclick = function (ev) { var b = ev.target.closest('[data-g]'); if (!b) return; grp = b.getAttribute('data-g'); q.value = ''; chips(); list(); lEl.parentNode.scrollTop = 0; };
  q.oninput = list;
  lEl.onclick = function (ev) {
    var b = ev.target.closest('[data-n]'); if (!b) return;
    var n = b.getAttribute('data-n');
    S.recent = [n].concat(S.recent.filter(function (x) { return x !== n; })).slice(0, 10); save();
    closeSheet(); cb(n);
  };
}

// ====================== 刷题首页 ======================
function viewQuiz() {
  setHeader('刷题');
  var sj = SUBJ[S.subj];
  var subjQs = QUESTIONS.filter(function (q) { return subjOf(q.c) === S.subj; });
  var st = qStats(subjQs);
  var wrongN = Object.keys(S.wrong).filter(function (id) { return QMAP[id]; }).length;
  var favN = Object.keys(S.fav).filter(function (id) { return QMAP[id]; }).length;
  var html = '<div class="seg" id="subj"><button data-s="sw" class="' + (S.subj === 'sw' ? 'on' : '') + '">初级会计实务</button><button data-s="jj" class="' + (S.subj === 'jj' ? 'on' : '') + '">经济法基础</button></div>' +
    '<div class="mode-grid">' +
      mode('#/run/rand/' + S.subj, IC.shuffle, '随机练习', '本科目随机 20 题') +
      mode('#/run/exam/' + S.subj, IC.clock, '模拟考试', '限时 60 分钟 · 百分制') +
      mode('#/run/wrong', IC.x, '错题本', wrongN + ' 道错题') +
      mode('#/run/fav', IC.star, '我的收藏', favN + ' 道题目') +
    '</div>' +
    '<div class="sec-title"><span>章节练习 · ' + sj.name + '</span><span class="muted" style="font-weight:400">已做 ' + st.done + '/' + st.total + (st.acc != null ? ' · 正确率 ' + st.acc + '%' : '') + '</span></div>' +
    '<div class="list">' + sj.chs.map(function (c, i) {
      var s = qStats(qsOf(c));
      return '<a class="item ch-row" href="#/run/ch/' + c + '"><div class="grow"><div style="font-weight:600">' + (S.subj === 'sw' ? '第' + '一二三四五六七八九十'.charAt(i) + '章 ' : '') + CH[c] + '</div>' +
        '<div class="row" style="margin-top:6px"><div class="bar grow"><i style="width:' + (s.total ? s.done / s.total * 100 : 0) + '%"></i></div><span class="pct">' + s.done + '/' + s.total + '</span></div>' +
        (s.acc != null ? '<div class="small muted" style="margin-top:2px">正确率 ' + s.acc + '%</div>' : '') + '</div>' + IC.chev + '</a>';
    }).join('') + '</div>';
  view.innerHTML = html;
  $$('#subj button').forEach(function (b) { b.onclick = function () { S.subj = b.getAttribute('data-s'); save(); viewQuiz(); }; });
}
function mode(href, icon, t, d) { return '<a class="mode" href="' + href + '"><div class="ic">' + icon + '</div><b>' + t + '</b><span>' + d + '</span></a>'; }

// ====================== 做题 ======================
var SES = null;
var EXAM_PLAN = {s:[25, 2], m:[10, 3], j:[10, 2]};
function buildSession(modeName, arg) {
  var ids = [], title = '', exam = false, idx = 0;
  if (modeName === 'ch') { ids = qsOf(arg).map(function (q) { return q.id; }); title = CH[arg]; idx = Math.min(S.pos[arg] || 0, Math.max(ids.length - 1, 0)); S.lastCh = arg; save(); }
  else if (modeName === 'rand') { ids = shuffle(QUESTIONS.filter(function (q) { return subjOf(q.c) === arg; })).slice(0, 20).map(function (q) { return q.id; }); title = '随机练习'; }
  else if (modeName === 'wrong') { ids = Object.keys(S.wrong).filter(function (id) { return QMAP[id]; }).sort(function (a, b) { return S.wrong[b] - S.wrong[a]; }); title = '错题本'; }
  else if (modeName === 'fav') { ids = Object.keys(S.fav).filter(function (id) { return QMAP[id]; }); title = '我的收藏'; }
  else if (modeName === 'exam') {
    exam = true; title = '模拟考试 · ' + SUBJ[arg].name;
    ['s', 'm', 'j'].forEach(function (t) {
      ids = ids.concat(shuffle(QUESTIONS.filter(function (q) { return subjOf(q.c) === arg && q.t === t; })).slice(0, EXAM_PLAN[t][0]).map(function (q) { return q.id; }));
    });
  }
  return {key:modeName + '/' + (arg || ''), mode:modeName, arg:arg, title:title, ids:ids, idx:idx, ans:{}, done:{}, exam:exam, start:Date.now(), limit:60 * 60, submitted:false};
}
function viewRun(m) {
  var modeName = m[1], arg = m[2];
  var key = modeName + '/' + (arg || '');
  if (!SES || SES.key !== key || SES.submitted) SES = buildSession(modeName, arg);
  if (!SES.ids.length) {
    setHeader(SES.title, {noTab:true});
    view.innerHTML = '<div class="empty">' + IC.list + '<div>' + (modeName === 'wrong' ? '错题本是空的，继续保持！' : modeName === 'fav' ? '还没有收藏题目，做题时点击 ☆ 收藏' : '暂无题目') + '</div></div>';
    return;
  }
  renderQ();
  if (SES.exam) {
    guard = function (proceed) { confirmBox('考试尚未交卷，确定退出吗？本次作答将不会保存。', '退出', function () { SES = null; proceed(); }); };
    var tm = setInterval(function () {
      var el = $('#timer'); if (!SES || !SES.exam) { clearInterval(tm); return; }
      var left = SES.limit - Math.floor((Date.now() - SES.start) / 1000);
      if (left <= 0) { clearInterval(tm); toast('时间到，自动交卷'); submitExam(); return; }
      if (el) el.textContent = pad(Math.floor(left / 60)) + ':' + pad(left % 60);
    }, 1000);
    cleanup = function () { clearInterval(tm); };
  }
}
function isCorrect(q, a) {
  if (a == null) return false;
  if (q.t === 'm') { if (!a.length) return false; var x = a.slice().sort().join(','), y = q.a.slice().sort().join(','); return x === y; }
  return a === q.a;
}
function renderQ() {
  var s = SES, id = s.ids[s.idx], q = QMAP[id];
  var ua = s.ans[id], shown = s.exam ? s.submitted : !!s.done[id];
  var fav = !!S.fav[id];
  var acts = (s.exam && !s.submitted ? '<span class="timer" id="timer">60:00</span>' : '') +
    '<button class="icon-btn" id="favBtn" aria-label="收藏">' + (fav ? IC.starOn : IC.star) + '</button>' +
    '<button class="icon-btn" id="cardBtn" aria-label="答题卡">' + IC.grid + '</button>';
  setHeader(s.title, {noTab:true, actions:acts});
  var opts;
  if (q.t === 'j') opts = [['√', '正确', true], ['×', '错误', false]];
  else opts = q.o.map(function (o, i) { return [KEYS.charAt(i), o, i]; });
  var optHtml = opts.map(function (o) {
    var v = o[2], sel = q.t === 'm' ? (ua || []).indexOf(v) >= 0 : ua === v;
    var cls = 'opt' + (q.t === 'm' ? ' m' : '');
    if (shown) {
      var right = q.t === 'm' ? q.a.indexOf(v) >= 0 : q.a === v;
      if (right) cls += (q.t === 'm' && !sel) ? ' miss' : ' right'; else if (sel) cls += ' wrong';
    } else if (sel) cls += ' sel';
    return '<button class="' + cls + '" data-v="' + (q.t === 'j' ? (v ? 1 : 0) : v) + '"' + (shown ? ' disabled' : '') + '><span class="k">' + o[0] + '</span><span class="grow">' + esc(o[1]) + '</span></button>';
  }).join('');
  var ansText = q.t === 'j' ? (q.a ? '正确' : '错误') : q.t === 'm' ? q.a.map(function (i) { return KEYS.charAt(i); }).join('') : KEYS.charAt(q.a);
  var exp = '';
  if (shown) {
    var ok = isCorrect(q, ua);
    exp = '<div class="ans-box"><div class="r ' + (ok ? 'ok' : 'bad') + '">' + (ua == null || (q.t === 'm' && !ua.length) ? '未作答' : ok ? '回答正确' : '回答错误') + ' · 正确答案：' + ansText + '</div>' + (q.t === 'm' && !ok && ua && ua.length ? '<div class="small muted" style="margin-bottom:4px">虚线框为漏选的正确选项</div>' : '') + '<div class="sub" style="font-size:14.5px">' + esc(q.e) + '</div></div>';
  }
  var isLast = s.idx === s.ids.length - 1;
  var confirmBtn = !shown && q.t === 'm' && !s.exam ? '<button class="btn" id="mOk"' + (ua && ua.length ? '' : ' disabled') + '>确认答案</button>' : '';
  var nextBtn = s.exam && !s.submitted && isLast ? '<button class="btn" id="handIn">交卷</button>' : '<button class="btn' + (confirmBtn ? ' plain' : '') + '" id="nx"' + (isLast ? ' disabled' : '') + '>下一题</button>';
  view.innerHTML =
    '<div class="q-head"><span class="tag">' + TYPE[q.t] + '</span><span>' + CH[q.c] + '</span><span class="grow"></span><span>' + (s.idx + 1) + ' / ' + s.ids.length + '</span></div>' +
    '<div class="q-text">' + esc(q.q) + '</div>' + optHtml + exp +
    '<div class="qnav"><button class="btn plain" id="pv"' + (s.idx === 0 ? ' disabled' : '') + '>上一题</button>' + (confirmBtn || '') + nextBtn + '</div>';
  $$('.opt').forEach(function (b) {
    b.onclick = function () {
      var raw = b.getAttribute('data-v'), v = q.t === 'j' ? raw === '1' : +raw;
      if (q.t === 'm') {
        var arr = (s.ans[id] || []).slice(), k = arr.indexOf(v);
        if (k >= 0) arr.splice(k, 1); else arr.push(v);
        s.ans[id] = arr; renderQ();
      } else {
        s.ans[id] = v;
        if (!s.exam) { s.done[id] = true; record(q, isCorrect(q, v), s.mode === 'wrong'); }
        renderQ();
        if (s.exam && !isLast) setTimeout(function () { if (SES === s && s.ids[s.idx] === id) { s.idx++; renderQ(); } }, 220);
      }
    };
  });
  if ($('#mOk')) $('#mOk').onclick = function () { s.done[id] = true; record(q, isCorrect(q, s.ans[id]), s.mode === 'wrong'); renderQ(); };
  $('#pv').onclick = function () { if (s.idx > 0) { s.idx--; savePos(); renderQ(); view.scrollTop = 0; } };
  if ($('#nx')) $('#nx').onclick = function () { if (!isLast) { s.idx++; savePos(); renderQ(); view.scrollTop = 0; } };
  if ($('#handIn')) $('#handIn').onclick = askSubmit;
  $('#favBtn').onclick = function () { if (S.fav[id]) { delete S.fav[id]; toast('已取消收藏'); } else { S.fav[id] = Date.now(); toast('已收藏'); } save(); renderQ(); };
  $('#cardBtn').onclick = showCard;
  if (s.exam && !s.submitted) { var left = s.limit - Math.floor((Date.now() - s.start) / 1000); $('#timer').textContent = pad(Math.floor(left / 60)) + ':' + pad(left % 60); }
}
function savePos() { if (SES.mode === 'ch') { S.pos[SES.arg] = SES.idx; save(); } }
function showCard() {
  var s = SES;
  var cells = s.ids.map(function (id, i) {
    var q = QMAP[id], cls = '';
    var answered = s.ans[id] != null && !(q.t === 'm' && !s.ans[id].length);
    if (s.exam && !s.submitted) cls = answered ? 'done' : '';
    else if (s.done[id] || s.submitted) cls = isCorrect(q, s.ans[id]) ? 'ok' : (answered ? 'bad' : '');
    if (i === s.idx) cls += ' cur';
    return '<button class="' + cls + '" data-i="' + i + '">' + (i + 1) + '</button>';
  }).join('');
  var answeredN = s.ids.filter(function (id) { var a = s.ans[id]; return a != null && !(Array.isArray(a) && !a.length); }).length;
  var sh = openSheet('答题卡', '<p class="small sub" style="margin-top:0">已答 ' + answeredN + ' / ' + s.ids.length + '</p><div class="card-grid">' + cells + '</div>' +
    (s.exam && !s.submitted ? '<button class="btn block" id="cSub" style="margin-top:16px">交卷</button>' : ''));
  $$('.card-grid button', sh).forEach(function (b) { b.onclick = function () { s.idx = +b.getAttribute('data-i'); savePos(); closeSheet(); renderQ(); view.scrollTop = 0; }; });
  if ($('#cSub', sh)) $('#cSub', sh).onclick = function () { closeSheet(); askSubmit(); };
}
function askSubmit() {
  var s = SES;
  var un = s.ids.filter(function (id) { var a = s.ans[id]; return a == null || (Array.isArray(a) && !a.length); }).length;
  confirmBox(un ? '还有 ' + un + ' 题未作答，确定交卷吗？' : '确定交卷吗？', '交卷', submitExam);
}
function submitExam() {
  var s = SES; if (!s || s.submitted) return;
  var got = 0, full = 0, right = 0, by = {s:[0, 0], m:[0, 0], j:[0, 0]};
  s.ids.forEach(function (id) {
    var q = QMAP[id], w = EXAM_PLAN[q.t][1], ok = isCorrect(q, s.ans[id]);
    full += w; by[q.t][1]++;
    if (ok) { got += w; right++; by[q.t][0]++; }
    var a = s.ans[id]; if (a != null && !(Array.isArray(a) && !a.length)) record(q, ok, false); else S.wrong[id] = Date.now();
  });
  var score = Math.round(got / full * 100);
  var used = Math.min(s.limit, Math.floor((Date.now() - s.start) / 1000));
  s.submitted = true; s.result = {score:score, right:right, total:s.ids.length, by:by, used:used};
  S.exams.unshift({d:today(), subj:s.arg, score:score, used:used}); S.exams = S.exams.slice(0, 30); save();
  guard = null;
  go('#/result', true);
}
function viewResult() {
  if (!SES || !SES.result) { go('#/quiz'); return; }
  var r = SES.result;
  setHeader('考试结果', {noTab:true});
  var C = 2 * Math.PI * 64, col = r.score >= 60 ? 'var(--ok)' : 'var(--bad)';
  view.innerHTML = '<div class="card" style="text-align:center"><div class="score-ring"><svg viewBox="0 0 150 150"><circle cx="75" cy="75" r="64" stroke="var(--line)" stroke-width="10" fill="none"/><circle cx="75" cy="75" r="64" stroke="' + col + '" stroke-width="10" fill="none" stroke-linecap="round" stroke-dasharray="' + (C * r.score / 100) + ' ' + C + '"/></svg><div class="v"><div><b>' + r.score + '</b><div class="small muted">分</div></div></div></div>' +
    '<div style="font-weight:700;font-size:17px">' + (r.score >= 60 ? '恭喜通过！' : '继续加油，离及格还差 ' + (60 - r.score) + ' 分') + '</div>' +
    '<div class="small muted" style="margin-top:4px">答对 ' + r.right + ' / ' + r.total + ' 题 · 用时 ' + Math.floor(r.used / 60) + ' 分 ' + (r.used % 60) + ' 秒</div></div>' +
    '<div class="card"><table class="tbl"><tr><th>题型</th><th>答对</th><th>题数</th><th>每题分值</th></tr>' +
    ['s', 'm', 'j'].map(function (t) { return '<tr><td>' + TYPE[t] + '</td><td>' + r.by[t][0] + '</td><td>' + r.by[t][1] + '</td><td>' + EXAM_PLAN[t][1] + '</td></tr>'; }).join('') + '</table>' +
    '<p class="small muted" style="margin:10px 0 0">得分按百分制折算；多选题须全部选对才得分。错题已自动加入错题本。</p></div>' +
    '<div class="row"><button class="btn plain grow" id="review">查看解析</button><button class="btn grow" id="again">再考一次</button></div>';
  $('#review').onclick = function () { SES.idx = 0; stack[stack.length - 1] = '#/run/' + SES.mode + '/' + SES.arg; try { history.replaceState(null, '', stack[stack.length - 1]); } catch (e) {} reviewMode(); };
  $('#again').onclick = function () { var a = SES.arg; SES = null; go('#/run/exam/' + a, true); };
}
function reviewMode() { cleanup = null; guard = null; renderQ(); }

// ====================== 知识点 ======================
var knowSubj = 'sw';
function viewKnow() {
  setHeader('知识点');
  var chs = knowSubj === 'sw' ? SUBJ.sw.chs : ['j0'];
  view.innerHTML = '<div class="seg" id="ks"><button data-s="sw" class="' + (knowSubj === 'sw' ? 'on' : '') + '">初级会计实务</button><button data-s="jj" class="' + (knowSubj === 'jj' ? 'on' : '') + '">经济法基础</button></div>' +
    '<input class="inp" id="kq" placeholder="搜索知识点，如：折旧、包装物、城建税" style="margin-bottom:12px">' +
    '<div id="kres"></div>';
  var res = $('#kres');
  function listChs() {
    res.innerHTML = '<div class="list">' + chs.map(function (c, i) {
      var k = KNOWLEDGE.filter(function (x) { return x.c === c; })[0];
      return '<a class="item" href="#/know/' + c + '"><div class="grow"><div style="font-weight:600">' + (knowSubj === 'sw' ? '第' + '一二三四五六七八九十'.charAt(i) + '章 ' : '') + CH[c] + '</div><div class="small muted">' + (k ? k.items.length : 0) + ' 个要点' + (k ? ' · ' + k.items.slice(0, 3).map(function (x) { return x.h; }).join('、') : '') + '</div></div>' + IC.chev + '</a>';
    }).join('') + '</div>' + (knowSubj === 'jj' ? '<p class="small muted" style="padding:0 4px">经济法基础以高频数字速记为主，更多内容可在“刷题 → 经济法基础”中通过题目解析学习。</p>' : '');
  }
  listChs();
  $('#kq').oninput = function () {
    var kw = this.value.trim();
    if (!kw) { listChs(); return; }
    var hits = [];
    KNOWLEDGE.forEach(function (k) { k.items.forEach(function (it) { var txt = it.b.replace(/<[^>]+>/g, ''); if (it.h.indexOf(kw) >= 0 || txt.indexOf(kw) >= 0) hits.push({c:k.c, it:it}); }); });
    res.innerHTML = hits.length ? hits.map(function (h) { return '<details class="acc" open><summary><span class="grow">' + esc(h.it.h) + '<div class="small muted" style="font-weight:400">' + CH[h.c] + '</div></span>' + IC.chev + '</summary><div class="kb">' + h.it.b + '</div></details>'; }).join('') : '<div class="empty">没有找到相关知识点</div>';
  };
  $$('#ks button').forEach(function (b) { b.onclick = function () { knowSubj = b.getAttribute('data-s'); viewKnow(); }; });
}
function viewKnowCh(m) {
  var c = m[1], k = KNOWLEDGE.filter(function (x) { return x.c === c; })[0];
  if (!k) { go('#/know'); return; }
  setHeader(CH[c], {noTab:true});
  var hasQ = qsOf(c).length, hasE = ENTRIES.some(function (e) { return e.c === c; });
  view.innerHTML = k.items.map(function (it, i) { return '<details class="acc"' + (i === 0 ? ' open' : '') + '><summary><span class="grow">' + esc(it.h) + '</span>' + IC.chev + '</summary><div class="kb">' + it.b + '</div></details>'; }).join('') +
    '<div class="row" style="margin-top:14px">' + (hasQ ? '<a class="btn ghost grow" href="#/run/ch/' + c + '">本章练习（' + hasQ + '题）</a>' : '') + (hasE ? '<a class="btn grow" href="#/entries" id="toEnt">本章分录</a>' : '') + '</div>';
  if ($('#toEnt')) $('#toEnt').onclick = function () { entFilter = c; };
}

// ====================== 工具 ======================
var TOOLS = [
  ['accounts', '会计科目表', '常用科目编码、类别、余额方向及说明'],
  ['dep', '固定资产折旧', '年限平均法 / 双倍余额递减法 / 年数总和法 / 工作量法'],
  ['mcv', '材料成本差异', '计划成本法下差异率与发出、结存实际成本'],
  ['inv', '存货估价', '毛利率法 / 售价金额核算法'],
  ['eq', '约当产量法', '完工产品与月末在产品成本分配'],
  ['vat', '增值税计算', '价税分离 / 应纳税额'],
  ['profit', '利润计算', '营业利润 → 利润总额 → 净利润'],
  ['bank', '银行存款余额调节表', '未达账项调节'],
  ['iit', '个人所得税', '居民个人综合所得年度应纳税额']
];
function viewTools() {
  setHeader('工具');
  view.innerHTML = '<div class="list">' + TOOLS.map(function (t) { return '<a class="item" href="#/tools/' + t[0] + '"><div class="grow"><div style="font-weight:600">' + t[1] + '</div><div class="small muted">' + t[2] + '</div></div>' + IC.chev + '</a>'; }).join('') + '</div>' +
    '<p class="small muted" style="padding:0 4px">计算结果仅供学习参考，考试时请按题目给定条件计算。</p>';
}
function viewTool(m) {
  var t = TOOLS.filter(function (x) { return x[0] === m[1]; })[0];
  if (!t) { go('#/tools'); return; }
  setHeader(t[1], {noTab:true});
  TOOL_IMPL[t[0]]();
}
function fi(id, label, val, ph) { return '<div class="fi"><label for="' + id + '">' + label + '</label><input class="inp" id="' + id + '" inputmode="decimal" value="' + (val == null ? '' : val) + '" placeholder="' + (ph || '') + '"></div>'; }
function kv(k, v, strong) { return '<div class="kv"><span>' + k + '</span>' + (strong ? '<b>' + v + '</b>' : '<span>' + v + '</span>') + '</div>'; }
function bindCalc(fn) { $$('#view input, #view select').forEach(function (el) { el.addEventListener('input', fn); el.addEventListener('change', fn); }); fn(); }
function v(id) { return num($('#' + id).value); }
function z(x) { return isNaN(x) ? 0 : x; }

var TOOL_IMPL = {
  accounts: function () {
    var grp = '全部';
    view.innerHTML = '<input class="inp" id="aq" placeholder="搜索科目名称或编码" style="margin-bottom:10px"><div class="chips" id="ag"></div><div class="list" id="al"></div>';
    function draw() {
      $('#ag').innerHTML = ['全部'].concat(GROUPS).map(function (g) { return '<button class="chip' + (g === grp ? ' on' : '') + '" data-g="' + g + '">' + g + '</button>'; }).join('');
      var kw = $('#aq').value.trim();
      var r = ACCOUNTS.filter(function (a) { return (grp === '全部' || a.g === grp) && (!kw || a.n.indexOf(kw) >= 0 || (a.code && a.code.indexOf(kw) === 0)); });
      $('#al').innerHTML = r.length ? r.map(function (a) {
        return '<div class="acct-row"' + (a.p ? ' style="padding-left:30px"' : '') + '><div class="nm">' + esc(a.n) + '<span class="meta">' + (a.code ? a.code + ' · ' : '') + a.g + '类 · 余额在' + a.d + '方</span></div>' + (a.tip ? '<div class="tp">' + esc(a.tip) + '</div>' : '') + '</div>';
      }).join('') : '<div class="empty">没有找到相关科目</div>';
    }
    draw();
    $('#aq').oninput = draw;
    $('#ag').onclick = function (e) { var b = e.target.closest('[data-g]'); if (b) { grp = b.getAttribute('data-g'); draw(); } };
  },
  dep: function () {
    view.innerHTML = '<div class="card form"><div class="fi"><label>折旧方法</label><select class="inp" id="dm"><option value="sl">年限平均法</option><option value="ddb">双倍余额递减法</option><option value="syd">年数总和法</option><option value="wl">工作量法</option></select></div>' +
      '<div class="two">' + fi('dc', '固定资产原价', 100000) + fi('dr', '预计净残值率（%）', 4) + '</div>' +
      '<div class="two" id="yrBox">' + fi('dy', '预计使用年限', 5) + '<div></div></div>' +
      '<div class="two" id="wlBox" hidden>' + fi('dw', '预计总工作量', 200000) + fi('dn', '本期实际工作量', 3000) + '</div></div><div class="card" id="dout"></div>';
    bindCalc(function () {
      var mth = $('#dm').value, c = v('dc'), rate = z(v('dr')) / 100, n = Math.floor(v('dy'));
      $('#yrBox').hidden = mth === 'wl'; $('#wlBox').hidden = mth !== 'wl';
      var sal = c * rate, out = $('#dout');
      if (isNaN(c) || c <= 0) { out.innerHTML = '<span class="muted">请输入原价</span>'; return; }
      if (mth === 'wl') {
        var W = v('dw'), w = v('dn');
        if (!(W > 0)) { out.innerHTML = '<span class="muted">请输入预计总工作量</span>'; return; }
        var unit = c * (1 - rate) / W;
        out.innerHTML = '<div class="out">' + kv('预计净残值', fmt(sal)) + kv('单位工作量折旧额', fmt(unit, 4)) + kv('本期折旧额', fmt(unit * z(w)), true) + '</div><p class="small muted" style="margin:10px 0 0">单位工作量折旧额 = 原价 ×（1 − 净残值率）÷ 预计总工作量</p>';
        return;
      }
      if (!(n >= 1 && n <= 100)) { out.innerHTML = '<span class="muted">请输入 1~100 的使用年限</span>'; return; }
      var rows = [], bv = c, acc = 0, sum = n * (n + 1) / 2;
      for (var y = 1; y <= n; y++) {
        var d;
        if (mth === 'sl') d = (c - sal) / n;
        else if (mth === 'syd') d = (c - sal) * (n - y + 1) / sum;
        else d = y <= n - 2 ? bv * 2 / n : (y === n - 1 ? (bv - sal) / 2 : bv - sal);
        if (mth === 'ddb' && y === n) d = bv - sal;
        acc += d; bv -= d;
        rows.push('<tr><td>第' + y + '年</td><td>' + fmt(d) + '</td><td>' + fmt(acc) + '</td><td>' + fmt(bv) + '</td></tr>');
      }
      var note = {sl:'年折旧额 =（原价 − 预计净残值）÷ 预计使用年限', ddb:'年折旧率 = 2 ÷ 预计使用年限 = ' + fmt(200 / n) + '%；前 ' + Math.max(n - 2, 0) + ' 年按期初账面净值 × 折旧率计算，最后两年改用年限平均法（扣除净残值）', syd:'年折旧额 =（原价 − 净残值）× 尚可使用年限 ÷ ' + sum}[mth];
      out.innerHTML = '<div class="out">' + kv('预计净残值', fmt(sal)) + kv('应计折旧总额', fmt(c - sal), true) + '</div><table class="tbl"><tr><th>年度</th><th>折旧额</th><th>累计折旧</th><th>期末净值</th></tr>' + rows.join('') + '</table><p class="small muted" style="margin:10px 0 0">' + note + '</p>';
    });
  },
  mcv: function () {
    view.innerHTML = '<div class="card form"><div class="two">' + fi('m1', '月初结存计划成本', 100000) + fi('m2', '月初差异（超支+，节约−）', 2000) + '</div><div class="two">' + fi('m3', '本月入库计划成本', 300000) + fi('m4', '本月入库实际成本', 294000) + '</div>' + fi('m5', '本月发出计划成本', 200000) + '</div><div class="card" id="mout"></div>';
    bindCalc(function () {
      var p0 = z(v('m1')), d0 = z(v('m2')), p1 = z(v('m3')), a1 = z(v('m4')), po = z(v('m5'));
      var d1 = a1 - p1, base = p0 + p1;
      if (!base) { $('#mout').innerHTML = '<span class="muted">请输入计划成本</span>'; return; }
      var r = (d0 + d1) / base, dOut = po * r;
      $('#mout').innerHTML = '<div class="out">' + kv('本月入库差异', fmt(d1) + (d1 > 0 ? '（超支）' : d1 < 0 ? '（节约）' : '')) + kv('材料成本差异率', fmt(r * 100, 4) + '%', true) +
        kv('发出材料负担差异', fmt(dOut)) + kv('发出材料实际成本', fmt(po + dOut), true) + kv('月末结存计划成本', fmt(base - po)) + kv('月末结存实际成本', fmt(base - po + d0 + d1 - dOut)) + '</div>' +
        '<p class="small muted" style="margin:10px 0 0">差异率 =（月初差异 + 本月入库差异）÷（月初计划成本 + 本月入库计划成本）</p>';
    });
  },
  inv: function () {
    var md = 'gp';
    function draw() {
      view.innerHTML = '<div class="seg" id="im"><button data-m="gp" class="' + (md === 'gp' ? 'on' : '') + '">毛利率法</button><button data-m="rp" class="' + (md === 'rp' ? 'on' : '') + '">售价金额核算法</button></div>' +
        (md === 'gp' ? '<div class="card form"><div class="two">' + fi('g1', '期初存货成本', 600000) + fi('g2', '本期购货成本', 1400000) + '</div><div class="two">' + fi('g3', '本期销售收入', 1600000) + fi('g4', '销售退回与折让', 0) + '</div>' + fi('g5', '毛利率（%）', 25) + '</div>'
                     : '<div class="card form"><div class="two">' + fi('r1', '期初库存商品进价成本', 200000) + fi('r2', '期初库存商品售价', 300000) + '</div><div class="two">' + fi('r3', '本期购进进价成本', 1000000) + fi('r4', '本期购进售价', 1200000) + '</div>' + fi('r5', '本期销售收入', 1000000) + '</div>') +
        '<div class="card" id="iout"></div>';
      $$('#im button').forEach(function (b) { b.onclick = function () { md = b.getAttribute('data-m'); draw(); }; });
      bindCalc(function () {
        if (md === 'gp') {
          var net = z(v('g3')) - z(v('g4')), gp = net * z(v('g5')) / 100, cost = net - gp;
          $('#iout').innerHTML = '<div class="out">' + kv('销售净额', fmt(net)) + kv('销售毛利', fmt(gp)) + kv('销售成本', fmt(cost), true) + kv('期末存货成本', fmt(z(v('g1')) + z(v('g2')) - cost), true) + '</div>';
        } else {
          var sp = z(v('r2')) + z(v('r4')), diff = sp - z(v('r1')) - z(v('r3'));
          if (!sp) { $('#iout').innerHTML = '<span class="muted">请输入售价</span>'; return; }
          var rate = diff / sp, sales = z(v('r5')), cost2 = sales - sales * rate;
          $('#iout').innerHTML = '<div class="out">' + kv('商品进销差价率', fmt(rate * 100, 4) + '%', true) + kv('本期销售商品应分摊的进销差价', fmt(sales * rate)) + kv('本期销售商品成本', fmt(cost2), true) + kv('期末结存商品成本', fmt(z(v('r1')) + z(v('r3')) - cost2)) + '</div>';
        }
      });
    }
    draw();
  },
  eq: function () {
    view.innerHTML = '<div class="card form"><div class="two">' + fi('e1', '本月完工数量（件）', 400) + fi('e2', '月末在产品数量（件）', 100) + '</div><div class="two">' + fi('e3', '在产品完工程度（%）', 50) +
      '<div class="fi"><label>原材料投入方式</label><select class="inp" id="e4"><option value="once">开工时一次投入</option><option value="prog">随加工进度陆续投入</option></select></div></div>' +
      '<div class="two">' + fi('e5', '直接材料费用合计', 50000) + fi('e6', '直接人工 + 制造费用合计', 36000) + '</div></div><div class="card" id="eout"></div>';
    bindCalc(function () {
      var f = z(v('e1')), w = z(v('e2')), p = z(v('e3')) / 100, mat = z(v('e5')), conv = z(v('e6'));
      var eqC = w * p, eqM = $('#e4').value === 'once' ? w : eqC;
      if (!(f + eqM) || !(f + eqC)) { $('#eout').innerHTML = '<span class="muted">请输入数量</span>'; return; }
      var um = mat / (f + eqM), uc = conv / (f + eqC);
      var fm = um * f, fc = uc * f;
      $('#eout').innerHTML = '<table class="tbl"><tr><th>项目</th><th>约当产量</th><th>单位成本</th><th>完工产品</th><th>在产品</th></tr>' +
        '<tr><td>直接材料</td><td>' + fmt(eqM) + '</td><td>' + fmt(um) + '</td><td>' + fmt(fm) + '</td><td>' + fmt(mat - fm) + '</td></tr>' +
        '<tr><td>加工费用</td><td>' + fmt(eqC) + '</td><td>' + fmt(uc) + '</td><td>' + fmt(fc) + '</td><td>' + fmt(conv - fc) + '</td></tr></table>' +
        '<div class="out" style="margin-top:12px">' + kv('完工产品总成本', fmt(fm + fc), true) + kv('完工产品单位成本', fmt(f ? (fm + fc) / f : NaN)) + kv('月末在产品成本', fmt(mat + conv - fm - fc), true) + '</div>';
    });
  },
  vat: function () {
    var md = 'sep';
    function draw() {
      view.innerHTML = '<div class="seg" id="vm"><button data-m="sep" class="' + (md === 'sep' ? 'on' : '') + '">价税分离</button><button data-m="pay" class="' + (md === 'pay' ? 'on' : '') + '">应纳税额</button></div>' +
        (md === 'sep' ? '<div class="card form">' + fi('v1', '金额', 113000) + '<div class="two"><div class="fi"><label>金额类型</label><select class="inp" id="v2"><option value="in">含税金额</option><option value="ex">不含税金额</option></select></div>' +
          '<div class="fi"><label>税率 / 征收率</label><select class="inp" id="v3"><option>13</option><option>9</option><option>6</option><option>5</option><option>3</option><option>1</option></select></div></div></div>'
          : '<div class="card form"><div class="two">' + fi('p1', '当期销项税额', 60000) + fi('p2', '当期进项税额', 45000) + '</div><div class="two">' + fi('p3', '进项税额转出', 3000) + fi('p4', '上期留抵税额', 0) + '</div></div>') +
        '<div class="card" id="vout"></div>';
      $$('#vm button').forEach(function (b) { b.onclick = function () { md = b.getAttribute('data-m'); draw(); }; });
      bindCalc(function () {
        if (md === 'sep') {
          var a = z(v('v1')), r = +$('#v3').value / 100, ex = $('#v2').value === 'in' ? a / (1 + r) : a;
          $('#vout').innerHTML = '<div class="out">' + kv('不含税金额', fmt(ex), true) + kv('增值税额', fmt(ex * r), true) + kv('价税合计', fmt(ex * (1 + r))) + '</div><p class="small muted" style="margin:10px 0 0">不含税金额 = 含税金额 ÷（1 + 税率）</p>';
        } else {
          var t = z(v('p1')) - (z(v('p2')) - z(v('p3'))) - z(v('p4'));
          $('#vout').innerHTML = '<div class="out">' + (t >= 0 ? kv('本期应纳增值税', fmt(t), true) : kv('期末留抵税额', fmt(-t), true)) + '</div><p class="small muted" style="margin:10px 0 0">应纳税额 = 销项税额 −（进项税额 − 进项税额转出）− 上期留抵税额</p>';
        }
      });
    }
    draw();
  },
  profit: function () {
    var F = [['pf1', '营业收入', 1000], ['pf2', '营业成本', 600], ['pf3', '税金及附加', 20], ['pf4', '销售费用', 50], ['pf5', '管理费用', 80], ['pf6', '研发费用', 0], ['pf7', '财务费用', 10],
      ['pf8', '其他收益', 0], ['pf9', '投资收益（损失以−填）', 30], ['pf10', '公允价值变动收益（损失以−填）', 0], ['pf11', '信用减值损失', 0], ['pf12', '资产减值损失', 0], ['pf13', '资产处置收益（损失以−填）', 0],
      ['pf14', '营业外收入', 15], ['pf15', '营业外支出', 5], ['pf16', '所得税费用', 0]];
    var html = '<div class="card form">';
    for (var i = 0; i < F.length; i += 2) html += '<div class="two">' + fi(F[i][0], F[i][1], F[i][2]) + (F[i + 1] ? fi(F[i + 1][0], F[i + 1][1], F[i + 1][2]) : '') + '</div>';
    view.innerHTML = html + '</div><div class="card" id="pout"></div>';
    bindCalc(function () {
      var g = function (k) { return z(v(k)); };
      var op = g('pf1') - g('pf2') - g('pf3') - g('pf4') - g('pf5') - g('pf6') - g('pf7') + g('pf8') + g('pf9') + g('pf10') - g('pf11') - g('pf12') + g('pf13');
      var tp = op + g('pf14') - g('pf15');
      $('#pout').innerHTML = '<div class="out">' + kv('营业利润', fmt(op), true) + kv('利润总额', fmt(tp), true) + kv('净利润', fmt(tp - g('pf16')), true) + '</div>';
    });
  },
  bank: function () {
    view.innerHTML = '<div class="card form"><div class="sec-title" style="margin-top:0">企业方</div>' + fi('b1', '银行存款日记账余额', 56000) + '<div class="two">' + fi('b2', '加：银行已收、企业未收', 4000) + fi('b3', '减：银行已付、企业未付', 0) + '</div>' +
      '<div class="sec-title">银行方</div>' + fi('b4', '银行对账单余额', 62000) + '<div class="two">' + fi('b5', '加：企业已收、银行未收', 0) + fi('b6', '减：企业已付、银行未付', 2000) + '</div></div><div class="card" id="bout"></div>';
    bindCalc(function () {
      var l = z(v('b1')) + z(v('b2')) - z(v('b3')), r = z(v('b4')) + z(v('b5')) - z(v('b6'));
      var okb = Math.abs(l - r) < 0.005;
      $('#bout').innerHTML = '<div class="out">' + kv('企业调节后余额', fmt(l), true) + kv('银行调节后余额', fmt(r), true) + '</div>' +
        '<div class="result ' + (okb ? 'ok' : 'bad') + '" style="margin-bottom:0">' + (okb ? '✓ 双方余额相等，企业可动用的银行存款为 ' + fmt(l) : '✗ 调节后不相等，差额 ' + fmt(l - r) + '，可能存在记账错误') + '</div>';
    });
  },
  iit: function () {
    view.innerHTML = '<div class="card form">' + fi('i1', '全年综合所得收入额', 200000) + '<div class="two">' + fi('i2', '专项扣除（三险一金）', 20000) + fi('i3', '专项附加扣除', 24000) + '</div>' + fi('i4', '依法确定的其他扣除', 0) + '</div><div class="card" id="iout"></div>';
    var T = [[36000, 3, 0], [144000, 10, 2520], [300000, 20, 16920], [420000, 25, 31920], [660000, 30, 52920], [960000, 35, 85920], [Infinity, 45, 181920]];
    bindCalc(function () {
      var ti = Math.max(0, z(v('i1')) - 60000 - z(v('i2')) - z(v('i3')) - z(v('i4')));
      var row = T.filter(function (t) { return ti <= t[0]; })[0];
      var tax = ti * row[1] / 100 - row[2];
      $('#iout').innerHTML = '<div class="out">' + kv('基本减除费用', '60,000') + kv('应纳税所得额', fmt(ti), true) + kv('适用税率', row[1] + '%') + kv('速算扣除数', fmt(row[2])) + kv('全年应纳税额', fmt(Math.max(0, tax)), true) + '</div>' +
        '<p class="small muted" style="margin:10px 0 0">应纳税额 = 应纳税所得额 × 税率 − 速算扣除数。劳务报酬、稿酬、特许权使用费所得以收入减除 20% 费用后为收入额，稿酬所得收入额再减按 70% 计算。</p>';
    });
  }
};

// ====================== 我的 ======================
function viewMe() {
  setHeader('学习统计与设置', {noTab:true});
  var es = entStats();
  function subjBlock(k) {
    return '<div class="sec-title">' + SUBJ[k].name + '</div><div class="card">' + SUBJ[k].chs.map(function (c) {
      var s = qStats(qsOf(c));
      return '<div style="margin-bottom:10px"><div class="row small"><span class="grow">' + CH[c] + '</span><span class="muted">' + s.done + '/' + s.total + (s.acc != null ? ' · ' + s.acc + '%' : '') + '</span></div><div class="bar" style="margin-top:4px"><i style="width:' + (s.total ? s.done / s.total * 100 : 0) + '%"></i></div></div>';
    }).join('') + '</div>';
  }
  var exams = S.exams.length ? '<div class="list">' + S.exams.slice(0, 10).map(function (x) { return '<div class="item"><div class="grow"><div style="font-weight:600">' + SUBJ[x.subj].name + '</div><div class="small muted">' + x.d + ' · 用时 ' + Math.ceil(x.used / 60) + ' 分钟</div></div><span class="tag ' + (x.score >= 60 ? 'ok' : 'bad') + '">' + x.score + ' 分</span></div>'; }).join('') + '</div>' : '<div class="card muted small">还没有模拟考试记录</div>';
  view.innerHTML =
    '<div class="card"><div class="row"><div class="grow"><div style="font-size:13px" class="muted">分录实操</div><div style="font-size:22px;font-weight:700">' + es.ok + ' <span class="small muted">/ ' + es.total + ' 已掌握</span></div></div><div><div style="font-size:13px" class="muted">已练习</div><div style="font-size:22px;font-weight:700">' + es.done + '</div></div></div></div>' +
    subjBlock('sw') + subjBlock('jj') +
    '<div class="sec-title">模拟考试记录</div>' + exams +
    '<div class="sec-title">设置</div><div class="list">' +
      '<div class="item"><div class="grow">考试日期</div><button class="btn sm plain" id="sDate">' + (S.examDate || '未设置') + '</button></div>' +
      '<div class="item"><div class="grow">外观</div><div class="seg" id="sTheme" style="margin:0;width:180px"><button data-t="auto">自动</button><button data-t="light">浅色</button><button data-t="dark">深色</button></div></div>' +
      '<div class="item"><div class="grow">错题本中答对后自动移除</div><button class="btn sm ' + (S.autoRemove ? '' : 'plain') + '" id="sAuto">' + (S.autoRemove ? '已开启' : '已关闭') + '</button></div>' +
      '<div class="item"><div class="grow" style="color:var(--bad)">清空全部学习记录</div><button class="btn sm plain" id="sReset">清空</button></div>' +
    '</div>' +
    '<p class="small muted" style="text-align:center;margin-top:18px">初级会计实操 v1.0 · 题库 ' + QUESTIONS.length + ' 题 · 分录 ' + ENTRIES.length + ' 笔<br>数据仅保存在本机，卸载应用会清除记录</p>';
  $$('#sTheme button').forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-t') === S.theme); b.onclick = function () { S.theme = b.getAttribute('data-t'); save(); applyTheme(); viewMe(); }; });
  $('#sDate').onclick = pickExamDate;
  $('#sAuto').onclick = function () { S.autoRemove = !S.autoRemove; save(); viewMe(); };
  $('#sReset').onclick = function () { confirmBox('将清空做题记录、错题、收藏、分录进度和考试记录，且无法恢复。', '清空', function () {
    ['qa', 'wrong', 'fav', 'ent', 'pos', 'days'].forEach(function (k) { S[k] = {}; }); S.exams = []; S.recent = []; S.lastEntry = ''; S.lastCh = ''; drafts = {}; SES = null; save(); toast('已清空'); viewMe(); }); };
}

// ====================== 启动 ======================
var start = location.hash && location.hash.indexOf('#/') === 0 ? location.hash : '#/home';
stack = ROOTS[start] ? [start] : ['#/home', start];
try { history.replaceState(null, '', start); } catch (e) {}
render();
})();
