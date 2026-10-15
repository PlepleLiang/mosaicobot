"""Finish source-only migration without altering the original checkouts."""
from pathlib import Path
import importlib.util
import json
import os
import shutil
import subprocess

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
# Shared operational skills are source documentation, not machine credentials.
for source in ["projects/esp-mosaico-vibe/.agents/skills"]:
    dest = HERE / "esp-mosaico-vibe-custom/.agents/skills"
    shutil.copytree(ROOT / source, dest, dirs_exist_ok=True)
# Deeper ignore rules take precedence over root exceptions.
(HERE / "mosaicobot-firmware/components/gen_bmgr_codes/.gitignore").write_text("# Board source is included for reproducible builds.\n", encoding="utf-8")
for name in ["mosaicobot-firmware/.gitignore", "esp-mosaico-vibe-custom/.gitignore"]:
    p = HERE / name
    s = p.read_text(encoding="utf-8")
    s = s.replace("dependencies.lock\n", "").replace("**/dependencies.lock\n", "")
    p.write_text(s, encoding="utf-8")
# Preserve the old machine-independent resolution, not message-source parsing.
p = HERE / "mosaicobot-firmware/CMakeLists.txt"
s = p.read_text(encoding="utf-8")
s = s.replace('EDGE_AGENT_BOARD_PATH\n            "${EDGE_AGENT_BOARD_PATH_LINE}"\n        )', 'EDGE_AGENT_BOARD_PATH\n            "${EDGE_AGENT_BOARD_PATH_LINE}"\n        )\n        if(EDGE_AGENT_BOARD_PATH MATCHES "CMAKE_CURRENT_LIST_DIR")\n            get_filename_component(EDGE_AGENT_BOARD_PATH\n                "${CMAKE_SOURCE_DIR}/boards/esp_mosaico" ABSOLUTE)\n        endif()')
p.write_text(s, encoding="utf-8")
# Record the actual modified source paths; baseline SHA alone is insufficient.
p = HERE / "tools/source-snapshot.json"
m = json.loads(p.read_text(encoding="utf-8"))
for repo in list(m["repositories"]):
    changes = subprocess.check_output(["git", "-C", str(ROOT / repo), "status", "--porcelain"], text=True)
    m.setdefault("local_changes", {})[repo] = changes.splitlines()
p.write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
print("Board source ignore rules, resource path and shared skills repaired")
