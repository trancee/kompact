#!/usr/bin/env python3
"""Validate the maintained repeat/scalar benchmark report, not timing thresholds."""

import argparse
import json
import math
from pathlib import Path

RETAINED_FIELDS = {
    "benchmark", "mode", "threads", "forks", "jmhVersion", "jdkVersion",
    "vmName", "vmVersion", "compilationMode", "configurationName",
    "warmupIterations", "warmupTime", "warmupBatchSize",
    "measurementIterations", "measurementTime", "measurementBatchSize",
    "params", "advanced", "primaryMetric", "secondaryMetrics",
}


def retained_results(results):
    """Keep measurement metadata/samples, excluding machine paths and JVM properties."""
    return [
        {key: value for key, value in result.items() if key in RETAINED_FIELDS}
        for result in results
    ]


def validate_results(results, profile):
    prefix = "ch.trancee.kompact.benchmark."
    counts = ("65",) if profile == "smoke" else ("65", "4096", "65536")
    methods = ("fixedIndex",) if profile == "fixed" else ("fixedIndex", "variableIndex")
    expected = {
        (prefix + "RepeatIndexBenchmark." + method, count)
        for method in methods
        for count in counts
    }
    if profile != "fixed":
        expected.update(
            (prefix + "ScalarBenchmark." + method, None)
            for method in ("read", "write", "allocationControl")
        )
    if not isinstance(results, list):
        raise ValueError("expected a JSON array of benchmark records")
    seen = set()
    for result in results:
        if not isinstance(result, dict):
            raise ValueError("expected a benchmark object")
        identity = (result["benchmark"], result.get("params", {}).get("count"))
        if identity not in expected or identity in seen:
            raise ValueError("unexpected or duplicate benchmark case")
        seen.add(identity)
        metric = result["primaryMetric"]
        samples = [sample for fork in metric["rawData"] for sample in fork]
        values = [metric["score"], *samples]
        if (
            result["mode"] != "avgt"
            or metric["scoreUnit"] != "ns/op"
            or result["warmupIterations"] < 1
            or len(samples) < (1 if profile == "smoke" else 5)
            or any(
                type(value) not in (int, float) or not math.isfinite(value) or value <= 0
                for value in values
            )
        ):
            raise ValueError("expected positive ns/op measurements with warmup and raw samples")
    if seen != expected:
        raise ValueError("benchmark report is missing maintained cases")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=("main", "smoke", "fixed"), required=True)
    parser.add_argument("--retain-dir", type=Path)
    parser.add_argument("reports", nargs="+", type=Path)
    arguments = parser.parse_args()
    for path in arguments.reports:
        try:
            results = json.loads(path.read_text())
            validate_results(results, arguments.profile)
            if arguments.retain_dir is not None:
                arguments.retain_dir.mkdir(parents=True, exist_ok=True)
                with (arguments.retain_dir / path.name).open("x") as destination:
                    json.dump(retained_results(results), destination, indent=2)
                    destination.write("\n")
        except (OSError, ValueError, KeyError, TypeError) as error:
            parser.exit(1, f"{path}: invalid benchmark report ({error})\n")
        print(f"{path}: valid {arguments.profile} measurements")


if __name__ == "__main__":
    main()
