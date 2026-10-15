"""GSPC 0.5 Windows depfile normalization regression."""
import importlib.util
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / "firmware/recovery/tools/normalize_gsp_depfile.py"
spec = importlib.util.spec_from_file_location("normalize_gsp_depfile", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def test_extended_windows_paths_keep_make_escaping():
    prefix = "\\\\\\\\?\\\\"
    source = prefix + r"D:\\My\ Project\\bundle.gspb: " + prefix + r"D:\\My\ Project\\icon\#1.png" + "\n"
    expected = r"D\:/My\ Project/bundle.gspb: D\:/My\ Project/icon\#1.png" + "\n"
    assert module.normalize(source) == expected
    assert module.normalize(expected) == expected


def test_posix_paths_unchanged():
    source = "/tmp/bundle.gspb: /tmp/icon.png\n"
    assert module.normalize(source) == source
