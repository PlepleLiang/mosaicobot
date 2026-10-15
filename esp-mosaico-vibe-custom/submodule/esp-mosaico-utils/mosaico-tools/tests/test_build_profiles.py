"""Isolated builds must neither reuse old artifacts nor ignore configured CSVs."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

import pytest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("profile_runner", ROOT / "skills/idf-low-noise-build/scripts/idf_low_noise_build.py")
RUNNER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUNNER
SPEC.loader.exec_module(RUNNER)


def test_build_warning_is_not_misidentified_as_linker():
    diagnostic = RUNNER.find_diagnostic("patch; continuing build: error: patch failed")
    assert diagnostic["category"] != "linker"


def test_build_directory_and_definitions_are_forwarded(tmp_path, monkeypatch):
    (tmp_path / "CMakeLists.txt").write_text("project(test)")
    calls = []
    monkeypatch.setattr(RUNNER, "resolve_idf_path", lambda *a: tmp_path)
    monkeypatch.setattr(RUNNER, "run_build_step", lambda **kw: calls.append(kw) or 0)
    assert RUNNER.main(["--project", str(tmp_path), "build", "--build-dir", "build-profile", "-D", "PROFILE=retained"]) == 0
    assert calls[0]["arguments"] == ["-B", str(tmp_path / "build-profile"), "-D", "PROFILE=retained", "build"]
    assert calls[0]["build_dir"] == tmp_path / "build-profile"


def test_profile_artifacts_do_not_include_default_build(tmp_path):
    for name in ("build", "build-profile"):
        (tmp_path / name).mkdir()
        (tmp_path / name / (name + ".bin")).write_bytes(b"firmware")
    artifacts = RUNNER.collect_artifacts(tmp_path, tmp_path / "build-profile")
    assert len(artifacts) == 1
    assert artifacts[0]["path"] == str(tmp_path / "build-profile/build-profile.bin")


@pytest.mark.parametrize("absolute", [False, True])
def test_system_bundle_uses_configured_custom_csv(tmp_path, absolute):
    custom = tmp_path / "partitions_profile.csv"
    custom.write_text("# selected table")
    csv = custom.as_posix() if absolute else custom.name
    cmake = shutil.which("cmake")
    assert cmake
    source = '\n'.join([
        'cmake_minimum_required(VERSION 3.16)',
        'project(profile LANGUAGES NONE)',
        'add_custom_target(flash)', 'add_custom_target(app-flash)',
        'add_custom_target(app)', 'add_custom_target(partition_table_bin)',
        'add_custom_target(resource COMMAND "${CMAKE_COMMAND}" -E touch "${CMAKE_BINARY_DIR}/system.bin")',
        'set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_LABELS system)',
        'set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_system_IMAGE "${CMAKE_BINARY_DIR}/system.bin")',
        'set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_system_TARGET resource)',
        f'set(ESP_IRIS_PYTHON "{Path(sys.executable).as_posix()}")',
        'set(CONFIG_PARTITION_TABLE_CUSTOM ON)',
        f'set(CONFIG_PARTITION_TABLE_FILENAME "{csv}")',
        f'include("{(ROOT / "cmake/system_update.cmake").as_posix()}")',
        f'if(NOT system_update_partition_csv STREQUAL "{custom.as_posix()}")',
        'message(FATAL_ERROR "wrong partition table")', 'endif()',
    ])
    (tmp_path / "CMakeLists.txt").write_text(source)
    subprocess.run([cmake, "-G", "Ninja", "-S", str(tmp_path), "-B", str(tmp_path / "build")], check=True, capture_output=True)
    subprocess.run([cmake, "--build", str(tmp_path / "build"), "--target", "system-update-bundle", "--", "-n"],
                   check=True, capture_output=True)
