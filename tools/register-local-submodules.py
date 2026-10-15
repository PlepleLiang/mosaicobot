"""Turn clean upstream snapshots into pinned submodules of this repository."""
from pathlib import Path
import subprocess
import shutil

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parents[1]
DEPS = [
    ("projects/esp-mosaico-claw/third-party/esp-claw", "mosaicobot-firmware/third-party/esp-claw", "https://github.com/espressif/esp-claw.git"),
    ("projects/esp-mosaico-vibe/submodule/esp-mosaico-bsp", "esp-mosaico-vibe-custom/submodule/esp-mosaico-bsp", "https://github.com/esp-mosaico/esp-mosaico-bsp.git"),
    ("projects/esp-mosaico-vibe/submodule/raylib-lite-engine", "esp-mosaico-vibe-custom/submodule/raylib-lite-engine", "https://github.com/espressif2022/raylib-lite-engine.git"),
]


def git(*args, cwd):
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


for source, target, url in DEPS:
    original = ROOT / source
    dest = HERE / target
    commit = git("rev-parse", "HEAD", cwd=original)
    if git("status", "--porcelain", cwd=original):
        raise RuntimeError(f"Upstream dependency has local changes: {source}")
    # Restore every tracked source the source-only copier omitted, except .git.
    for line in git("ls-files", cwd=original).splitlines():
        src = original / line
        dst = dest / line
        if src.is_file() and not dst.exists():
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
    if not (dest / ".git").exists():
        git("init", cwd=dest)
        git("fetch", str(original), commit, cwd=dest)
        git("reset", "--mixed", commit, cwd=dest)
        git("remote", "add", "origin", url, cwd=dest)
    changes = git("diff", "--name-only", cwd=dest)
    if changes:
        raise RuntimeError(f"Copied upstream differs: {target}: {changes}")
    git("submodule", "add", url, target, cwd=HERE)
    print(target, commit)
print("Pinned clean dependencies registered; customized utils remains ordinary source")
