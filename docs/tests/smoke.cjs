/* Run with NODE_PATH pointing to a Playwright installation. No hardware is accessed. */
const { chromium } = require("playwright");
const AxeBuilder = require("@axe-core/playwright").default;
const assert = require("node:assert/strict");
const path = require("node:path");
const os = require("node:os");
const { pathToFileURL } = require("node:url");
const fs = require("node:fs/promises");
(async () => {
  const browser = await chromium.launch({
    executablePath:
      process.env.BROWSER_PATH ||
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1100 },
  });
  const page = await context.newPage();
  const errors = [],
    passed = [];
  const artifactDir = path.join(os.tmpdir(), "mosaicobot-review-artifacts");
  await fs.mkdir(artifactDir, { recursive: true });
  page.on("pageerror", (e) => errors.push(e.message));
  await page.clock.install();
  await page.goto(pathToFileURL(path.join(__dirname, "../index.html")).href);
  const check = (condition, name) => {
    assert.ok(condition, name);
    passed.push(name);
    console.log("PASS " + name);
  };
  const advance = async (count = 6) => {
    for (let i = 0; i < count; i++) await page.clock.runFor(1000);
  };
  const click = async (sel) => {
    await page.locator(sel).filter({ visible: true }).click();
    if (sel.startsWith('[data-prompt=')) {
      assert.ok(await page.locator("#prompt-input").inputValue(), "inspiration only fills a draft");
      await page.locator("#send-button").click();
    }
  };
  try {
    await page.screenshot({
      path: path.join(artifactDir, "desktop-idle.png"),
      fullPage: true,
    });
    const accessibility = await new AxeBuilder({ page }).analyze();
    console.log(
      "ACCESSIBILITY " +
        JSON.stringify(
          accessibility.violations.map((v) => ({
            id: v.id,
            nodes: v.nodes.map((n) => n.target),
          })),
        ),
    );
    check(
      accessibility.violations.length === 0,
      "idle page automated accessibility",
    );
    console.log(
      "LAYOUT " +
        JSON.stringify(
          await page.evaluate(() => {
            const el = document.getElementById("conversation");
            return {
              height: el.clientHeight,
              scrollHeight: el.scrollHeight,
              scrollTop: el.scrollTop,
            };
          }),
        ),
    );
    const chrome = await page.evaluate(() => {
      const height = selector => document.querySelector(selector).getBoundingClientRect().height;
      return { header:height('.app-header'), connection:height('.connection-pill'), composer:height('.composer'), navigation:height('.bottom-nav') };
    });
    check(chrome.header >= 72 && chrome.header <= 74, 'reference header is 73 logical pixels');
    check(chrome.connection >= 61 && chrome.connection <= 63, 'reference connection card is 62 logical pixels');
    check(chrome.composer >= 139 && chrome.composer <= 143, 'reference three-part composer is about 140 logical pixels');
    check(chrome.navigation >= 59 && chrome.navigation <= 61, 'reference navigation is 60 logical pixels excluding OS gesture area');
    await page.locator('#prompt-input').fill('第一行\n第二行\n第三行\n第四行\n第五行');
    check(await page.locator('#prompt-input').evaluate(el => el.clientHeight <= 78 && el.scrollHeight > el.clientHeight), 'long draft scrolls within three lines');
    await page.locator('#prompt-input').fill('');
    check(await page.locator('#prompt-input').evaluate(el => el.clientHeight === 56), 'cleared draft restores reference composer height');
    check(await page.locator('#send-button').isDisabled(), 'empty draft has a real disabled send action');
    await page.locator('#prompt-input').blur();
    await page.locator('.phone-column').screenshot({path:path.join(artifactDir,'preview.png')});
    const confirmRun = async () => {
      await click('[data-result-action="run"]');
      check(await page.locator('#run-confirm').isVisible(), 'per-version run confirmation');
      await click('#run-confirm');
      await advance();
    };
    const reconnect = async () => {
      await click('#connection-button');
      await click('#discover-device');
      await page.clock.runFor(1000);
      await click('#connection-done');
    };
    const edit = async text => {
      await click('[data-edit-prompt="' + text + '"]');
      check(await page.locator('#prompt-input').inputValue() === text, 'suggestion fills draft without sending');
      await click('#send-button');
      await advance();
    };
    await click('[data-prompt="生成贪食蛇"]');
    check(await page.locator('#job-card').isVisible(), 'generation progress visible');
    await advance();
    check((await page.locator('#result-card').innerText()).includes('尚未部署'), 'generation saves without auto-run');
    check(await page.evaluate(() => state.running === null), 'saving does not start hardware');
    check(await page.locator('#mini-preview').count() === 0, 'application icon never claims a live device frame');
    await confirmRun();
    check((await page.locator('#result-card').innerText()).includes('已在 Mosaico 上运行'), 'confirm -> deploy -> simulated running');
    await page.screenshot({path:path.join(artifactDir,'desktop-running.png'),fullPage:true});
    await page.locator(".phone-column").screenshot({path:path.join(artifactDir,"preview-creation-running.png")});
    const before = await page.locator('#device-canvas').evaluate(c => c.toDataURL());
    await click('[data-direction="up"]'); await page.clock.runFor(600);
    check(before !== await page.locator('#device-canvas').evaluate(c => c.toDataURL()), 'snake responds to direction');
    await edit('速度慢一点');
    check((await page.locator('#result-card').innerText()).includes('v2'), 'iteration saves v2');
    await click('[data-result-action="run"]');
    check(!await page.locator('#modal').isVisible(), 'new version does not replace running work without stopping');
    await click('#hardware-home');
    await confirmRun();
    await click('[data-result-action="details"]');
    check((await page.locator('.code-block').innerText()).includes('430'), 'slower parameter applied');
    await click('#modal-close');
    await click('[data-result-action="stop"]');
    await click('[data-result-action="details"]');
    await click('#rollback-work');
    check(await page.locator('#run-confirm').isVisible(), 'rollback also requires explicit confirmation');
    await click('#run-confirm'); await advance();
    check((await page.locator('#result-card').innerText()).includes('v1'), 'rollback restores v1');
    await click('#connection-button'); await click('#disconnect-device');
    check(await page.locator('#offline-banner').isVisible(), 'disconnect retains context');
    await edit('把蛇改成橙色');
    check(await page.locator('[data-result-action="run"]').isDisabled(), 'offline saved work cannot run');
    await reconnect();
    check(await page.evaluate(() => state.running.version === 1), 'reconnection never silently deploys');
    await click('#hardware-home'); await confirmRun();
    await click('[data-result-action="stop"]');
    await click('#scenario-button'); await click('#scene-fail');
    await edit('速度慢一点');
    check((await page.locator('#job-card').innerText()).includes('这次没成功'), 'check failure visible');
    await click('[data-job-action="retry"]'); await advance(); await confirmRun();
    await click('[data-tab="works"]');
    await click('[data-work-action="delete"]'); await click('#keep-work');
    check(await page.locator('.work-item').count() === 1, 'cancel deletion retains work');
    await click('[data-work-action="delete"]'); await click('#confirm-delete');
    check(await page.locator('.work-item').count() === 0, 'confirmed local deletion removes record');
    check(await page.evaluate(() => !!state.running), 'local deletion does not stop the device');
    await click('#hardware-home'); await click('#new-button');
    await page.locator('#prompt-input').fill('<script>alert(1)</script> 一个未知的想法');
    await click('#send-button');
    check((await page.locator('#messages').innerText()).includes('只演示贪食蛇'), 'unsupported intent not faked');
    check(await page.locator('#messages script').count() === 0, 'input escaped');
    await click('#new-button'); await click('[data-prompt="做一个 25 分钟的专注时钟"]');
    await advance(); await confirmRun();
    check((await page.locator('#result-card').innerText()).includes('专注时钟'), 'focus creation');
    await click('[data-result-action="details"]');
    check(await page.locator('#rollback-work').count() === 0, 'no cross-work rollback');
    await click('#modal-close'); await click('#pause-button');
    check((await page.locator('#pause-button').innerText()).includes('继续'), 'focus pause');
    await click('#hardware-home'); await click('#new-button');
    await page.locator('#prompt-input').fill('用 IMU 做一个水平仪'); await click('#send-button');
    await advance(); await confirmRun();
    await page.locator('#tilt-input').fill('12');
    check((await page.locator('#tilt-output').innerText()).includes('12'), 'future IMU concept remains clearly simulated');
    await click('#hardware-home');
    await click('[data-tab="device"]');
    check(await page.locator('#auto-run').isDisabled() && !await page.locator('#auto-run').isChecked(), 'global auto-run unavailable');
    await page.locator(".phone-column").screenshot({path:path.join(artifactDir,"preview-device.png")});
    await click('#new-button'); await click('[data-prompt="生成贪食蛇"]');
    await advance(); await confirmRun();
    await page.reload();
    await click('[data-tab="works"]');
    check(await page.locator('.work-item').count() === 3, 'three works persist after reload');
    await page.locator('.work-item').filter({hasText:'专注时钟'}).locator('[data-work-action="run"]').click();
    await click('#run-confirm'); await advance();
    check((await page.locator('#messages').innerText()).includes('25 分钟'), 'saved work restores context');
    await click('[data-tab="device"]'); await click('#device-talk'); await click('#claw-send');
    check((await page.locator('#claw-reply').innerText()).includes('专注时钟'), 'separate device Claw conversation');
    await click('#modal-close');
    // Existing Claw apps share the device, without replacing the phone thread.
    await click('[data-tab="works"]');
    await click('[data-app-group="device"]');
    check(
      (await page.locator("[data-builtin-detail]").count()) === 14,
      "14 original application entries preserved",
    );
    check(
      (
        await page.locator('[data-builtin-detail="album"]').innerText()
      ).includes("系统相册") &&
        (
          await page.locator('[data-builtin-detail="album_app"]').innerText()
        ).includes("Works 相册"),
      "native and Works Albums remain distinct",
    );
    check(
      (await new AxeBuilder({ page }).analyze()).violations.length === 0,
      "application center accessibility",
    );
    await page.screenshot({
      path: path.join(artifactDir, "desktop-apps.png"),
      fullPage: true,
    });
    for (const id of [
      "camera",
      "ai_create",
      "works",
      "settings",
      "imu",
      "album",
      "music",
      "breakout",
      "weather",
      "interact",
      "peer",
      "dino",
      "flappybird",
      "album_app",
    ]) {
      await click('[data-builtin-detail="' + id + '"]');
      const oldFrame = await page
        .locator("#device-canvas")
        .evaluate((c) => c.toDataURL());
      await click("#builtin-open");
      if (id === "camera") await click("#camera-allow");
      check(
        (await page.evaluate((id) => state.builtin === id, id)) &&
          oldFrame !==
            (await page
              .locator("#device-canvas")
              .evaluate((c) => c.toDataURL())),
        "original app opens and renders " + id,
      );
      await click("#back-to-apps");
    }
    await click('[data-builtin-detail="imu"]');
    await click("#builtin-open");
    check(
      await page.evaluate(
        () => state.builtin === "imu" && state.running === null,
      ),
      "native app replaces foreground without deleting creations",
    );
    const imuFrame = await page
      .locator("#device-canvas")
      .evaluate((c) => c.toDataURL());
    await page.locator("#builtin-tilt").fill("17");
    check(
      (await page.locator("#builtin-tilt-output").innerText()).includes("17") &&
        imuFrame !==
          (await page.locator("#device-canvas").evaluate((c) => c.toDataURL())),
      "native IMU control updates device and receipt",
    );
    check(
      (await new AxeBuilder({ page }).analyze()).violations.length === 0,
      "native app detail accessibility",
    );
    await page.screenshot({
      path: path.join(artifactDir, "desktop-imu.png"),
      fullPage: true,
    });
    await click("#back-to-apps");
    check(
      await page.evaluate(() => state.builtin === "imu"),
      "phone back to list does not stop hardware",
    );
    await click('[data-builtin-detail="imu"]');
    await click("#builtin-stop");
    check(
      await page.evaluate(
        () =>
          state.builtin === null &&
          state.running === null &&
          state.hubPage === 0,
      ),
      "exit native app returns clock homepage",
    );
    await click("#back-to-apps");
    await click('[data-builtin-detail="camera"]');
    await click("#builtin-open");
    await click("#camera-deny");
    check(
      await page.evaluate(() => state.builtin === null),
      "camera denial retains current screen",
    );
    await click("#builtin-open");
    check(
      (await new AxeBuilder({ page }).analyze()).violations.length === 0,
      "camera permission dialog accessibility",
    );
    await click("#camera-allow");
    check(
      await page.evaluate(() => state.builtin === "camera"),
      "camera opens only after one-session authorization",
    );
    await click("#hardware-home");
    await click('[data-device-open="camera"]');
    check(
      await page.locator("#camera-allow").isVisible(),
      "camera authorization requested again after leaving",
    );
    await click("#camera-deny");
    await click('[data-hub-page="1"]');
    check(
      (await page.locator("[data-device-open]").count()) === 6,
      "first launcher app page has six apps",
    );
    await click('[data-device-open="music"]');
    await click("#back-to-apps");
    await click('[data-builtin-detail="music"]');
    await click("#builtin-music-toggle");
    check(
      await page.evaluate(() => state.builtinValues.playing),
      "music playback state can be toggled without real audio",
    );
    await click('[data-hub-page="2"]');
    check(
      (await page.locator("[data-device-open]").count()) === 2,
      "second launcher page keeps Interaction and Partner",
    );
    await click('[data-device-open="peer"]');
    check(
      (await page.locator("#hardware-status").innerText()).includes("Partner"),
      "Partner has device runtime feedback",
    );
    await click("#hardware-home");
    await click('[data-device-open="works"]');
    check(
      await page.locator('[data-device-open="dino"]').isVisible(),
      "Works includes preinstalled runtime apps",
    );
    await click('[data-device-open="dino"]');
    await click("#hardware-back");
    check(
      await page.evaluate(() => state.builtin === "works"),
      "preinstalled runtime back returns Works",
    );
    await click('[data-works-page="1"]');
    const threadBefore = await page.locator("#messages").innerText();
    await page.locator("[data-device-work]").first().click();
    check(
      await page.evaluate(
        () => !!state.running && state.builtinReturn === "works" && !state.job,
      ),
      "installed creation starts directly from Works without deployment",
    );
    check(
      threadBefore === (await page.locator("#messages").innerText()),
      "physical launch leaves phone conversation unchanged",
    );
    await click("#hardware-back");
    check(
      await page.evaluate(
        () => state.builtin === "works" && state.worksPage === 1,
      ),
      "created app returns to its Works page",
    );
    await click('[data-hub-page="1"]');
    await click('[data-device-open="imu"]');
    await click("#back-to-apps");
    await click('[data-builtin-detail="imu"]');
    await click("#connection-button");
    await click("#disconnect-device");
    const nativeFrozen = await page
      .locator("#builtin-preview")
      .evaluate((c) => c.toDataURL());
    await click("#hardware-home");
    check(
      nativeFrozen ===
        (await page.locator("#builtin-preview").evaluate((c) => c.toDataURL())),
      "offline native detail preserves last frame after physical navigation",
    );
    check(
      (await page.locator("#builtin-open").isDisabled()) &&
        (await page.locator("#builtin-stop").isDisabled()),
      "offline native remote controls disabled",
    );
    check(
      await page.evaluate(
        () => state.deviceReceipt.builtin === "imu" && state.builtin === null,
      ),
      "offline physical state does not overwrite mobile receipt",
    );
    await click("#connection-button");
    await click("#discover-device");
    await page.clock.runFor(1000);
    await click("#connection-done");
    check(
      (await page.locator(".builtin-runtime-status").innerText()).includes(
        "设备主屏",
      ),
      "reconnection refreshes actual foreground state",
    );
    await click("#new-button");
    await click('[data-prompt="生成贪食蛇"]');
    await advance();
    await click('[data-result-action="run"]');
    await click("#run-confirm");
    check(
      await page.locator('[data-hub-page="1"]').isDisabled(),
      "launcher navigation blocked during deployment",
    );
    await advance();
    await click('[data-result-action="stop"]');
    check(
      await page.evaluate(
        () =>
          state.builtin === null &&
          state.running === null &&
          state.hubPage === 0,
      ),
      "stopping generated app returns original homepage",
    );
    await click('[data-tab="works"]');
    await click('[data-app-group="device"]');
    for (const [width, height] of [
      [375, 812],

      [1440, 1000],
    ]) {
      await page.setViewportSize({ width, height });
      if (width === 375)
        check(
          (await new AxeBuilder({ page }).analyze()).violations.length === 0,
          "small-screen application center accessibility",
        );
      check(
        await page.evaluate(
          () =>
            document.documentElement.scrollWidth <= innerWidth &&
            document.getElementById("works-panel").scrollWidth <=
              document.getElementById("works-panel").clientWidth,
        ),
        "apps center fits " + width + "x" + height,
      );
      await page.screenshot({
        path: path.join(artifactDir, "apps-" + width + "x" + height + ".png"),
        fullPage: true,
      });
    }
    await click("#new-button");
    await page.emulateMedia({ reducedMotion: "reduce" });
    for (const [width, height] of [
      [375, 812],
      [390, 844],
      [768, 1024],

      [1440, 1000],
    ]) {
      await page.setViewportSize({ width, height });
      check(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        "no horizontal overflow " + width + "x" + height,
      );
      await page.screenshot({
        path: path.join(artifactDir, "layout-" + width + "x" + height + ".png"),
        fullPage: true,
      });
    }
    await click("#connection-button");
    await page.keyboard.press("Tab");
    check(
      await page.evaluate(() =>
        document.getElementById("modal").contains(document.activeElement),
      ),
      "dialog contains keyboard focus",
    );
    await page.keyboard.press("Escape");
    check(!(await page.locator("#modal").isVisible()), "Escape closes dialog");
    const smallAccessibility = await new AxeBuilder({ page }).analyze();
    console.log(
      "FINAL ACCESSIBILITY " +
        JSON.stringify(
          smallAccessibility.violations.map((v) => ({
            id: v.id,
            nodes: v.nodes.map((n) => ({
              target: n.target,
              summary: n.failureSummary,
            })),
          })),
        ),
    );
    check(
      smallAccessibility.violations.length === 0,
      "final page automated accessibility",
    );
    check(errors.length === 0, "no JavaScript errors");
    console.log(
      JSON.stringify(
        { passed: passed.length, errors, artifacts: artifactDir },
        null,
        2,
      ),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
