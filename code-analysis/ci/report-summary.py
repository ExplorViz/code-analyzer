#!/usr/bin/env python3
"""Render markdown summaries for Checkstyle CI job logs (GitHub Step Summary / GitLab)."""

from __future__ import annotations

import argparse
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


def summarize_checkstyle(xml_path: Path, root: Path, limit: int) -> str:
    if not xml_path.is_file():
        return f"## Checkstyle\n\nNo report found at `{xml_path.as_posix()}`.\n"

    tree = ET.parse(xml_path)
    issues: list[tuple[str, str, str, str]] = []
    for file_node in tree.getroot().findall("file"):
        file_name = _relativize(file_node.get("name", ""), root)
        for error in file_node.findall("error"):
            line = error.get("line", "?")
            severity = error.get("severity", "unknown")
            message = error.get("message", "")
            rule = error.get("source", "")
            issues.append((severity, file_name, line, f"{rule}: {message}" if rule else message))

    lines = [
        "## Checkstyle",
        "",
        f"**Total findings:** {len(issues)}",
        "",
    ]
    if not issues:
        lines.append("_No violations reported._")
        lines.append("")
        return "\n".join(lines)

    lines.extend(
        [
            "| Severity | Location | Message |",
            "| --- | --- | --- |",
        ]
    )
    for severity, file_name, line, message in issues[:limit]:
        location = f"`{file_name}:{line}`"
        safe_message = message.replace("|", "\\|")
        lines.append(f"| {severity} | {location} | {safe_message} |")

    if len(issues) > limit:
        lines.append("")
        lines.append(f"_Showing {limit} of {len(issues)} findings._")

    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", type=Path, help="Path to the Checkstyle XML report.")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path.cwd(),
        help="Repository root used to shorten absolute file paths.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=25,
        help="Maximum number of findings to include in the table.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Optional file to write markdown to (defaults to stdout).",
    )
    args = parser.parse_args()

    markdown = summarize_checkstyle(args.xml, args.root.resolve(), args.limit)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        sys.stdout.write(markdown)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
