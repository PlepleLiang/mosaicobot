/* Local HTML verification. No firmware, phone, serial or network access. */
const { chromium } = require("playwright");
const AxeBuilder = require("@axe-core/playwright").default;
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs/promises");
const os = require("node:os");
const { pathToFileURL } = require("node:url");
(async () => {
  const browser = await chromium.launch({
    executablePath:
      process.env.BROWSER_PATH ||
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1120 },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  const errors = [],
    requests = [],
    checks = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("request", (r) => {
    if (/^https?:/.test(r.url())) requests.push(r.url());
  });
  const url = pathToFileURL(path.join(__dirname, "index.html")).href;
  const check = (condition, name) => {
    assert.ok(condition, name);
    checks.push(name);
    console.log("PASS " + name);
  };
  const select = async (id) =>
    page.locator('.scene-button[data-screen="' + id + '"]').click();
  const screen = page.locator("#device-screen");
  try {
    await page.goto(url);
    await page.locator("#device-screen .clock-time").waitFor();
    check(
      (await page.locator(".scene-button").count()) === 9,
      "nine system scenes",
    );
    check(
      (await page.locator(".gallery-card").count()) === 9,
      "nine consistent gallery previews",
    );
    const ids = [
      "home",
      "apps",
      "more",
      "works",
      "controls",
      "notifications",
      "aod",
      "charging",
      "insert",
    ];
    const appIds = [];
    const exportSize = await page.addStyleTag({
      content: ".main-frame{width:502px!important;max-width:none!important}",
    });
    await page.waitForFunction(
      () =>
        Math.abs(
          document.querySelector("#device-screen").getBoundingClientRect()
            .width - 480,
        ) < 0.1,
    );
    for (const id of ids) {
      await select(id);
      check((await screen.getAttribute("data-scene")) === id, "scene " + id);
      if (["home", "apps", "more"].includes(id))
        appIds.push(
          ...(await screen
            .locator("[data-app]")
            .evaluateAll((els) => els.map((el) => el.dataset.app))),
        );
      const result = await new AxeBuilder({ page }).analyze();
      check(
        result.violations.length === 0,
        "accessibility " +
          id +
          ": " +
          JSON.stringify(
            result.violations.map((v) => ({
              id: v.id,
              nodes: v.nodes.map((n) => n.target),
            })),
          ),
      );
      // Integer-origin clipping avoids an extra pixel from fractional page layout.
      const frame = await screen.boundingBox();
      const png = await page.screenshot({
        path: path.join(__dirname, id + ".png"),
        clip: {
          x: Math.round(frame.x),
          y: Math.round(frame.y),
          width: 480,
          height: 480,
        },
      });
      assert.equal(png.readUInt32BE(16), 480, id + " export width");
      assert.equal(png.readUInt32BE(20), 480, id + " export height");
    }
    await exportSize.evaluate((el) => el.remove());
    check(
      appIds.join(",") ===
        "camera,claw,works,settings,imu,album,music,bricks,weather,interaction,partner",
      "all 11 original Hub entries in original order",
    );
    await select("home");
    check(
      (await screen.innerText()).includes("Weather unavailable"),
      "missing weather not fabricated",
    );
    await page.locator('[data-state="weather"]').click();
    check((await screen.innerText()).includes("24°"), "weather demo state");
    await page.locator('[data-state="agent"]').click();
    check(
      (await screen.innerText()).includes("CFG"),
      "configured indicator does not imply model online",
    );
    await page.locator('[data-state="weather"]').click();
    await page.locator('[data-state="agent"]').click();
    await screen.locator('[data-app="claw"]').click();
    check(
      (await screen.getAttribute("data-scene")) === "app",
      "CLAW original app boundary",
    );
    check(
      (await screen.innerText()).includes("NO APP OR DEVICE"),
      "boundary explicitly not a running app",
    );
    await page.keyboard.press("Escape");
    check(
      (await screen.getAttribute("data-scene")) === "home",
      "Escape returns from app",
    );
    await select("apps");
    await page.locator('[data-review="drawer"]').click();
    const expected = {
      wifi: [34, 62],
      join: [138, 62],
      bluetooth: [34, 154],
      lowpower: [138, 154],
      ringtone: [34, 246],
      vibration: [138, 246],
    };
    for (const [id, xy] of Object.entries(expected)) {
      const b = screen.locator('[data-toggle="' + id + '"]');
      const box = await b.evaluate((el) => [
        el.offsetLeft,
        el.offsetTop,
        el.offsetWidth,
        el.offsetHeight,
      ]);
      check(
        JSON.stringify(box) === JSON.stringify([...xy, 72, 72]),
        "unchanged hit geometry " + id,
      );
    }
    await screen.locator('[data-toggle="wifi"]').click();
    check(
      (await screen
        .locator('[data-toggle="wifi"]')
        .getAttribute("aria-pressed")) === "false",
      "Wi-Fi toggles off with textual state",
    );
    await screen.locator('[data-toggle="wifi"]').click();
    await screen.locator('[data-toggle="join"]').click();
    check(
      (await page.locator("#feedback").innerText()).includes("Not Supported"),
      "reserved controls stay unsupported",
    );
    check(
      (await screen
        .locator('[data-toggle="join"]')
        .getAttribute("aria-pressed")) === null,
      "reserved control does not fake an active state",
    );
    for (const [id, x] of [
      ["volume", 270],
      ["brightness", 374],
    ]) {
      const level = screen.locator('[data-level="' + id + '"]');
      const box = await level.evaluate((el) => [
        el.parentElement.offsetLeft,
        el.parentElement.offsetTop,
        el.parentElement.offsetWidth,
        el.parentElement.offsetHeight,
      ]);
      check(
        JSON.stringify(box) === JSON.stringify([x, 298, 72, 152]),
        "unchanged level geometry " + id,
      );
      await level.fill("42");
      check(
        (await level.getAttribute("aria-valuetext")) === "42%",
        "slider value text " + id,
      );
      await level.focus();
      await page.keyboard.press("ArrowUp");
      check((await level.inputValue()) === "43", "keyboard alternative " + id);
    }
    await screen.locator('[data-app="music"]').click();
    check(
      (await screen.innerText()).includes("Music"),
      "media card only opens Music",
    );
    await screen.locator("[data-back]").first().click();
    check(
      (await screen.getAttribute("data-scene")) === "controls",
      "review returns to control context",
    );
    await screen.locator("[data-back]").click();
    check(
      (await screen.getAttribute("data-scene")) === "apps",
      "drawer returns to originating Hub page",
    );
    await select("works");
    check(
      (await screen.locator(".work-row").count()) === 4,
      "Works keeps 4 rows",
    );
    check(
      (await screen.locator('[data-work="works-album"]').count()) === 1,
      "Works Album remains distinct",
    );
    await screen.locator('[data-work="focus"]').click();
    check(
      (await screen.locator('[data-work="focus"]').innerText()).includes(
        "Open",
      ),
      "running row preserves toggle to stop",
    );
    await screen.locator('[data-work="focus"]').click();
    check(
      (await screen.getAttribute("data-scene")) === "app",
      "stopped row opens work boundary",
    );
    await page.keyboard.press("Escape");
    check(
      (await screen.getAttribute("data-scene")) === "works",
      "work returns to Works",
    );
    await screen.locator('[data-tab="recent"]').click();
    check(
      (await screen.locator(".work-row").count()) === 1,
      "Recent filters demo list",
    );
    for (const s of ["queued", "stopping", "failed", "stopped", "running"]) {
      await page.locator('[data-workstate="' + s + '"]').click();
      check(
        (await screen.locator(".work-row").count()) === 1,
        "Works state " + s,
      );
    }
    await page.locator('[data-state="worksEmpty"]').click();
    check(
      (await screen.locator(".work-row").count()) === 0,
      "Works empty state",
    );
    await page.locator('[data-state="worksEmpty"]').click();
    await page.locator('[data-state="worksPaged"]').click();
    check(
      (await screen.locator(".work-row").count()) === 4,
      "pagination keeps four rows on page one",
    );
    await screen.locator('[data-workpage="1"]').click();
    check(
      (await screen.locator(".work-row").count()) === 1,
      "pagination second page",
    );
    await screen.locator('[data-work="snake-demo"]').click();
    await page.keyboard.press("Escape");
    check(
      (await screen.locator('[data-work="snake-demo"]').count()) === 1,
      "work returns to same installed page",
    );
    check(
      (await screen.locator('[data-work="snake-demo"]').innerText()).includes(
        "Running",
      ),
      "return does not stop work",
    );
    await screen.locator('[data-workpage="-1"]').click();
    await page.locator('[data-state="worksPaged"]').click();
    await select("notifications");
    const list = screen.locator(".notification-list");
    check(
      await list.evaluate((el) => el.scrollHeight > el.clientHeight),
      "notifications genuinely scroll",
    );
    await list.focus();
    await page.keyboard.press("End");
    await page.locator('[data-state="notifEmpty"]').click();
    check(
      (await screen.innerText()).includes("All quiet."),
      "notification empty state",
    );
    await page.locator('[data-state="notifEmpty"]').click();
    await select("aod");
    await screen.locator("[data-back]").click();
    check(
      ["home", "apps", "more"].includes(
        await screen.getAttribute("data-scene"),
      ),
      "AOD open alternative",
    );
    await select("insert");
    await screen.locator('[data-app="camera"]').click();
    check(
      (await screen.innerText()).includes("Camera"),
      "insert Open keeps Camera route",
    );
    await select("home");
    const box = await screen.boundingBox();
    await page.mouse.move(box.x + box.width * 0.75, box.y + box.height * 0.5);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.25, box.y + box.height * 0.5, {
      steps: 6,
    });
    await page.mouse.up();
    check(
      (await screen.getAttribute("data-scene")) === "apps",
      "horizontal swipe pages",
    );
    await page.mouse.move(box.x + box.width * 0.5, box.y + 10);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.3, {
      steps: 6,
    });
    await page.mouse.up();
    check(
      (await screen.getAttribute("data-scene")) === "controls",
      "top swipe opens drawer",
    );
    await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.75);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.25, {
      steps: 6,
    });
    await page.mouse.up();
    check(
      (await screen.getAttribute("data-scene")) === "apps",
      "swipe up closes drawer",
    );
    await select("home");
    await page.screenshot({
      path: path.join(os.tmpdir(), "mosaicobot-review-desktop.png"),
      fullPage: true,
    });
    for (const width of [375, 768]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.evaluate(() => window.scrollTo(0, 0));
      check(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        "no page overflow " + width,
      );
      const size = await screen.boundingBox();
      check(
        Math.abs(size.width - size.height) < 1,
        "square canvas at " + width,
      );
      check(
        await screen.evaluate(
          (el) => el.offsetWidth === 480 && el.offsetHeight === 480,
        ),
        "no hardware reflow at " + width,
      );
      const result = await new AxeBuilder({ page }).analyze();
      check(
        result.violations.length === 0,
        "responsive accessibility " +
          width +
          ": " +
          JSON.stringify(
            result.violations.map((v) => ({
              id: v.id,
              nodes: v.nodes.map((n) => n.target),
            })),
          ),
      );
      if (width === 375) {
        await page.screenshot({
          path: path.join(os.tmpdir(), "mosaicobot-review-mobile.png"),
          fullPage: true,
        });
        await page.locator(".workbench").screenshot({
          path: path.join(os.tmpdir(), "mosaicobot-review-mobile-workbench.png"),
        });
      }
    }
    await page.emulateMedia({ reducedMotion: "reduce" });
    check(
      (await page.evaluate(
        () => getComputedStyle(document.documentElement).scrollBehavior,
      )) === "auto",
      "reduced motion",
    );
    await page.setViewportSize({ width: 1440, height: 1120 });
    await page.goto(url + "?export=sheet");
    await page.locator(".export-sheet").waitFor();
    await page.screenshot({
      path: path.join(__dirname, "overview.png"),
      fullPage: true,
    });
    check(errors.length === 0, "no browser errors");
    check(requests.length === 0, "no external requests");
    await fs.writeFile(
      path.join(__dirname, "review-results.json"),
      JSON.stringify(
        {
          scope:
            "Local browser design review only; not GSP_SIM or device acceptance",
          date: "2026-10-13",
          checks,
          errors,
          externalRequests: requests,
        },
        null,
        2,
      ) + "\n",
    );
    console.log(checks.length + " checks passed");
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
