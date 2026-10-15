"""Copy the approved source snapshot without modifying original projects."""
from pathlib import Path
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]
DEST = ROOT / "projects/mosaicobot"
SKIP_DIRS = {".git", ".gradle", ".kotlin", ".codex-runs", ".pytest_cache",
             "__pycache__", "node_modules", "managed_components", ".venv",
             ".codex", ".cursor", ".idea", ".cache", ".mosaico-state",
             ".tools", "firmware_output", "artifacts", ".agents"}
SKIP_FILES = {".mcp.json", "local.properties", "sdkconfig", "sdkconfig.old",
              "sdkconfig.priv", "sdkconfig.bmgr_board.old", ".git-credentials"}
SOURCES = {"projects/esp-mosaico-claw": "mosaicobot-firmware",
           "projects/mosaicobot-android": "mosaicobot-android",
           "projects/esp-mosaico-vibe": "esp-mosaico-vibe-custom",
           "docs/mosaicobot": "docs"}


def excluded(path, directory=False):
    name = path.name
    return (name in SKIP_DIRS or name in SKIP_FILES or (directory and (name == "build"
            or name.startswith(("build-", "build_"))))
            or name.startswith(".env") or name.endswith((".pyc", ".jks", ".keystore"))
            or name.endswith(".local.json") or name == ".git")


def main():
    manifest = {"sources": {}, "repositories": {}}
    for source, target in SOURCES.items():
        count = size = 0
        for folder, dirs, files in os.walk(ROOT / source):
            dirs[:] = [d for d in dirs if not excluded(Path(d), directory=True)]
            for name in files:
                src = Path(folder) / name
                if excluded(src):
                    continue
                dst = DEST / target / src.relative_to(ROOT / source)
                if dst.exists():
                    raise RuntimeError(f"Refusing to overwrite {dst}")
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
                count += 1
                size += src.stat().st_size
        manifest["sources"][source] = {"target": target, "files": count, "bytes": size}
    for repo in ["projects/esp-mosaico-claw", "projects/esp-mosaico-vibe",
                 "projects/esp-mosaico-claw/third-party/esp-claw",
                 "projects/esp-mosaico-vibe/submodule/esp-mosaico-utils",
                 "projects/esp-mosaico-vibe/submodule/esp-mosaico-bsp",
                 "projects/esp-mosaico-vibe/submodule/raylib-lite-engine"]:
        head = subprocess.check_output(["git", "-C", str(ROOT / repo), "rev-parse", "HEAD"], text=True).strip()
        manifest["repositories"][repo] = head
    (DEST / "tools/source-snapshot.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest["sources"], indent=2))


if __name__ == "__main__":
    main()
