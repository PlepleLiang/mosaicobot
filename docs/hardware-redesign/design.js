if (new URLSearchParams(location.search).get("embed") === "1") document.documentElement.classList.add("embedded");
"use strict";
// Local design review only: no network, storage, device bridge or model calls.
const paths = {
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  back: '<path d="m14 6-6 6 6 6"/>',
  up: '<path d="m6 14 6-6 6 6"/>',
  wifi: '<path d="M3 8a15 15 0 0 1 18 0M6 12a10 10 0 0 1 12 0m-9 4a5 5 0 0 1 6 0"/><circle cx="12" cy="20" r=".8" fill="currentColor" stroke="none"/>',
  wifiOff:
    '<path d="m3 3 18 18M3 8a15 15 0 0 1 3-1.8m4-1A15 15 0 0 1 21 8M6 12a10 10 0 0 1 4-1.8m-1 5.8a5 5 0 0 1 6 0"/><circle cx="12" cy="20" r=".8"/>',
  battery:
    '<rect x="2" y="6" width="17" height="12" rx="3"/><path d="M22 10v4"/><path d="M6 9v6m3-6v6m3-6v6m3-6v6"/>',
  camera: '<path d="M3 7h4l2-3h6l2 3h4v13H3z"/><circle cx="12" cy="13" r="4"/>',
  claw: '<path d="M4 9h16v10H4zM8 5v4m8-4v4M1 12v4m22-4v4M8 19v3m8-3v3M9 15h.01M15 15h.01M10 2h4"/>',
  works:
    '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 17.5h7m-3.5-3.5v7"/>',
  settings:
    '<path d="m9 3-1 3-3 1-2 3 2 2v3l2 3 3-1 2 3 3-1 1-3 3-1 2-3-2-2V9l-2-3-3 1-2-3z"/><circle cx="12" cy="12" r="3"/>',
  imu: '<circle cx="12" cy="12" r="8"/><path d="M12 1v5m0 12v5M1 12h5m12 0h5m-14 1 3-5 3 7"/>',
  album:
    '<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 6"/>',
  music:
    '<path d="M10 18V5l10-2v13M10 9l10-2"/><ellipse cx="6.5" cy="18" rx="3.5" ry="3"/><ellipse cx="16.5" cy="16" rx="3.5" ry="3"/>',
  bricks:
    '<rect x="3" y="3" width="7" height="4" rx="1"/><rect x="14" y="3" width="7" height="4" rx="1"/><rect x="3" y="11" width="7" height="4" rx="1"/><path d="M4 21h12m1-9 3-3"/><circle cx="18" cy="17" r="2"/>',
  weather:
    '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  interaction:
    '<path d="M7 14V7a2 2 0 0 1 4 0v5-8a2 2 0 0 1 4 0v9-6a2 2 0 0 1 4 0v8l-2 6H9l-6-7a2 2 0 0 1 3-2l1 2Z"/>',
  partner:
    '<circle cx="8" cy="7" r="3"/><path d="M2 21v-4a6 6 0 0 1 12 0v4M16 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 4 5v2"/>',
  module:
    '<rect x="5" y="5" width="14" height="14" rx="2"/><rect x="9" y="9" width="6" height="6" rx="1"/><path d="M8 2v3m8-3v3M8 19v3m8-3v3M2 8h3m-3 8h3M19 8h3m-3 8h3"/>',
  join: '<path d="M9 8V6a4 4 0 0 1 8 0v3m-2 7v2a4 4 0 0 1-8 0v-3M12 8v8"/>',
  bluetooth: '<path d="M12 2v20l7-6L5 5m0 14L19 8z"/>',
  leaf: '<path d="M20 3C5 1 0 13 7 18c8 5 15-4 13-15ZM5 22 16 9"/>',
  bell: '<path d="M5 17h14l-2-4V8A5 5 0 0 0 7 8v5zM10 21h4M12 1v2"/>',
  vibration:
    '<rect x="8" y="3" width="8" height="18" rx="2"/><path d="m4 7-2 3 2 4-2 3m18-10 2 3-2 4 2 3"/>',
  volume:
    '<path d="m3 9 5 0 5-5v16l-5-5H3zM17 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',
  slot: '<rect x="3" y="7" width="18" height="10" rx="2"/><path d="M7 10v4m5-4v4m5-4v4"/>',
  bolt: '<path d="m14 2-9 12h6l-1 8 9-12h-6z"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 5v2"/>',
  dino: '<path d="M3 10v6h4v5m0-5h7v5m0-8h4V8h3V3h-8v7H8v3H5zM17 6h.01"/>',
  bird: '<path d="M4 13a7 7 0 0 1 14-2l4 2-4 2a7 7 0 0 1-12 2L2 19l2-6Zm4-1 4 2-4 2m7-9h.01"/>',
  timer:
    '<circle cx="12" cy="13" r="8"/><path d="M10 1h4m-2 0v4m6 1 2-2m-8 5v5l3 2"/>',
  play: '<path d="m8 4 12 8-12 8z"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="2"/>',
  warning: '<path d="m12 3 10 18H2zM12 9v5m0 3v.01"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
};
const icon = (name) =>
  '<svg viewBox="0 0 24 24" aria-hidden="true">' +
  (paths[name] || paths.module) +
  "</svg>";
