"""Read-only partition inspection must never become an alternate flash path."""
import hashlib
from pathlib import Path
import struct
import sys
from types import SimpleNamespace
from unittest.mock import Mock, patch

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from mosaico_cli import rom_layout
from mosaico_cli.commands import recover
from mosaico_cli.errors import DeviceError


def table(entries=None):
    if entries is None:
        entries = [(1, 2, 0x200000, 0x6000, b"nvs", 0)]
    raw = b"".join(struct.pack("<HBBII16sI", 0x50AA, *e) for e in entries)
    raw += b"\xeb\xeb" + b"\xff" * 14 + hashlib.md5(raw).digest()
    return raw.ljust(rom_layout.TABLE_SIZE, b"\xff")


def test_valid_table():
    assert rom_layout.parse_partition_table(table()) == [dict(
        type=1, subtype=2, offset=0x200000, size=0x6000, label="nvs", flags=0)]


@pytest.mark.parametrize("data", [b"", b"\xff" * 0xC00, table()[:-1],
    bytes([0]) + table()[1:], table()[:50] + b"\x00" + table()[51:],
    table()[:-1] + b"\x00",
    table([(1, 2, 0x200001, 0x6000, b"nvs", 0)]),
    table([(1, 2, 0x1000000, 0x6000, b"nvs", 0)]),
    table([(1, 2, 0x200000, 0x6000, b"nvs", 0), (1, 2, 0x201000, 0x6000, b"dup", 0)]),
])
def test_reject_invalid_tables(data):
    with pytest.raises(ValueError):
        rom_layout.parse_partition_table(data)


@pytest.mark.parametrize("dry_run,mac", [(False, "30:ed:a0:12:34:56"), (True, None)])
def test_reject_inspection_without_safe_flags_before_any_gateway(dry_run, mac):
    with patch("mosaico_cli.commands.ensure_gateway") as gateway:
        with pytest.raises(DeviceError, match="requires --dry-run"):
            recover(SimpleNamespace(inspect_layout=True, dry_run=dry_run, hardware_mac=mac), Mock())
        gateway.assert_not_called()


def test_read_only_commands_and_evidence(tmp_path):
    identity = {"argv": ["python", "-m", "esptool", "--chip", "esp32s31", "--port", "{port}",
                         "--before", "no-reset", "--after", "no-reset", "--no-stub", "read-mac"], "env": {}}
    context = Mock(directory=tmp_path)
    def host(ctx, session, spec, **kwargs):
        assert spec["action"] == "host.probe"
        assert spec["endpoint"] == "COM18"
        assert len(spec["commands"]) == 3
        assert spec["commands"][0]["expect"]["value"] == "30:ed:a0:12:34:56"
        for command, offset in zip(spec["commands"][1:], ("0x8000", "0x9000")):
            argv = command["argv"]
            assert argv[:-4] == identity["argv"][:-1]
            assert argv[-4:-1] == ["read-flash", offset, "0xc00"]
            Path(argv[-1]).write_bytes(table() if offset == "0x8000" else b"\xff" * 0xC00)
        return {"operation_id": "read-only-op"}
    with patch.object(rom_layout, "rom_identity_command", return_value=identity), \
         patch.object(rom_layout, "run_host_operation", side_effect=host):
        result = rom_layout.inspect_rom_layout(context, object(), object(), "COM18", Path("/idf"), "30:ed:a0:12:34:56", 30)
    assert result["read_only"]
    assert [c["valid"] for c in result["candidates"]] == [True, False]
    assert (tmp_path / "layout-inspection.json").exists()


def test_missing_artifact_is_not_treated_as_erased_flash(tmp_path):
    with patch.object(rom_layout, "rom_identity_command", return_value={"argv": ["read-mac"], "env": {}}), \
         patch.object(rom_layout, "run_host_operation", return_value={"operation_id": "op"}):
        with pytest.raises(DeviceError, match="artifact"):
            rom_layout.inspect_rom_layout(Mock(directory=tmp_path), object(), object(), "COM18", Path("/idf"), "30:ed:a0:12:34:56", 30)
