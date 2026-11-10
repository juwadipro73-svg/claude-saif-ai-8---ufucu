#!/usr/bin/env python3
"""
SAIF AI — licence provenance tool.

Compares this checkout against the upstream open-source project it was derived from
(AntiGem by Santosh Kurmi, https://github.com/Santoshkurmi/antigem) and reports, file by file,
how much of this tree is still upstream code and how much is original SAIF AI code.

Why this exists
---------------
"Which files are actually mine?" is the question every licence decision depends on. This script
answers it with evidence instead of guesswork, and it can be re-run after every release so the
answer never goes stale.

Usage
-----
    python3 tools/licence/provenance.py --upstream /path/to/antigem

    # or just refresh the checked-in report using the last known upstream location
    python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write

Outputs
-------
    tools/licence/saif_owned_files.txt    files that never existed upstream  (yours)
    tools/licence/upstream_derived_files.txt
                                          files that came from upstream, with a similarity score
    stdout                                the summary table

Similarity is measured as shared non-empty lines / max(lines). It is deliberately simple:
  >= 0.95  identical      - upstream code, unmodified
  >= 0.60  modified       - upstream code, edited
  >= 0.20  rewritten      - upstream code, heavily reworked (still derived)
  <  0.20  different      - effectively a different file
A file that appears only here is original unless it is a verbatim copy of an upstream file under a
different path, which this script also detects.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter

SKIP_DIRS = {".git", "build", ".gradle", "node_modules", ".aistudio", "captures", ".idea"}
TEXT_EXT = {
    ".kt", ".java", ".go", ".kts", ".gradle", ".py", ".sh", ".bash",
    ".js", ".mjs", ".ts", ".json", ".md", ".proto", ".xml", ".yml", ".yaml",
}
BUCKETS = [("identical", 0.95), ("modified", 0.60), ("rewritten", 0.20), ("different", 0.0)]


def classify(score: float) -> str:
    for name, threshold in BUCKETS:
        if score >= threshold:
            return name
    return "different"


def walk(root: str) -> dict[str, str]:
    out: dict[str, str] = {}
    if not os.path.isdir(root):
        return out
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            if os.path.splitext(name)[1] in TEXT_EXT:
                path = os.path.join(dirpath, name)
                out[os.path.relpath(path, root)] = path
    return out


def meaningful_lines(path: str) -> set[str]:
    try:
        with open(path, encoding="utf-8", errors="ignore") as handle:
            return {line.strip() for line in handle.read().split("\n") if line.strip()}
    except OSError:
        return set()


def line_count(path: str) -> int:
    return len(meaningful_lines(path))


def fingerprint(path: str) -> str:
    try:
        with open(path, encoding="utf-8", errors="ignore") as handle:
            text = re.sub(r"\s+", " ", handle.read())
    except OSError:
        return ""
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def similarity(a: set[str], b: set[str]) -> float:
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    return len(a & b) / max(len(a), len(b))


def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    default_self = os.path.abspath(os.path.join(here, "..", ".."))

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self", default=default_self, help="this checkout (default: repo root)")
    parser.add_argument("--upstream", required=True, help="path to a checkout of the upstream project")
    parser.add_argument("--write", action="store_true", help="write the file lists next to this script")
    args = parser.parse_args()

    mine = walk(args.self)
    theirs = walk(args.upstream)
    if not theirs:
        print(f"error: no source files found under {args.upstream}", file=sys.stderr)
        return 1

    shared = sorted(set(mine) & set(theirs))
    only_mine = sorted(set(mine) - set(theirs))

    # Catch "copied the file but renamed the path".
    upstream_hashes = {fingerprint(p): rel for rel, p in theirs.items()}
    renamed = [(rel, upstream_hashes[fingerprint(mine[rel])]) for rel in only_mine
               if fingerprint(mine[rel]) in upstream_hashes]

    rows = []
    per_area: Counter[tuple[str, str]] = Counter()
    per_area_lines: Counter[tuple[str, str]] = Counter()
    totals: Counter[str] = Counter()
    total_lines: Counter[str] = Counter()

    for rel in shared:
        score = similarity(meaningful_lines(theirs[rel]), meaningful_lines(mine[rel]))
        kind = classify(score)
        rows.append((rel, round(score, 3), kind))
        count = line_count(mine[rel])
        area = rel.split("/")[0] if "/" in rel else rel
        per_area[(area, kind)] += 1
        per_area_lines[(area, kind)] += count
        totals[kind] += 1
        total_lines[kind] += count

    print(f"\nSAIF AI checkout : {args.self}  ({len(mine)} source files)")
    print(f"Upstream checkout: {args.upstream}  ({len(theirs)} source files)")
    print(f"\nShared paths: {len(shared)}   Only in SAIF AI: {len(only_mine)}   Only upstream: {len(set(theirs) - set(mine))}")
    print("\nShared files, by how much of the upstream file survives here:")
    for name, _ in BUCKETS:
        print(f"  {name:10s} {totals[name]:4d} files  {total_lines[name]:7d} lines")

    print(f"\nOriginal SAIF AI files (never existed upstream): {len(only_mine)}")
    if renamed:
        print(f"  !! {len(renamed)} of those are verbatim copies of an upstream file under a new path:")
        for new, old in renamed[:20]:
            print(f"     {new}  <=  {old}")

    print("\nUpstream-derived files by area:")
    for (area, kind), count in sorted(per_area.items()):
        print(f"  {kind:10s} {count:4d} files {per_area_lines[(area, kind)]:7d} lines   {area}/")

    derived_lines = sum(total_lines.values())
    own_lines = sum(line_count(mine[r]) for r in only_mine)
    share = 100.0 * derived_lines / max(1, derived_lines + own_lines)
    print(f"\nBottom line: ~{share:.0f}% of the compared source lines still derive from the upstream GPL project.")
    print("Those files stay under the upstream licence no matter how they are renamed or reformatted.")

    if args.write:
        out_dir = here
        with open(os.path.join(out_dir, "saif_owned_files.txt"), "w", encoding="utf-8") as handle:
            handle.write("\n".join(only_mine) + "\n")
        with open(os.path.join(out_dir, "upstream_derived_files.txt"), "w", encoding="utf-8") as handle:
            handle.write("# relative path\tsimilarity\tbucket\n")
            for rel, score, kind in rows:
                handle.write(f"{rel}\t{score}\t{kind}\n")
        with open(os.path.join(out_dir, "provenance_report.json"), "w", encoding="utf-8") as handle:
            json.dump(
                {
                    "self": args.self,
                    "upstream": args.upstream,
                    "buckets": {name: {"files": totals[name], "lines": total_lines[name]} for name, _ in BUCKETS},
                    "original_file_count": len(only_mine),
                    "renamed_copies": renamed,
                    "rows": rows,
                },
                handle,
                indent=1,
            )
        print(f"\nWrote saif_owned_files.txt, upstream_derived_files.txt and provenance_report.json to {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
