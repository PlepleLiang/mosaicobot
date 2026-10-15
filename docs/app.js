/* Local interaction prototype. No network, AI, microphone or device APIs are called. */
"use strict";
const $ = (id) => document.getElementById(id);
const icon = (name) =>
  '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
const escapeHTML = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const STORE = "mosaicobot.design.v2";
const catalog = {
  ...Object.fromEntries(centerPresets.map(a => [a.type, {name:a.name, icon:a.icon, description:a.description, capability:"屏幕 + 触摸", color:a.color, centerOnly:true}])),
  snake: {
    name: "贪食蛇",
    icon: "game",
    description: "滑动转向 · 触屏小游戏",
    capability: "屏幕 + 触摸",
    color: "#c0e8b1",
  },
  focus: {
    name: "专注时钟",
    icon: "clock",
    description: "把这段时间，留给一件事",
    capability: "屏幕 + 触摸",
    color: "#ff9b73",
  },
  level: {
    name: "桌面水平仪",
    icon: "level",
    description: "感知倾斜 · 找到一点平衡",
    capability: "屏幕 + IMU",
    color: "#c0e8b1",
  },
};
let stored = [];
try {
  const data = JSON.parse(localStorage.getItem(STORE) || "[]");
  if (Array.isArray(data))
    stored = data.filter(
      (w) =>
        catalog[w.type] &&
        typeof w.id === "string" &&
        Number.isInteger(w.version) &&
        w.params &&
        Array.isArray(w.thread),
    );
} catch {
  /* Private/file-mode storage is optional. */
}
const state = {
  connected: true,
  paired: true,
  autoDiscovery: true,
  tab: "create",
  appGroup: "mine",
  builtinDetail: null,
  builtin: null,
  hubPage: 0,
  builtinReturn: null,
  worksPage: 0,
  builtinValues: { tilt: 0, brightness: 75, playing: false },
  deviceReceipt: null,
  works: stored,
  messages: [],
  selected: null,
  running: null,
  job: null,
  timer: null,
  failNext: false,
  pairing: false,
  simulation: null,
  lastFrame: document.createElement("canvas"),
  receipt: null,
};
state.lastFrame.width = state.lastFrame.height = 480;
const canvas = $("device-canvas");
const ctx = canvas.getContext("2d");
let toastTimer;
let returnFocus;
function persist() {
  try {
    localStorage.setItem(STORE, JSON.stringify(state.works));
  } catch {
    toast("浏览器未允许本地保存；本次会话仍可继续。");
  }
}
function toast(text) {
  $("toast").textContent = text;
  $("toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    $("toast").hidden = true;
  }, 4000);
}
function scrollChat() {
  requestAnimationFrame(() => {
    $("conversation").scrollTop =
      state.messages.length || state.job ? $("conversation").scrollHeight : 0;
  });
}
function showTab(tab) {
  state.tab = tab;
  $("connection-button").hidden = tab === "device";
  $("offline-banner").hidden = tab === "device" || state.connected || !state.paired;
  ["create", "works", "device"].forEach((t) => {
    $(t + "-panel").hidden = t !== tab;
  });
  document.querySelectorAll("[data-tab]").forEach((b) => {
    if (b.dataset.tab === tab) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  });
  if (tab === "works") {
    renderWorks();
    renderBuiltinApps();
  }
  resizeComposer();
}
function addMessage(role, text) {
  state.messages.push({ role, text });
  renderMessages();
}
function renderMessages() {
  $("welcome").hidden = state.messages.length > 0 || !!state.job;
  $("messages").innerHTML = state.messages
    .map((m) =>
      m.role === "user"
        ? '<div class="message user">' + escapeHTML(m.text) + "</div>"
        : '<div class="message agent"><div class="author">' +
          icon("spark") +
          " 手机 Agent</div><p>" +
          escapeHTML(m.text) +
          "</p></div>",
    )
    .join("");
  scrollChat();
}
function initSimulation() {
  state.simulation = {
    snake: [
      { x: 7, y: 7 },
      { x: 6, y: 7 },
      { x: 5, y: 7 },
      { x: 4, y: 7 },
      { x: 4, y: 8 },
      { x: 4, y: 9 },
    ],
    direction: { x: 1, y: 0 },
    next: { x: 1, y: 0 },
    food: { x: 11, y: 5 },
    score: 0,
    paused: false,
    over: false,
    started: false,
    last: performance.now(),
    elapsed: 0,
    time: performance.now(),
    tilt: 0,
  };
  $("tilt-input").value = "0";
  $("tilt-output").textContent = "0°";
}
function rounded(c, x, y, w, h, r, color) {
  c.fillStyle = color;
  c.beginPath();
  c.roundRect(x, y, w, h, r);
  c.fill();
}
function text(
  c,
  value,
  x,
  y,
  size,
  color = "#eef2e9",
  align = "left",
  weight = 400,
  font = "Segoe UI, Microsoft YaHei, sans-serif",
) {
  c.fillStyle = color;
  c.font = weight + " " + size + "px " + font;
  c.textAlign = align;
  c.fillText(value, x, y);
}
function baseScreen(c) {
  c.fillStyle = "#111713";
  c.fillRect(0, 0, 480, 480);
}
function drawApp(c, work, sim) {
  baseScreen(c);
  const color = work.params.color;
  text(
    c,
    work.type === "snake"
      ? "SNAKE / 01"
      : work.type === "focus"
        ? "A LITTLE FOCUS"
        : "FIND YOUR BALANCE",
    31,
    43,
    12,
    "#b7c5b0",
    "left",
    400,
    "Consolas, monospace",
  );
  text(
    c,
    work.type === "snake"
      ? "SCORE " + String(sim?.score || 0).padStart(2, "0")
      : "MOSAICO",
    449,
    43,
    12,
    color,
    "right",
    500,
    "Consolas, monospace",
  );
  if (work.type === "snake") {
    const cell = 24,
      ox = 48,
      oy = 82;
    rounded(c, 34, 68, 412, 350, 14, "#182219");
    c.fillStyle = "#293628";
    for (let x = 0; x < 16; x++)
      for (let y = 0; y < 13; y++)
        c.fillRect(ox + x * cell + 10, oy + y * cell + 10, 2, 2);
    const snake = sim?.snake || [
      { x: 7, y: 7 },
      { x: 6, y: 7 },
      { x: 5, y: 7 },
      { x: 4, y: 7 },
      { x: 4, y: 8 },
      { x: 4, y: 9 },
    ];
    snake.forEach((p, i) =>
      rounded(
        c,
        ox + p.x * cell,
        oy + p.y * cell,
        22,
        22,
        5,
        i === 0 ? "#e0f8c9" : color,
      ),
    );
    const head = snake[0];
    c.fillStyle = "#263d20";
    c.fillRect(ox + head.x * cell + 13, oy + head.y * cell + 5, 3, 3);
    c.fillRect(ox + head.x * cell + 13, oy + head.y * cell + 14, 3, 3);
    const food = sim?.food || { x: 11, y: 5 };
    rounded(
      c,
      ox + food.x * cell + 3,
      oy + food.y * cell + 3,
      16,
      16,
      5,
      "#ff7840",
    );
    if (sim?.over || sim?.paused) {
      rounded(c, 72, 187, 336, 108, 16, "#111713ed");
      text(
        c,
        sim.over ? "这一局，也很不错。" : "休息一下。",
        240,
        231,
        24,
        "#e0efce",
        "center",
        600,
      );
      text(
        c,
        sim.over ? "点击方屏重新开始" : "点击继续，或使用暂停按钮",
        240,
        263,
        13,
        "#b7c5b0",
        "center",
      );
    }
    text(
      c,
      !sim?.started
        ? "滑动开始 · 吃到橙色方块"
        : sim.over
          ? "得分 " + sim.score
          : "滑动 / 方向键控制",
      240,
      440,
      12,
      "#aab99f",
      "center",
    );
  } else if (work.type === "focus") {
    const total = work.params.minutes * 60;
    const remain = Math.max(0, Math.ceil(total - (sim?.elapsed || 0) / 1000));
    c.strokeStyle = "#293228";
    c.lineWidth = 3;
    c.beginPath();
    c.arc(240, 222, 124, 0, Math.PI * 2);
    c.stroke();
    c.strokeStyle = color;
    c.lineWidth = 5;
    c.beginPath();
    c.arc(
      240,
      222,
      124,
      -Math.PI / 2,
      -Math.PI / 2 + Math.PI * 2 * (remain / total),
    );
    c.stroke();
    text(
      c,
      String(Math.floor(remain / 60)).padStart(2, "0") +
        ":" +
        String(remain % 60).padStart(2, "0"),
      240,
      239,
      66,
      "#f0eddf",
      "center",
      400,
      "Consolas, monospace",
    );
    text(
      c,
      remain === 0
        ? "做得很好。"
        : sim?.paused
          ? "暂停，深呼吸。"
          : "只做眼前这一件事。",
      240,
      280,
      16,
      "#b9c4af",
      "center",
    );
    text(
      c,
      "FOCUS IS A SUPERPOWER",
      240,
      394,
      11,
      color,
      "center",
      400,
      "Consolas, monospace",
    );
    text(
      c,
      work.params.minutes + " 分钟 · 点击方屏暂停 / 继续",
      240,
      423,
      12,
      "#9eae93",
      "center",
    );
  } else {
    const tilt = sim?.tilt || 0;
    c.strokeStyle = "#2b3929";
    c.lineWidth = 1;
    [58, 116].forEach((r) => {
      c.beginPath();
      c.arc(240, 220, r, 0, Math.PI * 2);
      c.stroke();
    });
    c.beginPath();
    c.moveTo(88, 220);
    c.lineTo(392, 220);
    c.moveTo(240, 68);
    c.lineTo(240, 372);
    c.stroke();
    rounded(c, 213 + tilt * 3.3, 193, 54, 54, 27, color);
    text(
      c,
      (tilt > 0 ? "+" : "") + tilt.toFixed(1) + "°",
      240,
      390,
      40,
      "#edf2e4",
      "center",
      500,
      "Consolas, monospace",
    );
    text(
      c,
      tilt === 0 ? "刚刚好，保持平衡。" : "慢慢调整，找到水平。",
      240,
      425,
      14,
      "#acbca1",
      "center",
    );
  }
}
function draw() {
  if (state.pairing) {
    baseScreen(ctx);
    text(ctx, "配对你的手机", 240, 170, 28, "#eef2e9", "center", 600);
    text(
      ctx,
      "482 916",
      240,
      254,
      56,
      "#ff9b73",
      "center",
      400,
      "Consolas, monospace",
    );
    text(ctx, "核对号码后，在设备端确认", 240, 310, 16, "#aab99f", "center");
  } else if (state.running) drawApp(ctx, state.running, state.simulation);
  else if (state.builtin) drawBuiltinScreen(ctx, state.builtin);
  else drawHubScreen(ctx);
  if (state.connected && !state.pairing)
    state.lastFrame.getContext("2d").drawImage(canvas, 0, 0);
  captureDeviceReceipt();
  drawMini();
  drawBuiltinPreview();
}
function drawMini() {
  const mini = $("mini-preview");
  if (!mini) return;
  const c = mini.getContext("2d"),
    work = currentWork();
  if (
    work &&
    (state.connected ? state.running?.id : state.deviceReceipt?.work) ===
      work.id &&
    (state.connected
      ? state.running?.version
      : state.deviceReceipt?.version) === work.version
  )
    c.drawImage(state.connected ? canvas : state.lastFrame, 0, 0);
  else if (work) drawApp(c, work, null);
}
function turn(direction) {
  const sim = state.simulation;
  if (state.running?.type !== "snake" || !sim || sim.over || sim.paused) return;
  const vectors = {
    left: { x: -1, y: 0 },
    right: { x: 1, y: 0 },
    up: { x: 0, y: -1 },
    down: { x: 0, y: 1 },
  };
  const v = vectors[direction];
  if (!v || (v.x === -sim.direction.x && v.y === -sim.direction.y)) return;
  sim.next = v;
  sim.started = true;
  draw();
}
function pauseSimulation() {
  if (!state.simulation || state.simulation.over) return;
  state.simulation.paused = !state.simulation.paused;
  state.simulation.time = performance.now();
  state.simulation.last = performance.now();
  updateUI();
}
function tick() {
  const sim = state.simulation,
    work = state.running,
    now = performance.now();
  if (!sim || !work) return;
  const dt = now - sim.time;
  sim.time = now;
  if (work.type === "focus" && !sim.paused)
    sim.elapsed = Math.min(work.params.minutes * 60000, sim.elapsed + dt);
  if (
    work.type === "snake" &&
    sim.started &&
    !sim.paused &&
    !sim.over &&
    now - sim.last >= work.params.speed
  ) {
    sim.last = now;
    sim.direction = sim.next;
    const head = {
      x: sim.snake[0].x + sim.direction.x,
      y: sim.snake[0].y + sim.direction.y,
    };
    const eating = head.x === sim.food.x && head.y === sim.food.y;
    const body = eating ? sim.snake : sim.snake.slice(0, -1);
    if (
      head.x < 0 ||
      head.x >= 16 ||
      head.y < 0 ||
      head.y >= 13 ||
      body.some((p) => p.x === head.x && p.y === head.y)
    ) {
      sim.over = true;
      updateUI();
    } else {
      sim.snake.unshift(head);
      if (!eating) sim.snake.pop();
      else {
        sim.score++;
        const free = [];
        for (let x = 0; x < 16; x++)
          for (let y = 0; y < 13; y++)
            if (!sim.snake.some((p) => p.x === x && p.y === y))
              free.push({ x, y });
        if (free.length)
          sim.food = free[Math.floor(Math.random() * free.length)];
        else {
          sim.over = true;
          updateUI();
        }
      }
    }
  }
  draw();
}
setInterval(tick, 80);
let pointerStart = null;
canvas.addEventListener("pointerdown", (e) => {
  pointerStart = { x: e.clientX, y: e.clientY };
  canvas.setPointerCapture(e.pointerId);
});
canvas.addEventListener("pointercancel", () => {
  pointerStart = null;
});
canvas.addEventListener("pointerup", (e) => {
  if (!pointerStart) return;
  const dx = e.clientX - pointerStart.x,
    dy = e.clientY - pointerStart.y;
  pointerStart = null;
  if (state.running?.type === "snake") {
    if (state.simulation.over) {
      initSimulation();
      updateUI();
      return;
    }
    if (Math.max(Math.abs(dx), Math.abs(dy)) > 12)
      turn(
        Math.abs(dx) > Math.abs(dy)
          ? dx > 0
            ? "right"
            : "left"
          : dy > 0
            ? "down"
            : "up",
      );
    else if (state.simulation.paused) pauseSimulation();
    else state.simulation.started = true;
  } else if (state.running?.type === "focus") pauseSimulation();
});
canvas.addEventListener("keydown", (e) => {
  const directions = {
    ArrowUp: "up",
    ArrowDown: "down",
    ArrowLeft: "left",
    ArrowRight: "right",
  };
  if (directions[e.key]) {
    e.preventDefault();
    turn(directions[e.key]);
  } else if (e.key === " ") {
    e.preventDefault();
    pauseSimulation();
  } else if (e.key === "Enter" && state.running) {
    initSimulation();
    updateUI();
  }
});
$("tilt-input").addEventListener("input", (e) => {
  if (state.simulation) {
    state.simulation.tilt = Number(e.target.value);
    $("tilt-output").textContent = e.target.value + "°";
    draw();
  }
});
$("pause-button").addEventListener("click", pauseSimulation);
$("restart-button").addEventListener("click", () => {
  if (state.running) {
    initSimulation();
    updateUI();
  }
});
function resizeComposer() {
  const input = $("prompt-input");
  $("send-button").disabled = activeJob() || !input.value.trim();
  if (!input.clientWidth) return;
  input.style.height = "56px";
  input.style.height = Math.min(78, Math.max(56, input.scrollHeight)) + "px";
}
$("prompt-input").addEventListener("input", resizeComposer);
$("prompt-input").addEventListener("focus", resizeComposer);
$("composer").addEventListener("submit", (e) => {
  e.preventDefault();
  submitIntent($("prompt-input").value);
});
$("prompt-input").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    submitIntent(e.target.value);
  }
});
$("connection-button").addEventListener("click", showConnection);
$("manage-connection").addEventListener("click", showConnection);
$("reconnect-inline").addEventListener("click", showConnection);
$("new-button").addEventListener("click", newCreation);
$("scenario-button").addEventListener("click", showScenarios);
$("device-talk").addEventListener("click", showDeviceChat);
$("voice-button")?.addEventListener("click", () => {
  openModal(
    "说出一个想法。",
    '<p>语音输入演示：不会访问麦克风。正式产品应支持录音权限、转写预览与发送前修改。</p><div class="voice-transcript">“生成贪食蛇”</div><div class="modal-actions"><button class="secondary-button" id="voice-cancel">取消</button><button class="primary-button" id="voice-use">使用这段文字</button></div>',
    "VOICE / DEMO",
  );
  modalAction("voice-cancel", closeModal);
  modalAction("voice-use", () => {
    closeModal();
    $("prompt-input").value = "生成贪食蛇";
    $("prompt-input").focus();
  });
});
$("reset-button").addEventListener("click", () => {
  openModal(
    "重新开始体验？",
    '<p>只重置本原型的作品、对话、模拟设备状态和自动运行选项，不会访问真实硬件。</p><div class="modal-actions"><button class="secondary-button" id="reset-cancel">保留体验</button><button class="danger-button" id="reset-confirm">重置演示</button></div>',
  );
  modalAction("reset-cancel", closeModal);
  modalAction("reset-confirm", () => {
    clearTimeout(state.timer);
    state.job = null;
    state.works = [];
    deviceWorks = null;
    resetAppCenter();
    state.messages = [];
    state.selected = null;
    state.running = null;
    state.simulation = null;
    state.connected = true;
    state.paired = true;
    state.failNext = false;
    state.receipt = null;
    state.builtin = null;
    state.builtinDetail = null;
    state.builtinReturn = null;
    state.hubPage = 0;
    state.worksPage = 0;
    state.appGroup = "mine";
    state.deviceReceipt = null;
    state.builtinValues = { tilt: 0, brightness: 75, playing: false };
    $("auto-run").checked = false;
    $("prompt-input").value = "";
    persist();
    closeModal();
    renderMessages();
    showTab("create");
    updateUI();
  });
});
document.addEventListener("click", (e) => {
  const tab = e.target.closest("[data-tab]");
  if (tab) showTab(tab.dataset.tab);
  const prompt = e.target.closest("[data-prompt]");
  if (prompt) { showTab("create"); $("prompt-input").value = prompt.dataset.prompt; resizeComposer(); $("prompt-input").focus(); }
  const edit = e.target.closest("[data-edit-prompt]");
  if (edit) { $("prompt-input").value = edit.dataset.editPrompt; resizeComposer(); $("prompt-input").focus(); }
  const dir = e.target.closest("[data-direction]");
  if (dir) turn(dir.dataset.direction);
  const action = e.target.closest("[data-job-action]");
  if (action && state.job) {
    if (action.dataset.jobAction === "cancel") cancelJob();
    else if (action.dataset.jobAction === "retry") {
      state.job.status = "building";
      state.job.detail = "从失败步骤继续，保留已有生成结果。";
      state.job.attempt++;
      updateUI();
      queueStep();
    } else if (action.dataset.jobAction === "deploy")
      confirmDeployment(state.job.work);
  }
  const result = e.target.closest("[data-result-action]");
  if (result) {
    const w = currentWork();
    if (!w) return;
    const a = result.dataset.resultAction;
    if (a === "details") showDetails(w);
    if (a === "run") confirmDeployment(w);
    if (a === "stop" && state.connected) {
      if (!canNavigateHardware("phone")) return;
      returnToHub(0, "phone");
      addMessage(
        "agent",
        "设备已停止这件作品并返回时钟首页（模拟）。应用仍在「我的创作」里，随时可以再运行。",
      );
      updateUI();
    }
  }
  const wb = e.target.closest("[data-work-action]");
  if (wb) {
    const w = state.works.find((x) => x.id === wb.dataset.id);
    if (!w) return;
    const a = wb.dataset.workAction;
    if (a === "delete") deleteWork(w);
    if (a === "run") confirmDeployment(w);
    if (a === "open") {
      if (state.job) {
        toast("先完成或取消当前创作，再切换作品。");
        return;
      }
      state.selected = w.id;
      state.messages = [...w.thread];
      renderMessages();
      showTab("create");
      updateUI();
      scrollChat();
    }
  }
});
document
  .querySelectorAll("svg:not(.svg-defs)")
  .forEach((el) => el.setAttribute("aria-hidden", "true"));
