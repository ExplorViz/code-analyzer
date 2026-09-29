#!/usr/bin/env python3
"""Render markdown summaries for CI job logs (GitHub Step Summary / GitLab)."""

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


def _iter_testsuites(root: ET.Element) -> list[ET.Element]:
    if root.tag == "testsuites":
        return root.findall("testsuite")
    if root.tag == "testsuite":
        return [root]
    return []


def _format_duration(seconds: float) -> str:
    if seconds < 60:
        return f"{seconds:.1f} s"
    minutes, remainder = divmod(seconds, 60)
    return f"{int(minutes)} min {remainder:.1f} s"


def _collect_test_failures(testsuite: ET.Element) -> list[tuple[str, str, str, str]]:
    failures: list[tuple[str, str, str, str]] = []
    for testcase in testsuite.findall("testcase"):
        failure = testcase.find("failure")
        error = testcase.find("error")
        if failure is None and error is None:
            continue
        node = failure if failure is not None else error
        kind = "Failed" if failure is not None else "Error"
        name = testcase.get("name", "?")
        classname = testcase.get("classname", "?")
        message = (node.get("message") or "").strip()
        if not message and node.text:
            message = node.text.strip().splitlines()[0]
        failures.append((kind, classname, name, message))
    return failures


def summarize_junit(results_path: Path, limit: int) -> str:
    if results_path.is_file():
        xml_files = [results_path]
    elif results_path.is_dir():
        xml_files = sorted(results_path.rglob("TEST-*.xml"))
    else:
        return f"## Unit Tests\n\nNo test results found at `{results_path.as_posix()}`.\n"

    if not xml_files:
        return f"## Unit Tests\n\nNo JUnit XML reports found under `{results_path.as_posix()}`.\n"

    suites = 0
    tests = 0
    failures = 0
    errors = 0
    skipped = 0
    duration = 0.0
    failed_cases: list[tuple[str, str, str, str]] = []

    for xml_file in xml_files:
        try:
            tree = ET.parse(xml_file)
        except ET.ParseError:
            continue
        file_suites = _iter_testsuites(tree.getroot())
        if not file_suites:
            continue
        suites += len(file_suites)
        for suite in file_suites:
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0))
            errors += int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            duration += float(suite.get("time", 0) or 0)
            failed_cases.extend(_collect_test_failures(suite))

    passed = max(tests - failures - errors - skipped, 0)
    lines = [
        "## Unit Tests",
        "",
        "| Metric | Count |",
        "| --- | ---: |",
        f"| Test suites | {suites} |",
        f"| Tests (total) | {tests} |",
        f"| Passed | {passed} |",
        f"| Failed | {failures} |",
        f"| Errors | {errors} |",
        f"| Skipped | {skipped} |",
        f"| Duration | {_format_duration(duration)} |",
        "",
    ]

    if failed_cases:
        lines.extend(
            [
                "### Failures and errors",
                "",
                "| Result | Test class | Test | Message |",
                "| --- | --- | --- | --- |",
            ]
        )
        for kind, classname, name, message in failed_cases[:limit]:
            safe_message = message.replace("|", "\\|")
            lines.append(f"| {kind} | `{classname}` | `{name}` | {safe_message} |")
        if len(failed_cases) > limit:
            lines.append("")
            lines.append(f"_Showing {limit} of {len(failed_cases)} failures and errors._")
        lines.append("")
    elif tests == 0:
        lines.append("_No tests were executed._")
        lines.append("")
    else:
        lines.append("_All tests passed._")
        lines.append("")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command")

    checkstyle_parser = subparsers.add_parser("checkstyle", help="Summarize a Checkstyle XML report.")
    checkstyle_parser.add_argument("xml", type=Path, help="Path to the Checkstyle XML report.")
    checkstyle_parser.add_argument(
        "--root",
        type=Path,
        default=Path.cwd(),
        help="Repository root used to shorten absolute file paths.",
    )
    checkstyle_parser.add_argument(
        "--limit",
        type=int,
        default=25,
        help="Maximum number of findings to include in the table.",
    )

    junit_parser = subparsers.add_parser(
        "junit", help="Summarize Gradle/JUnit XML test results."
    )
    junit_parser.add_argument(
        "results",
        type=Path,
        help="Path to a JUnit XML file or a directory containing TEST-*.xml files.",
    )
    junit_parser.add_argument(
        "--limit",
        type=int,
        default=25,
        help="Maximum number of failures and errors to include in the table.",
    )

    parser.add_argument(
        "--output",
        type=Path,
        help="Optional file to write markdown to (defaults to stdout).",
    )

    # Legacy invocation: report-summary.py path/to/checkstyle.xml
    argv = sys.argv[1:]
    if argv and argv[0] not in {"checkstyle", "junit"} and not argv[0].startswith("-"):
        argv = ["checkstyle", *argv]

    args = parser.parse_args(argv)
    if args.command is None:
        parser.error("the following arguments are required: command")

    if args.command == "checkstyle":
        markdown = summarize_checkstyle(args.xml, args.root.resolve(), args.limit)
    else:
        markdown = summarize_junit(args.results, args.limit)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        sys.stdout.write(markdown)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
