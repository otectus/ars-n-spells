#!/usr/bin/env python3
"""Fail closed when ForgeGradle exits zero without completing the requested GameTests.

Only parses ANS/Minecraft evidence; dependency-owned recipe warnings are not gameplay
failures. Scenario counts are optional-mod integration cases, not the total test count.
"""
import argparse
from pathlib import Path
import re
import sys


FAILURE_PATTERNS = (
    r"No test functions were given",
    r"Mixin apply failed",
    r"Failed to decode",
    r"\b[1-9]\d* required tests failed\b",
    r"Encountered an unexpected exception",
    r"Failed to start the minecraft server",
)


def verify(text: str, *, required: int, executed: int, skipped: int,
           present=(), absent=()) -> list[str]:
    errors = []
    passed = re.findall(r"\bAll (\d+) required tests passed\b", text)
    if passed != [str(required)]:
        errors.append(f"Expected one completion of {required} required tests; found {passed}")
    counts = re.findall(r"ANS-GAMETEST executed=(\d+) skipped=(\d+)\b", text)
    if counts != [(str(executed), str(skipped))]:
        errors.append(f"Expected one scenario report ({executed} executed, {skipped} skipped); found {counts}")
    for pattern in FAILURE_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            errors.append(f"GameTest log contains failure signature: {pattern}")
    profiles = re.findall(r"ANS-PROFILE mod=(\S+) expected=(present|absent) version=(\S+)", text)
    for mod, expected in [(m, "present") for m in present] + [(m, "absent") for m in absent]:
        matches = [(state, version) for name, state, version in profiles if name == mod]
        if len(matches) != 1 or matches[0][0] != expected:
            errors.append(f"Expected one {expected} profile assertion for {mod}; found {matches}")
        elif (matches[0][1] == "<absent>") != (expected == "absent"):
            errors.append(f"Runtime identity contradicts {mod}={expected}: {matches[0][1]}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--required", type=int, required=True)
    parser.add_argument("--executed", type=int, required=True)
    parser.add_argument("--skipped", type=int, required=True)
    parser.add_argument("--present", action="append", default=[])
    parser.add_argument("--absent", action="append", default=[])
    args = parser.parse_args()
    try:
        text = args.log.read_text(encoding="utf-8", errors="replace")
    except OSError as error:
        print(f"GameTest evidence unavailable: {error}", file=sys.stderr)
        return 1
    errors = verify(text, required=args.required, executed=args.executed, skipped=args.skipped,
                    present=args.present, absent=args.absent)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Verified {args.required} required tests; optional scenarios: "
          f"{args.executed} executed, {args.skipped} not exercised")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
