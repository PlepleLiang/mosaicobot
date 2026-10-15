# Pinned ESP32-S31 SDK USB restart workaround

Target: ESP-IDF 7b9cc1ac79f865983f59bb8ff3ff43eb74ff1dbe, ESP32-S31.

The pinned CPU-only restart leaves the USB OTG HS DMA engine active. A host
GET_DESCRIPTOR setup packet can then overwrite the old EP0 buffer after the
next image has loaded different code at that address. Captured failing startup
snapshots showed eight instruction bytes replaced by 80 06 01 03 00 00 ff 00
before application initialization. Resetting GDMA alone did not help: USB has
an independent DMA engine.

Apply idf-7b9cc1ac-s31-usb-restart.patch to the pinned SDK, not managed_components.
Use git apply --check before applying; git apply --check --reverse can verify
an already-applied patch. Preserve unrelated SDK changes. Both the outgoing
normal application and retained Vibe firmware must be built with the fix.
A new application alone cannot prevent an older Vibe writer from leaving DMA
active on its restart.

The change runs in esp_system_reset_modules_on_exit after interrupts and the
other CPU have stopped. It checks both USB clock gates, only handles device
mode with DMA enabled, disconnects, waits for AHB idle, resets the DWC2 core,
waits for idle again, disables DMA and leaves the device disconnected. The
sequence follows TinyUSB's DWC2 deinit and uses the pinned S31 HAL. The RTC
watchdog already armed by esp_restart_noos bounds a stalled reset. It does not
change cryptographic cleanup, eFuses, memory protection or partition layout.

The native test tests/test_s31_usb_restart.py exercises the exact patch block
with register/HAL doubles: clock gates, DMA and host/device mode combinations,
including delayed idle acknowledgement and shutdown ordering. Hardware
acceptance still requires same-device normal -> Vibe -> normal, new Boot IDs,
matching firmware hashes and healthy application behavior.

Build and package candidates separately. Do not replace reviewed prebuilt
images merely because compilation succeeds. For a custom application layout,
the recovery-only System Update bundle must hash the verified current full
partition table as its layout precondition, while writing only the immutable
factory slot. It must not include or replace that partition table.
