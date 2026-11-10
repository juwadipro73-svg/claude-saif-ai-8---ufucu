#!/usr/bin/env python3
"""Package only a prepared, bounded project tree; never archive runner caches or symlinks."""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

MAX_FILES = 200
MAX_BYTES = 300 * 1024 * 1024
SKIP_DIRS = {".gradle", "build", "node_modules", ".git", "out", ".idea"}


def package_source(source: Path, target: Path) -> int:
    source = source.resolve(strict=True)
    target = target.resolve()
    if not source.is_dir() or source == target or source in target.parents:
        raise ValueError("ZIP output must be outside the isolated source directory")
    files = []
    total = 0
    for entry in sorted(source.rglob("*")):
        relative = entry.relative_to(source)
        if any(part in SKIP_DIRS for part in relative.parts) or entry.is_symlink() or not entry.is_file():
            continue
        if source not in entry.resolve().parents:
            raise ValueError("Project file escapes the source directory")
        total += entry.stat().st_size
        files.append((entry, relative.as_posix()))
        if len(files) > MAX_FILES or total > MAX_BYTES:
            raise ValueError("ZIP source exceeds the 200-file / 300 MB limit")
    if not files:
        raise ValueError("No project sources to package")
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6,
                         allowZip64=False) as archive:
        for entry, name in files:
            archive.write(entry, arcname=name)
    return len(files)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: package_source.py SOURCE_DIR OUTPUT.zip")
    print(f"Packaged {package_source(Path(sys.argv[1]), Path(sys.argv[2]))} vetted source files")
