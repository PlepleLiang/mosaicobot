"""Validate the opt-in layout and preserve the user's normal sdkconfig."""
import csv
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def test_recovery_starts_early_and_mirror_attaches_after_board_init():
    main = (ROOT / "main/main.c").read_text()
    assert main.index("iris_ota_support_start();") < main.index("ESP_ERROR_CHECK(esp_board_manager_init());")
    assert main.index("ESP_ERROR_CHECK(main_install_touch_mux());") < main.index("ESP_ERROR_CHECK(iris_screen_mirror_attach_presenter());")
    assert main.index("ESP_ERROR_CHECK(iris_screen_mirror_attach_presenter());") < main.index("ESP_ERROR_CHECK(esp_iris_mark_healthy());")


def test_retained_prefix_and_original_resource_capacities():
    rows = list(csv.reader(line for line in (ROOT / "partitions_mosaicobot.csv").read_text().splitlines() if line and not line.startswith("#")))
    def number(value):
        value = value.strip()
        return int(value[:-1]) * 1024 if value.endswith("K") else int(value, 0)
    layout = {row[0].strip(): (number(row[3]), number(row[4])) for row in rows}
    assert layout["otadata"] == (0x9000, 0x2000)
    assert layout["phy_init"] == (0xb000, 0x1000)
    assert layout["sysmeta"] == (0xc000, 0x14000)
    assert layout["factory"] == (0x20000, 0x1c0000)
    assert layout["coredump"] == (0x1e0000, 0x20000)
    assert layout["ota_0"] == (0x210000, 8000 * 1024)
    assert layout["ui_apps"][1] == 3200 * 1024
    assert layout["system"][1] == 3000 * 1024
    ordered = sorted(layout.values())
    assert all(a + size <= b for (a, size), (b, _) in zip(ordered, ordered[1:]))
    assert ordered[-1][0] + ordered[-1][1] <= 16 * 1024 * 1024


def test_partition_defaults_only_touch_selected_sdkconfig(tmp_path):
    original = 'CONFIG_PARTITION_TABLE_CUSTOM_FILENAME="original.csv"\nCONFIG_SENTINEL=y\n'
    (tmp_path / "sdkconfig").write_text(original)
    build = tmp_path / "build-profile"
    build.mkdir()
    selected = build / "sdkconfig"
    selected.write_text(original)
    script = tmp_path / "test.cmake"
    script.write_text('\n'.join([
        f'set(CMAKE_SOURCE_DIR "{tmp_path.as_posix()}")',
        f'set(CMAKE_BINARY_DIR "{build.as_posix()}")',
        f'set(SDKCONFIG "{selected.as_posix()}")',
        'set(EDGE_AGENT_PARTITION_TABLE "partitions_mosaicobot.csv")',
        f'include("{(ROOT / "tools/cmake/flash_partition_defaults.cmake").as_posix()}")',
    ]))
    subprocess.run([shutil.which("cmake"), "-P", str(script)], check=True, capture_output=True)
    assert (tmp_path / "sdkconfig").read_text() == original
    assert selected.read_text() == 'CONFIG_SENTINEL=y\n'
    assert 'partitions_mosaicobot.csv' in (build / "edge_agent_partition_auto.defaults").read_text()
