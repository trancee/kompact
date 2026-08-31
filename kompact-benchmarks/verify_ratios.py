#!/usr/bin/env python3

import json
import pathlib
import sys

MAX_RATIO = 1.10
REFERENCE_SUFFIX = "Reference"


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    reports = sorted(root.rglob("jvm.json"), key=lambda path: path.stat().st_mtime)
    if not reports:
        raise SystemExit(f"no JVM benchmark report found under {root}")
    report = reports[-1]
    entries = {entry["benchmark"]: entry for entry in json.loads(report.read_text())}
    failures: list[str] = []
    checked = 0
    for reference_name, reference in sorted(entries.items()):
        if not reference_name.endswith(REFERENCE_SUFFIX):
            continue
        generated_name = reference_name[: -len(REFERENCE_SUFFIX)]
        generated = entries.get(generated_name)
        if generated is None:
            failures.append(f"missing generated benchmark for {reference_name}")
            continue
        for entry in (generated, reference):
            metric = entry["primaryMetric"]
            if entry["mode"] != "thrpt" or metric["scoreUnit"] != "ops/ns":
                failures.append(f"incomparable mode or unit for {entry['benchmark']}")
        generated_score = float(generated["primaryMetric"]["score"])
        reference_score = float(reference["primaryMetric"]["score"])
        if generated_score <= 0.0 or reference_score <= 0.0:
            failures.append(f"non-positive score for {generated_name}")
            continue
        latency_ratio = reference_score / generated_score
        checked += 1
        print(f"{generated_name}: latency ratio {latency_ratio:.4f} (limit {MAX_RATIO:.2f})")
        if latency_ratio > MAX_RATIO:
            failures.append(f"{generated_name} ratio {latency_ratio:.4f} exceeds {MAX_RATIO:.2f}")
    if checked == 0:
        failures.append("no generated/reference benchmark pairs found")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
