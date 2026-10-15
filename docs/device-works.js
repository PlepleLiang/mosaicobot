/* UX review only: device packages are independent of phone source records. */
let deviceWorks = null;
let deletingDeviceWork = false;
function deviceWorkRecords() {
  if (deviceWorks === null) {
    deviceWorks = builtinApps.filter(a => a.origin === "works").map(a => ({
      id: a.id, name: a.name, version: 1, preset: true, app: a,
    }));
    [1, 2, 3].forEach(version => deviceWorks.push({
      id: "demo-run-v" + version, name: "极速跑酷", version, preset: false,
      type: "snake", params: {color: "#c0e8b1"}, thread: [], source: "已部署",
    }));
    state.works.filter(w => w.installed).forEach(registerDeviceWork);
  }
  return deviceWorks;
}
function registerDeviceWork(work) {
  const records = deviceWorkRecords();
  const id = "device-" + work.id + "-v" + work.version;
  if (!records.some(w => w.id === id)) records.push({
    ...JSON.parse(JSON.stringify(work)), id, phoneId: work.id,
    name: work.title || catalog[work.type].name, preset: false, source: "已部署",
  });
}
function deviceWorkRunning(work) {
  return work.preset ? state.builtin === work.id : state.running?.id === work.id ||
    (work.phoneId && state.running?.id === work.phoneId && state.running.version === work.version);
}
function renderDeviceWorks() {
  const records = deviceWorkRecords();
  const online = state.connected && !state.pairing;
  return '<section id="device-works" aria-labelledby="device-works-heading">' +
    '<div class="builtin-section-heading"><h3 id="device-works-heading">Works 作品</h3><span>' + records.length + ' 件作品</span></div>' +
    '<p class="device-works-copy">设备上的 Lua 作品与已部署版本，都在这里。删除设备文件，手机创作仍保留。</p>' +
    (!online ? '<p class="device-works-notice" role="status">设备离线 · 显示上次列表，连接后才能删除</p>' : '') +
    '<div class="device-works-list">' + (records.length ? records.map(w => {
      const running = deviceWorkRunning(w);
      return '<article class="device-work-row"><span class="builtin-tile-icon">' + (w.preset ? builtinIcon(w.app) : icon("code")) +
        '</span><div class="device-work-info"><b>' + escapeHTML(w.name) + '</b><small>v' + w.version + ' · ' +
        (w.preset ? '预装' : '已部署') + '</small><span class="device-work-status' + (running ? ' is-running' : '') + '">' +
        (!online ? '状态待同步' : running ? '● 正在运行' : '已安装') + '</span></div>' +
        (w.preset ? '<span class="device-works-footnote">预装 · 不可删除</span>' :
          '<button class="icon-button device-work-delete" data-device-delete="' + escapeHTML(w.id) + '" aria-label="删除 ' + escapeHTML(w.name) + ' v' + w.version + '" ' +
          (!online || activeJob() || deletingDeviceWork ? 'disabled' : '') + '>' + icon('trash') + '</button>') + '</article>';
    }).join('') : '<p class="device-works-empty">设备上暂无 Works 作品。部署后会显示在这里。</p>') + '</div>' +
    '<p class="device-works-footnote">仅管理 Works 文件，不影响上方系统应用。</p></section>';
}
function reviewDeviceWorks() {
  showTab("works"); setAppGroup("device");
  requestAnimationFrame(() => $("device-works").scrollIntoView({block: "start", behavior: "smooth"}));
}
function confirmDeviceWorkDeletion(id, failed = false) {
  const work = deviceWorkRecords().find(w => w.id === id);
  if (!work || work.preset || !state.connected || state.pairing || activeJob() || deletingDeviceWork) return;
  const running = deviceWorkRunning(work);
  openModal((running ? '停止并删除「' : '删除「') + work.name + '」？',
    '<div class="device-delete-summary"><span class="builtin-tile-icon">' + icon('code') + '</span><div><b>' + escapeHTML(work.name) +
    '</b><small>v' + work.version + ' · Mosaico 设备上的 Lua 作品</small></div></div>' +
    (failed ? '<p class="device-delete-error" role="alert">删除失败，设备条目仍保留。请重试。</p>' : '') +
    '<p>' + (running ? '此作品正在运行。将先停止运行，确认退出后再删除。' : '将从 Mosaico 删除此版本的作品及对应设备文件。') +
    '</p><p>手机中的创作、源码和版本记录仍保留；其他版本与系统应用不受影响。设备删除不可撤销。</p>' +
    '<label class="device-delete-test"><input id="device-delete-fail" type="checkbox"> 演示删除失败（仅设计评审）</label>' +
    '<div class="modal-actions"><button class="secondary-button" id="device-delete-cancel">取消</button><button class="danger-button" id="device-delete-confirm">' +
    (failed ? '重试删除' : running ? '停止并删除' : '从设备删除') + '</button></div><p class="device-works-footnote">交互模拟 · 不会访问真实设备</p>', 'WORKS / DEVICE STORAGE');
  modalAction('device-delete-cancel', closeModal);
  modalAction('device-delete-confirm', () => {
    if (!state.connected || state.pairing || activeJob()) { closeModal(); toast('设备不可用，未删除任何作品。'); return; }
    const fail = $('device-delete-fail').checked;
    deletingDeviceWork = true;
    $('device-delete-confirm').disabled = true;
    $('device-delete-confirm').textContent = running ? '正在停止并删除…' : '正在删除…';
    $('device-delete-cancel').disabled = true;
    setTimeout(() => {
      deletingDeviceWork = false;
      if (!state.connected || state.pairing) { closeModal(); updateUI(); toast('连接中断，删除结果待同步；列表暂时保留。'); return; }
      if (fail) { confirmDeviceWorkDeletion(id, true); return; }
      if (deviceWorkRunning(work)) {
        state.running = null; state.simulation = null; state.builtin = null; state.builtinReturn = null;
      }
      deviceWorks = deviceWorks.filter(w => w.id !== id);
      const phone = state.works.find(w => w.id === work.phoneId);
      if (phone) phone.installed = deviceWorks.some(w => w.phoneId === phone.id && w.version === phone.version);
      persist(); closeModal(); updateUI();
      toast('已从设备删除「' + work.name + '」v' + work.version + '（模拟），手机创作保留。');
    }, 650);
  });
}
document.addEventListener('click', e => {
  const button = e.target.closest('[data-device-delete]');
  if (button && !button.disabled) confirmDeviceWorkDeletion(button.dataset.deviceDelete);
  if (e.target.closest('#review-device-works')) reviewDeviceWorks();
});