initBuiltinNavigation();
renderMessages();
updateUI();
initAppCenter();
function renderResult() {
  const work = currentWork();
  const waiting = state.job && ["ready", "waiting"].includes(state.job.status);
  $("result-card").hidden = !work || (!!state.job && !waiting);
  if (!work || (state.job && !waiting)) return;
  const running = state.connected
    ? state.running?.id === work.id && state.running.version === work.version
    : state.deviceReceipt?.work === work.id &&
      state.deviceReceipt?.version === work.version;
  const status = catalog[work.type].centerOnly ? "已添加 · 方案预览" : !state.connected
    ? "连接中断 · 上次状态"
    : running
      ? "已在 Mosaico 上运行"
      : work.installed
        ? "已保存 · 当前未运行"
        : "已准备 · 尚未部署";
  const copy =
    catalog[work.type].centerOnly ? "应用中心预置方案 · Lua 待实现" :
    work.type === "snake"
      ? "滑动 / 方向键控制，点击屏幕开始"
      : work.type === "focus"
        ? work.params.minutes + " 分钟 · 可暂停与重开"
        : "模拟 IMU 姿态 · 滑杆调节";
  const suggestions =
    catalog[work.type].centerOnly ? [] :
    work.type === "snake"
      ? ["速度慢一点", "把蛇改成橙色"]
      : work.type === "focus"
        ? ["改成 15 分钟", "改成 5 分钟"]
        : ["改成橙色", "改成绿色"];
  $("result-card").innerHTML =
    '<div class="result-header">' +
    icon(state.connected ? "check" : "wifi") +
    "<span>" +
    status +
    '</span><small>模拟设备状态</small></div><div class="result-body"><div class="mini-placeholder">' + icon(catalog[work.type].icon) + '<small>LUA 应用</small></div><div><h3>' +
    catalog[work.type].name +
    "</h3><p>" +
    copy +
    '</p><p class="version">v' +
    work.version +
    " · " +
    catalog[work.type].capability +
    '</p></div></div><div class="result-actions"><button class="primary-button" data-result-action="' +
    (running ? "stop" : "run") +
    '" ' +
    (!state.connected ? "disabled" : "") +
    ">" +
    icon(running ? "stop" : "play") +
    (running ? "停止运行" : work.installed ? "在设备上运行" : "部署并运行") +
    '</button><button class="secondary-button" data-result-action="details">' +
    icon("code") +
    '作品详情</button></div><div class="follow-up">' +
    suggestions
      .map((p) => '<button data-edit-prompt="' + p + '">' + p + "</button>")
      .join("") +
    '</div><div class="result-footer">' +
    (state.connected
      ? "Mosaico · 7C4A / " + (state.receipt || "已保存") + " · 作品已自动保存"
      : "连接已中断，不代表当前设备实时状态") +
    "</div>";
  drawMini();
}
function renderWorks() {
  $("work-count").textContent = state.works.length;
  if (!state.works.length) {
    $("works-list").innerHTML =
      '<div class="empty-works">' +
      icon("spark") +
      '<h3>第一个想法，就从这里开始</h3><p>创造的应用会自动保存在这里。<br>不用另存为，也不用记住文件名。</p><button class="primary-button" data-prompt="生成贪食蛇">创造第一件作品</button></div>';
    return;
  }
  $("works-list").innerHTML = state.works
    .map(
      (w) =>
        '<article class="work-item"><div class="work-top"><span class="work-icon">' +
        icon(catalog[w.type].icon) +
        "</span><div><h3>" +
        escapeHTML(w.title || catalog[w.type].name) +
        "</h3><p>v" +
        w.version +
        " · " +
        (catalog[w.type].centerOnly ? "方案预览 · 待实现" : !state.connected
          ? "设备状态待同步"
          : state.running?.id === w.id && state.running.version === w.version
            ? "设备运行中"
            : w.installed
              ? "已安装"
              : "待部署") +
        '</p></div></div><div class="job-actions"><button class="secondary-button" data-work-action="open" data-id="' +
        escapeHTML(w.id) +
        '">继续创作</button><button class="primary-button" data-work-action="run" data-id="' +
        escapeHTML(w.id) +
        '" ' +
        (activeJob() ? "disabled" : "") +
        '>运行</button><button class="icon-button" data-work-action="delete" data-id="' +
        escapeHTML(w.id) +
        '" aria-label="删除' +
        catalog[w.type].name +
        '">' +
        icon("trash") +
        "</button></div></article>",
    )
    .join("");
}
function showDetails(work) {
  if (catalog[work.type].centerOnly) { showCenterPlan(work); return; }
  const nativeNote =
    work.type === "snake"
      ? "Vibe 仓库现有贪食蛇是原生 C / Raylib 应用。此处展示的是拟议的运行时版本，不是直接安装那个固件。"
      : "展示的是产品级作品契约草案，不是已有设备 API。";
  openModal(
    catalog[work.type].name + " · v" + work.version,
    "<p>" +
      nativeNote +
      '</p><table class="detail-table"><tr><th>执行位置</th><td>Mosaico 设备</td></tr><tr><th>生成与编排</th><td>手机 Agent（模拟）</td></tr><tr><th>所需能力</th><td>' +
      catalog[work.type].capability +
      "</td></tr><tr><th>版本状态</th><td>" +
      (work.installed ? "模拟安装已确认" : "等待部署") +
      "</td></tr><tr><th>作品标识</th><td>" +
      escapeHTML(work.id) +
      '</td></tr></table><pre class="code-block">' +
      escapeHTML(
        JSON.stringify(
          {
            app_id: work.id,
            version: work.version,
            runtime: "lua (proposed)",
            permissions:
              work.type === "level" ? ["display", "imu"] : ["display", "touch"],
            parameters: work.params,
          },
          null,
          2,
        ),
      ) +
      "</pre>" +
      (work.previous
        ? '<div class="modal-actions"><button class="secondary-button" id="rollback-work">恢复到 v' +
          work.previous.version +
          "</button></div>"
        : ""),
    "ARTIFACT / PROPOSED CONTRACT",
  );
  modalAction("rollback-work", () => {
    if (activeJob()) {
      toast("请先完成当前任务。");
      return;
    }
    if (!state.connected) {
      toast("请先重新连接设备。");
      return;
    }
    const previous = work.previous;
    closeModal();
    confirmDeployment({
      ...work,
      version: previous.version,
      params: { ...previous.params },
      previous: null,
      installed: false,
    });
  });
}
function deleteWork(work) {
  if (state.job?.work.id === work.id) {
    toast("请先取消这件作品正在进行的任务。");
    return;
  }
  openModal(
    "删除「" + catalog[work.type].name + "」？",
    '<p>仅删除手机本地作品和版本记录，不停止运行、不卸载设备文件。系统应用不会受影响。此操作不能撤销；本页为模拟。</p><div class="modal-actions"><button class="secondary-button" id="keep-work">保留作品</button><button class="danger-button" id="confirm-delete">确认删除</button></div>',
  );
  modalAction("keep-work", closeModal);
  modalAction("confirm-delete", () => {
    state.works = state.works.filter((w) => w.id !== work.id);
    if (state.selected === work.id) {
      state.selected = null;
      state.messages = [];
      renderMessages();
    }
    persist();
    closeModal();
    updateUI();
    toast("作品已删除，系统应用保持不变。");
  });
}
let connectionTimer;
function showConnection() {
  openModal("管理连接",
    '<p>同一 Wi-Fi 下自动发现并连接。此处仅演示，不扫描真实网络。</p><label class="setting-row"><span>自动发现并连接<small>打开 App 或网络变化时扫描</small></span><input id="auto-discovery" type="checkbox" role="switch" ' + (state.autoDiscovery ? 'checked' : '') + '></label><div class="pair-device">' + icon("device") + '<span><b>Mosaico</b><small id="scan-status">' + (state.connected ? '已连接 · 局域网（模拟）' : '尚未连接') + '</small></span></div><button class="primary-button full-width" id="discover-device">扫描并自动连接</button><details><summary>找不到设备？手动输入地址</summary><label class="modal-field-label" for="manual-address">局域网 IPv4 地址</label><input id="manual-address" class="modal-input" placeholder="192.168.1.80"><button class="secondary-button" id="manual-connect">连接此地址（模拟）</button></details><p>扫描只读接口，不上传或运行作品；尚无认证配对，仅在可信局域网使用。</p><div class="modal-actions"><button class="secondary-button" id="disconnect-device">模拟断开连接</button><button class="primary-button" id="connection-done">完成</button></div>', "CONNECTION / SIMULATION");
  let scanning = false;
  $("auto-discovery").addEventListener("change", e => { state.autoDiscovery = e.target.checked; });
  modalAction("discover-device", () => {
    scanning = !scanning;
    clearTimeout(connectionTimer);
    $("discover-device").textContent = scanning ? "停止扫描" : "扫描并自动连接";
    $("scan-status").textContent = scanning ? "正在寻找同一网络的设备…" : "已停止扫描";
    if(scanning) connectionTimer = setTimeout(() => {
      if(!$("scan-status") || !$("modal").open) return;
      scanning = false; setConnection(true);
      $("scan-status").textContent = "已连接 Mosaico · 接口验证通过（模拟）";
      $("discover-device").textContent = "扫描并自动连接";
    }, 900);
  });
  modalAction("manual-connect", () => {
    const address = $("manual-address").value.trim().replace(/^http:\/\//, "");
    if(!/^(10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)$/.test(address) || address.split('.').some(n => Number(n)>255)) { toast("请输入有效的私有 IPv4 地址"); return; }
    clearTimeout(connectionTimer); setConnection(true); closeModal(); toast("已连接模拟设备，没有访问该地址。");
  });
  modalAction("disconnect-device", () => { clearTimeout(connectionTimer); setConnection(false); closeModal(); });
  modalAction("connection-done", () => { clearTimeout(connectionTimer); closeModal(); });
}
function newCreation() {
  const perform = () => {
    if (state.job) cancelJob();
    state.messages = [];
    state.selected = null;
    state.job = null;
    $("prompt-input").value = "";
    renderMessages();
    showTab("create");
    updateUI();
    $("conversation").scrollTop = 0;
  };
  if (state.job || $("prompt-input").value.trim()) {
    openModal(
      "开始一个新想法？",
      '<p>正在进行的创作会取消，未发送的输入会清空。已保存作品和设备当前运行不受影响。</p><div class="modal-actions"><button class="secondary-button" id="new-cancel">继续当前创作</button><button class="primary-button" id="new-confirm">开始新创作</button></div>',
    );
    modalAction("new-cancel", closeModal);
    modalAction("new-confirm", () => {
      closeModal();
      perform();
    });
  } else perform();
}
function showScenarios() {
  openModal(
    "看看每一步如何衔接。",
    '<p>这些是设计评审工具，不属于手机产品导航。</p><div class="scenario-list"><button id="scene-pair"><b>首次连接</b><small>保留作品，模拟一台尚未连接的设备</small></button><button id="scene-loss"><b>' +
      (state.connected ? "模拟断连" : "重新连接") +
      '</b><small>不清空对话，不停止设备上的应用</small></button><button id="scene-fail"><b>下一次检查失败</b><small>验证失败反馈与原步骤重试</small></button><button id="scene-native"><b>原生应用路径说明</b><small>为何 Vibe 固件更新不是热安装应用</small></button></div>',
    "PROTOTYPE CONTROLS",
  );
  modalAction("scene-pair", () => {
    closeModal();
    state.paired = false;
    setConnection(false);
    showConnection();
  });
  modalAction("scene-loss", () => {
    closeModal();
    if (state.connected) setConnection(false);
    else showConnection();
  });
  modalAction("scene-fail", () => {
    state.failNext = true;
    closeModal();
    toast("已设置：下一次能力检查失败。试着创造或修改作品。");
  });
  modalAction("scene-native", () =>
    openModal(
      "两种应用，两条部署路径。",
      '<p>轻量行为优先扩展 Claw 的 Lua / Works 运行时。手机端包校验、授权、原子安装和运行回执仍需开发。</p><p>Vibe 的原生 C / Raylib 应用需要编译与固件更新，可能重启并切换当前固件；不能承诺与 Claw 主屏应用直接共存。</p><p>手机可负责任务编排，原生构建可委托受信的主机 / 服务。使用 Vibe Mode 和 mosaico.py 的现有安全更新契约，不直接刷写偏移。</p><a class="documentation-link" href="interaction.md">查看完整方案 ' +
        icon("arrow") +
        "</a>",
      "IMPLEMENTATION BOUNDARY",
    ),
  );
}
function showDeviceChat() {
  openModal(
    "和设备 Claw 对话",
    '<p>执行者：设备上的 Claw。此入口不生成应用。演示支持询问「电量」或「正在运行什么」。</p><label class="modal-field-label" for="claw-input">问问设备</label><input id="claw-input" class="modal-input" value="你正在运行什么？" maxlength="200"><p id="claw-reply" role="status"></p><button id="claw-send" class="primary-button full-width">发送给设备 Claw</button>',
    "DEVICE CLAW / SIMULATION",
  );
  modalAction("claw-send", () => {
    const text = $("claw-input").value;
    $("claw-reply").textContent = !state.connected
      ? "连接已断开。请重连后再询问；这里不会假装已送达。"
      : /电量/.test(text)
        ? "设备 Claw：电量 86%（演示数据，非传感器读数）。"
        : /运行|做什么/.test(text)
          ? "设备 Claw：" +
            (state.running
              ? "当前运行「" +
                catalog[state.running.type].name +
                "」v" +
                state.running.version +
                "（模拟）。"
              : "当前显示「" + runtimeName() + "」（模拟）。")
          : "这个问题未接入演示。正式产品通过独立的设备会话处理。";
  });
}
function openModal(title, html, eyebrow = "MOSAICOBOT") {
  clearTimeout(connectionTimer);
  if (!$("modal").open) returnFocus = document.activeElement;
  $("modal-title").textContent = title;
  $("modal-content").innerHTML = html;
  $("modal-eyebrow").textContent = eyebrow;
  if (!$("modal").open) $("modal").showModal();
  $("modal-close").focus();
}
function closeModal() {
  clearTimeout(connectionTimer);
  $("modal").close();
}
$("modal").addEventListener("close", () => {
  state.pairing = false;
  draw();
  if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true });
});
$("modal-close").addEventListener("click", closeModal);
$("modal").addEventListener("click", (e) => {
  if (e.target === $("modal")) {
    const r = $("modal").getBoundingClientRect();
    if (
      e.clientX < r.left ||
      e.clientX > r.right ||
      e.clientY < r.top ||
      e.clientY > r.bottom
    )
      closeModal();
  }
});
function modalAction(id, fn) {
  $(id)?.addEventListener("click", fn);
}
function currentWork() {
  return state.works.find((w) => w.id === state.selected);
}
function activeJob() {
  return (
    state.job &&
    !["failed", "ready", "waiting", "cancelled"].includes(state.job.status)
  );
}
function updateUI() {
  $("connection-detail").textContent = state.connected
    ? "同一 Wi-Fi · 已连接"
    : state.paired
      ? "连接已断开 · 上下文已保留"
      : "添加一台设备，开始创造";
  $("connection-state").innerHTML =
    "<i></i> " + (state.connected ? "在线" : "离线");
  $("connection-state").style.color = state.connected
    ? "var(--green)"
    : "var(--secondary)";
  $("offline-banner").hidden = state.tab === "device" || state.connected || !state.paired;
  $("connection-name").textContent = state.paired
    ? "我的 Mosaico"
    : "连接你的 Mosaico";
  $("device-page-status").textContent = state.connected
    ? "已连接 / 局域网（演示）"
    : "离线 / 不等于停止运行";
  $("send-button").disabled =
    !!state.job &&
    state.job.status !== "failed" &&
    state.job.status !== "cancelled" &&
    !["ready", "waiting"].includes(state.job.status);
  $("new-button").disabled = false;
  $("composer-label").textContent = state.selected ? "接下来，让它变得更好" : "你想让 Mosaico 做什么？";
  $("composer-hint").innerHTML = "<i></i> " + (activeJob() ? "Agent 正在创造" : "手机 Agent");
  resizeComposer();
  $("journey-build").classList.toggle("active", !!state.job);
  $("journey-run").classList.toggle("active", !!state.running && !activeJob());
  const runtime = state.running;
  $("hardware-status").textContent = runtime
    ? catalog[runtime.type].name +
      " · " +
      (state.simulation?.paused
        ? "已暂停"
        : state.simulation?.over
          ? "本局结束"
          : "设备运行中")
    : state.builtin
      ? getBuiltin(state.builtin).name + " · 已在设备打开"
      : (state.hubPage ? "应用主屏 " + state.hubPage : "时钟首页") +
        " · 原有应用随时可用";
  $("hardware-dot").style.color = runtime ? "var(--green)" : "var(--secondary)";
  if (!state.connected) {
    $("feedback-title").textContent = "连接断开，不等于身体停止";
    $("feedback-copy").textContent =
      "手机保留最后回执；右侧模拟独立运行的设备。";
    $("feedback-badge").textContent = "OFFLINE";
  } else if (runtime) {
    $("feedback-title").textContent =
      "设备已回应：" + catalog[runtime.type].name;
    $("feedback-copy").textContent =
      "应用 v" + runtime.version + " · 任务 running（模拟，不代表首帧验收）";
    $("feedback-badge").textContent = "RUNNING";
  } else if (state.builtin) {
    $("feedback-title").textContent = "设备已打开：" + runtimeName();
    $("feedback-copy").textContent =
      "Claw 原有应用 · 本地模拟回执，未连接真实硬件";
    $("feedback-badge").textContent = "BUILT-IN";
  } else {
    $("feedback-title").textContent = "时钟首页，也是原有应用的起点";
    $("feedback-copy").textContent =
      "Camera、CLAW、Works 与两页应用入口均已保留（模拟）。";
    $("feedback-badge").textContent = "LAN";
  }
  $("physical-controls").hidden = !runtime || runtime.type === "level";
  $("direction-pad").hidden = runtime?.type !== "snake";
  $("level-controls").hidden = runtime?.type !== "level";
  $("pause-button").innerHTML =
    icon(state.simulation?.paused ? "play" : "pause") +
    "<span>" +
    (state.simulation?.paused ? "继续" : "暂停") +
    "</span>";
  draw();
  renderJob();
  renderResult();
  renderWorks();
  renderBuiltinApps();
  renderHardwareNavigation();
}
function renderJob() {
  const job = state.job;
  $("job-card").hidden = !job || ["ready", "waiting"].includes(job.status);
  if (!job) return;
  const labels = [
    "生成应用行为",
    "检查设备能力",
    "准备可运行的作品",
    "部署到 Mosaico",
    "确认设备开始运行",
  ];
  const titles = {
    failed: "这次没成功，想法还在。",
    ready: "作品准备好了。",
    waiting: "等身体重新连上。",
    cancelled: "创作已取消。",
  };
  const title =
    titles[job.status] ||
    (job.step < 3 ? "正在把想法变成应用" : "让作品在 Mosaico 上发生");
  const stopped = ["failed", "ready", "waiting", "cancelled"].includes(
    job.status,
  );
  let actions = "";
  if (job.status === "failed")
    actions =
      '<button class="primary-button" data-job-action="retry">' +
      icon("refresh") +
      "重试这一步</button>";
  if (job.status === "ready" || job.status === "waiting")
    actions =
      '<button class="primary-button" data-job-action="deploy">' +
      icon("play") +
      (state.connected ? "部署并运行" : "连接设备") +
      "</button>";
  if (job.status !== "cancelled")
    actions +=
      '<button class="secondary-button" data-job-action="cancel">' +
      (stopped ? "稍后再说" : "取消") +
      "</button>";
  $("job-card").innerHTML =
    '<div class="card-overline"><span>PHONE AGENT / ' +
    catalog[job.work.type].capability +
    "</span><span>v" +
    job.work.version +
    "</span></div><h3>" +
    title +
    '</h3><div class="job-steps">' +
    labels
      .map(
        (label, i) =>
          '<div class="job-step ' +
          (i < job.step
            ? "done"
            : i === job.step
              ? job.status === "failed"
                ? "failed"
                : stopped
                  ? ""
                  : "current"
              : "") +
          '"><span class="step-indicator">' +
          (i < job.step ? icon("check") : i + 1) +
          "</span><span>" +
          label +
          "</span></div>",
      )
      .join("") +
    '</div><p class="job-detail">' +
    escapeHTML(job.detail) +
    '</p><div class="job-actions">' +
    actions +
    "</div>";
}
function makeIntent(text) {
  const old = currentWork();
  let type = /贪[食吃]蛇|snake/i.test(text)
    ? "snake"
    : /专注|番茄|时钟|计时/.test(text)
      ? "focus"
      : /水平仪|imu|倾斜/i.test(text)
        ? "level"
        : null;
  const edit = /改|慢|快|橙|绿/.test(text);
  if (!type && edit && old && !catalog[old.type].centerOnly) type = old.type;
  if (!type) return null;
  const same = old && old.type === type;
  const params = same
    ? { ...old.params }
    : { speed: 260, color: catalog[type].color, minutes: 25 };
  if (/慢/.test(text)) params.speed = 430;
  if (/快/.test(text)) params.speed = 160;
  if (/橙/.test(text)) params.color = "#ff9b73";
  if (/绿/.test(text)) params.color = "#c0e8b1";
  const duration = text.match(/(\d+)\s*分钟/);
  if (duration && type === "focus")
    params.minutes = Math.max(1, Math.min(120, Number(duration[1])));
  return {
    id: same ? old.id : "work-" + Date.now(),
    type,
    version: same ? old.version + 1 : 1,
    params,
    installed: false,
    thread: state.messages,
    previous: same
      ? old.installed
        ? { version: old.version, params: { ...old.params } }
        : old.previous || null
      : null,
  };
}
function submitIntent(text) {
  if (!text.trim()) {
    $("prompt-input").focus();
    toast("先写下一个想法吧。");
    return;
  }
  if (state.job && !["failed", "cancelled", "ready", "waiting"].includes(state.job.status)) {
    toast("先完成或取消当前创作，避免覆盖正在进行的任务。");
    return;
  }
  showTab("create");
  addMessage("user", text.trim());
  $("prompt-input").value = "";
  if (/相机|录音|电机|马达|执行器/.test(text)) {
    addMessage(
      "agent",
      "这个想法涉及额外硬件权限。此原型暂不执行相机、录音或外部执行器行为；正式产品需要先展示行为范围并单独授权。",
    );
    return;
  }
  const work = makeIntent(text);
  if (!work) {
    addMessage(
      "agent",
      "我记下了你的想法。当前是交互原型，只演示贪食蛇、专注时钟和水平仪，以及速度、颜色、专注时长的修改；不会把任意文本假装生成成功。试试「生成贪食蛇」？",
    );
    return;
  }
  if (/碰撞音效|声音|天气|联机/.test(text)) {
    addMessage(
      "agent",
      "这项扩展还没有接入演示。我不会忽略它继续显示成功；可以先试试改变颜色、速度或专注时长。",
    );
    return;
  }
  state.selected = work.id;
  addMessage(
    "agent",
    work.version > 1
      ? "保留当前版本，生成 v" +
          work.version +
          "。运行新版本前请先停止旧版，两个版本都保留。"
      : "好，让 Mosaico 变成" +
          catalog[work.type].name +
          "。我会检查" +
          catalog[work.type].capability +
          "，生成并保存后，请确认部署运行。",
  );
  work.thread = [...state.messages];
  state.job = {
    work,
    step: 0,
    status: "building",
    detail: "这里演示手机 Agent 的生成过程，不调用真实 AI。",
    attempt: 0,
  };
  updateUI();
  scrollChat();
  queueStep();
}
function queueStep() {
  clearTimeout(state.timer);
  const job = state.job,
    attempt = job.attempt;
  state.timer = setTimeout(
    () => {
      if (state.job !== job || job.attempt !== attempt || !activeJob()) return;
      if (job.step === 1 && state.failNext) {
        state.failNext = false;
        job.status = "failed";
        job.detail =
          "模拟：能力检查没有通过。设备上的旧版本仍保留；可从检查步骤重试。";
        updateUI();
        scrollChat();
        return;
      }
      job.step++;
      if (job.step === 3) {
        const existing = state.works.findIndex((w) => w.id === job.work.id);
        if (existing < 0) state.works.unshift(job.work);
        else state.works[existing] = job.work;
        persist();
        if (!state.connected || !$("auto-run").checked) {
          job.status = state.connected ? "ready" : "waiting";
          job.detail = state.connected
            ? "作品已保存。请先查看源码，确认后发布到 Works 并启动；不覆盖系统应用。"
            : "应用已保存在手机。连接恢复后，由你确认继续部署。";
          updateUI();
          scrollChat();
          return;
        }
      }
      if (job.step >= 5) {
        finishDeployment(job.work);
        return;
      }
      job.detail =
        [
          "",
          "",
          "屏幕与所需能力检查通过（模拟）。",
          "正在传输作品；上传结束不等于已经运行。",
          "已传输并发布，正在等待设备任务变为 running（模拟，不代表首帧或行为验收）。",
        ][job.step] || "检查当前设备的屏幕、输入和运行能力。";
      updateUI();
      scrollChat();
      queueStep();
    },
    [850, 950, 650, 1000, 800][job.step] || 700,
  );
}
function confirmDeployment(work) {
  if (catalog[work.type].centerOnly) { showCenterPlan(work); return; }
  if (!state.connected) { showConnection(); return; }
  if(state.running && (state.running.id !== work.id || state.running.version !== work.version)) {
    toast("请先在设备上停止当前作品，再运行新版本。"); return;
  }
  openModal("在 Mosaico 上运行？", '<p>将此版本发布到 Works 并启动，不覆盖系统应用。请先检查源码；Lua 校验不是安全沙箱。仅在可信局域网使用。</p><p>启动后读取设备任务状态；running 不等于首帧、正确性或硬件行为已验收。本页全程模拟。</p><div class="modal-actions"><button class="secondary-button" id="run-cancel">取消</button><button class="primary-button" id="run-confirm">确认部署并运行</button></div>');
  modalAction("run-cancel", closeModal);
  modalAction("run-confirm", () => { closeModal(); beginDeployment(work); });
}
function beginDeployment(work) {
  if (!state.connected) {
    showConnection();
    return;
  }
  if (state.job && state.job.work.id !== work.id) {
    toast("先完成或取消当前作品的任务，再切换运行目标。");
    return;
  }
  if (activeJob()) {
    toast("当前有任务正在进行，请先等待或取消。");
    return;
  }
  if (state.selected !== work.id) {
    state.messages = [...work.thread];
    renderMessages();
  }
  state.selected = work.id;
  state.job = {
    work,
    step: 3,
    status: "deploying",
    detail: "沿用这件作品继续部署；不重新生成。",
    attempt: (state.job?.attempt || 0) + 1,
  };
  showTab("create");
  updateUI();
  scrollChat();
  queueStep();
}
function finishDeployment(work) {
  work.installed = true;
  registerDeviceWork(work);
  work.thread = [...state.messages];
  const i = state.works.findIndex((w) => w.id === work.id);
  if (i < 0) state.works.unshift(work);
  else state.works[i] = work;
  state.job = null;
  state.selected = work.id;
  state.running = { ...work, params: { ...work.params } };
  state.builtin = null;
  state.builtinReturn = null;
  state.hubPage = 0;
  state.receipt = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  initSimulation();
  addMessage(
    "agent",
    "已经在 Mosaico 上运行了。" +
      (work.type === "snake"
        ? "直接在右侧方屏滑动，或用方向键玩一局。还想改点什么？"
        : work.type === "focus"
          ? "现在，把这段时间留给一件事。也可以继续修改时长。"
          : "拖动方屏下方的倾斜滑杆，看看身体如何回应。"),
  );
  work.thread = [...state.messages];
  persist();
  updateUI();
  scrollChat();
}
function cancelJob() {
  if (!state.job) return;
  clearTimeout(state.timer);
  state.job.attempt++;
  state.job = null;
  addMessage("agent", "已取消当前任务。已准备的作品、旧版本和对话仍保留。");
  updateUI();
}
function setConnection(connected) {
  state.connected = connected;
  if (!connected && state.job && state.job.step >= 3 && activeJob()) {
    clearTimeout(state.timer);
    state.job.attempt++;
    state.job.status = "waiting";
    state.job.step = 3;
    state.job.detail =
      "部署链路中断。保留作品与操作上下文，重连后先核对，再继续本次部署（模拟）。";
  }
  if (connected) state.paired = true;
  updateUI();
}
