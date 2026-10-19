#!/usr/bin/env python3
"""Validate benchmark reports and optionally compare full-profile timing budgets."""

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
    smoke = profile in ("smoke", "holders-smoke")
    holders = profile in ("holders-main", "holders-smoke")
    counts = ("65",) if smoke else ("65", "4096", "65536")
    if holders:
        prefix += "holders."
        repeated_class = "RepeatedHolderBenchmark."
        methods = ("fixedDecode", "variableDecode")
        scalar_class = "ScalarHolderBenchmark."
        scalar_methods = ("decode", "allocationControl")
    else:
        repeated_class = "RepeatIndexBenchmark."
        methods = ("fixedIndex",) if profile == "fixed" else ("fixedIndex", "variableIndex")
        scalar_class = "ScalarBenchmark."
        scalar_methods = (
            "read",
            "write",
            "readFrame",
            "writeFrame",
            "readUnaligned",
            "writeUnaligned",
            "allocationControl",
        )
    expected = {
        (prefix + repeated_class + method, count)
        for method in methods
        for count in counts
    }
    if profile != "fixed":
        expected.update(
            (prefix + scalar_class + method, None)
            for method in scalar_methods
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
        if holders and not smoke and (
            result["forks"] != 3
            or result["warmupIterations"] != 5
            or result["measurementIterations"] != 5
            or [len(fork) for fork in metric["rawData"]] != [5, 5, 5]
        ):
            raise ValueError("expected three holder forks with five warmup and measurement iterations")
        samples = [sample for fork in metric["rawData"] for sample in fork]
        values = [metric["score"], *samples]
        if (
            result["mode"] != "avgt"
            or metric["scoreUnit"] != "ns/op"
            or result["warmupIterations"] < 1
            or len(samples) < (1 if smoke else 5)
            or any(
                type(value) not in (int, float) or not math.isfinite(value) or value <= 0
                for value in values
            )
        ):
            raise ValueError("expected positive ns/op measurements with warmup and raw samples")
    if seen != expected:
        raise ValueError("benchmark report is missing maintained cases")


def validate_comparison(baseline, candidate, profile):
    """Check comparable full profiles against the 10% mean timing budget."""
    if profile in ("smoke", "holders-smoke"):
        raise ValueError("smoke reports do not establish a timing budget")
    validate_results(baseline, profile)
    validate_results(candidate, profile)
    references = {
        (record["benchmark"], record.get("params", {}).get("count")): record
        for record in baseline
    }
    regressions = []
    for record in candidate:
        identity = (record["benchmark"], record.get("params", {}).get("count"))
        reference = references[identity]
        native = "compilationMode" in reference or "compilationMode" in record
        if native:
            if reference.get("compilationMode") != "Release" or record.get("compilationMode") != "Release":
                raise ValueError("expected comparable Release Native measurements")
            environment = ("compilationMode", "configurationName")
        else:
            environment = (
                "jdkVersion", "vmVersion", "vmName", "jmhVersion", "threads", "forks",
                "warmupBatchSize", "measurementBatchSize",
            )
        fields = environment + (
            "warmupIterations", "warmupTime", "measurementIterations", "measurementTime",
        )
        if any(
            field not in reference or field not in record
            or not reference[field] or reference[field] != record[field]
            for field in fields
        ):
            raise ValueError(f"incomparable measurement metadata for {identity}")
        if (
            reference.get("params", {}) != record.get("params", {})
            or reference.get("advanced") != record.get("advanced")
            or reference.get("configurationName") != record.get("configurationName")
            or set(reference.get("secondaryMetrics", {})) != set(record.get("secondaryMetrics", {}))
        ):
            raise ValueError(f"incomparable parameter or profiling metadata for {identity}")
        for measurement in (reference, record):
            samples = [len(fork) for fork in measurement["primaryMetric"]["rawData"]]
            if (
                measurement["warmupIterations"] != 5
                or measurement["measurementIterations"] != 5
                or samples != ([5] if native else [5, 5, 5])
                or (not native and measurement["forks"] != 3)
            ):
                raise ValueError("expected full five-iteration profiles and three JVM forks")
        ratio = record["primaryMetric"]["score"] / reference["primaryMetric"]["score"]
        if ratio > 1.1:
            regressions.append(f"{identity}: +{(ratio - 1) * 100:.2f}%")
    if regressions:
        raise ValueError("timing regression exceeds 10%: " + "; ".join(regressions))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--profile", choices=("main", "smoke", "fixed", "holders-main", "holders-smoke"),
        required=True,
    )
    parser.add_argument("--retain-dir", type=Path)
    parser.add_argument("--baseline", type=Path, help="enforce the 10-percent budget against a comparable full report")
    parser.add_argument("reports", nargs="+", type=Path)
    arguments = parser.parse_args()
    baseline = None
    if arguments.baseline is not None:
        try:
            baseline = json.loads(arguments.baseline.read_text())
        except (OSError, ValueError) as error:
            parser.exit(1, f"{arguments.baseline}: invalid baseline ({error})\n")
    for path in arguments.reports:
        try:
            results = json.loads(path.read_text())
            validate_results(results, arguments.profile)
            if arguments.baseline is not None:
                validate_comparison(baseline, results, arguments.profile)
            if arguments.retain_dir is not None:
                arguments.retain_dir.mkdir(parents=True, exist_ok=True)
                with (arguments.retain_dir / path.name).open("x") as destination:
                    json.dump(retained_results(results), destination, indent=2)
                    destination.write("\n")
        except (OSError, ValueError, KeyError, TypeError) as error:
            parser.exit(1, f"{path}: invalid benchmark report ({error})\n")
        comparison = "; within 10% timing budget" if arguments.baseline is not None else ""
        print(f"{path}: valid {arguments.profile} measurements{comparison}")


if __name__ == "__main__":
    main()