const scenarios = [
  {
    id: "home",
    name: "时钟首页",
    group: "DAILY / 日常",
    title: "一眼可读的日常。",
    desc: "时间占据视觉中心，天气退居辅助。三个熟悉的入口始终在下方，不需要先穿过一层仪表盘。",
    visual:
      "大数字、平直分隔线和独立快捷区。只把 CLAW 入口点亮为信号橙，建立稳定的视觉锚点。",
    boundary:
      "Camera / CLAW / Works 原入口不变。天气未获取时显示 --；时钟改数字需要少量显示绑定调整。",
    caption: "时间是主角，三个原入口保持直达。",
  },
  {
    id: "apps",
    name: "应用 · 第一页",
    group: null,
    title: "工具，各归其位。",
    desc: "原来的六个应用，统一为三列两行。图标辨功能，名称保留原称呼，间距与首页保持同一节奏。",
    visual:
      "统一线性图标、16px 圆角和左对齐名称。删除图标各自的彩色底，不改变应用顺序。",
    boundary:
      "Settings、IMU、Album、Music、Bricks、Weather 全部保留。应用内部功能与操作不在本轮重写。",
    caption: "保留原来的六个应用与原有顺序。",
  },
  {
    id: "more",
    name: "应用 · 第二页",
    group: null,
    title: "留白，也是秩序。",
    desc: "保留 Interaction 和 Partner 两个入口。没有新应用时，让画面安静下来，不用假卡片填满屏幕。",
    visual:
      "延续相同网格，底部用低干扰的模块说明收束。两页应用与首页共享页码位置。",
    boundary:
      "不新增商店、实验室或调试中心；用户生成作品仍进入 Works，不自动改变静态 Hub 网格。",
    caption: "两个原生入口，不用虚构功能填空。",
  },
  {
    id: "works",
    name: "Works 作品",
    group: null,
    title: "作品，有清晰的状态。",
    desc: "保留 Recent / Installed 与每页四行。名字、来源、运行状态各有自己的位置，容易找到，也容易分辨。",
    visual:
      "轻量列表替代大块装饰。运行态同时显示 Running 与停止图标，失败同时显示 Failed，不只依赖颜色。",
    boundary:
      "Dino / Flappy Bird / Works Album 保留；系统 Album 与 Works Album 不合并。列表按钮继续沿用原启停行为。",
    caption: "四行列表，预装与用户作品同时保留。",
  },
  {
    id: "controls",
    name: "下拉控制栏",
    group: "SYSTEM / 系统",
    title: "控制，还在熟悉的位置。",
    desc: "保留四个控制区域：连接、音乐、左右模块和两根纵向滑杆。滑下即可看清常用设置。",
    visual:
      "原位置换成清晰面板与填充滑杆。实线表示现有控制，虚线与 RESERVED 表示预留入口。",
    boundary:
      "Wi-Fi / 振动可切换；Join / 蓝牙 / 低功耗 / 铃声仍提示 Not Supported。音乐区域只打开 Music，不伪装播放控制。",
    caption: "原六个控制区与滑杆触点位置保持一致。",
  },
  {
    id: "notifications",
    name: "通知列表",
    group: null,
    title: "信息，按轻重排好。",
    desc: "标题、来源、时间和正文分层，保持原通知列表的滚动与返回。这里只展示现有场景中的示例数据。",
    visual:
      "用细分隔线和统一来源图标组织列表；不把每一条通知都做成高对比卡片。",
    boundary:
      "现有固件列表为静态示例，不宣称接入了实时历史服务。此页从屏外评审入口进入，不新增未核实的通知手势或清空操作。",
    caption: "保留滚动列表；示例态与空态都能评审。",
  },
  {
    id: "aod",
    name: "息屏时钟",
    group: "AMBIENT / 环境",
    title: "安静，但不失联。",
    desc: "保留原锁屏时钟与上滑打开的动作。降低亮度感，留下时间、日期和电量，不做持续动画。",
    visual: "近黑底色、柔灰数字、无卡片。仍然保持方屏，不裁圆，不加底托。",
    boundary:
      "这里只定义显示层，不承诺屏幕功耗下降；亮度、电池、唤醒和锁屏生命周期沿用现有逻辑。",
    caption: "息屏显示低干扰信息，保留上滑打开。",
  },
  {
    id: "charging",
    name: "充电状态",
    group: null,
    title: "补充能量，清晰可见。",
    desc: "同一套锁屏骨架，加上直观电量和充电标记。把正在充电这件事讲清楚，不附加虚构的预测。",
    visual:
      "橙色只出现在充电标记和电量条；用文本明确 Charging，读数不依赖颜色。",
    boundary:
      "绑定原电池快照；不新增剩余充电时间、充电功率或电池健康值。86% 是设计示例。",
    caption: "沿用电池快照，不增加虚构电池指标。",
  },
  {
    id: "insert",
    name: "模块插入",
    group: null,
    title: "新能力，即插即见。",
    desc: "端口、模块名称、能力说明和动作各占一层。沿用原来的 Open 与 Done，插入后知道下一步。",
    visual:
      "统一模块图标与短信息层级，主次按钮清晰区分。仅在已有弹层内调整排版。",
    boundary:
      "沿用 insert_port / insert_board_name / insert_board_cap 与 Open / Done 回调。相机是演示模块，不是当前硬件插入状态。",
    caption: "端口、模块、能力与两个原动作。",
  },
];
const apps = {
  camera: ["Camera", "camera"],
  claw: ["CLAW", "claw"],
  works: ["Works", "works"],
  settings: ["Settings", "settings"],
  imu: ["IMU", "imu"],
  album: ["Album", "album"],
  music: ["Music", "music"],
  bricks: ["Bricks", "bricks"],
  weather: ["Weather", "weather"],
  interaction: ["Interaction", "interaction"],
  partner: ["Partner", "partner"],
};
const works = [
  { id: "dino", name: "Dino", icon: "dino", meta: "PREINSTALLED" },
  { id: "flappy", name: "Flappy Bird", icon: "bird", meta: "PREINSTALLED" },
  {
    id: "works-album",
    name: "Album",
    icon: "album",
    meta: "WORKS / PREINSTALLED",
  },
  { id: "focus", name: "Focus Timer", icon: "timer", meta: "USER APP / v1" },
];
const extraWork = {
  id: "snake-demo",
  name: "Snake",
  icon: "bricks",
  meta: "USER APP / v1",
};
const state = {
  scene: "home",
  hub: "home",
  drawerFrom: "home",
  returnTo: "home",
  app: null,
  workApp: null,
  tab: "installed",
  workStatus: "running",
  otherWorkStates: {},
  workPage: 0,
  worksPaged: false,
  worksEmpty: false,
  notifEmpty: false,
  wifi: true,
  vibration: true,
  volume: 65,
  brightness: 80,
  weather: false,
  agent: false,
  module: false,
};
const mainScreen = document.querySelector("#device-screen");
const feedback = (text) => {
  document.querySelector("#feedback").textContent = text;
};
function status(mode = "") {
  return (
    '<span class="drawer-grip" aria-hidden="true"></span><div class="status-bar"><span class="status-brand"><i class="device-dot" aria-hidden="true"></i>' +
    (mode ? "10:08" : "mosaico") +
    "</span>" +
    (mode ? '<span class="status-mode">' + mode + "</span>" : "") +
    (state.agent
      ? '<span class="agent-mark" title="Agent configured: LLM and IM channel">CFG</span>'
      : "") +
    '<span role="img" aria-label="' +
    (state.wifi ? "Wi-Fi 开启（演示）" : "Wi-Fi 关闭（演示）") +
    '">' +
    icon(state.wifi ? "wifi" : "wifiOff") +
    '</span><span class="battery-label">86</span>' +
    icon("battery") +
    "</div>"
  );
}
function dots(active) {
  return (
    '<div class="page-footer"><span>HUB / ' +
    String(active + 1).padStart(2, "0") +
    '</span><div class="page-dots">' +
    ["home", "apps", "more"]
      .map(
        (id, i) =>
          '<button type="button" class="page-dot" data-screen="' +
          id +
          '" aria-label="' +
          scenarios[i].name +
          '"' +
          (i === active ? ' aria-current="page"' : "") +
          "></button>",
      )
      .join("") +
    "</div></div>"
  );
}
function tile(id, index, accent = false) {
  return (
    '<button type="button" class="app-tile' +
    (accent ? " accent" : "") +
    '" data-app="' +
    id +
    '"><span class="tile-index">' +
    String(index).padStart(2, "0") +
    "</span>" +
    icon(apps[id][1]) +
    '<span class="app-label">' +
    apps[id][0] +
    "</span></button>"
  );
}
function titlebar(title, meta = "") {
  return (
    '<div class="screen-titlebar"><button type="button" class="back-button" data-back aria-label="返回">' +
    icon("back") +
    "</button><h3>" +
    title +
    '</h3><span class="title-meta">' +
    meta +
    "</span></div>"
  );
}
function home() {
  return (
    status() +
    '<div class="home-clock"><div class="clock-meta"><span>TUE, OCT 13</span><span>LOCAL TIME</span></div><div class="clock-time">10<span class="colon">:</span>08</div></div><div class="weather-line">' +
    icon("weather") +
    '<span class="weather-value">' +
    (state.weather ? "24°" : "--°") +
    '</span><span class="weather-copy">' +
    (state.weather ? "Shenzhen · Clear" : "Weather unavailable") +
    '</span></div><div class="home-launchers">' +
    tile("camera", 1) +
    tile("claw", 2, true) +
    tile("works", 3) +
    "</div>" +
    dots(0)
  );
}
function appPage(more = false) {
  return (
    status("APPS") +
    '<div class="screen-heading"><h3>' +
    (more ? "Stay curious." : "Your toolkit.") +
    "</h3><span>" +
    (more ? "02" : "06") +
    ' APPS</span></div><div class="app-grid">' +
    (more
      ? ["interaction", "partner"]
      : ["settings", "imu", "album", "music", "bricks", "weather"]
    )
      .map((id, i) => tile(id, i + 1))
      .join("") +
    "</div>" +
    (more
      ? '<div class="extension-note">' +
        icon("module") +
        "<div><p>A little body. More possibilities.</p><small>INTERACT / CONNECT / EXPLORE</small></div></div>"
      : "") +
    dots(more ? 2 : 1)
  );
}
function workView() {
  const available = state.worksPaged ? [...works, extraWork] : works;
  const rows =
    state.tab === "recent"
      ? [works[3]]
      : available.slice(state.workPage * 4, state.workPage * 4 + 4);
  const pager =
    state.tab === "installed" && state.worksPaged && !state.worksEmpty;
  return (
    titlebar(
      "Works",
      state.worksEmpty
        ? "0 ITEMS"
        : (state.tab === "recent" ? 1 : available.length) + " ITEMS",
    ) +
    '<div class="works-tabs"><button type="button" data-tab="recent" aria-pressed="' +
    (state.tab === "recent") +
    '">Recent</button><button type="button" data-tab="installed" aria-pressed="' +
    (state.tab === "installed") +
    '">Installed</button></div>' +
    (state.worksEmpty
      ? '<div class="empty-state">' +
        icon("works") +
        "<h4>No works yet.</h4><p>" +
        (state.tab === "recent"
          ? "Your recent works appear here."
          : "Your installed works appear here.") +
        "</p><small>READY WHEN YOU ARE</small></div>"
      : '<div class="work-list">' +
        rows
          .map((w) => {
            const s =
              w.id === "focus"
                ? state.workStatus
                : state.otherWorkStates[w.id] || "stopped";
            const active = ["running", "queued", "stopping"].includes(s);
            const label = {
              running: "Running",
              queued: "Starting",
              stopping: "Stopping",
              failed: "Failed",
              stopped: "Open",
            }[s];
            return (
              '<button type="button" class="work-row' +
              (active ? " is-active" : "") +
              (s === "failed" ? " is-failed" : "") +
              '" data-work="' +
              w.id +
              '" aria-label="' +
              (active ? "停止 " : "打开 ") +
              w.name +
              " · " +
              label +
              '"><span class="work-symbol">' +
              icon(w.icon) +
              '</span><span class="work-copy"><strong>' +
              w.name +
              "</strong><small>" +
              w.meta +
              '</small></span><span class="work-state">' +
              label +
              icon(active ? "stop" : s === "failed" ? "warning" : "play") +
              "</span></button>"
            );
          })
          .join("") +
        "</div>") +
    '<div class="page-footer"><span>' +
    (state.tab === "recent"
      ? "RECENT WORKS"
      : "INSTALLED / " + String(state.workPage + 1).padStart(2, "0")) +
    "</span>" +
    (pager
      ? '<span class="works-pager"><button type="button" data-workpage="-1" aria-label="Works 上一页"' +
        (state.workPage === 0 ? " disabled" : "") +
        ">" +
        icon("back") +
        "</button><span>" +
        String(state.workPage + 1).padStart(2, "0") +
        ' / 02</span><button type="button" data-workpage="1" aria-label="Works 下一页"' +
        (state.workPage === 1 ? " disabled" : "") +
        ">" +
        icon("arrow") +
        "</button></span>"
      : "<span>" + (state.worksEmpty ? "0" : "01 / 01") + "</span>") +
    "</div>"
  );
}
const toggles = [
  { id: "wifi", label: "Wi-Fi", icon: "wifi", x: 34, y: 62 },
  { id: "join", label: "Join", icon: "join", x: 138, y: 62, reserved: true },
  {
    id: "bluetooth",
    label: "Bluetooth",
    icon: "bluetooth",
    x: 34,
    y: 154,
    reserved: true,
  },
  {
    id: "lowpower",
    label: "Low power",
    icon: "leaf",
    x: 138,
    y: 154,
    reserved: true,
  },
  {
    id: "ringtone",
    label: "Ringtone",
    icon: "bell",
    x: 34,
    y: 246,
    reserved: true,
  },
  { id: "vibration", label: "Vibration", icon: "vibration", x: 138, y: 246 },
];
function level(id, label, iconName, x) {
  return (
    '<div class="vertical-level ' +
    id +
    '" style="left:' +
    x +
    'px;top:298px"><div class="level-fill" style="height:' +
    state[id] +
    '%"></div><input type="range" min="0" max="100" step="1" value="' +
    state[id] +
    '" data-level="' +
    id +
    '" aria-label="' +
    label +
    '（演示）" aria-valuetext="' +
    state[id] +
    '%"><span class="level-value">' +
    state[id] +
    '</span><span class="level-icon">' +
    icon(iconName) +
    "</span></div>"
  );
}
function controls() {
  return (
    status("CONTROL") +
    '<div class="quick-panel quick-connect"></div><button type="button" class="quick-panel quick-media" data-app="music"><span class="music-art" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span><strong>Music</strong><small>YOUR SOUND, YOUR SPACE</small><span class="media-bottom">Open Music ' +
    icon("arrow") +
    '</span></button><div class="quick-panel quick-slots">' +
    ["LEFT", "RIGHT"]
      .map(
        (port, i) =>
          '<div class="module-slot"><span class="slot-label">' +
          port +
          " PORT</span>" +
          icon(state.module && i === 0 ? "camera" : "slot") +
          '<span class="slot-value">' +
          (state.module && i === 0 ? "Camera" : "Empty") +
          "</span></div>",
      )
      .join("") +
    '</div><div class="quick-panel quick-levels"></div>' +
    toggles
      .map(
        (t) =>
          '<button type="button" class="quick-toggle' +
          (t.reserved ? " reserved" : "") +
          '" data-toggle="' +
          t.id +
          '" style="left:' +
          t.x +
          "px;top:" +
          t.y +
          'px"' +
          (t.reserved
            ? ' aria-label="' + t.label + ' · Not Supported"'
            : ' aria-pressed="' + state[t.id] + '"') +
          ">" +
          icon(t.icon) +
          '<span class="toggle-label">' +
          t.label +
          '</span></button><span class="toggle-state" style="left:' +
          t.x +
          "px;top:" +
          (t.y + 73) +
          'px">' +
          (t.reserved ? "RESERVED" : state[t.id] ? "ON" : "OFF") +
          "</span>",
      )
      .join("") +
    level("volume", "音量", "volume", 270) +
    level("brightness", "亮度", "weather", 374) +
    '<button type="button" class="drawer-close" data-back aria-label="收起下拉栏">' +
    icon("up") +
    "</button>"
  );
}
function notifications() {
  const messages = [
    [
      "warning",
      "Unexpected removal",
      "just now",
      "Camera disconnected from left port.",
    ],
    ["module", "Environment Sensor", "2m", "Module connected."],
    ["bolt", "Charging", "16min", "Power connected."],
    ["check", "System", "1h", "Setup completed."],
    ["claw", "CLAW", "1h", "Benchmark finished."],
  ];
  return (
    titlebar("Notifications", state.notifEmpty ? "0" : "05") +
    (state.notifEmpty
      ? '<div class="empty-state">' +
        icon("bell") +
        "<h4>All quiet.</h4><p>No notifications right now.</p></div>"
      : '<div class="notification-list" tabindex="0" role="region" aria-label="通知示例列表，可滚动">' +
        messages
          .map(
            (m) =>
              '<article class="notification-row">' +
              icon(m[0]) +
              '<div class="notification-copy"><div class="notification-heading"><strong>' +
              m[1] +
              "</strong><time>" +
              m[2] +
              "</time></div><p>" +
              m[3] +
              "</p></div></article>",
          )
          .join("") +
        "</div>") +
    '<div class="notification-footer">' +
    (state.notifEmpty
      ? "NOTIFICATION CENTER"
      : "LATEST ON TOP / SCROLL FOR OLDER") +
    "</div>"
  );
}
function lock(charging = false) {
  return (
    '<div class="lock-screen' +
    (charging ? " charge-screen" : "") +
    '"><div class="lock-label">' +
    icon(charging ? "bolt" : "lock") +
    (charging ? "POWER CONNECTED" : "MOSAICO / AMBIENT") +
    '</div><div class="lock-clock">10:08</div><div class="lock-date">TUE / OCT 13</div>' +
    (charging
      ? '<div class="charge-card"><div class="charge-icon">' +
        icon("bolt") +
        '</div><div class="charge-copy"><strong>86<small>% · Charging</small></strong><div class="charge-track"><i></i></div></div></div>'
      : "") +
    '<div class="lock-bottom"><span>SWIPE UP TO OPEN</span><span>' +
    (!charging ? "86% " + icon("battery") : "") +
    '</span></div><button type="button" class="unlock-button" data-back aria-label="上滑打开（点击替代）">' +
    icon("up") +
    "</button></div>"
  );
}
function insert() {
  return (
    titlebar("Module connected") +
    '<div class="insert-body"><span class="insert-port">LEFT PORT / INSERTED</span><div class="module-illustration">' +
    icon("camera") +
    '</div><h4>Camera</h4><p>Camera module</p></div><div class="insert-actions"><button type="button" class="device-button" data-back>Done</button><button type="button" class="device-button primary" data-app="camera">Open ' +
    icon("arrow") +
    "</button></div>"
  );
}
function boundary() {
  const w = state.workApp
    ? [...works, extraWork].find((w) => w.id === state.workApp)
    : null;
  const app = w || { name: apps[state.app][0], icon: apps[state.app][1] };
  return (
    titlebar(app.name, "PREVIEW") +
    '<div class="app-boundary">' +
    icon(app.icon) +
    "<h4>" +
    app.name +
    "</h4>" +
    (w ? '<div class="work-status-label">RUNNING · DEMO ONLY</div>' : "") +
    '<p>原有应用入口保留。<br>应用内部界面不在本轮重绘，<br>此处只演示进入与返回。</p><small>NO APP OR DEVICE IS ACTUALLY RUNNING</small><br><button type="button" class="device-button" data-back>返回' +
    (w ? " Works" : "") +
    "</button></div>"
  );
}
function sceneHTML(id) {
  return {
    home,
    apps: () => appPage(false),
    more: () => appPage(true),
    works: workView,
    controls,
    notifications,
    aod: () => lock(false),
    charging: () => lock(true),
    insert,
    app: boundary,
  }[id]();
}
function stateTools(id) {
  if (id === "home")
    return (
      '<button type="button" data-state="weather" aria-pressed="' +
      state.weather +
      '">天气示例</button><button type="button" data-state="agent" aria-pressed="' +
      state.agent +
      '">Agent 已配置示例</button>'
    );
  if (id === "works")
    return (
      ["running", "stopped", "failed", "queued", "stopping"]
        .map(
          (s) =>
            '<button type="button" data-workstate="' +
            s +
            '" aria-pressed="' +
            (state.workStatus === s) +
            '">' +
            {
              running: "运行中",
              stopped: "已停止",
              failed: "失败",
              queued: "启动中",
              stopping: "停止中",
            }[s] +
            "</button>",
        )
        .join("") +
      '<button type="button" data-state="worksEmpty" aria-pressed="' +
      state.worksEmpty +
      '">空列表</button><button type="button" data-state="worksPaged" aria-pressed="' +
      state.worksPaged +
      '">分页示例</button>'
    );
  if (id === "notifications")
    return (
      '<button type="button" data-state="notifEmpty" aria-pressed="' +
      state.notifEmpty +
      '">空通知状态</button>'
    );
  if (id === "controls")
    return (
      '<button type="button" data-state="module" aria-pressed="' +
      state.module +
      '">左侧相机示例</button>'
    );
  return "";
}
function render() {
  const el = document.activeElement;
  const key =
    el && el.closest("#device-screen")
      ? ["data-toggle", "data-tab"].find((k) => el.hasAttribute(k))
      : null;
  const value = key ? el.getAttribute(key) : null;
  mainScreen.innerHTML = sceneHTML(state.scene);
  mainScreen.dataset.scene = state.scene;
  mainScreen.style.touchAction =
    state.scene === "notifications" ? "pan-y" : "none";
  const s =
    scenarios.find(
      (s) => s.id === (state.scene === "app" ? state.returnTo : state.scene),
    ) || scenarios[0];
  document.querySelector("#scene-title").textContent = s.title;
  document.querySelector("#scene-index").textContent =
    "SCREEN " +
    String(scenarios.indexOf(s) + 1).padStart(2, "0") +
    " / " +
    s.id.toUpperCase();
  document.querySelector("#scene-description").textContent = s.desc;
  document.querySelector("#scene-visual").textContent = s.visual;
  document.querySelector("#scene-boundary").textContent = s.boundary;
  document.querySelector("#state-tools").innerHTML = stateTools(state.scene);
  document
    .querySelectorAll(".scene-button")
    .forEach((b) =>
      b.setAttribute("aria-pressed", b.dataset.screen === state.scene),
    );
  const isHub = ["home", "apps", "more"].includes(state.scene);
  document
    .querySelectorAll(
      '[data-review="next"],[data-review="previous"],[data-review="drawer"]',
    )
    .forEach((b) => (b.disabled = !isHub));
  if (key) {
    mainScreen
      .querySelector("[" + key + '="' + value + '"]')
      ?.focus({ preventScroll: true });
  }
}
function show(id) {
  if (!scenarios.some((s) => s.id === id)) return;
  if (["home", "apps", "more"].includes(id)) state.hub = id;
  if (id === "controls" && ["home", "apps", "more"].includes(state.scene))
    state.drawerFrom = state.scene;
  state.scene = id;
  state.app = null;
  state.workApp = null;
  render();
  feedback(
    "已切换：" + scenarios.find((s) => s.id === id).name + "。本地设计演示。",
  );
}
function back() {
  if (state.scene === "app") {
    const target = state.returnTo;
    show(target);
  } else if (state.scene === "controls") {
    show(state.drawerFrom);
  } else {
    show(state.hub);
  }
  feedback("已返回。评审操作不会启动或停止真实设备。");
}
function flip(step) {
  const pages = ["home", "apps", "more"];
  const i = pages.indexOf(state.scene);
  if (i >= 0) show(pages[(i + step + 3) % 3]);
}
function openApp(id) {
  if (id === "works") {
    show("works");
    return;
  }
  state.returnTo = state.scene;
  state.scene = "app";
  state.app = id;
  state.workApp = null;
  render();
  feedback(
    "保留 " + apps[id][0] + " 入口；此处为应用边界示意，未启动真实应用。",
  );
}
document.querySelector("#scene-selectors").innerHTML = scenarios
  .map(
    (s, i) =>
      (s.group ? '<p class="scene-group-label">' + s.group + "</p>" : "") +
      '<button type="button" class="scene-button" data-screen="' +
      s.id +
      '" aria-pressed="false"><span class="number">' +
      String(i + 1).padStart(2, "0") +
      "</span>" +
      s.name +
      "</button>",
  )
  .join("");
