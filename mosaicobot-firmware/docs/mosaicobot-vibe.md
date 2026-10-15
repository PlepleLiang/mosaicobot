# MosaicoBot / retained Vibe build

This is an opt-in build of the existing Claw application, not a replacement UI or a Vibe example. Original launcher apps, Works and Partner resources remain in the UI and SYSTEM images.

## Build

Use the adjacent esp-mosaico-vibe checkout and its pinned ESP-IDF revision. Run its low-noise build doctor first. Select GSPC 0.6.1 for Claw ESP-GSP 1.5.1; retained Vibe independently uses GSPC 0.5.0 and ESP-GSP 1.4.0.

A local experiment adding P4-style DMA cleanup to S31 restart did not fix the
reproduced boot failure. It has been reverted from the SDK; the rejected patch
is archived in the consuming workspace validation/experiments directory, not
required by this profile. Preserve the pinned SDK and unrelated existing fixes.

The confirmed failure was the independent USB OTG DMA engine surviving CPU-only
restart, then writing an EP0 GET_DESCRIPTOR packet over the next image's SRAM
instructions before app_main. Apply the USB-specific SDK patch documented in
`../esp-mosaico-vibe/submodule/esp-mosaico-utils/esp-mosaico-recovery/tools/patches/README.md`
to ESP-IDF 7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe. Both outgoing Claw and
Vibe firmware must contain it; installing only a patched Claw does not fix the
old Vibe writer. Keep unrelated camera patches. No security/trial configuration
is changed. Build the recovery-only candidate separately and retain reviewed
prebuilt images. Its layout precondition must be the verified current full
Claw table, not Vibe's default application layout.

```powershell
$env:GSPC_EXECUTABLE = "<verified path to gspc 0.6.1>"
python ../esp-mosaico-vibe/submodule/esp-mosaico-utils/mosaico-tools/skills/idf-low-noise-build/scripts/idf_low_noise_build.py --project . build --build-dir build-mosaicobot -D ESP_MOSAICO_BUILD_PROFILE=tools/cmake/mosaicobot_profile.cmake -D IDF_TARGET=esp32s31 -D ESP_IRIS_PYTHON=<prepared-Iris-Python>
```

`build-mosaicobot/sdkconfig` is separate from the user's root sdkconfig. Do not copy credentials into tracked defaults or pass secrets as CMake definitions (arguments are logged). The profile rejects the ordinary `build` directory.

The profile packages `edge_agent-system-update.irisfw` as part of a successful build. It includes ota_0, the configured partition table, ui_apps and system. A fresh LittleFS producer is ordered by its target rather than requiring its image before the first build.

## Device installation

Only use the consuming workspace's `mosaico.py`. First verify the selected device is live in Vibe Mode with the expected identity, version and OTA capability. Preserve available crash evidence. From esp-mosaico-vibe:

```text
python mosaico.py iris system-update --bundle ../esp-mosaico-claw/build-mosaicobot/edge_agent-system-update.irisfw --device-id <verified Device ID>
```

Do not use direct flash/app-flash or write the original Claw partition image. The fixed first 2 MiB and installed Vibe bootloader are retained. Original NOR configuration is not migrated by moving partition addresses. NAND user content is not erased by this profile.

For a local prebuilt bundle, `--project` and `--skip-build` must be omitted; they select a build source, not the bundle destination. Always keep the verified Device ID. `iris restart` verifies a normal reboot without rewriting firmware. The recovery adapter selects factory persistently: restarting Vibe alone does not return to Claw. A subsequent System Update from normal exercises normal -> Vibe -> normal and selects the application again. Verify live modes and new Boot IDs at both transitions.

USB CDC0 belongs to Iris. The application calls the public recovery adapter and never enables its own OTA writer. Shared presenter capture supports switching between the original GSP and Works display producers. Health acceptance occurs after UI, storage and local service initialization, before waiting for optional Internet connectivity.

Iris and its optional screen shadow start before Board Manager; the presenter is
attached after Board Manager and touch initialization. Moving the shadow after
Board Manager did not fix the reproduced SPI/DMA crash and that experiment was
reverted. Matching core dumps, the USB SETUP instruction-overwrite snapshots
and the SDK USB restart workaround are tracked in the consuming workspace
validation evidence. A successful first boot does not establish repeated-boot
reliability.

On 2026-10-12, device 4553502d49524953010030eda0f45ede passed recovery-only
self-update, Claw installation, two explicit normal -> Vibe -> normal rounds,
and a normal restart with the USB-specific fix in both images. Each update
validated the exact target ELF and health, with unchanged bootloader/layout
hashes. The directly inspected screenshot shows the original launcher with
Camera / CLAW / Works. This is bounded startup/transport validation, not an
acceptance of every original app or the phone-generated Works runtime. See
the consuming workspace docs/mosaicobot/validation/mosaico-usb-acceptance.json.

An installation is accepted only after live identity/Boot ID and application health checks, direct device screenshot inspection, original-app behavior checks, and normal → Vibe → normal transitions. Host tests/builds alone are not hardware acceptance. This profile does not yet provide the phone Works transaction/start/stop API.
