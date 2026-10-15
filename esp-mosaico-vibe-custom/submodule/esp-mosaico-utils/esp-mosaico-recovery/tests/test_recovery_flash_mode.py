"""Exercise ROM reset selection without opening a device or changing images."""
from pathlib import Path
import shutil
import subprocess

import pytest

HELPER = Path(__file__).resolve().parents[1] / "firmware/recovery/cmake/recovery_flash_mode.cmake"


@pytest.mark.parametrize("already_in_rom,after", [("ON", "hard-reset"), ("ON", "no-reset"), ("OFF", "hard-reset")])
def test_reset_selection_preserves_security_and_other_flags(tmp_path, already_in_rom, after):
    cmake = shutil.which("cmake")
    assert cmake, "CMake is required"
    before = "no-reset" if already_in_rom == "ON" else "default-reset"
    script = tmp_path / "check.cmake"
    dollar = chr(36)
    script.write_text(f'''include("{HELPER.as_posix()}")
set(original "--before=default-reset;--after={after};--no-stub")
mosaico_recovery_flash_args(result {already_in_rom} {dollar}{{original}})
if(NOT result STREQUAL "--before={before};--after={after};--no-stub")
    message(FATAL_ERROR "Unexpected args: {dollar}{{result}}")
endif()
if(NOT original STREQUAL "--before=default-reset;--after={after};--no-stub")
    message(FATAL_ERROR "Caller arguments changed")
endif()
''', encoding="utf-8")
    subprocess.run([cmake, "-P", str(script)], check=True, capture_output=True, text=True)
