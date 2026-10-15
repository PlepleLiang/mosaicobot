"""Archive original Git history and omitted local evidence before cleanup."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
BACKUP = HERE / '.migration-backup'
BACKUP.mkdir(exist_ok=True)
spec = importlib.util.spec_from_file_location('snapshot', HERE / 'tools/import-local-snapshot.py')
snapshot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(snapshot)
repos = ['projects/esp-mosaico-claw', 'projects/esp-mosaico-claw/third-party/esp-claw',
         'projects/esp-mosaico-vibe', 'projects/esp-mosaico-vibe/submodule/esp-mosaico-utils',
         'projects/esp-mosaico-vibe/submodule/esp-mosaico-bsp',
         'projects/esp-mosaico-vibe/submodule/raylib-lite-engine']
for i, repo in enumerate(repos):
    dest = BACKUP / 'git' / str(i)
    dest.mkdir(parents=True, exist_ok=True)
    bundle = dest / 'history.bundle'
    if not bundle.exists():
        subprocess.run(['git', '-C', str(ROOT / repo), 'bundle', 'create', str(bundle), '--all'], check=True)
    subprocess.run(['git', '-C', str(ROOT / repo), 'bundle', 'verify', str(bundle)], check=True, capture_output=True)
    for args, name in [(['diff', '--binary', 'HEAD'], 'worktree.patch'), (['status', '--porcelain'], 'status.txt')]:
        data = subprocess.check_output(['git', '-C', str(ROOT / repo), *args])
        (dest / name).write_bytes(data)
    (dest / 'source.txt').write_text(repo + '\n', encoding='utf-8')
records = []
for source, target in snapshot.SOURCES.items():
    for folder, dirs, files in os.walk(ROOT / source):
        dirs[:] = [d for d in dirs if d not in {'.git', 'node_modules', 'managed_components', '.gradle', '.kotlin', '__pycache__', '.pytest_cache', '.venv'} and not d.startswith(('build-', 'build_')) and d != 'build']
        for name in files:
            src = Path(folder) / name
            if name == '.git':
                continue
            relative = src.relative_to(ROOT / source)
            new = HERE / target / relative
            # Archive excluded data and changed old versions; do not overwrite new code.
            if not new.is_file() or hashlib.sha256(src.read_bytes()).digest() != hashlib.sha256(new.read_bytes()).digest():
                dest = BACKUP / 'original-files' / target / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dest)
                records.append(str(src.relative_to(ROOT)))
(BACKUP / 'archive-manifest.json').write_text(json.dumps({'repositories': repos, 'archived_original_files': records}, indent=2) + '\n', encoding='utf-8')
print('Verified Git bundles:', len(repos))
print('Archived omitted/changed originals:', len(records))
print('Private archive:', BACKUP)
