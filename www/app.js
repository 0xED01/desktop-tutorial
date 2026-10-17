(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const MAX_DIM = 200;
  const ZOOMS = [4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40];

  // ---------- color helpers ----------
  const lin = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  const fLab = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  function rgb2lab(r, g, b) {
    const R = lin(r), G = lin(g), B = lin(b);
    const x = fLab((0.4124 * R + 0.3576 * G + 0.1805 * B) / 0.95047);
    const y = fLab(0.2126 * R + 0.7152 * G + 0.0722 * B);
    const z = fLab((0.0193 * R + 0.1192 * G + 0.9505 * B) / 1.08883);
    return [116 * y - 16, 500 * (x - y), 200 * (y - z)];
  }
  const dist2 = (a, b) => { const d0 = a[0] - b[0], d1 = a[1] - b[1], d2 = a[2] - b[2]; return d0 * d0 + d1 * d1 + d2 * d2; };
  const hex2rgb = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const rgb2hex = (c) => '#' + c.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();
  const luma = (c) => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

  function mulberry32(a) {
    return () => { a |= 0; a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }

  // ---------- state ----------
  const S = {
    img: null, src: null, srcW: 0, srcH: 0,
    W: 29, H: 29, lock: true, board: 29,
    pal: 'auto', maxc: 16, sat: 100, con: 100, bg: false, bgtol: 12, clean: true,
    codes: true, round: false, mirror: false,
    grid: null, baseGrid: null, palette: [], cellLab: null,
    zoom: 16, tool: 'view', highlight: -1, paintIdx: -1,
  };
  const SAVED_KEYS = ['W', 'H', 'lock', 'board', 'pal', 'maxc', 'sat', 'con', 'bg', 'bgtol', 'clean', 'codes', 'round', 'mirror'];
  try {
    const saved = JSON.parse(localStorage.getItem('pindou-settings') || '{}');
    for (const k of SAVED_KEYS) if (k in saved) S[k] = saved[k];
  } catch (e) { /* storage unavailable */ }
  function persist() {
    try { const o = {}; for (const k of SAVED_KEYS) o[k] = S[k]; localStorage.setItem('pindou-settings', JSON.stringify(o)); } catch (e) { /* ignore */ }
  }

  // ---------- toast ----------
  let toastTimer;
  function toast(msg) {
    const t = $('toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2600);
  }

  // ---------- image loading ----------
  $('drop').addEventListener('click', () => $('file').click());
  $('file').addEventListener('change', (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    const url = URL.createObjectURL(f);
    const img = new Image();
    img.onload = () => { loadImage(img, url); };
    img.onerror = () => toast('这张图片打不开，换一张试试');
    img.src = url;
    e.target.value = '';
  });

  function loadImage(img, url) {
    S.img = img;
    const maxSide = 1200;
    const k = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
    S.srcW = Math.max(1, Math.round(img.naturalWidth * k));
    S.srcH = Math.max(1, Math.round(img.naturalHeight * k));
    const c = document.createElement('canvas');
    c.width = S.srcW; c.height = S.srcH;
    const ctx = c.getContext('2d', { willReadFrequently: true });
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(img, 0, 0, S.srcW, S.srcH);
    S.src = ctx.getImageData(0, 0, S.srcW, S.srcH).data;

    const d = $('drop');
    d.classList.add('has');
    d.innerHTML = '';
    const th = new Image(); th.src = url; d.appendChild(th);
    const info = document.createElement('div');
    info.innerHTML = `<strong>已选择图片</strong><div class="hint">${img.naturalWidth} × ${img.naturalHeight} 像素 · 点击更换</div>`;
    d.appendChild(info);

    if (S.lock) fitInto(Math.max(S.W, S.H), Math.max(S.W, S.H));
    S.zoom = 0; // auto-fit on next render
    process();
  }

  // ---------- size controls ----------
  const ratio = () => (S.img ? S.srcW / S.srcH : 1);
  function fitInto(bw, bh) {
    const r = ratio();
    if (bw / bh > r) { S.H = bh; S.W = Math.max(1, Math.round(bh * r)); }
    else { S.W = bw; S.H = Math.max(1, Math.round(bw / r)); }
    syncInputs();
  }
  function syncInputs() {
    $('w').value = S.W; $('h').value = S.H; $('board').value = S.board;
    $('lock').classList.toggle('on', S.lock);
    $('lock').textContent = S.lock ? '🔗' : '⛓️‍💥';
  }
  function readDim(el) { return clamp(parseInt(el.value, 10) || 1, 1, MAX_DIM); }
  $('w').addEventListener('change', () => {
    S.W = readDim($('w'));
    if (S.lock) S.H = clamp(Math.round(S.W / ratio()), 1, MAX_DIM);
    syncInputs(); S.zoom = 0; schedule();
  });
  $('h').addEventListener('change', () => {
    S.H = readDim($('h'));
    if (S.lock) S.W = clamp(Math.round(S.H * ratio()), 1, MAX_DIM);
    syncInputs(); S.zoom = 0; schedule();
  });
  $('lock').addEventListener('click', () => {
    S.lock = !S.lock;
    if (S.lock) { S.H = clamp(Math.round(S.W / ratio()), 1, MAX_DIM); S.zoom = 0; schedule(); }
    syncInputs(); persist();
  });
  $('presets').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    const bw = +b.dataset.w, bh = +b.dataset.h;
    if (S.lock) fitInto(bw, bh); else { S.W = bw; S.H = bh; syncInputs(); }
    S.board = bw >= 104 ? 52 : bw === 58 ? 29 : bw;
    syncInputs(); S.zoom = 0; schedule();
  });
  $('board').addEventListener('change', () => { S.board = clamp(parseInt($('board').value, 10) || 0, 0, MAX_DIM); syncInputs(); persist(); render(); });

  // ---------- color controls ----------
  function bindRange(id, key, fmt) {
    const el = $(id), out = $(id + 'v');
    el.value = S[key]; out.textContent = fmt(S[key]);
    el.addEventListener('input', () => { S[key] = +el.value; out.textContent = fmt(S[key]); schedule(); });
  }
  bindRange('maxc', 'maxc', (v) => v);
  bindRange('sat', 'sat', (v) => v + '%');
  bindRange('con', 'con', (v) => v + '%');
  bindRange('bgtol', 'bgtol', (v) => v);
  $('pal').value = S.pal;
  $('pal').addEventListener('change', () => { S.pal = $('pal').value; schedule(); });
  $('bg').checked = S.bg;
  $('bg').addEventListener('change', () => { S.bg = $('bg').checked; schedule(); });
  $('clean').checked = S.clean;
  $('clean').addEventListener('change', () => { S.clean = $('clean').checked; schedule(); });
  for (const k of ['codes', 'round', 'mirror']) {
    $(k).checked = S[k];
    $(k).addEventListener('change', () => { S[k] = $(k).checked; persist(); render(); });
  }

  let timer;
  function schedule() { clearTimeout(timer); timer = setTimeout(process, 120); }

  // ---------- core conversion ----------
  function sampleCells() {
    const { W, H, srcW, srcH, src } = S;
    const rgb = new Float32Array(W * H * 3);
    const empty = new Uint8Array(W * H);
    const kc = S.con / 100, ks = S.sat / 100;
    for (let y = 0; y < H; y++) {
      const y0 = Math.floor((y * srcH) / H), y1 = Math.max(y0 + 1, Math.floor(((y + 1) * srcH) / H));
      for (let x = 0; x < W; x++) {
        const x0 = Math.floor((x * srcW) / W), x1 = Math.max(x0 + 1, Math.floor(((x + 1) * srcW) / W));
        let r = 0, g = 0, b = 0, a = 0, n = 0;
        for (let yy = y0; yy < y1; yy++) {
          let p = (yy * srcW + x0) * 4;
          for (let xx = x0; xx < x1; xx++, p += 4) {
            const al = src[p + 3];
            r += src[p] * al; g += src[p + 1] * al; b += src[p + 2] * al; a += al; n++;
          }
        }
        const i = y * W + x;
        if (a / (n * 255) < 0.5) { empty[i] = 1; continue; }
        let c = [r / a, g / a, b / a];
        c = c.map((v) => (v - 128) * kc + 128);
        const gr = luma(c);
        c = c.map((v) => clamp(gr + (v - gr) * ks, 0, 255));
        rgb[i * 3] = c[0]; rgb[i * 3 + 1] = c[1]; rgb[i * 3 + 2] = c[2];
      }
    }
    return { rgb, empty };
  }

  function removeBackground(lab, empty) {
    const { W, H } = S;
    // most common colour along the border is taken as the background
    const buckets = new Map();
    const border = [];
    for (let x = 0; x < W; x++) { border.push(x, (H - 1) * W + x); }
    for (let y = 0; y < H; y++) { border.push(y * W, y * W + W - 1); }
    for (const i of border) {
      if (empty[i]) continue;
      const L = lab[i];
      const key = `${Math.round(L[0] / 6)},${Math.round(L[1] / 6)},${Math.round(L[2] / 6)}`;
      const e = buckets.get(key) || { n: 0, lab: L };
      e.n++; buckets.set(key, e);
    }
    let ref = null, best = 0;
    for (const e of buckets.values()) if (e.n > best) { best = e.n; ref = e.lab; }
    if (!ref) return;
    const tol2 = S.bgtol * S.bgtol;
    const seen = new Uint8Array(W * H);
    const stack = [];
    for (const i of border) if (!seen[i]) { seen[i] = 1; stack.push(i); }
    while (stack.length) {
      const i = stack.pop();
      if (!empty[i] && dist2(lab[i], ref) > tol2) continue;
      empty[i] = 1;
      const x = i % W, y = (i / W) | 0;
      const nb = [];
      if (x > 0) nb.push(i - 1); if (x < W - 1) nb.push(i + 1);
      if (y > 0) nb.push(i - W); if (y < H - 1) nb.push(i + W);
      for (const j of nb) if (!seen[j]) { seen[j] = 1; stack.push(j); }
    }
  }

  function kmeans(lab, rgb, empty, k) {
    // aggregate identical-ish colours to keep it fast
    const agg = new Map();
    for (let i = 0; i < empty.length; i++) {
      if (empty[i]) continue;
      const key = ((rgb[i * 3] >> 2) << 12) | ((rgb[i * 3 + 1] >> 2) << 6) | (rgb[i * 3 + 2] >> 2);
      let e = agg.get(key);
      if (!e) { e = { w: 0, lab: lab[i] }; agg.set(key, e); }
      e.w++;
    }
    const pts = [...agg.values()];
    if (!pts.length) return [];
    k = Math.min(k, pts.length);
    const rnd = mulberry32(12345);
    // k-means++ init
    const cent = [pts.reduce((a, b) => (b.w > a.w ? b : a)).lab.slice()];
    const d = new Float64Array(pts.length).fill(Infinity);
    while (cent.length < k) {
      let sum = 0;
      const c = cent[cent.length - 1];
      for (let i = 0; i < pts.length; i++) { d[i] = Math.min(d[i], dist2(pts[i].lab, c)); sum += d[i] * pts[i].w; }
      if (sum === 0) break;
      let r = rnd() * sum, i = 0;
      for (; i < pts.length - 1; i++) { r -= d[i] * pts[i].w; if (r <= 0) break; }
      cent.push(pts[i].lab.slice());
    }
    const asg = new Int32Array(pts.length);
    for (let it = 0; it < 20; it++) {
      const acc = cent.map(() => [0, 0, 0, 0]);
      let moved = 0;
      for (let i = 0; i < pts.length; i++) {
        let bi = 0, bd = Infinity;
        for (let c = 0; c < cent.length; c++) { const dd = dist2(pts[i].lab, cent[c]); if (dd < bd) { bd = dd; bi = c; } }
        if (asg[i] !== bi) { moved++; asg[i] = bi; }
        const a = acc[bi], w = pts[i].w;
        a[0] += pts[i].lab[0] * w; a[1] += pts[i].lab[1] * w; a[2] += pts[i].lab[2] * w; a[3] += w;
      }
      for (let c = 0; c < cent.length; c++) if (acc[c][3]) cent[c] = [acc[c][0] / acc[c][3], acc[c][1] / acc[c][3], acc[c][2] / acc[c][3]];
      if (!moved && it) break;
    }
    return cent;
  }

  function nearest(L, pal, allowed) {
    let bi = -1, bd = Infinity;
    for (const j of allowed) { const dd = dist2(L, pal[j].lab); if (dd < bd) { bd = dd; bi = j; } }
    return bi;
  }

  // a bead whose colour matches none of its 4 neighbours takes the most common neighbour colour
  function despeckle(grid) {
    const { W, H } = S;
    const src = grid.slice();
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = y * W + x, g = src[i];
        if (g < 0) continue;
        const nb = [];
        if (x > 0) nb.push(src[i - 1]); if (x < W - 1) nb.push(src[i + 1]);
        if (y > 0) nb.push(src[i - W]); if (y < H - 1) nb.push(src[i + W]);
        if (nb.length < 2 || nb.includes(g)) continue;
        const cnt = new Map();
        for (const v of nb) if (v >= 0) cnt.set(v, (cnt.get(v) || 0) + 1);
        let best = -1, bn = 1;
        for (const [v, n] of cnt) if (n > bn) { bn = n; best = v; }
        if (best >= 0) grid[i] = best;
      }
    }
  }

  function process() {
    persist();
    if (!S.src) return;
    const { W, H } = S;
    const { rgb, empty } = sampleCells();
    const lab = new Array(W * H);
    for (let i = 0; i < W * H; i++) if (!empty[i]) lab[i] = rgb2lab(rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2]);
    if (S.bg) removeBackground(lab, empty);

    let pal;
    const grid = new Int16Array(W * H).fill(-1);
    if (S.pal === 'auto') {
      const cent = kmeans(lab, rgb, empty, S.maxc);
      const sums = cent.map(() => [0, 0, 0, 0]);
      const all = cent.map((_, j) => j);
      pal = cent.map((c) => ({ lab: c }));
      for (let i = 0; i < W * H; i++) {
        if (empty[i]) continue;
        const j = nearest(lab[i], pal, all);
        grid[i] = j;
        sums[j][0] += rgb[i * 3]; sums[j][1] += rgb[i * 3 + 1]; sums[j][2] += rgb[i * 3 + 2]; sums[j][3]++;
      }
      pal = pal.map((p, j) => {
        const n = sums[j][3] || 1;
        const c = [sums[j][0] / n, sums[j][1] / n, sums[j][2] / n];
        return { code: '', name: '', hex: rgb2hex(c), rgb: c, lab: p.lab };
      });
    } else {
      pal = window.PALETTES[S.pal].map(([code, name, hex]) => { const c = hex2rgb(hex); return { code, name, hex, rgb: c, lab: rgb2lab(...c) }; });
      const allowed = new Set(pal.map((_, j) => j));
      for (let i = 0; i < W * H; i++) if (!empty[i]) grid[i] = nearest(lab[i], pal, allowed);
      // merge the rarest colours until within the limit
      for (;;) {
        const cnt = new Map();
        for (const g of grid) if (g >= 0) cnt.set(g, (cnt.get(g) || 0) + 1);
        if (cnt.size <= S.maxc) break;
        let minJ = -1, minN = Infinity;
        for (const [j, n] of cnt) if (n < minN) { minN = n; minJ = j; }
        allowed.clear();
        for (const j of cnt.keys()) if (j !== minJ) allowed.add(j);
        for (let i = 0; i < grid.length; i++) if (grid[i] === minJ) grid[i] = nearest(lab[i], pal, allowed);
      }
    }

    if (S.clean) despeckle(grid);

    // compact palette to used colours, most used first
    const cnt = new Map();
    for (const g of grid) if (g >= 0) cnt.set(g, (cnt.get(g) || 0) + 1);
    const order = [...cnt.keys()].sort((a, b) => cnt.get(b) - cnt.get(a));
    const remap = new Map(order.map((j, n) => [j, n]));
    S.palette = order.map((j, n) => {
      const p = pal[j];
      return { ...p, code: p.code || String(n + 1), text: luma(p.rgb) > 150 ? '#222' : '#fff' };
    });
    for (let i = 0; i < grid.length; i++) if (grid[i] >= 0) grid[i] = remap.get(grid[i]);
    S.grid = grid; S.baseGrid = grid.slice(); S.cellLab = lab;
    S.highlight = -1; S.paintIdx = -1;
    $('result').hidden = false; $('empty').hidden = true;
    render(); renderLegend();
  }

  // ---------- drawing ----------
  function margin(C) { return Math.max(18, Math.round(C * 1.1)); }

  function drawPattern(ctx, C, opts) {
    const { W, H, grid, palette, board } = S;
    const M = margin(C);
    const hl = opts.highlight;
    ctx.save();
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, M + W * C + 2, M + H * C + 2);
    ctx.translate(M, M);
    // cells
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    const showCodes = opts.codes && C >= 14;
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const sx = opts.mirror ? W - 1 - x : x;
        const g = grid[y * W + sx];
        const px = x * C, py = y * C;
        if (g < 0) {
          ctx.fillStyle = '#f2f2f2'; ctx.fillRect(px, py, C, C);
          continue;
        }
        const p = palette[g];
        const dim = hl >= 0 && g !== hl;
        ctx.globalAlpha = dim ? 0.12 : 1;
        if (opts.round) {
          ctx.fillStyle = '#f7f7f7'; ctx.fillRect(px, py, C, C);
          ctx.fillStyle = p.hex;
          ctx.beginPath(); ctx.arc(px + C / 2, py + C / 2, C * 0.46, 0, Math.PI * 2); ctx.fill();
          if (!showCodes && C >= 8) {
            ctx.fillStyle = '#ffffff99';
            ctx.beginPath(); ctx.arc(px + C / 2, py + C / 2, C * 0.15, 0, Math.PI * 2); ctx.fill();
          }
        } else {
          ctx.fillStyle = p.hex; ctx.fillRect(px, py, C, C);
        }
        if (showCodes) {
          ctx.fillStyle = p.text;
          const len = p.code.length;
          ctx.font = `600 ${Math.floor(C * (len >= 3 ? 0.34 : 0.44))}px sans-serif`;
          ctx.fillText(p.code, px + C / 2, py + C / 2 + 0.5);
        }
        ctx.globalAlpha = 1;
      }
    }
    // grid lines
    const line = (x0, y0, x1, y1) => { ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke(); };
    const pass = (filter, style, width) => {
      ctx.strokeStyle = style; ctx.lineWidth = width;
      for (let x = 0; x <= W; x++) if (filter(x, W)) line(x * C, 0, x * C, H * C);
      for (let y = 0; y <= H; y++) if (filter(y, H)) line(0, y * C, W * C, y * C);
    };
    if (C >= 5) pass(() => true, 'rgba(0,0,0,0.13)', 1);
    pass((i, n) => i % 5 === 0 || i === n, 'rgba(0,0,0,0.38)', 1);
    if (board > 0) pass((i, n) => i % board === 0 || i === n, '#e2463b', 2);
    // coordinates
    ctx.fillStyle = '#777';
    ctx.font = `${Math.max(9, Math.min(13, Math.floor(C * 0.5)))}px sans-serif`;
    const every = C >= 18 ? 1 : C >= 9 ? 5 : 10;
    for (let x = 0; x < W; x++) if ((x + 1) % every === 0 || x === 0) ctx.fillText(String(x + 1), x * C + C / 2, -M / 2);
    for (let y = 0; y < H; y++) if ((y + 1) % every === 0 || y === 0) ctx.fillText(String(y + 1), -M / 2, y * C + C / 2);
    ctx.restore();
    return { M };
  }

  let rafPending = false;
  function render() {
    if (!S.grid || rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => { rafPending = false; renderNow(); });
  }
  function renderNow() {
    const { W, H } = S;
    if (!S.zoom) {
      const avail = $('wrap').clientWidth - 4;
      let best = ZOOMS[0];
      for (const z of ZOOMS) if (margin(z) + W * z <= avail) best = z;
      S.zoom = best;
    }
    const C = S.zoom, M = margin(C);
    const cssW = M + W * C + 2, cssH = M + H * C + 2;
    let dpr = Math.min(2, window.devicePixelRatio || 1);
    dpr = Math.min(dpr, 8000 / Math.max(cssW, cssH));
    const cv = $('stage');
    cv.width = Math.round(cssW * dpr); cv.height = Math.round(cssH * dpr);
    cv.style.width = cssW + 'px'; cv.style.height = cssH + 'px';
    const ctx = cv.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawPattern(ctx, C, { codes: S.codes, round: S.round, mirror: S.mirror, highlight: S.highlight });
    updateStats();
  }

  function counts() {
    const c = new Array(S.palette.length).fill(0);
    for (const g of S.grid) if (g >= 0) c[g]++;
    return c;
  }
  function updateStats() {
    const c = counts();
    const total = c.reduce((a, b) => a + b, 0);
    const used = c.filter((n) => n > 0).length;
    let s = `尺寸 <strong>${S.W} × ${S.H}</strong> 颗 · 共 <strong>${total}</strong> 颗豆 · <strong>${used}</strong> 种颜色`;
    if (S.board > 0) s += ` · 需要 <strong>${Math.ceil(S.W / S.board) * Math.ceil(S.H / S.board)}</strong> 块 ${S.board}×${S.board} 板`;
    $('stats').innerHTML = s;
  }

  function renderLegend() {
    const c = counts();
    const el = $('legend');
    el.innerHTML = '';
    S.palette.forEach((p, j) => {
      if (!c[j] && j !== S.paintIdx) return;
      const d = document.createElement('div');
      d.className = 'sw' + (j === S.highlight || j === S.paintIdx ? ' sel' : '');
      d.innerHTML = `<i style="background:${p.hex};color:${p.text}">${p.code.length <= 3 ? p.code : ''}</i>
        <div><b>${p.code}${p.name ? ' ' + p.name : ''}</b><small>${c[j]} 颗 · ${p.hex}</small></div>`;
      d.addEventListener('click', () => {
        if (S.tool === 'paint') { S.paintIdx = j; S.highlight = -1; }
        else S.highlight = S.highlight === j ? -1 : j;
        renderLegend(); render();
      });
      el.appendChild(d);
    });
  }

  // ---------- tools ----------
  const TOOL_HINTS = {
    view: '点格子：高亮同色豆子，方便一种颜色一种颜色地摆。再点一次取消。',
    paint: '先在下方清单里选一个颜色，再在图纸上点或拖动来改色。',
    erase: '点或拖动，把格子变成空（不放豆子）。',
  };
  function setTool(t) {
    S.tool = t;
    $('tView').classList.toggle('on', t === 'view');
    $('tPaint').classList.toggle('on', t === 'paint');
    $('tErase').classList.toggle('on', t === 'erase');
    $('wrap').style.touchAction = t === 'view' ? 'pan-x pan-y' : 'none';
    $('toolHint').textContent = TOOL_HINTS[t];
    if (t === 'paint' && S.paintIdx < 0) S.paintIdx = S.highlight >= 0 ? S.highlight : 0;
    if (t !== 'paint') S.paintIdx = -1;
    S.highlight = -1;
    renderLegend(); render();
  }
  $('tView').addEventListener('click', () => setTool('view'));
  $('tPaint').addEventListener('click', () => setTool('paint'));
  $('tErase').addEventListener('click', () => setTool('erase'));
  $('zIn').addEventListener('click', () => { const i = ZOOMS.indexOf(S.zoom); S.zoom = ZOOMS[Math.min(ZOOMS.length - 1, i + 1)]; render(); });
  $('zOut').addEventListener('click', () => { const i = ZOOMS.indexOf(S.zoom); S.zoom = ZOOMS[Math.max(0, i - 1)]; render(); });
  $('reset').addEventListener('click', () => {
    if (!S.baseGrid) return;
    S.grid = S.baseGrid.slice(); renderLegend(); render(); toast('已恢复到自动生成的图纸');
  });

  function cellAt(ev) {
    const cv = $('stage');
    const r = cv.getBoundingClientRect();
    const C = S.zoom, M = margin(C);
    const cx = Math.floor((ev.clientX - r.left - M) / C), cy = Math.floor((ev.clientY - r.top - M) / C);
    if (cx < 0 || cy < 0 || cx >= S.W || cy >= S.H) return -1;
    return cy * S.W + (S.mirror ? S.W - 1 - cx : cx);
  }
  let drawing = false, lastCell = -1;
  function applyTool(i) {
    if (i < 0 || i === lastCell) return;
    lastCell = i;
    const v = S.tool === 'erase' ? -1 : S.paintIdx;
    if (S.grid[i] !== v) { S.grid[i] = v; render(); }
  }
  $('stage').addEventListener('pointerdown', (ev) => {
    const i = cellAt(ev);
    if (S.tool === 'view') {
      if (i < 0) return;
      const g = S.grid[i];
      S.highlight = g < 0 || g === S.highlight ? -1 : g;
      renderLegend(); render();
      if (S.highlight >= 0) {
        const p = S.palette[g];
        toast(`${p.code}${p.name ? ' ' + p.name : ''}：${counts()[g]} 颗`);
      }
      return;
    }
    drawing = true; lastCell = -1;
    $('stage').setPointerCapture(ev.pointerId);
    applyTool(i);
  });
  $('stage').addEventListener('pointermove', (ev) => { if (drawing) applyTool(cellAt(ev)); });
  const stop = () => { if (drawing) { drawing = false; renderLegend(); } };
  $('stage').addEventListener('pointerup', stop);
  $('stage').addEventListener('pointercancel', stop);

  // ---------- export ----------
  function buildExport() {
    const { W, H } = S;
    const C = clamp(Math.floor(6000 / Math.max(W, H)), 8, 32);
    const M = margin(C);
    const gridW = M + W * C + 2, gridH = M + H * C + 2;
    const c = counts();
    const items = S.palette.map((p, j) => ({ p, n: c[j] })).filter((e) => e.n > 0);
    const pad = 24, width = Math.max(gridW + pad * 2, 520);
    const colW = 170, cols = Math.max(1, Math.floor((width - pad * 2) / colW)), rowH = 34;
    const legendH = Math.ceil(items.length / cols) * rowH;
    const titleH = 64;
    const height = titleH + gridH + 24 + legendH + pad * 2;
    const cv = document.createElement('canvas');
    cv.width = width; cv.height = height;
    const ctx = cv.getContext('2d');
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, width, height);
    ctx.fillStyle = '#222'; ctx.font = 'bold 22px sans-serif'; ctx.textBaseline = 'alphabetic';
    ctx.fillText('拼豆图纸' + (S.mirror ? '（镜像）' : ''), pad, pad + 20);
    const total = c.reduce((a, b) => a + b, 0);
    ctx.font = '15px sans-serif'; ctx.fillStyle = '#666';
    ctx.fillText(`${W} × ${H} 颗 · 共 ${total} 颗 · ${items.length} 种颜色`, pad, pad + 46);
    ctx.save();
    ctx.translate(Math.round((width - gridW) / 2), pad + titleH);
    drawPattern(ctx, C, { codes: true, round: S.round, mirror: S.mirror, highlight: -1 });
    ctx.restore();
    const ly = pad + titleH + gridH + 24;
    items.forEach((e, k) => {
      const x = pad + (k % cols) * colW, y = ly + Math.floor(k / cols) * rowH;
      ctx.fillStyle = e.p.hex; ctx.strokeStyle = '#0003';
      ctx.beginPath(); ctx.arc(x + 13, y + 13, 12, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#222'; ctx.font = 'bold 14px sans-serif'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
      ctx.fillText(`${e.p.code}${e.p.name ? ' ' + e.p.name : ''}`, x + 32, y + 7);
      ctx.fillStyle = '#777'; ctx.font = '12px sans-serif';
      ctx.fillText(`${e.n} 颗  ${e.p.hex}`, x + 32, y + 22);
    });
    return cv;
  }

  const cap = window.Capacitor;
  const isNative = !!(cap && cap.isNativePlatform && cap.isNativePlatform());
  const plugin = (name) => (cap && cap.Plugins && cap.Plugins[name]) || (cap && cap.registerPlugin ? cap.registerPlugin(name) : null);

  $('save').addEventListener('click', async () => {
    if (!S.grid) return;
    const btn = $('save'); btn.disabled = true;
    try {
      const cv = buildExport();
      const dataUrl = cv.toDataURL('image/png');
      const name = `pindou-${S.W}x${S.H}-${Date.now()}.png`;
      if (!isNative) {
        const a = document.createElement('a'); a.href = dataUrl; a.download = name; a.click();
        toast('图纸已下载');
        return;
      }
      const FS = plugin('Filesystem'), Share = plugin('Share');
      const data = dataUrl.split(',')[1];
      let saved = false;
      try {
        await FS.writeFile({ path: `Pictures/拼豆图纸/${name}`, data, directory: 'EXTERNAL_STORAGE', recursive: true });
        saved = true;
      } catch (e) {
        try { await FS.writeFile({ path: `拼豆图纸/${name}`, data, directory: 'DOCUMENTS', recursive: true }); saved = 'docs'; } catch (e2) { /* fall through to share */ }
      }
      if (saved) toast(saved === 'docs' ? '已保存到 文档/拼豆图纸 文件夹' : '已保存到 相册 Pictures/拼豆图纸');
      const tmp = await FS.writeFile({ path: name, data, directory: 'CACHE' });
      await Share.share({ title: '拼豆图纸', files: [tmp.uri], dialogTitle: saved ? '也可以分享给…' : '保存或分享图纸' });
    } catch (e) {
      if (!/cancel/i.test(String(e && e.message))) toast('保存失败：' + (e && e.message ? e.message : e));
    } finally { btn.disabled = false; }
  });

  window.addEventListener('resize', () => { if (S.grid) render(); });
  syncInputs();
})();