document.querySelector("#screen-gallery").innerHTML = scenarios
  .map(
    (s, i) =>
      '<figure class="gallery-card" data-gallery="' +
      s.id +
      '"><div class="device-frame" inert aria-hidden="true"><div class="screen-window"><div class="device-screen">' +
      sceneHTML(s.id) +
      "</div></div></div><figcaption><span>" +
      String(i + 1).padStart(2, "0") +
      "</span>" +
      s.name +
      '<button type="button" data-screen="' +
      s.id +
      '" data-gallery-open>交互预览 ↗</button></figcaption><p>' +
      s.caption +
      "</p></figure>",
  )
  .join("");
document.addEventListener("click", (e) => {
  const b = e.target.closest("button");
  if (!b || b.disabled) return;
  if (b.hasAttribute("data-screen")) {
    const internal = mainScreen.contains(b);
    show(b.dataset.screen);
    if (internal)
      mainScreen
        .querySelector('[data-screen="' + b.dataset.screen + '"]')
        ?.focus({ preventScroll: true });
    if (b.hasAttribute("data-gallery-open")) {
      document.querySelector(".workbench").scrollIntoView({
        behavior: matchMedia("(prefers-reduced-motion:reduce)").matches
          ? "auto"
          : "smooth",
      });
      document
        .querySelector('.scene-button[data-screen="' + b.dataset.screen + '"]')
        .focus({ preventScroll: true });
    }
  } else if (b.hasAttribute("data-app")) {
    openApp(b.dataset.app);
    mainScreen.querySelector("[data-back]")?.focus({ preventScroll: true });
  } else if (b.hasAttribute("data-back")) {
    back();
    mainScreen.querySelector("button")?.focus({ preventScroll: true });
  } else if (b.hasAttribute("data-review")) {
    const a = b.dataset.review;
    if (a === "next") flip(1);
    if (a === "previous") flip(-1);
    if (a === "drawer") show("controls");
    if (a === "back") back();
  } else if (b.hasAttribute("data-toggle")) {
    const id = b.dataset.toggle;
    const t = toggles.find((t) => t.id === id);
    if (t.reserved) {
      feedback(t.label + " · Not Supported。保留原预留项，未切换任何状态。");
      return;
    }
    state[id] = !state[id];
    render();
    feedback(
      t.label +
        " 已" +
        (state[id] ? "开启" : "关闭") +
        "（演示，不影响设备）。",
    );
  } else if (b.hasAttribute("data-tab")) {
    state.tab = b.dataset.tab;
    render();
  } else if (b.hasAttribute("data-state")) {
    const key = b.dataset.state;
    state[key] = !state[key];
    if (key === "worksPaged") {
      state.workPage = 0;
      state.tab = "installed";
      state.worksEmpty = false;
    }
    render();
    document
      .querySelector('[data-state="' + key + '"]')
      .focus({ preventScroll: true });
    feedback(
      key === "agent"
        ? "CFG 仅表示 LLM 与至少一个 IM 通道已配置，不代表手机在线或模型调用成功。"
        : "已切换示例状态；不代表实时硬件数据。",
    );
  } else if (b.hasAttribute("data-workstate")) {
    state.workStatus = b.dataset.workstate;
    state.worksEmpty = false;
    state.workPage = 0;
    render();
    document
      .querySelector('[data-workstate="' + state.workStatus + '"]')
      .focus({ preventScroll: true });
    feedback("Works 状态示例：" + state.workStatus + "。");
  } else if (b.hasAttribute("data-workpage")) {
    state.workPage = Math.max(
      0,
      Math.min(1, state.workPage + Number(b.dataset.workpage)),
    );
    render();
    mainScreen
      .querySelector(".works-pager button:not(:disabled)")
      ?.focus({ preventScroll: true });
  } else if (b.hasAttribute("data-work")) {
    const id = b.dataset.work;
    const current =
      id === "focus" ? state.workStatus : state.otherWorkStates[id];
    const active = ["running", "queued", "stopping"].includes(current);
    if (id === "focus") state.workStatus = active ? "stopped" : "running";
    else state.otherWorkStates[id] = active ? "stopped" : "running";
    if (active) {
      render();
      mainScreen.querySelector('[data-work="' + id + '"]')?.focus();
      feedback("作品已停止（演示）。沿用整行切换启停的行为。");
    } else {
      state.workApp = id;
      state.app = null;
      state.returnTo = "works";
      state.scene = "app";
      render();
      mainScreen.querySelector("[data-back]")?.focus();
      feedback("模拟打开 Works 作品；返回仍回到原来的 Works 标签与分页。");
    }
  }
});
mainScreen.addEventListener("input", (e) => {
  const input = e.target;
  if (!input.matches("[data-level]")) return;
  state[input.dataset.level] = Number(input.value);
  input.setAttribute("aria-valuetext", input.value + "%");
  input.parentElement.querySelector(".level-fill").style.height =
    input.value + "%";
  input.parentElement.querySelector(".level-value").textContent = input.value;
});
mainScreen.addEventListener("change", (e) => {
  if (e.target.matches("[data-level]"))
    feedback(
      e.target.getAttribute("aria-label") + "设为 " + e.target.value + "%。",
    );
});
let pointerStart = null;
mainScreen.addEventListener("pointerdown", (e) => {
  if (e.target.closest("input,.notification-list")) return;
  const r = mainScreen.getBoundingClientRect();
  pointerStart = {
    x: e.clientX,
    y: e.clientY,
    top: ((e.clientY - r.top) * 480) / r.height,
    scene: state.scene,
  };
});
mainScreen.addEventListener("pointercancel", () => {
  pointerStart = null;
});
mainScreen.addEventListener("pointerup", (e) => {
  if (!pointerStart) return;
  const p = pointerStart;
  pointerStart = null;
  const scale = mainScreen.getBoundingClientRect().width / 480;
  const dx = (e.clientX - p.x) / scale,
    dy = (e.clientY - p.y) / scale;
  let changed = false;
  if (["home", "apps", "more"].includes(p.scene)) {
    if (p.top < 60 && dy > 60 && Math.abs(dy) > Math.abs(dx)) {
      show("controls");
      changed = true;
    } else if (Math.abs(dx) > 75 && Math.abs(dx) > Math.abs(dy)) {
      flip(dx < 0 ? 1 : -1);
      changed = true;
    }
  } else if (
    ["controls", "aod", "charging"].includes(p.scene) &&
    dy < -60 &&
    Math.abs(dy) > Math.abs(dx)
  ) {
    back();
    changed = true;
  }
  if (changed) {
    mainScreen.addEventListener(
      "click",
      (event) => {
        event.preventDefault();
        event.stopPropagation();
      },
      { capture: true, once: true },
    );
  }
});
document.addEventListener("keydown", (e) => {
  if (e.target.matches("input,textarea")) return;
  if (e.key === "Escape") {
    back();
    document
      .querySelector('.scene-button[aria-pressed="true"]')
      ?.focus({ preventScroll: true });
  }
  if (
    e.target.closest("#device-screen") &&
    ["ArrowLeft", "ArrowRight"].includes(e.key)
  ) {
    e.preventDefault();
    flip(e.key === "ArrowLeft" ? -1 : 1);
  }
});
const scaleScreens = new ResizeObserver((entries) => {
  entries.forEach((entry) => {
    const screen = entry.target.querySelector(".device-screen");
    if (screen)
      screen.style.transform = "scale(" + entry.contentRect.width / 480 + ")";
  });
});
document
  .querySelectorAll(".screen-window")
  .forEach((el) => scaleScreens.observe(el));
render();
// Reproducible export is opt-in via ?export=sheet and does not affect device behavior.
if (new URLSearchParams(location.search).get("export") === "sheet") {
  document.body.classList.add("export-sheet");
  document.querySelector(".section-heading .eyebrow").textContent =
    "MOSAICO / SYSTEM UI · 06 / DESIGN PROPOSAL";
}
