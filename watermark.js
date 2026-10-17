// 玻璃水印渲染：在照片底部画 iOS 26 液态玻璃风格的「 型号 | 日期」水印
(() => {
  const APPLE = new Path2D("M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701");
  // 苹果标志路径在 24×24 视框里的大致范围
  const APPLE_BOX = { x: 2.3, y: 0, w: 19.4, h: 24 };
  const FONT = '-apple-system, BlinkMacSystemFont, "SF Pro Display", "PingFang SC", "Helvetica Neue", "Noto Sans SC", system-ui, sans-serif';
  const MAX_AREA = 14e6; // iOS Safari 单个 canvas 上限约 1670 万像素

  const pad = (n) => String(n).padStart(2, "0");
  function dateText(d, style) {
    if (style === "dotted") return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
    if (style === "dashed") return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
  }
  const timeText = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  function readExif(buffer) {
    try {
      const v = new DataView(buffer);
      if (v.getUint16(0) !== 0xffd8) return {};
      let off = 2;
      while (off + 4 < v.byteLength) {
        const marker = v.getUint16(off), len = v.getUint16(off + 2);
        if (marker === 0xffe1 && v.getUint32(off + 4) === 0x45786966) return parseTiff(v, off + 10);
        if ((marker & 0xff00) !== 0xff00 || marker === 0xffda) break;
        off += 2 + len;
      }
    } catch (e) {}
    return {};
  }
  function parseTiff(v, t) {
    const le = v.getUint16(t) === 0x4949;
    const u16 = (o) => v.getUint16(t + o, le), u32 = (o) => v.getUint32(t + o, le);
    const SIZE = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 7: 1, 9: 4, 10: 8 };
    function value(type, count, at) {
      if (type === 2) { let s = ""; for (let i = 0; i < count; i++) { const c = v.getUint8(t + at + i); if (!c) break; s += String.fromCharCode(c); } return s.trim(); }
      if (type === 3) return u16(at);
      if (type === 4) return u32(at);
      if (type === 5 || type === 10) { const d = u32(at + 4); return d ? u32(at) / d : 0; }
      return null;
    }
    function ifd(o) {
      const out = {}, n = u16(o);
      for (let i = 0; i < n; i++) {
        const e = o + 2 + i * 12, tag = u16(e), type = u16(e + 2), count = u32(e + 4);
        const size = (SIZE[type] || 1) * count;
        out[tag] = value(type, count, size > 4 ? u32(e + 8) : e + 8);
      }
      return out;
    }
    const ifd0 = ifd(u32(4));
    const exif = ifd0[0x8769] ? ifd(ifd0[0x8769]) : {};
    let date = null;
    const dt = exif[0x9003] || ifd0[0x0132];
    if (typeof dt === "string") {
      const m = dt.match(/(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
      if (m) date = new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5], +m[6]);
    }
    return {
      make: ifd0[0x010f] || null,
      model: ifd0[0x0110] || null,
      exposure: exif[0x829a] || null,
      fNumber: exif[0x829d] || null,
      iso: exif[0x8827] || null,
      focal35: exif[0xa405] || null,
      date,
    };
  }
  function paramText(x) {
    const parts = [];
    const trim = (n) => n.toFixed(2).replace(/\.?0+$/, "");
    if (x.focal35) parts.push(`${Math.round(x.focal35)}mm`);
    if (x.fNumber) parts.push(`ƒ/${trim(x.fNumber)}`);
    if (x.exposure) parts.push(x.exposure < 1 ? `1/${Math.round(1 / x.exposure)}s` : `${trim(x.exposure)}s`);
    if (x.iso) parts.push(`ISO ${x.iso}`);
    return parts.length ? parts.join("  ") : null;
  }

  // ---------- 画布工具 ----------
  function canvas(w, h) { const c = document.createElement("canvas"); c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(h)); return c; }

  function roundRect(ctx, x, y, w, h, r) {
    r = Math.min(r, h / 2, w / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /** 把 src 的 rect 区域缩小后做三次盒式模糊（近似高斯）并调饱和度，返回小画布和平均亮度。 */
  function frosted(src, rect, sigma, sat, bright) {
    const k = Math.max(1, sigma / 4);
    const sw = Math.max(4, Math.round(rect.w / k)), sh = Math.max(4, Math.round(rect.h / k));
    const small = canvas(sw, sh);
    const sctx = small.getContext("2d", { willReadFrequently: true });
    sctx.imageSmoothingEnabled = true;
    sctx.imageSmoothingQuality = "high";
    sctx.drawImage(src, rect.x, rect.y, rect.w, rect.h, 0, 0, sw, sh);
    const img = sctx.getImageData(0, 0, sw, sh);
    const d = img.data;
    const r = Math.max(1, Math.round((sigma / k) * 0.9));
    for (let pass = 0; pass < 3; pass++) { boxBlur(d, sw, sh, r, true); boxBlur(d, sw, sh, r, false); }
    let lumSum = 0;
    for (let i = 0; i < d.length; i += 4) {
      const R = d[i], G = d[i + 1], B = d[i + 2];
      const l = 0.2126 * R + 0.7152 * G + 0.0722 * B;
      d[i] = clamp(l + (R - l) * sat + bright * 255);
      d[i + 1] = clamp(l + (G - l) * sat + bright * 255);
      d[i + 2] = clamp(l + (B - l) * sat + bright * 255);
      lumSum += (0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]) / 255;
    }
    sctx.putImageData(img, 0, 0);
    return { canvas: small, luminance: lumSum / (sw * sh) };
  }
  const clamp = (x) => (x < 0 ? 0 : x > 255 ? 255 : x);
  function boxBlur(d, w, h, r, horizontal) {
    const len = horizontal ? w : h, lines = horizontal ? h : w;
    const buf = new Float32Array(len * 3);
    const win = 2 * r + 1;
    for (let line = 0; line < lines; line++) {
      const idx = (i) => 4 * (horizontal ? line * w + i : i * w + line);
      for (let i = 0; i < len; i++) { const p = idx(i); buf[i * 3] = d[p]; buf[i * 3 + 1] = d[p + 1]; buf[i * 3 + 2] = d[p + 2]; }
      let sr = 0, sg = 0, sb = 0;
      for (let j = -r; j <= r; j++) { const q = Math.min(len - 1, Math.max(0, j)) * 3; sr += buf[q]; sg += buf[q + 1]; sb += buf[q + 2]; }
      for (let i = 0; i < len; i++) {
        const p = idx(i);
        d[p] = sr / win; d[p + 1] = sg / win; d[p + 2] = sb / win;
        const add = Math.min(len - 1, i + r + 1) * 3, rem = Math.max(0, i - r) * 3;
        sr += buf[add] - buf[rem]; sg += buf[add + 1] - buf[rem + 1]; sb += buf[add + 2] - buf[rem + 2];
      }
    }
  }

  // ---------- 水印渲染 ----------
  function render(src, opts) {
    const W0 = src.width, H0 = src.height;
    const unit0 = Math.min(W0, H0);
    const extra = opts.style === "bar" ? unit0 * 0.22 : 0;
    const scale = Math.min(1, Math.sqrt(MAX_AREA / (W0 * (H0 + extra))));
    const W = Math.round(W0 * scale), H = Math.round(H0 * scale);
    const unit = Math.min(W, H), margin = unit * 0.04;

    let canvasH = H, panel;
    if (opts.style === "bar") {
      const barH = Math.round(unit * 0.22), ph = barH * 0.68;
      canvasH = H + barH;
      panel = { x: margin, y: H + (barH - ph) / 2, w: W - margin * 2, h: ph };
    } else {
      const ph = unit * 0.15;
      panel = { x: margin, y: H - margin - ph, w: W - margin * 2, h: ph };
    }

    const out = canvas(W, canvasH);
    const ctx = out.getContext("2d");
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(src, 0, 0, W, H);

    if (opts.style === "bar") {
      // 底栏：照片最下面一条垂直翻转成倒影，再重度模糊，像磨砂玻璃延伸出去
      const barH = canvasH - H;
      ctx.save();
      ctx.translate(0, H + barH);
      ctx.scale(1, -1);
      ctx.drawImage(src, 0, H0 - barH / scale, W0, barH / scale, 0, 0, W, barH);
      ctx.restore();
      const band = { x: 0, y: H, w: W, h: barH };
      const f = frosted(out, band, unit * 0.035, 1.3, -0.04);
      ctx.drawImage(f.canvas, band.x, band.y, band.w, band.h);
    }

    // 玻璃背后的模糊画面
    const sigma = unit * 0.022;
    const padPx = sigma * 2.5;
    const sx = Math.max(0, panel.x - padPx), sy = Math.max(0, panel.y - padPx);
    const sample = { x: sx, y: sy, w: Math.min(W, panel.x + panel.w + padPx) - sx, h: Math.min(canvasH, panel.y + panel.h + padPx) - sy };
    const glass = frosted(out, sample, sigma, 1.6, 0);
    const light = glass.luminance > 0.62;

    drawGlass(ctx, panel, glass.canvas, sample, light);
    drawText(ctx, panel, opts, light);
    return out;
  }

  function drawGlass(ctx, p, backdrop, sample, light) {
    const radius = p.h * 0.46;
    // 投影
    ctx.save();
    ctx.shadowColor = `rgba(0,0,0,${light ? 0.18 : 0.3})`;
    ctx.shadowBlur = p.h * 0.4;
    ctx.shadowOffsetY = p.h * 0.08;
    roundRect(ctx, p.x, p.y, p.w, p.h, radius);
    ctx.fillStyle = "#000";
    ctx.fill();
    ctx.restore();

    // 玻璃本体
    ctx.save();
    roundRect(ctx, p.x, p.y, p.w, p.h, radius);
    ctx.clip();
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(backdrop, sample.x, sample.y, sample.w, sample.h);
    ctx.fillStyle = `rgba(255,255,255,${light ? 0.24 : 0.12})`;
    ctx.fillRect(p.x, p.y, p.w, p.h);
    let g = ctx.createLinearGradient(0, p.y, 0, p.y + p.h * 0.55);
    g.addColorStop(0, "rgba(255,255,255,0.32)");
    g.addColorStop(1, "rgba(255,255,255,0)");
    ctx.fillStyle = g;
    ctx.fillRect(p.x, p.y, p.w, p.h);
    g = ctx.createLinearGradient(0, p.y + p.h * 0.6, 0, p.y + p.h);
    g.addColorStop(0, "rgba(255,255,255,0)");
    g.addColorStop(1, "rgba(255,255,255,0.14)");
    ctx.fillStyle = g;
    ctx.fillRect(p.x, p.y, p.w, p.h);
    ctx.restore();

    // 边缘高光：上亮、中淡、下再亮一点
    const lw = Math.max(2, p.h * 0.014);
    ctx.save();
    roundRect(ctx, p.x + lw / 2, p.y + lw / 2, p.w - lw, p.h - lw, radius - lw / 2);
    g = ctx.createLinearGradient(0, p.y, 0, p.y + p.h);
    g.addColorStop(0, "rgba(255,255,255,0.9)");
    g.addColorStop(0.5, "rgba(255,255,255,0.18)");
    g.addColorStop(1, "rgba(255,255,255,0.5)");
    ctx.strokeStyle = g;
    ctx.lineWidth = lw;
    ctx.stroke();
    ctx.restore();
  }

  function drawText(ctx, p, opts, light) {
    const h = p.h;
    const primary = light ? "rgba(20,20,22,0.9)" : "#ffffff";
    const secondary = light ? "rgba(20,20,22,0.6)" : "rgba(255,255,255,0.75)";
    const padX = h * 0.42;
    const available = p.w - padX * 2;
    const model = opts.model, date = opts.date, time = opts.time, params = opts.params;

    const measure = (text, font) => { if (!text) return 0; ctx.font = font; return ctx.measureText(text).width; };
    function layout(s) {
      const L = {
        s,
        modelFont: `600 ${h * 0.25 * s}px ${FONT}`,
        paramFont: `500 ${h * 0.15 * s}px ${FONT}`,
        dateFont: `600 ${h * 0.21 * s}px ${FONT}`,
        timeFont: `500 ${h * 0.15 * s}px ${FONT}`,
        logoH: h * 0.235 * s,
        logoGap: h * 0.09 * s,
        dividerGap: h * 0.3 * s,
      };
      L.logoW = (L.logoH * APPLE_BOX.w) / APPLE_BOX.h;
      L.left = Math.max(L.logoW + L.logoGap + measure(model, L.modelFont), measure(params, L.paramFont));
      L.right = Math.max(measure(date, L.dateFont), measure(time, L.timeFont));
      L.need = L.left + L.right + L.dividerGap * 2;
      return L;
    }
    let L = layout(1);
    for (let i = 0; i < 3 && L.need > available; i++) L = layout(L.s * (available / L.need));

    const k = Math.max(L.s, 0.8);
    const upper = p.y + h / 2 - h * 0.13 * k, lower = p.y + h / 2 + h * 0.17 * k, mid = p.y + h / 2;
    const line = (text, font, color, x, cy, align) => {
      ctx.font = font;
      ctx.fillStyle = color;
      ctx.textAlign = align;
      ctx.textBaseline = "alphabetic";
      const size = parseFloat(font.split(" ")[1]);
      ctx.fillText(text, x, cy + size * 0.36); // 让大写字母高度居中
    };

    // 左： + 型号，下方参数
    const x0 = p.x + padX;
    const firstY = params ? upper : mid;
    ctx.save();
    const sc = L.logoH / APPLE_BOX.h;
    ctx.translate(x0 - APPLE_BOX.x * sc, firstY - L.logoH / 2 - L.logoH * 0.06);
    ctx.scale(sc, sc);
    ctx.fillStyle = primary;
    ctx.fill(APPLE);
    ctx.restore();
    line(model, L.modelFont, primary, x0 + L.logoW + L.logoGap, firstY, "left");
    if (params) line(params, L.paramFont, secondary, x0, lower, "left");

    // 右：日期，下方时间
    const rx = p.x + p.w - padX;
    line(date, L.dateFont, primary, rx, time ? upper : mid, "right");
    if (time) line(time, L.timeFont, secondary, rx, lower, "right");

    // 分隔线
    const dx = rx - L.right - L.dividerGap, dh = h * 0.42 * L.s, dw = Math.max(1, h * 0.006);
    ctx.fillStyle = light ? "rgba(20,20,22,0.28)" : "rgba(255,255,255,0.3)";
    ctx.fillRect(dx - dw / 2, mid - dh / 2, dw, dh);
  }


  window.Watermark = { render, readExif, paramText, dateText, timeText, APPLE };
})();
