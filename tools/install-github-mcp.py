"""Install the official Windows GitHub MCP release with checksum verification."""
from pathlib import Path
import hashlib
import json
import urllib.request
import zipfile

version = "2.0.2"
name = "github-mcp-server_Windows_x86_64.zip"
base = f"https://github.com/github/github-mcp-server/releases/download/v{version}/"
root = Path.home() / ".stepgo/tools/github-mcp-server" / version
root.mkdir(parents=True, exist_ok=True)
checksums = urllib.request.urlopen(base + f"github-mcp-server_{version}_checksums.txt", timeout=60).read().decode()
expected = next(line.split()[0] for line in checksums.splitlines() if line.split()[-1] == name)
data = urllib.request.urlopen(base + name, timeout=180).read()
actual = hashlib.sha256(data).hexdigest()
if actual != expected:
    raise RuntimeError("Official release checksum mismatch")
archive = root / name
archive.write_bytes(data)
with zipfile.ZipFile(archive) as z:
    for entry in z.infolist():
        dest = (root / entry.filename).resolve()
        if not dest.is_relative_to(root.resolve()):
            raise RuntimeError("Unsafe archive path")
    z.extractall(root)
(root / "release-verification.json").write_text(json.dumps({"version": version, "source": base + name, "sha256": actual}, indent=2) + "\n")
print("Official GitHub MCP", version, "SHA256 verified")
print(root / "github-mcp-server.exe")
