"""Device mutations through the managed Gateway; never replay on timeout."""
from __future__ import annotations

from typing import Any

from .errors import DeviceError, OperationError
from .evidence import _live_status
from .gateway import connected_devices, ensure_gateway, gateway_json, select_device
from .runtime import RunContext


def restart_device(arguments: Any, context: RunContext) -> dict[str, Any]:
    session = ensure_gateway(context, arguments.gateway_profile)
    selected = select_device(connected_devices(context, session), arguments.device_id)
    device_id = str(selected["device_id"])
    before = _live_status(context, session, device_id)
    response = gateway_json(context, session, "restart", device_id, timeout=45)
    operation = response.get("operation", {})
    if not operation.get("operation_id") or operation.get("status") != "succeeded":
        raise OperationError("Restart is not confirmed; query its operation before another action.",
                             details={"response": response})
    after = _live_status(context, session, device_id)
    if after["boot_id"] == before["boot_id"]:
        raise DeviceError("Restart did not produce a new live Boot ID.",
                          details={"before": before, "after": after, "operation": operation})
    return {"command": "restart", "status": "succeeded", "device_id": device_id,
            "previous_boot_id": before["boot_id"], "boot_id": after["boot_id"],
            "device": after, "operation": operation, "log": str(context.log_path)}
