"""Bounded, read-only ROM partition inspection through the managed host API.

This is evidence, never permission to migrate or erase. Only the two historical
Mosaico partition-table locations are read; no arbitrary flash dump interface.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct
from typing import Any

from .errors import DeviceError
from .gateway import run_host_operation
from .recovery import rom_identity_command

TABLE_OFFSETS = (0x8000, 0x9000)
TABLE_SIZE = 0xC00
ENTRY_SIZE = 32


def parse_partition_table(data: bytes) -> list[dict[str, Any]]:
    """Require the IDF checksum; reject erased, ambiguous or damaged metadata."""
    if len(data) != TABLE_SIZE:
        raise ValueError("Partition table read has the wrong size")
    entries: list[dict[str, Any]] = []
    for position in range(0, TABLE_SIZE, ENTRY_SIZE):
        entry = data[position:position + ENTRY_SIZE]
        if entry[:16] == b"\xeb\xeb" + b"\xff" * 14:
            if not entries or hashlib.md5(data[:position]).digest() != entry[16:]:
                raise ValueError("Partition table checksum mismatch")
            if data[position + ENTRY_SIZE:] != b"\xff" * (TABLE_SIZE - position - ENTRY_SIZE):
                raise ValueError("Unexpected bytes after partition checksum")
            return entries
        magic, kind, subtype, offset, size, raw_label, flags = struct.unpack("<HBBII16sI", entry)
        if magic != 0x50AA:
            raise ValueError("No checksummed partition table at this location")
        label_bytes = raw_label.rstrip(b"\x00")
        if not label_bytes or any(c < 32 or c > 126 for c in label_bytes):
            raise ValueError("Invalid partition label")
        if not size or offset % 0x1000 or size % 0x1000 or offset + size > 0x1000000:
            raise ValueError("Partition outside the Mosaico 16 MiB flash bounds")
        if any(offset < item["offset"] + item["size"] and item["offset"] < offset + size for item in entries):
            raise ValueError("Overlapping partition entries")
        entries.append(dict(label=label_bytes.decode("ascii"), type=kind, subtype=subtype,
                            offset=offset, size=size, flags=flags))
    raise ValueError("Partition table checksum missing")


def inspect_rom_layout(context, session, model, port: str, idf_path: Path,
                       hardware_mac: str, timeout: float) -> dict[str, Any]:
    identity = rom_identity_command(model, "{port}", idf_path)
    identity["expect"] = {"pattern": r"(?i)MAC:\s*([0-9a-f]{2}(?::[0-9a-f]{2}){5})",
                          "value": hardware_mac}
    commands = [identity]
    paths = []
    for offset in TABLE_OFFSETS:
        path = (context.directory / f"partition-table-{offset:x}.bin").resolve()
        paths.append(path)
        commands.append({"argv": identity["argv"][:-1] +
                         ["read-flash", hex(offset), hex(TABLE_SIZE), str(path)],
                         "env": identity["env"]})
    context.status("layout: reading only partition metadata; no reset, stub or flash write")
    completed = run_host_operation(context, session, {
        "action": "host.probe", "endpoint": port, "commands": commands,
        "timeout": timeout,
    }, timeout=timeout + 15)
    candidates = []
    for offset, path in zip(TABLE_OFFSETS, paths):
        try:
            data = path.read_bytes()
        except OSError as error:
            raise DeviceError("Partition metadata read did not produce an artifact") from error
        candidate: dict[str, Any] = {"offset": offset, "path": str(path),
                                     "sha256": hashlib.sha256(data).hexdigest()}
        try:
            candidate["partitions"] = parse_partition_table(data)
            candidate["valid"] = True
        except ValueError as error:
            candidate.update(valid=False, reason=str(error))
        candidates.append(candidate)
    result = {"hardware_mac": hardware_mac, "operation_id": completed["operation_id"],
              "read_only": True, "candidates": candidates,
              "warning": "Layout evidence only; does not authorize recovery or data migration."}
    (context.directory / "layout-inspection.json").write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result
