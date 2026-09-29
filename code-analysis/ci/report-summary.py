#!/usr/bin/env python3
"""Render markdown summaries for CI job logs (GitHub Step Summary / GitLab)."""

from __future__ import annotations

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timedelta
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


def _parse_junit_seconds(raw: str | None) -> float:
    if not raw:
        return 0.0
    normalized = raw.strip().replace(",", "")
    try:
        return float(normalized)
    except ValueError:
        return 0.0


def _iter_testcases(suite: ET.Element):
    for testcase in suite.findall("testcase"):
        yield testcase
    for nested in suite.findall("testsuite"):
        yield from _iter_testcases(nested)


def _effective_suite_duration(suite: ET.Element) -> float:
    suite_time = _parse_junit_seconds(suite.get("time"))
    if suite_time > 0:
        return suite_time
    return sum(_parse_junit_seconds(testcase.get("time")) for testcase in _iter_testcases(suite))


def _suite_interval(suite: ET.Element) -> tuple[datetime, datetime] | None:
    timestamp = suite.get("timestamp")
    if not timestamp:
        return None
    try:
        start = datetime.fromisoformat(timestamp)
    except ValueError:
        return None
    duration = _effective_suite_duration(suite)
    return start, start + timedelta(seconds=duration)


def _total_execution_seconds(roots: list[ET.Element]) -> float:
    intervals: list[tuple[datetime, datetime]] = []
    fallback_sum = 0.0

    for root in roots:
        if root.tag == "testsuites":
            root_time = _parse_junit_seconds(root.get("time"))
            child_suites = root.findall("testsuite")
            if root_time > 0 and child_suites:
                child_suite_time = sum(_parse_junit_seconds(s.get("time")) for s in child_suites)
                if child_suite_time <= 0:
                    fallback_sum += root_time
                    for suite in child_suites:
                        interval = _suite_interval(suite)
                        if interval:
                            intervals.append(interval)
                    continue

        for suite in _iter_testsuites(root):
            fallback_sum += _effective_suite_duration(suite)
            interval = _suite_interval(suite)
            if interval:
                intervals.append(interval)

    if intervals:
        return (max(end for _, end in intervals) - min(start for start, _ in intervals)).total_seconds()
    return fallback_sum


def _format_duration(seconds: float) -> str:
    if seconds < 60:
        return f"{seconds:.2f} s"
    minutes, remainder = divmod(seconds, 60)
    return f"{int(minutes)} min {remainder:.2f} s"


def _format_test_duration(seconds: float) -> str:
    if seconds < 1:
        return f"{seconds * 1000:.0f} ms"
    return f"{seconds:.2f} s"


def _escape_table_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ").strip()


@dataclass
class TestCaseResult:
    classname: str
    name: str
    time: float
    outcome: str
    message: str = ""
    details: str = ""


@dataclass
class JunitSummary:
    test_cases: list[TestCaseResult]
    total_duration: float

    @property
    def total(self) -> int:
        return len(self.test_cases)

    @property
    def passed(self) -> int:
        return sum(1 for test in self.test_cases if test.outcome == "passed")

    @property
    def failed(self) -> int:
        return sum(1 for test in self.test_cases if test.outcome == "failed")

    @property
    def errors(self) -> int:
        return sum(1 for test in self.test_cases if test.outcome == "error")

    @property
    def skipped(self) -> int:
        return sum(1 for test in self.test_cases if test.outcome == "skipped")


@dataclass
class ReportMetadata:
    commit_sha: str | None = None
    branch: str | None = None
    workflow_name: str | None = None
    run_number: str | None = None
    os_name: str | None = None
    runtime: str | None = None
    runtime_version: str | None = None
    test_framework: str | None = None
    test_framework_version: str | None = None
    test_exit_code: int | None = None


def _node_text(node: ET.Element | None) -> str:
    if node is None:
        return ""
    message = (node.get("message") or "").strip()
    body = (node.text or "").strip()
    if message and body:
        return f"{message}\n{body}"
    return message or body


def _parse_testcase(testcase: ET.Element) -> TestCaseResult:
    name = testcase.get("name", "?")
    classname = testcase.get("classname", "?")
    time = _parse_junit_seconds(testcase.get("time"))

    skipped = testcase.find("skipped")
    if skipped is not None:
        message = _node_text(skipped) or "Skipped"
        return TestCaseResult(classname, name, time, "skipped", message)

    failure = testcase.find("failure")
    if failure is not None:
        message = (failure.get("message") or "").strip()
        details = _node_text(failure)
        if not message and details:
            message = details.splitlines()[0]
        return TestCaseResult(classname, name, time, "failed", message, details)

    error = testcase.find("error")
    if error is not None:
        message = (error.get("message") or "").strip()
        details = _node_text(error)
        if not message and details:
            message = details.splitlines()[0]
        return TestCaseResult(classname, name, time, "error", message, details)

    return TestCaseResult(classname, name, time, "passed")


