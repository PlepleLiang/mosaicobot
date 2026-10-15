/* Claw launcher integration for the browser design study; no device APIs. */
"use strict";
const builtinApps = [
  {
    id: "camera",
    name: "Camera",
    subtitle: "相机",
    origin: "hub",
    glyph: "camera",
    color: "#ff9469",
    capability: "相机 + 屏幕",
    note: "设备端取景示意。本原型不访问相机、不拍照、不上传图像。",
  },
  {
    id: "ai_create",
    name: "CLAW",
    subtitle: "设备 Agent",
    origin: "hub",
    glyph: "claw",
    color: "#ff7541",
    capability: "设备 Agent",
    note: "设备上的独立会话，不是手机 Agent 的创作任务。",
  },
  {
    id: "works",
    name: "Works",
    subtitle: "设备作品启动器",
    origin: "hub",
    glyph: "grid",
    color: "#c5d6b4",
    capability: "Lua 运行时",
    note: "预装运行时作品与已安装的自创应用，共享设备侧入口。",
  },
  {
    id: "settings",
    name: "Settings",
    subtitle: "系统设置",
    origin: "hub",
    glyph: "settings",
    color: "#b9c0cc",
    capability: "设备设置",
    note: "亮度与音量仅在当前模拟会话中生效，不改变真实设备。",
  },
  {
    id: "imu",
    name: "IMU",
    subtitle: "姿态感知",
    origin: "hub",
    glyph: "imu",
    color: "#b8d9a1",
    capability: "IMU + 屏幕",
    note: "姿态由演示滑杆提供，不是传感器实测值。",
  },
  {
    id: "album",
    name: "Album",
    subtitle: "系统相册",
    origin: "hub",
    glyph: "album",
    color: "#edca8e",
    capability: "本地相册",
    note: "系统原生相册入口；与 Works 中的 Album 运行时包不是同一应用。",
  },
  {
    id: "music",
    name: "Music",
    subtitle: "音乐播放器",
    origin: "hub",
    glyph: "music",
    color: "#e4a9ae",
    capability: "音频 + 屏幕",
    note: "演示播放 / 暂停状态，不下载歌曲、不输出声音。",
  },
  {
    id: "breakout",
    name: "Bricks",
    subtitle: "打砖块",
    origin: "hub",
    glyph: "game",
    color: "#eba06c",
    capability: "触摸 + 屏幕",
    note: "展示内置游戏的运行画面；此入口未复刻原游戏玩法。",
  },
  {
    id: "weather",
    name: "Weather",
    subtitle: "天气",
    origin: "hub",
    glyph: "weather",
    color: "#e7d17c",
    capability: "天气服务 + 屏幕",
    note: "保留未获取数据的状态，不用示例天气冒充当前读数。",
  },
  {
    id: "interact",
    name: "Interaction",
    subtitle: "模块交互",
    origin: "hub",
    glyph: "game",
    color: "#abcbb9",
    capability: "扩展模块",
    note: "未接入真实模块。此原型不驱动电机、继电器或其他执行器。",
  },
  {
    id: "peer",
    name: "Partner",
    subtitle: "设备伙伴",
    origin: "hub",
    glyph: "peer",
    color: "#a6c8de",
    capability: "设备互联",
    note: "等待另一台 Mosaico。原型不扫描附近设备、不建立真实连接。",
  },
  {
    id: "dino",
    name: "Dino",
    subtitle: "预装跑酷",
    origin: "works",
    glyph: "game",
    color: "#c5d6b4",
    capability: "Lua + 屏幕",
    note: "Works 预装运行时作品。展示运行页面，不复刻完整游戏。",
  },
  {
    id: "flappybird",
    name: "Flappy Bird",
    subtitle: "预装飞行游戏",
    origin: "works",
    glyph: "game",
    color: "#e7d17c",
    capability: "Lua + 屏幕",
    note: "Works 预装运行时作品。展示运行页面，不复刻完整游戏。",
  },
  {
    id: "album_app",
    name: "Album",
    subtitle: "Works 相册",
    origin: "works",
    glyph: "album",
    color: "#edca8e",
    capability: "Lua + 本地相册",
    note: "运行时包 album_app；与主屏的原生 Album 分开管理。",
  },
];
const builtinPaths = {
  camera: "M3 7h5l2-3h4l2 3h5v13H3Z M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  claw: "M5 5h14v14H5Z M9 9v4 M15 9v4 M9 16h6",
  grid: "M3 3h7v7H3Z M14 3h7v7h-7Z M3 14h7v7H3Z M14 14h7v7h-7Z",
  settings: "M4 7h16 M4 17h16 M8 4v6 M16 14v6",
  imu: "M12 2v20 M2 12h20 M18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0",
  album: "M3 4h18v16H3Z M3 17l6-6 5 5 3-3 4 4 M15 8h2",
  music:
    "M9 17V5l11-2v12 M9 17a3 3 0 1 1-6 0 3 3 0 0 1 6 0 M20 15a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
  weather:
    "M12 1v3 M12 20v3 M1 12h3 M20 12h3 M4 4l2 2 M18 18l2 2 M4 20l2-2 M18 6l2-2 M18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0",
  game: "M6 6h12l3 13h-4l-3-3h-4l-3 3H3Z M8 9v5 M5.5 11.5h5 M16 10h1 M18 13h1",
  peer: "M3 5h8v14H3Z M15 5h6v14h-6Z M11 9h4 M11 15h4",
};
const getBuiltin = (id) => builtinApps.find((app) => app.id === id);
function builtinIcon(app) {
  return (
    '<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="' +
    builtinPaths[app.glyph] +
    '"/></svg>'
  );
}
function runtimeName() {
  return state.running
    ? catalog[state.running.type].name
    : getBuiltin(state.builtin)?.name || "设备主屏";
}
function captureDeviceReceipt() {
  if (!state.connected || state.pairing) return;
  state.deviceReceipt = {
    builtin: state.builtin,
    work: state.running?.id || null,
    name: runtimeName(),
    version: state.running?.version,
    page: state.hubPage,
  };
}
function setAppGroup(group) {
  state.appGroup = group;
  state.builtinDetail = null;
  renderBuiltinApps();
  $("works-panel").scrollTop = 0;
}
function showBuiltinDetail(id) {
  state.appGroup = "device";
  state.builtinDetail = id;
  showTab("works");
  renderBuiltinApps();
  $("works-panel").scrollTop = 0;
  $("back-to-apps").focus({ preventScroll: true });
}
function renderBuiltinApps() {
  $("works-panel").classList.toggle("is-detail", !!state.builtinDetail);
  $("works-panel").classList.toggle("is-center", state.appGroup === "center");
  $("connection-button").hidden = state.tab === "device" || (state.tab === "works" && state.appGroup === "center");
  $("my-apps").hidden = state.appGroup !== "mine";
  $("builtin-apps").hidden =
    state.appGroup !== "device" || !!state.builtinDetail;
  $("builtin-detail").hidden = !state.builtinDetail;
  $("app-center").hidden = state.appGroup !== "center";
  $("works-panel").classList.toggle("is-center-detail", state.appGroup === "center" && !!centerState.detail);
  if (state.appGroup === "center") renderAppCenter();
  document
    .querySelectorAll("[data-app-group]")
    .forEach((b) =>
      b.setAttribute(
        "aria-pressed",
        String(b.dataset.appGroup === state.appGroup),
      ),
    );
  const title = (heading, sub) =>
    '<div class="builtin-section-heading"><h3>' +
    heading +
    "</h3><span>" +
    sub +
    "</span></div>";
  const cards = (origin) =>
    '<div class="builtin-grid">' +
    builtinApps
      .filter((a) => a.origin === origin)
      .map(
        (a) =>
          '<button class="builtin-tile" data-builtin-detail="' +
          a.id +
          '"><span class="builtin-tile-icon">' +
          builtinIcon(a) +
          "</span><b>" +
          a.name +
          "</b><small>" +
          a.subtitle +
          '</small><span class="builtin-tile-state">' +
          (state.connected && state.builtin === a.id
            ? "正在使用"
            : a.origin === "hub"
              ? "系统内置"
              : "预装作品") +
          "</span></button>",
      )
      .join("") +
    "</div>";
  $("builtin-list").innerHTML =
    title("Claw 主屏应用", "11 个入口") +
    cards("hub") +
    renderDeviceWorks();
  $("device-app-count").textContent = builtinApps.filter(a => a.origin === "hub").length + deviceWorkRecords().length;
  if (!state.builtinDetail) return;
  const app = getBuiltin(state.builtinDetail),
    receipt = state.deviceReceipt;
  const active = state.connected
    ? state.builtin === app.id
    : receipt?.builtin === app.id;
  $("builtin-detail").innerHTML =
    '<button class="text-button back-to-apps" id="back-to-apps">' +
    icon("back") +
    '返回设备应用</button><div class="builtin-detail-heading"><span class="builtin-tile-icon">' +
    builtinIcon(app) +
    "</span><div><h2>" +
    app.name +
    "</h2><p>" +
    app.subtitle +
    " · " +
    (app.origin === "hub" ? "Claw 系统内置" : "Works 预装") +
    '</p></div></div><div class="builtin-preview-wrap"><canvas id="builtin-preview" width="480" height="480" aria-label="' +
    app.name +
    '模拟画面；离线时只显示最后回执"></canvas><span class="preview-label">' +
    (!state.connected
      ? "离线 · 最后一次状态"
      : active
        ? "设备画面 · 模拟回执"
        : "应用预览 · 非运行实况") +
    '</span></div><p class="builtin-runtime-status" role="status">' +
    (!state.connected
      ? "连接已断开，当前运行状态待同步。"
      : active
        ? "已在 Mosaico 打开 · " + app.name
        : "当前设备：" + runtimeName()) +
    '</p><p class="builtin-description">' +
    app.note +
    '</p><div class="builtin-capability">' +
    app.capability +
    '<span>不可在此卸载</span></div><div class="builtin-remote-actions"><button id="builtin-open" class="primary-button full-width" ' +
    (!state.connected || active || activeJob() ? "disabled" : "") +
    ">" +
    icon("play") +
    (active ? "正在设备上使用" : "在 Mosaico 上打开") +
    '</button><button id="builtin-stop" class="secondary-button full-width" ' +
    (!state.connected || !active || activeJob() ? "disabled" : "") +
    ">" +
    icon("back") +
    "退出并返回设备主屏</button></div>" +
    (active && state.connected ? builtinControls(app) : "") +
    '<p class="app-boundary">本地交互模拟 · 未接入真实设备远程启动 API</p>';
  $("back-to-apps").onclick = () => {
    state.builtinDetail = null;
    renderBuiltinApps();
    document.querySelector('[data-builtin-detail="' + app.id + '"]').focus();
  };
  $("builtin-open").onclick = () => openBuiltin(app.id, "phone");
  $("builtin-stop").onclick = () => returnToHub(0, "phone");
  bindBuiltinControls(app);
  drawBuiltinPreview();
}
function builtinControls(app) {
  if (app.id === "imu")
    return (
      '<label class="builtin-field" for="builtin-tilt">模拟横滚角 <output id="builtin-tilt-output">' +
      state.builtinValues.tilt +
      '°</output></label><input id="builtin-tilt" type="range" min="-30" max="30" value="' +
      state.builtinValues.tilt +
      '" class="builtin-range">'
    );
  if (app.id === "settings")
    return (
      '<label class="builtin-field" for="builtin-brightness">模拟亮度 <output id="builtin-brightness-output">' +
      state.builtinValues.brightness +
      '%</output></label><input id="builtin-brightness" type="range" min="20" max="100" value="' +
      state.builtinValues.brightness +
      '" class="builtin-range">'
    );
  if (app.id === "music")
    return (
      '<button id="builtin-music-toggle" class="secondary-button full-width">' +
      icon(state.builtinValues.playing ? "pause" : "play") +
      (state.builtinValues.playing ? "暂停演示" : "播放演示（无音频）") +
      "</button>"
    );
  if (app.id === "ai_create")
    return '<button id="builtin-claw-talk" class="secondary-button full-width">和设备 Claw 对话</button>';
  if (app.id === "works")
    return '<p class="builtin-description">从右侧 Works 列表直接打开预装作品；已安装的自创应用也会出现在这里。</p>';
  return "";
}
function bindBuiltinControls(app) {
  if ($("builtin-tilt"))
    $("builtin-tilt").oninput = (e) => {
      if (!state.connected) return;
      state.builtinValues.tilt = Number(e.target.value);
      $("builtin-tilt-output").textContent = e.target.value + "°";
      draw();
    };
  if ($("builtin-brightness"))
    $("builtin-brightness").oninput = (e) => {
      if (!state.connected) return;
      state.builtinValues.brightness = Number(e.target.value);
      $("builtin-brightness-output").textContent = e.target.value + "%";
      draw();
    };
  if ($("builtin-music-toggle"))
    $("builtin-music-toggle").onclick = () => {
      state.builtinValues.playing = !state.builtinValues.playing;
      updateUI();
    };
  if ($("builtin-claw-talk")) $("builtin-claw-talk").onclick = showDeviceChat;
}
function drawBuiltinPreview() {
  const preview = $("builtin-preview");
  if (!preview) return;
  const c = preview.getContext("2d"),
    id = state.builtinDetail;
  if (!state.connected) {
    if (state.deviceReceipt?.builtin === id) c.drawImage(state.lastFrame, 0, 0);
    else {
      baseScreen(c);
      text(c, "离线 · 无当前回执", 240, 220, 24, "#d6ded1", "center");
      text(c, "连接后查看设备画面", 240, 260, 16, "#a8b5a2", "center");
    }
  } else if (state.builtin === id) c.drawImage(canvas, 0, 0);
  else drawBuiltinScreen(c, id, true);
}
function canNavigateHardware(source) {
  if (source === "phone" && !state.connected) {
    toast("设备离线，不能发送打开或退出指令。");
    return false;
  }
  if (state.job && state.job.step >= 3 && activeJob()) {
    toast("正在部署或确认运行，完成后再切换设备应用。");
    return false;
  }
  return true;
}
function openBuiltin(id, source = "phone", fromWorks = false) {
  const app = getBuiltin(id);
  if (!app || !canNavigateHardware(source)) return;
  const launch = () => {
    if (!canNavigateHardware(source)) return;
    state.running = null;
    state.simulation = null;
    state.builtin = id;
    state.builtinReturn = fromWorks ? "works" : null;
    state.receipt = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    if (source === "phone") state.builtinDetail = id;
    updateUI();
    if (source === "phone") $("builtin-stop")?.focus({ preventScroll: true });
    if (source === "phone") toast(app.name + " 已在 Mosaico 打开（模拟）。");
  };
  if (id === "camera") {
    openModal(
      "让设备使用相机？",
      '<p>执行位置：Mosaico。仅在相机应用前台显示取景，退出即结束本次授权；本次不录音、不保存、不上传。</p><p>当前为示意画面，不访问任何真实相机。正式产品必须在设备端执行权限校验。</p><div class="modal-actions"><button id="camera-deny" class="secondary-button">暂不开启</button><button id="camera-allow" class="primary-button">允许本次模拟取景</button></div>',
      "DEVICE PERMISSION / CAMERA",
    );
    modalAction("camera-deny", closeModal);
    modalAction("camera-allow", () => {
      closeModal();
      launch();
    });
  } else launch();
}
function returnToHub(page = 0, source = "hardware") {
  if (!canNavigateHardware(source)) return;
  state.running = null;
  state.simulation = null;
  state.builtin = null;
  state.builtinReturn = null;
  state.hubPage = page;
  updateUI();
  if (source === "phone") $("builtin-open")?.focus({ preventScroll: true });
}
function deviceBack() {
  if (!canNavigateHardware("hardware")) return;
  if (state.builtinReturn === "works") openBuiltin("works", "hardware");
  else returnToHub(0);
}
function playInstalledWork(id) {
  const work = deviceWorkRecords().find(w => w.id === id && !w.preset);
  if (!work || !canNavigateHardware("hardware")) return;
  state.running = { ...work, params: { ...work.params } };
  state.builtin = null;
  state.builtinReturn = "works";
  initSimulation();
  updateUI();
}
function canvasGlyph(c, app, x, y, size, color = app.color) {
  c.save();
  c.translate(x, y);
  c.scale(size / 24, size / 24);
  c.strokeStyle = color;
  c.lineWidth = 1.65;
  c.lineCap = "round";
  c.lineJoin = "round";
  c.stroke(new Path2D(builtinPaths[app.glyph]));
  c.restore();
}
function hubTiles(page = state.hubPage) {
  return (page === 0 ? ["camera", "ai_create", "works"] : page === 1
    ? ["settings", "imu", "album", "music", "breakout", "weather"] : ["interact", "peer"])
    .map((id, i) => ({ id, x: 24 + (i % 3) * 147,
      y: page === 0 ? 328 : 128 + Math.floor(i / 3) * 155,
      w: 137, h: page === 0 ? 106 : 145 }));
}
function drawHubScreen(c) {
  const bg = "#101416", panel = "#1c2226", ink = "#f1f2e9", sub = "#a6b0b5", orange = "#ff753e";
  c.fillStyle = bg; c.fillRect(0, 0, 480, 480);
  rounded(c, 222, 5, 36, 3, 1, "#626b6d");
  text(c, "mosaico", 28, 37, 18, ink, "left", 600);
  text(c, "86%", 452, 37, 14, sub, "right");
  if (state.hubPage === 0) {
    text(c, "Tue / 10.13", 28, 98, 16, sub);
    text(c, "LOCAL TIME", 452, 98, 12, sub, "right");
    text(c, "09", 22, 228, 104, ink, "left", 400);
    text(c, ":", 162, 228, 104, orange, "left", 400);
    text(c, "41", 202, 228, 104, ink, "left", 400);
    rounded(c, 28, 253, 424, 1, 0, "#485257");
    canvasGlyph(c, getBuiltin("weather"), 28, 270, 30, ink);
    text(c, "--°", 70, 296, 28, ink);
    text(c, "Weather unavailable", 452, 286, 16, sub, "right");
    text(c, "LOCAL WEATHER", 452, 309, 12, sub, "right");
  } else {
    text(c, state.hubPage === 1 ? "Your toolkit." : "Stay curious.", 24, 105, 28, ink);
    text(c, state.hubPage === 1 ? "06 APPS" : "02 APPS", 452, 105, 12, sub, "right");
  }
  for (const [i, tile] of hubTiles().entries()) {
    const app = getBuiltin(tile.id), accent = tile.id === "ai_create";
    rounded(c, tile.x, tile.y, tile.w, tile.h, 16, accent ? orange : panel);
    canvasGlyph(c, app, tile.x + 17, tile.y + 17, tile.h < 120 ? 34 : 42, accent ? bg : ink);
    text(c, app.name, tile.x + 17, tile.y + tile.h - 14, 18, accent ? bg : ink);
    text(c, String(i + 1).padStart(2, "0"), tile.x + 104, tile.y + 29, 12, accent ? bg : sub);
  }
  if (state.hubPage === 2) {
    rounded(c, 28, 327, 424, 1, 0, "#485257");
    text(c, "A little body. More possibilities.", 28, 374, 18, ink);
    text(c, "INTERACT / CONNECT / EXPLORE", 28, 405, 12, sub);
  }
  text(c, "HUB / 0" + (state.hubPage + 1), 24, 462, 12, sub);
  [0, 1, 2].forEach(i => rounded(c, 362 + i * 34, 453, i === state.hubPage ? 18 : 5, 4, 2,
    i === state.hubPage ? orange : sub));
}
function installedEntries() {
  return deviceWorkRecords();
}
function worksEntries() {
  const entries = installedEntries();
  state.worksPage = Math.min(
    state.worksPage,
    Math.max(0, Math.ceil(entries.length / 4) - 1),
  );
  return entries.slice(state.worksPage * 4, state.worksPage * 4 + 4);
}
function drawBuiltinScreen(c, id, preview = false) {
  const app = getBuiltin(id);
  if (!app) return;
  baseScreen(c);
  text(c, app.name.toUpperCase(), 28, 40, 16, "#e9eee3", "left", 600);
  text(c, preview ? "PREVIEW" : "LOCAL DEMO", 452, 40, 11, "#b7c5b0", "right");
  const footer = (message) =>
    text(c, message, 240, 449, 14, "#c0cbbb", "center");
  if (id === "works") {
    worksEntries().forEach((entry, i) => {
      const y = 68 + i * 77;
      rounded(c, 24, y, 432, 67, 13, "#222d22");
      canvasGlyph(c, entry.app || getBuiltin("works"), 40, y + 19, 28);
      text(c, entry.name, 85, y + 29, 19, "#eef2e9", "left", 550);
      text(
        c,
        entry.preset ? "预装作品" : "我的创作 · v" + entry.version,
        85,
        y + 51,
        12,
        "#b7c5b0",
      );
      text(c, "›", 431, y + 41, 29, "#c5d6b4", "right");
    });
    text(c, "上一页", 78, 407, 16, "#c5d6b4", "center");
    text(
      c,
      state.worksPage +
        1 +
        " / " +
        Math.max(1, Math.ceil(installedEntries().length / 4)),
      240,
      407,
      14,
      "#b7c5b0",
      "center",
    );
    text(c, "下一页", 402, 407, 16, "#c5d6b4", "center");
    footer("预装作品与已安装应用，都在这里");
    return;
  }
  if (id === "imu") {
    const tilt = preview ? 0 : state.builtinValues.tilt;
    c.strokeStyle = "#394737";
    c.lineWidth = 2;
    c.beginPath();
    c.moveTo(60, 233);
    c.lineTo(420, 233);
    c.moveTo(240, 95);
    c.lineTo(240, 363);
    c.stroke();
    c.save();
    c.translate(240, 233);
    c.rotate((tilt * Math.PI) / 180);
    rounded(c, -143, -5, 286, 10, 5, "#b8d9a1");
    rounded(c, -13, -13, 26, 26, 13, "#eef2e9");
    c.restore();
    text(c, tilt.toFixed(1) + "°", 240, 379, 40, "#c5d6b4", "center", 500);
    footer("ROLL · 姿态演示值，非传感器读数");
    return;
  }
  if (id === "camera") {
    rounded(c, 24, 69, 432, 318, 22, "#28332b");
    rounded(c, 148, 136, 178, 178, 34, "#65715a");
    rounded(c, 162, 150, 150, 150, 25, "#141e17");
    canvasGlyph(c, app, 212, 195, 56, "#d5dec8");
    text(c, "示意取景 · 无真实图像", 240, 357, 16, "#eef2e9", "center");
    footer("本次授权仅在前台有效 · 退出即结束");
    return;
  }
  if (id === "weather") {
    canvasGlyph(c, app, 204, 116, 72);
    text(c, "--°", 240, 264, 74, "#eef2e9", "center");
    text(c, "尚未获取天气数据", 240, 320, 22, "#d6ded1", "center");
    footer("等待接入天气服务 · 不提供假读数");
    return;
  }
  if (id === "settings") {
    const brightness = preview ? 75 : state.builtinValues.brightness;
    [
      ["屏幕亮度", brightness + "%"],
      ["音量", "静音演示"],
      ["连接方式", "局域网（模拟）"],
    ].forEach((row, i) => {
      rounded(c, 24, 91 + i * 93, 432, 78, 15, "#242d24");
      text(c, row[0], 44, 137 + i * 93, 19);
      text(c, row[1], 432, 137 + i * 93, 18, "#c5d6b4", "right");
    });
    footer("设置不会修改真实设备");
    return;
  }
  if (id === "album" || id === "album_app") {
    for (let i = 0; i < 4; i++) {
      const x = 30 + (i % 2) * 216,
        y = 82 + Math.floor(i / 2) * 153;
      rounded(
        c,
        x,
        y,
        204,
        141,
        15,
        ["#67785d", "#7d6559", "#586d72", "#777050"][i],
      );
      c.fillStyle = "#e2d8ae";
      c.beginPath();
      c.arc(x + 145, y + 38, 15, 0, Math.PI * 2);
      c.fill();
      c.fillStyle = "#303e32";
      c.beginPath();
      c.moveTo(x + 10, y + 131);
      c.lineTo(x + 73, y + 59);
      c.lineTo(x + 135, y + 131);
      c.fill();
    }
    footer(
      (id === "album" ? "系统相册" : "Works · album_app") +
        " / 生成图形，非用户照片",
    );
    return;
  }
  if (id === "music") {
    rounded(c, 152, 91, 176, 176, 30, "#443335");
    canvasGlyph(c, app, 207, 145, 66);
    text(c, "A quiet moment", 240, 310, 28, "#eef2e9", "center");
    text(
      c,
      !preview && state.builtinValues.playing ? "播放中 · 无音频" : "已暂停",
      240,
      346,
      18,
      "#e4a9ae",
      "center",
    );
    rounded(c, 85, 379, 310, 4, 2, "#495344");
    rounded(c, 85, 379, 94, 4, 2, "#e4a9ae");
    footer("播放状态示意 · 不输出声音");
    return;
  }
  if (["breakout", "dino", "flappybird"].includes(id)) {
    rounded(c, 24, 75, 432, 325, 16, "#1c291f");
    if (id === "breakout") {
      for (let row = 0; row < 3; row++)
        for (let col = 0; col < 6; col++)
          rounded(
            c,
            47 + col * 65,
            102 + row * 29,
            58,
            20,
            4,
            ["#eba06c", "#edca8e", "#c5d6b4"][row],
          );
      rounded(c, 187, 358, 106, 9, 4, "#e9eee3");
      rounded(c, 296, 295, 11, 11, 5, "#eba06c");
    } else if (id === "dino") {
      rounded(c, 47, 342, 389, 3, 1, "#68765f");
      rounded(c, 98, 270, 43, 52, 3, "#c5d6b4");
      rounded(c, 120, 249, 37, 32, 2, "#c5d6b4");
      rounded(c, 105, 317, 10, 27, 2, "#c5d6b4");
      rounded(c, 133, 317, 10, 27, 2, "#c5d6b4");
      rounded(c, 327, 292, 17, 51, 2, "#80976d");
    } else {
      rounded(c, 318, 75, 54, 115, 4, "#80976d");
      rounded(c, 318, 292, 54, 108, 4, "#80976d");
      rounded(c, 137, 226, 40, 29, 10, "#e7d17c");
      rounded(c, 167, 234, 20, 8, 3, "#eba06c");
    }
    footer("运行界面示意 · 此原型未复刻玩法");
    return;
  }
  canvasGlyph(c, app, 203, 126, 74);
  text(
    c,
    id === "ai_create"
      ? "你好，我是设备 Claw"
      : id === "peer"
        ? "等待另一台 Mosaico"
        : "尚未连接扩展模块",
    240,
    275,
    25,
    "#eef2e9",
    "center",
    500,
  );
  text(
    c,
    id === "ai_create"
      ? "手机创作与设备会话，相互独立"
      : id === "peer"
        ? "在连接建立前，不冒充已发现"
        : "连接模块后，再声明可用能力",
    240,
    319,
    17,
    "#c0cbbb",
    "center",
  );
  footer(
    id === "ai_create"
      ? "在手机应用详情中，试着问问我"
      : "本地交互示意 · 未访问真实硬件",
  );
}
function renderHardwareNavigation() {
  const locked = state.pairing || !!(activeJob() && state.job.step >= 3);
  const onHub = !state.running && !state.builtin;
  $("hardware-back").disabled = locked || (onHub && state.hubPage === 0);
  $("hardware-home").disabled = locked || (onHub && state.hubPage === 0);
  $("hardware-back").textContent =
    state.builtinReturn === "works" ? "返回 Works" : "返回主屏";
  document.querySelectorAll("[data-hub-page]").forEach((b) => {
    b.disabled = locked;
    b.setAttribute(
      "aria-pressed",
      String(onHub && state.hubPage === Number(b.dataset.hubPage)),
    );
  });
  let hotspots = [];
  if (onHub)
    hotspots = hubTiles().map((t) => ({
      ...t,
      label: "打开 " + getBuiltin(t.id).name,
      attr: 'data-device-open="' + t.id + '"',
    }));
  if (state.builtin === "works") {
    hotspots = worksEntries().map((e, i) => ({
      x: 24,
      y: 68 + i * 77,
      w: 432,
      h: 67,
      label: "打开 " + e.name,
      attr:
        (e.preset ? "data-device-open" : "data-device-work") +
        '="' +
        escapeHTML(e.id) +
        '"',
    }));
    if (state.worksPage > 0)
      hotspots.push({
        x: 24,
        y: 371,
        w: 112,
        h: 65,
        label: "Works 上一页",
        attr: 'data-works-page="-1"',
      });
    if ((state.worksPage + 1) * 4 < installedEntries().length)
      hotspots.push({
        x: 344,
        y: 371,
        w: 112,
        h: 65,
        label: "Works 下一页",
        attr: 'data-works-page="1"',
      });
  }
  const html = locked
    ? ""
    : hotspots
        .map(
          (t) =>
            '<button class="device-hotspot" ' +
            t.attr +
            ' aria-label="' +
            t.label +
            '" style="left:' +
            t.x / 4.8 +
            "%;top:" +
            t.y / 4.8 +
            "%;width:" +
            t.w / 4.8 +
            "%;height:" +
            t.h / 4.8 +
            '%"></button>',
        )
        .join("");
  if ($("device-hotspots").innerHTML !== html)
    $("device-hotspots").innerHTML = html;
}
function initBuiltinNavigation() {
  $("browse-builtin").onclick = () => setAppGroup("device");
  $("hardware-home").onclick = () => returnToHub(0);
  $("hardware-back").onclick = deviceBack;
  document.addEventListener("click", (e) => {
    const group = e.target.closest("[data-app-group]");
    if (group) setAppGroup(group.dataset.appGroup);
    const detail = e.target.closest("[data-builtin-detail]");
    if (detail) showBuiltinDetail(detail.dataset.builtinDetail);
    const page = e.target.closest("[data-hub-page]");
    if (page) returnToHub(Number(page.dataset.hubPage));
    const open = e.target.closest("[data-device-open]");
    if (open)
      openBuiltin(
        open.dataset.deviceOpen,
        "hardware",
        state.builtin === "works",
      );
    const work = e.target.closest("[data-device-work]");
    if (work) playInstalledWork(work.dataset.deviceWork);
    const pagination = e.target.closest("[data-works-page]");
    if (pagination && canNavigateHardware("hardware")) {
      state.worksPage += Number(pagination.dataset.worksPage);
      updateUI();
    }
  });
}
