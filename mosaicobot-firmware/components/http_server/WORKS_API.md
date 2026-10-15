# Phone Works API · protocol 1

LAN development bridge for MosaicoBot. Reuses app_registry, works_runtime and cap_lua; it is not authenticated pairing, a Lua sandbox, an atomic file installer or a general script-execution endpoint.

## Requests

GET /api/works (no id) returns the actual visible runnable registry entries, including read-only preinstalled Works, plus strict phone-ID orphan DATA directories:

~~~json
{"ok":true,"protocol":1,"items":[{"id":"dino_app","display_name":"Dino","builtin":true,"state":"stopped"}]}
~~~

The directory is independent of the capped UI runtime catalog. Orphans have their ID as display_name and state not_found. builtin records are read-only. A directory listing is not an atomic snapshot across concurrent external file mutations.

GET /api/works?id=mb_581f2da22d254b84_v1_5e0c173a

POST /api/works, Content-Type: application/json, at most 512 bytes:

~~~json
{"id":"mb_581f2da22d254b84_v1_5e0c173a","action":"publish"}
~~~

Actions: publish, start, stop, delete. IDs must match mb_[a-f0-9]{16}_v[1-9][0-9]*_[a-f0-9]{8} and the registry's 63-character limit. No caller-supplied path or Lua is executed by this route.

~~~json
{"ok":true,"protocol":1,"id":"mb_581f2da22d254b84_v1_5e0c173a","state":"running","error":""}
~~~

States: not_found, stopped, queued, running, stopping, failed. not_found means absent from the runtime catalog, not absence of files. Invalid commands return 400. Registry/runtime errors return 409. HTTP success and queued are not running; running is a job state, not proof of rendering or correct behavior. The current error field does not include a complete Lua stack trace.

## Lifecycle and ownership

The startup thread prepares the Works worker/mutex before the UI starts. After app_claw_start initializes the registry, HTTP route registration attaches registry notifications and loads the catalog even if the Works page has never opened. A NULL init config preserves any existing UI callback. The Works page can attach its normal changed callback later.

Publication validates the DATA manifest and reloads the registry; its change callback refreshes Works. Existing catalog IDs are no-ops for publication. Upload, readback, publication and start remain separate steps. Current file APIs are not transactional, and externally modifying an installed directory is not prevented by this API.

start/stop enqueue intent through the existing Works worker. Duplicate start on queued/running and stop on stopped/stopping are no-ops. Start while stopping, or while another Works job is active, is rejected. Queue full restores the previous state. Existing local toggle remains available. This is state-level duplicate handling, not durable request-ID idempotency across completed jobs or reboots. Jobs outside Works still rely on existing display/runtime arbitration.

delete removes only one confirmed mb_ DATA directory via app_registry_remove. It rejects read-only entries (including those outside the capped catalog), active states and nonempty job IDs. A runtime reservation prevents the same work being started/toggled during deletion; registry callbacks run without the runtime lock. The phone stops an active target and waits for stopped/failed first; a racing start causes deletion to fail rather than removing running files. Deletion refreshes Works and persists recent entries. A missing target is accepted; successful response state is not_found. Clients must additionally read the directory/files back, since not_found alone does not prove file absence. File deletion is not transactional: an I/O failure may leave a partially removed directory; the phone must refresh before retrying and must not delete its local source.

Phone clients must verify code/manifest, never attach model credentials, avoid automatic write retries, and query after an ambiguous result. Disconnection or cancellation does not establish whether the device ran the command. Only use on a trusted LAN; permissions, device identity binding, execution budgets and richer receipts remain future work.