def load_junit_summary(results_path: Path) -> JunitSummary | None:
    if results_path.is_file():
        xml_files = [results_path]
    elif results_path.is_dir():
        xml_files = sorted(results_path.rglob("TEST-*.xml"))
    else:
        return None

    if not xml_files:
        return None

    test_cases: list[TestCaseResult] = []
    roots: list[ET.Element] = []

    for xml_file in xml_files:
        try:
            tree = ET.parse(xml_file)
        except ET.ParseError:
            continue
        root = tree.getroot()
        roots.append(root)
        for suite in _iter_testsuites(root):
            for testcase in _iter_testcases(suite):
                test_cases.append(_parse_testcase(testcase))

    test_cases.sort(key=lambda test: (test.classname, test.name))
    total_duration = _total_execution_seconds(roots)
    return JunitSummary(test_cases=test_cases, total_duration=total_duration)


def _coverage_percent(missed: int, covered: int) -> float:
    total = missed + covered
    if total == 0:
        return 0.0
    return covered * 100.0 / total


def load_jacoco_coverage(jacoco_path: Path) -> dict[str, float] | None:
    if not jacoco_path.is_file():
        return None

    tree = ET.parse(jacoco_path)
    root = tree.getroot()
    if root.tag != "report":
        return None

    mapping = {
        "LINE": "lines",
        "BRANCH": "branches",
        "METHOD": "functions",
        "INSTRUCTION": "statements",
    }
    coverage: dict[str, float] = {}
    for counter in root.findall("counter"):
        counter_type = counter.get("type")
        key = mapping.get(counter_type or "")
        if not key:
            continue
        missed = int(counter.get("missed", 0))
        covered = int(counter.get("covered", 0))
        coverage[key] = _coverage_percent(missed, covered)

    return coverage or None


def _metadata_from_env() -> ReportMetadata:
    commit_sha = os.environ.get("REPORT_COMMIT_SHA") or os.environ.get("GITHUB_SHA") or os.environ.get(
        "CI_COMMIT_SHA"
    )
    branch = (
        os.environ.get("REPORT_BRANCH")
        or os.environ.get("GITHUB_HEAD_REF")
        or os.environ.get("GITHUB_REF_NAME")
        or os.environ.get("CI_COMMIT_REF_NAME")
    )
    workflow_name = os.environ.get("REPORT_WORKFLOW") or os.environ.get("GITHUB_WORKFLOW")
    run_number = os.environ.get("REPORT_RUN_NUMBER") or os.environ.get("GITHUB_RUN_NUMBER")
    os_name = os.environ.get("REPORT_OS") or os.environ.get("RUNNER_OS") or os.environ.get("CI_RUNNER_DESCRIPTION")
    runtime = os.environ.get("REPORT_RUNTIME") or "Java"
    runtime_version = os.environ.get("REPORT_RUNTIME_VERSION") or os.environ.get("JAVA_VERSION")
    test_framework = os.environ.get("REPORT_TEST_FRAMEWORK") or "JUnit Jupiter"
    test_framework_version = os.environ.get("REPORT_TEST_FRAMEWORK_VERSION")

    exit_code_raw = os.environ.get("REPORT_TEST_EXIT_CODE")
    test_exit_code = int(exit_code_raw) if exit_code_raw is not None and exit_code_raw != "" else None

    return ReportMetadata(
        commit_sha=commit_sha,
        branch=branch,
        workflow_name=workflow_name,
        run_number=run_number,
        os_name=os_name,
        runtime=runtime,
        runtime_version=runtime_version,
        test_framework=test_framework,
        test_framework_version=test_framework_version,
        test_exit_code=test_exit_code,
    )


def _test_label(test: TestCaseResult) -> str:
    return f"`{test.classname}.{test.name}`"


def _resolve_status(summary: JunitSummary | None, metadata: ReportMetadata) -> str:
    if summary is not None and summary.failed + summary.errors > 0:
        return "Failed ❌"
    if metadata.test_exit_code not in (None, 0):
        return "Failed ❌"
    if summary is None or summary.total == 0:
        return "No tests executed"
    return "Passed ✅"


def _pass_rate(summary: JunitSummary) -> float:
    executed = summary.passed + summary.failed + summary.errors
    if executed == 0:
        return 0.0
    return summary.passed * 100.0 / executed


def _append_report_context(lines: list[str], metadata: ReportMetadata) -> None:
    context_lines: list[str] = []
    if metadata.commit_sha:
        context_lines.append(f"**Commit:** `{metadata.commit_sha}`")
    if metadata.branch:
        context_lines.append(f"**Branch:** `{metadata.branch}`")
    if metadata.workflow_name:
        context_lines.append(f"**Workflow:** {metadata.workflow_name}")
    if metadata.run_number:
        context_lines.append(f"**Run:** {metadata.run_number}")
    if context_lines:
        lines.append("  \n".join(context_lines))
        lines.append("")


