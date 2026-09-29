#!/usr/bin/env python3
"""Convert Checkstyle XML reports into GitLab Code Quality JSON."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def _relativize(path: str, root: Path) -> str:
    try:
        file_path = Path(path)
        if file_path.is_absolute():
            return file_path.relative_to(root).as_posix()
    except ValueError:
        pass
    return path.replace("\\", "/")


def _fingerprint(*parts: str) -> str:
    payload = "|".join(parts).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _checkstyle_severity(raw: str | None) -> str:
    match (raw or "").lower():
        case "error":
            return "major"
        case "warning":
            return "minor"
        case _:
            return "info"


def parse_checkstyle(xml_path: Path, root: Path) -> list[dict]:
    tree = ET.parse(xml_path)
    issues: list[dict] = []
    for file_node in tree.getroot().findall("file"):
        path = _relativize(file_node.get("name", ""), root)
        for error in file_node.findall("error"):
            line = int(error.get("line", "1"))
            message = error.get("message", "")
            check_name = error.get("source", "checkstyle")
            severity = _checkstyle_severity(error.get("severity"))
            issues.append(
                {
                    "description": f"[Checkstyle] {message}",
                    "check_name": check_name,
                    "fingerprint": _fingerprint("checkstyle", check_name, path, str(line), message),
                    "severity": severity,
                    "location": {"path": path, "lines": {"begin": line}},
                }
            )
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Checkstyle XML report path.")
    parser.add_argument("output", type=Path, help="GitLab Code Quality JSON output path.")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path.cwd(),
        help="Repository root used to shorten absolute file paths.",
    )
    args = parser.parse_args()

    if not args.input.is_file():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text("[]\n", encoding="utf-8")
        print(f"No input report at {args.input}; wrote empty Code Quality JSON.", file=sys.stderr)
        return 0

    root = args.root.resolve()
    issues = parse_checkstyle(args.input, root)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(issues, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(issues)} issue(s) to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
