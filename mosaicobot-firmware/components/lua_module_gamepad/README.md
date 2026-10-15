# gamepad — automatic LAN controller

Opt-in input for one asynchronous Lua display game. No keys are sent to Hub,
the notification shade, other Lua VMs or native games. No emulator is loaded.
The user explicitly chose automatic control on a trusted LAN, without device
confirmation or permanent pairing. Existing enabled-module selections are preserved.

## Lua API

- require("gamepad") returns open, poll, close and the legacy approve stub.
- open(): call once after display.open(). Requires an async job; rejects another
  owning Lua runtime. Creates a random run ID.
- poll(): returns connected, held/pressed/released boolean-keyed tables,
  lx/ly/rx/ry (-1..1, right/down positive), and ordered events
  ({pressed=table,released=table}). connected becomes true only after a first
  valid input state and remains true during short input stalls; it becomes false
  on session timeout/release/socket close. input_active reports sample freshness.
  Each poll drains edges. Never reconstruct discrete presses from held: safety
  zeroing followed by restoration is not a new physical press.
- Keys: up/down/left/right/a/b/x/y/l1/r1/l2/r2/select/start/l3/r3.
  L2/R2 are digital. Use ordered events for discrete actions such as lane changes.
- close(): releases the runtime and all controller state; never stops the game.
  Registered runtime-exit cleanup also handles Lua errors.
- No pending field is returned. approve(...) is a no-op returning false, retained
  only for old v2 packages. Their old confirmation branches are never entered.
  New games must not draw ALLOW/DENY or call approve.

Preserve touch fallback and EXIT. Start is normal input, not a firmware pause.
For connected && input_active==false, use a temporary simulation freeze / signal
hint, never a persistent pause. Continue polling, rendering, frame-clock updates
and touch; touch can take over. Fresh input removes only the temporary freeze.
A real connected -> false transition pauses once; reconnection never resumes a
user/disconnect pause. Test input_active == false for old-firmware compatibility.
Transition logs distinguish stale/resumed input from session loss.
The three preset games pause themselves on connected -> false; menu input-zero
alone does not pause them. Existing v2 packages work without being rewritten;
the existing v2 preset sources are edited in place during development to remove
the unused confirmation UI; already acquired user copies are not overwritten.

## Wire contract

Dedicated /ws/gamepad, complete text JSON <=512 bytes, v=1. All responses include
access:"automatic". New phones reject older firmware that lacks this marker
instead of returning to its physical-confirmation workflow.

The pinned IDF skips URI handlers during upgrade. ws_pre_handshake_cb reserves
a single waiting controller before 101; HTTP 409 rejects another live controller.
Plain HTTP GET returns 400 without reserving anything. Socket close clears only
its own state. There is no automatic takeover of an active controller.

- poll: returns waiting_game while no runtime is open. Valid polls keep this
  waiting socket alive (2.5s inactivity limit). Once a game opens, the next poll
  automatically issues ready with a random token/run and seq=0. No physical action.
- state: token/run/seq/held/lx/ly/rx/ry. seq increases from 1 to <=2147483647.
  Phone sends an immediate all-zero sample when it receives ready; controls are
  enabled only after its first sequence ACK. Further samples are ~30Hz, with
  button changes sent immediately.
- release: releases only the sending socket. Lost runtime/session returns released;
  the foreground phone clears state and reconnects with a fresh token/sequence.

While the gamepad Lua runtime is open, Wi-Fi modem sleep is disabled; cleanup
restores the preceding mode if no other mode has replaced it. This does not write
the saved power profile. The gamepad socket uses TCP_NODELAY for small WS frames. The phone
limits periodic outstanding samples to 8, reserving up to 16 for button edges.
First-state synchronization and session loss use 2.5s. After >350ms without a new
valid sample, clear inputs/edges and set input_active=false, but keep connected=true
once synchronized, the token and highest sequence. New valid samples restore
input_active; only >2.5s revokes ownership and connected. Retain the last accepted
physical key baseline across safety zeroing so a resumed held Start does not
produce another pressed edge. An ambiguous release/repress entirely within a
stall cannot be recovered and is not invented. The timer checks every 50ms. Short jitter must not repeatedly
replace the phone controller UI with a connection overlay.
Polls and duplicate/older state sequences never refresh the running watchdog.
32 ordered edge records are bounded; overflow or invalid owner packets release
input instead of replaying it. Opposing digital directions cancel.

The phone repeats availability checks while foreground and not inside its menu;
it backs off on occupancy, never steals control, and clears state on background,
network change, exit, rotation and focus loss. Reconnecting never sends old keys
or starts/resumes/stops/deploys a game. Menu remains separate from Start.
HTTP/timer callbacks only modify mutex-protected state, never call Lua or draw.

## Security boundary

Trusted private LAN only. Any reachable client can automatically occupy the
controller; no physical authorization is required. WS is unencrypted. Random
token/run values isolate sessions and stale packets, not authentication or TLS.
Existing Claw file/execution APIs are outside this module. Lua code is trusted;
this is not a sandbox. Never expose this service to the Internet.