def _render_missing_junit_report(results_path: Path, metadata: ReportMetadata) -> str:
    status = _resolve_status(None, metadata)
    lines = [
        "# Unit Test Report",
        "",
        "## Summary",
        "",
        "| Metric | Result |",
        "| --- | --- |",
        f"| Status | {status} |",
        "",
        f"No test results found at `{results_path.as_posix()}`.",
        "",
    ]
    _append_report_context(lines, metadata)
    return "\n".join(lines)


def summarize_junit(
    results_path: Path,
    jacoco_path: Path | None = None,
    metadata: ReportMetadata | None = None,
) -> str:
    metadata = metadata or _metadata_from_env()
    summary = load_junit_summary(results_path)
    if summary is None:
        return _render_missing_junit_report(results_path, metadata)

    coverage = load_jacoco_coverage(jacoco_path) if jacoco_path else None

    failed_total = summary.failed + summary.errors
    pass_rate = _pass_rate(summary)
    status = _resolve_status(summary, metadata)

    lines = [
        "# Unit Test Report",
        "",
        "## Summary",
        "",
        "| Metric | Result |",
        "| --- | --- |",
        f"| Total tests | {summary.total} |",
        f"| Passed | {summary.passed} ✅ |",
        f"| Failed | {failed_total} ❌ |",
        f"| Skipped | {summary.skipped} ⏭️ |",
        f"| Pass rate | {pass_rate:.1f}% |",
        f"| Duration | {_format_duration(summary.total_duration)} |",
        f"| Status | {status} |",
        "",
    ]

    _append_report_context(lines, metadata)

    environment_items: list[str] = []
    if metadata.os_name:
        environment_items.append(f"- **OS:** {metadata.os_name}")
    if metadata.runtime:
        environment_items.append(f"- **Runtime:** {metadata.runtime}")
    if metadata.runtime_version:
        environment_items.append(f"- **Runtime version:** {metadata.runtime_version}")
    if metadata.test_framework:
        environment_items.append(f"- **Test framework:** {metadata.test_framework}")
    if metadata.test_framework_version:
        environment_items.append(f"- **Test framework version:** {metadata.test_framework_version}")

    if environment_items:
        lines.extend(["## Environment", ""])
        lines.extend(environment_items)
        lines.append("")

    if coverage:
        lines.extend(
            [
                "## Coverage",
                "",
                "| Metric | Coverage |",
                "| --- | ---: |",
            ]
        )
        labels = {
            "lines": "Lines",
            "branches": "Branches",
            "functions": "Functions",
            "statements": "Statements",
        }
        for key, label in labels.items():
            if key in coverage:
                lines.append(f"| {label} | {coverage[key]:.1f}% |")
        lines.append("")

    lines.extend(["## Detailed Test Results", ""])

    executed = [test for test in summary.test_cases if test.outcome != "skipped"]
    if executed:
        lines.extend(
            [
                "| Test | Duration |",
                "| --- | ---: |",
            ]
        )
        for test in executed:
            lines.append(f"| {_test_label(test)} | {_format_test_duration(test.time)} |")
        lines.append("")

    error_cases = [test for test in summary.test_cases if test.outcome == "error"]
    if error_cases:
        lines.extend(
            [
                "| Test | Error |",
                "| --- | --- |",
            ]
        )
        for test in error_cases:
            lines.append(f"| {_test_label(test)} | {_escape_table_cell(test.message)} |")
        lines.append("")

    detail_cases = [
        test for test in summary.test_cases if test.outcome in {"failed", "error"}
    ]
    if detail_cases:
        lines.append("### Failure Details")
        lines.append("")
        for test in detail_cases:
            lines.append(f"**{_test_label(test)}**")
            lines.append("")
            trace = test.details or test.message or "_No failure details recorded._"
            lines.append("```")
            lines.append(trace)
            lines.append("```")
            lines.append("")

    skipped_cases = [test for test in summary.test_cases if test.outcome == "skipped"]
    if skipped_cases:
        lines.extend(
            [
                "| Test | Reason |",
                "| --- | --- |",
            ]
        )
        for test in skipped_cases:
            lines.append(f"| {_test_label(test)} | {_escape_table_cell(test.message)} |")
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
        "--jacoco",
        type=Path,
        help="Optional JaCoCo XML report for coverage metrics.",
    )

    parser.add_argument(
        "--output",
        type=Path,
        help="Optional file to write markdown to (defaults to stdout).",
    )

    argv = sys.argv[1:]
    if argv and argv[0] not in {"checkstyle", "junit"} and not argv[0].startswith("-"):
        argv = ["checkstyle", *argv]

    args = parser.parse_args(argv)
    if args.command is None:
        parser.error("the following arguments are required: command")

    if args.command == "checkstyle":
        markdown = summarize_checkstyle(args.xml, args.root.resolve(), args.limit)
    else:
        markdown = summarize_junit(args.results, args.jacoco)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    else:
        sys.stdout.write(markdown)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
