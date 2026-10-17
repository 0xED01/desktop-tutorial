(() => {
  const W = window.Watermark;
  const $ = (id) => document.getElementById(id);
  const pad = (n) => String(n).padStart(2, "0");

  const MODELS = ["iPhone 17 Pro Max", "iPhone 17 Pro", "iPhone Air", "iPhone 17", "iPhone 16 Pro Max", "iPhone 16 Pro", "iPhone 16 Plus", "iPhone 16", "iPhone 16e", "iPhone 15 Pro Max", "iPhone 15 Pro", "iPhone 15 Plus", "iPhone 15", "iPhone 14 Pro Max", "iPhone 14 Pro", "iPhone 14 Plus", "iPhone 14", "iPhone 13 Pro Max", "iPhone 13 Pro", "iPhone 13", "iPhone 13 mini", "iPhone 12 Pro Max", "iPhone 12 Pro", "iPhone 12", "iPhone 12 mini", "iPhone 11 Pro Max", "iPhone 11 Pro", "iPhone 11", "iPhone SE"];
  const STYLE_TITLE = { floating: "悬浮玻璃", bar: "底部玻璃栏" };
  const APPLE_SVG = `<svg viewBox="2.3 0 19.4 24" aria-hidden="true"><path d="${"M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"}"/></svg>`;

  // ---------- 设置（保存在本机） ----------
  const defaults = { style: "floating", dateStyle: "chinese", showTime: true, showParams: true, model: "iPhone 17 Pro", custom: "" };
  let firstRun = true;
  let settings = { ...defaults };
  try {
    const saved = localStorage.getItem("glass-wm");
    if (saved) { Object.assign(settings, JSON.parse(saved)); firstRun = false; }
  } catch (e) {}
  const persist = () => { try { localStorage.setItem("glass-wm", JSON.stringify(settings)); } catch (e) {} };

  function chosenModel() {
    return settings.model === "custom" ? (settings.custom.trim() || "iPhone") : settings.model;
  }
  function modelFor(photo) {
    const e = photo.exif || {};
    return e.make === "Apple" && e.model ? e.model : chosenModel();
  }

  // ---------- 取景器上的实时水印 ----------
  function paintBadge() {
    const now = new Date();
    $("liveBadge").innerHTML = `
      <span class="model">${APPLE_SVG}<span></span></span>
      <span class="spacer"></span>
      <span class="divider"></span>
      <span class="when"><div class="date"></div>${settings.showTime ? '<div class="time"></div>' : ""}</span>`;
    $("liveBadge").querySelector(".model span").textContent = chosenModel();
    $("liveBadge").querySelector(".date").textContent = W.dateText(now, settings.dateStyle);
    const t = $("liveBadge").querySelector(".time");
    if (t) t.textContent = W.timeText(now);
    // 底栏样式时把预览条挪到取景框下方
    (settings.style === "bar" ? $("barArea") : $("viewfinder")).insertBefore($("liveBadge"), settings.style === "bar" ? null : $("blink"));
    $("styleToggle").textContent = STYLE_TITLE[settings.style];
  }
  setInterval(paintBadge, 30000);

  // ---------- 相机 ----------
  const video = $("video");
  let stream = null, facing = "environment";

  async function startCamera() {
    stopCamera();
    $("shutter").disabled = true;
    $("vfMsg").hidden = false;
    $("vfMsg").textContent = "正在打开相机…";
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      $("vfMsg").textContent = "这个浏览器不支持网页取景。可以点右上角用系统相机拍，或从相册选照片。";
      return;
    }
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { ideal: facing }, width: { ideal: 4032 }, height: { ideal: 3024 } },
      });
      video.srcObject = stream;
      video.classList.toggle("mirror", facing === "user");
      await video.play().catch(() => {});
      $("vfMsg").hidden = true;
      $("shutter").disabled = false;
    } catch (e) {
      const denied = e && (e.name === "NotAllowedError" || e.name === "SecurityError");
      $("vfMsg").textContent = denied
        ? "没有相机权限。请在 iPhone「设置 > Safari 浏览器 > 相机」里允许，然后重新打开。也可以点右上角用系统相机拍。"
        : "相机打不开（" + (e && e.name || "未知错误") + "）。可以点右上角用系统相机拍，或从相册选照片。";
    }
  }
  function stopCamera() {
    if (stream) stream.getTracks().forEach((t) => t.stop());
    stream = null;
  }

  function captureFrame() {
    const vw = video.videoWidth, vh = video.videoHeight;
    if (!vw || !vh) return null;
    // 按取景框的比例居中裁切，拍到的就是看到的
    const box = $("viewfinder");
    const target = box.clientWidth / box.clientHeight;
    let sw = vw, sh = vh;
    if (vw / vh > target) sw = Math.round(vh * target); else sh = Math.round(vw / target);
    const c = document.createElement("canvas");
    c.width = sw; c.height = sh;
    const ctx = c.getContext("2d");
    if (facing === "user") { ctx.translate(sw, 0); ctx.scale(-1, 1); }
    ctx.drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, 0, 0, sw, sh);
    return c;
  }

  $("shutter").addEventListener("click", () => {
    const frame = captureFrame();
    if (!frame) return;
    $("blink").classList.add("on");
    setTimeout(() => $("blink").classList.remove("on"), 90);
    if (navigator.vibrate) navigator.vibrate(10);
    openReview({ source: frame, exif: {}, date: new Date() });
  });

  $("flipBtn").addEventListener("click", () => {
    facing = facing === "environment" ? "user" : "environment";
    startCamera();
  });

  async function loadFile(file) {
    if (!file) return;
    try {
      const exif = W.readExif(await file.arrayBuffer());
      let source;
      try { source = await createImageBitmap(file, { imageOrientation: "from-image" }); }
      catch (e) {
        source = await new Promise((res, rej) => {
          const img = new Image();
          img.onload = () => res(img);
          img.onerror = () => rej(new Error("这张照片的格式打不开，请换一张"));
          img.src = URL.createObjectURL(file);
        });
      }
      openReview({ source, exif, date: exif.date || new Date(file.lastModified || Date.now()) });
    } catch (e) {
      alert(e.message || "读取照片失败");
    }
  }
  for (const id of ["libraryInput", "hqInput"]) {
    $(id).addEventListener("change", (e) => { loadFile(e.target.files[0]); e.target.value = ""; });
  }

  // ---------- 成片 ----------
  let current = null, blob = null, url = null, ticket = 0;

  function openReview(photo) {
    current = photo;
    $("reviewScreen").hidden = false;
    stopCamera();
    renderCurrent();
  }
  function closeReview() {
    $("reviewScreen").hidden = true;
    if (current && current.source && current.source.close) current.source.close();
    current = null;
    startCamera();
  }
  $("closeReview").addEventListener("click", closeReview);

  function renderCurrent() {
    if (!current) return;
    const my = ++ticket;
    $("spinner").hidden = false;
    $("saveBtn").disabled = true;
    setNote("");
    paintSegments();
    setTimeout(() => {
      if (my !== ticket || !current) return;
      const d = current.date;
      let out;
      try {
        out = W.render(current.source, {
          style: settings.style,
          model: modelFor(current),
          date: W.dateText(d, settings.dateStyle),
          time: settings.showTime ? W.timeText(d) : null,
          params: settings.showParams ? W.paramText(current.exif || {}) : null,
        });
      } catch (e) { $("spinner").hidden = true; setNote("生成失败：" + e.message, "err"); return; }
      out.toBlob((b) => {
        if (my !== ticket || !b) return;
        if (url) URL.revokeObjectURL(url);
        blob = b;
        url = URL.createObjectURL(b);
        $("resultImg").src = url;
        $("spinner").hidden = true;
        $("saveBtn").disabled = false;
        out.width = out.height = 0;
      }, "image/jpeg", 0.93);
    }, 30);
  }

  function setNote(text, kind) {
    $("saveNote").textContent = text || "也可以长按照片，选“存储到照片”";
    $("saveNote").className = "note" + (kind ? " " + kind : "");
  }

  $("saveBtn").addEventListener("click", async () => {
    if (!blob || !current) return;
    const d = current.date;
    const name = `IMG_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.jpg`;
    const file = new File([blob], name, { type: "image/jpeg" });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      try {
        await navigator.share({ files: [file] });
        setNote("在分享菜单里选“存储图像”就会存进相册", "ok");
      } catch (e) {
        if (e && e.name !== "AbortError") setNote("分享失败，请长按照片保存", "err");
      }
    } else {
      const a = document.createElement("a");
      a.href = url; a.download = name;
      document.body.appendChild(a); a.click(); a.remove();
      setNote("已下载", "ok");
    }
  });

  // ---------- 设置面板 ----------
  function openSheet() { $("sheet").hidden = false; $("sheetBackdrop").hidden = false; }
  function closeSheet() { $("sheet").hidden = true; $("sheetBackdrop").hidden = true; }
  $("settingsBtn").addEventListener("click", openSheet);
  $("sheetBackdrop").addEventListener("click", closeSheet);

  function changed() { persist(); paintBadge(); paintSegments(); if (current) renderCurrent(); }

  function paintSegments() {
    document.querySelectorAll("#styleSeg button, #reviewStyle button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.v === settings.style)));
    document.querySelectorAll("#dateSeg button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.v === settings.dateStyle)));
  }
  for (const id of ["styleSeg", "reviewStyle"]) {
    $(id).addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; settings.style = b.dataset.v; changed(); });
  }
  $("dateSeg").addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; settings.dateStyle = b.dataset.v; changed(); });
  $("styleToggle").addEventListener("click", () => { settings.style = settings.style === "floating" ? "bar" : "floating"; changed(); });

  const sel = $("modelSelect");
  MODELS.forEach((m) => sel.append(new Option(m, m)));
  sel.append(new Option("自定义…", "custom"));
  if (![...sel.options].some((o) => o.value === settings.model)) settings.model = defaults.model;
  sel.value = settings.model;
  $("customRow").hidden = settings.model !== "custom";
  $("customModel").value = settings.custom;
  sel.addEventListener("change", () => { settings.model = sel.value; $("customRow").hidden = sel.value !== "custom"; changed(); });
  $("customModel").addEventListener("input", (e) => { settings.custom = e.target.value; changed(); });

  $("showTime").checked = settings.showTime;
  $("showParams").checked = settings.showParams;
  $("showTime").addEventListener("change", (e) => { settings.showTime = e.target.checked; changed(); });
  $("showParams").addEventListener("change", (e) => { settings.showParams = e.target.checked; changed(); });

  const now = new Date();
  document.querySelectorAll("#dateSeg button").forEach((b) => (b.textContent = W.dateText(now, b.dataset.v)));

  // 切到后台时关相机，回来再开
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stopCamera();
    else if ($("reviewScreen").hidden) startCamera();
  });

  paintBadge();
  paintSegments();
  startCamera();
  if (firstRun) { persist(); openSheet(); }

  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
})();
