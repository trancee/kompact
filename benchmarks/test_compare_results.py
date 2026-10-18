import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from check_results import validate_comparison
from test_check_results import holder_results, smoke_results


def jvm_results():
    results = holder_results(full=True)
    for result in results:
        result.update({
            "jdkVersion": "test-jdk",
            "vmVersion": "test-vm",
            "vmName": "test-vm",
            "jmhVersion": "1.37",
            "threads": 1,
            "warmupTime": "300 ms",
            "warmupBatchSize": 1,
            "measurementTime": "300 ms",
            "measurementBatchSize": 1,
        })
    return results


def native_results():
    results = []
    for count in ("65", "4096", "65536"):
        result = copy.deepcopy(smoke_results()[0])
        result.update({
            "params": {"count": count},
            "compilationMode": "Release",
            "configurationName": "fixed",
            "warmupIterations": 5,
            "warmupTime": "300 ms",
            "measurementIterations": 5,
            "measurementTime": "300 ms",
        })
        result["primaryMetric"]["rawData"] = [[1.0] * 5]
        results.append(result)
    return results


class CompareResultsTest(unittest.TestCase):
    def test_cli_help_describes_the_optional_comparison(self):
        command = [sys.executable, str(Path(__file__).with_name("check_results.py")), "--help"]

        result = subprocess.run(command, capture_output=True, text=True)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--baseline", result.stdout)

    def test_cli_reports_a_regression_as_a_nonzero_typed_error(self):
        baseline = jvm_results()
        candidate = copy.deepcopy(baseline)
        candidate[0]["primaryMetric"]["score"] = 1.2
        with tempfile.TemporaryDirectory() as directory:
            baseline_path = Path(directory) / "baseline.json"
            candidate_path = Path(directory) / "candidate.json"
            baseline_path.write_text(json.dumps(baseline))
            candidate_path.write_text(json.dumps(candidate))
            command = [
                sys.executable, str(Path(__file__).with_name("check_results.py")),
                "--profile", "holders-main", "--baseline", str(baseline_path), str(candidate_path),
            ]

            result = subprocess.run(command, capture_output=True, text=True)

            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("timing regression exceeds 10%", result.stderr)

    def test_accepts_the_exact_budget_boundary_independently_of_record_order(self):
        baseline = jvm_results()
        candidate = copy.deepcopy(baseline)
        candidate[0]["primaryMetric"]["score"] = 1.1
        candidate.reverse()

        result = validate_comparison(baseline, candidate, "holders-main")

        self.assertIsNone(result)

    def test_rejects_a_timing_regression_above_the_budget(self):
        baseline = jvm_results()
        candidate = copy.deepcopy(baseline)
        candidate[0]["primaryMetric"]["score"] = 1.100001

        with self.assertRaisesRegex(ValueError, "fixedDecode.*65"):
            validate_comparison(baseline, candidate, "holders-main")

    def test_applies_the_budget_to_unchanged_controls_too(self):
        baseline = jvm_results()
        candidate = copy.deepcopy(baseline)
        candidate[-1]["primaryMetric"]["score"] = 1.2

        with self.assertRaisesRegex(ValueError, "allocationControl"):
            validate_comparison(baseline, candidate, "holders-main")

    def test_rejects_missing_or_incomparable_environment_and_profile_metadata(self):
        for field in (
            "jdkVersion", "vmVersion", "warmupTime", "measurementTime", "threads",
            "warmupBatchSize", "measurementBatchSize",
        ):
            for missing in (False, True):
                with self.subTest(field=field, missing=missing):
                    baseline = jvm_results()
                    candidate = copy.deepcopy(baseline)
                    if missing:
                        del candidate[0][field]
                    else:
                        candidate[0][field] = "different"

                    with self.assertRaisesRegex(ValueError, "metadata"):
                        validate_comparison(baseline, candidate, "holders-main")

    def test_rejects_incomplete_baselines_before_comparing(self):
        baseline = jvm_results()[:-1]
        candidate = jvm_results()

        with self.assertRaisesRegex(ValueError, "missing"):
            validate_comparison(baseline, candidate, "holders-main")

    def test_rejects_unknown_environment_values_even_when_both_reports_match(self):
        for field, value in (
            ("jdkVersion", None), ("vmVersion", ""), ("vmName", None),
            ("threads", 0), ("warmupTime", None), ("measurementTime", ""),
        ):
            with self.subTest(field=field):
                baseline = jvm_results()
                baseline[0][field] = value
                candidate = copy.deepcopy(baseline)

                with self.assertRaisesRegex(ValueError, "metadata"):
                    validate_comparison(baseline, candidate, "holders-main")

    def test_rejects_null_baselines_instead_of_skipping_comparison(self):
        candidate = jvm_results()

        with self.assertRaisesRegex(ValueError, "JSON array"):
            validate_comparison(None, candidate, "holders-main")

    def test_rejects_different_parameters_or_profiling_modes(self):
        for field, value in (
            ("params", {"count": "65", "other": "value"}),
            ("secondaryMetrics", {"gc.alloc.rate.norm": {}}),
        ):
            with self.subTest(field=field):
                baseline = jvm_results()
                candidate = copy.deepcopy(baseline)
                candidate[0][field] = value

                with self.assertRaisesRegex(ValueError, "metadata"):
                    validate_comparison(baseline, candidate, "holders-main")

    def test_rejects_nonfinite_scores_on_either_side(self):
        for invalid_side in (0, 1):
            with self.subTest(invalid_side=invalid_side):
                reports = [jvm_results(), jvm_results()]
                reports[invalid_side][0]["primaryMetric"]["score"] = float("nan")

                with self.assertRaisesRegex(ValueError, "positive"):
                    validate_comparison(*reports, "holders-main")

    def test_rejects_smoke_reports_as_a_timing_budget(self):
        baseline = holder_results()
        candidate = copy.deepcopy(baseline)

        with self.assertRaisesRegex(ValueError, "smoke"):
            validate_comparison(baseline, candidate, "holders-smoke")

    def test_accepts_comparable_release_native_measurements(self):
        baseline = native_results()
        candidate = copy.deepcopy(baseline)

        result = validate_comparison(baseline, candidate, "fixed")

        self.assertIsNone(result)

    def test_rejects_debug_native_measurements(self):
        baseline = native_results()
        candidate = copy.deepcopy(baseline)
        candidate[0]["compilationMode"] = "Debug"

        with self.assertRaisesRegex(ValueError, "Release"):
            validate_comparison(baseline, candidate, "fixed")


if __name__ == "__main__":
    unittest.main()
