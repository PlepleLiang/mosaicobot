"""Normalize GSPC 0.5 Windows depfiles before CMake/Ninja consume them."""
from pathlib import Path
import re
import sys


def normalize(text: str) -> str:
    # GSPC doubles path separators and includes the Win32 extended-path prefix.
    # Preserve Make escaping for spaces/hash signs and escape drive colons.
    text = text.replace("\\\\\\\\?\\\\", "").replace("\\\\", "/")
    return re.sub(r"\b([A-Za-z]):/", r"\1\\:/", text)


if __name__ == "__main__":
    path = Path(sys.argv[1])
    path.write_text(normalize(path.read_text(encoding="utf-8")), encoding="utf-8")
