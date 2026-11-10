#!/usr/bin/env python3
"""
SAIF AI — copyright header tool.

Stamps `Copyright (c) SAIF AI. All rights reserved.` on the files this project actually owns, and
refuses to touch anything that came from an upstream open-source project.

The owned-file list comes from tools/licence/saif_owned_files.txt, which is produced by
provenance.py. Anything not on that list is upstream-derived and must keep its upstream licence —
stamping a SAIF AI notice on it would be a false copyright claim, so this script will not do it.

Usage
-----
    python3 tools/licence/add_saif_headers.py            # dry run, shows what would change
    python3 tools/licence/add_saif_headers.py --apply    # actually write the headers

    # re-derive the owned list first (needs an upstream checkout), then stamp
    python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
    python3 tools/licence/add_saif_headers.py --apply
"""

from __future__ import annotations

import argparse
import os
import sys

HEADER = """/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
"""

MARKER = "Copyright (c) SAIF AI"
STAMPABLE = {".kt", ".java", ".go"}

HERE = os.path.dirname(os.path.abspath(__file__))
OWNED_LIST = os.path.join(HERE, "saif_owned_files.txt")
DEFAULT_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))


def load_owned(path: str) -> set[str]:
    if not os.path.isfile(path):
        print(f"error: {path} not found. Run provenance.py --write first.", file=sys.stderr)
        raise SystemExit(1)
    with open(path, encoding="utf-8") as handle:
        return {line.strip() for line in handle if line.strip() and not line.startswith("#")}


def already_stamped(text: str) -> bool:
    return MARKER in text[:2000]


def insert_after_shebang(text: str, header: str) -> str:
    if text.startswith("#!"):
        newline = text.find("\n")
        if newline == -1:
            return text + "\n" + header
        return text[: newline + 1] + header + text[newline + 1 :]
    return header + text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=DEFAULT_ROOT, help="checkout root (default: repo root)")
    parser.add_argument("--apply", action="store_true", help="write the headers instead of a dry run")
    args = parser.parse_args()

    owned = load_owned(OWNED_LIST)
    stamped = skipped = would_stamp = 0
    refused: list[str] = []

    for rel in sorted(owned):
        path = os.path.join(args.root, rel)
        if not os.path.isfile(path):
            continue
        if os.path.splitext(rel)[1] not in STAMPABLE:
            skipped += 1
            continue
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        if already_stamped(text):
            stamped += 1
            continue
        new_text = insert_after_shebang(text, HEADER)
        would_stamp += 1
        if args.apply:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(new_text)
        else:
            print(f"would stamp: {rel}")

    # Guard rail: make sure no upstream-derived file ended up with the notice.
    derived_list = os.path.join(HERE, "upstream_derived_files.txt")
    if os.path.isfile(derived_list):
        with open(derived_list, encoding="utf-8") as handle:
            for line in handle:
                if line.startswith("#") or not line.strip():
                    continue
                rel = line.split("\t")[0].strip()
                path = os.path.join(args.root, rel)
                if os.path.isfile(path) and os.path.splitext(rel)[1] in STAMPABLE:
                    with open(path, encoding="utf-8", errors="ignore") as src:
                        if MARKER in src.read()[:2000]:
                            refused.append(rel)

    verb = "Stamped" if args.apply else "Would stamp"
    print(f"\n{verb}: {would_stamp}   already had the notice: {stamped}   non-source skipped: {skipped}")
    if refused:
        print(f"\n!! {len(refused)} upstream-derived files carry a SAIF AI notice. That is a false")
        print("!! copyright claim on someone else's GPL code - remove it:")
        for rel in refused[:20]:
            print(f"   {rel}")
        return 2
    print("\nNo upstream-derived file carries a SAIF AI notice. Good.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
