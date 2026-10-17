import copy
import unittest

from check_results import retained_results, validate_results


def smoke_results():
    prefix = "ch.trancee.kompact.benchmark."
    return [
        {
            "benchmark": prefix + name,
            "params": parameters,
            "mode": "avgt",
            "warmupIterations": 1,
            "primaryMetric": {"score": 1.0, "scoreUnit": "ns/op", "rawData": [[1.0]]},
        }
        for name, parameters in (
            ("RepeatIndexBenchmark.fixedIndex", {"count": "65"}),
            ("RepeatIndexBenchmark.variableIndex", {"count": "65"}),
            ("ScalarBenchmark.read", {}),
            ("ScalarBenchmark.write", {}),
            ("ScalarBenchmark.allocationControl", {}),
        )
    ]


class CheckResultsTest(unittest.TestCase):
    def test_retains_measurements_without_machine_paths_or_properties(self):
        results = smoke_results()
        results[0].update({"jvm": "/local/tool/bin/java", "jvmArgs": ["-Duser.language=en"]})
        expected = smoke_results()

        retained = retained_results(results)

        self.assertEqual(retained, expected)

    def test_accepts_complete_finite_smoke_measurements(self):
        results = smoke_results()

        validate_results(results, "smoke")

        self.assertEqual(len(results), 5)

    def test_rejects_non_finite_primary_measurements(self):
        for invalid in (float("inf"), float("-inf"), float("nan")):
            with self.subTest(invalid=invalid):
                results = smoke_results()
                results[0]["primaryMetric"]["score"] = invalid

                with self.assertRaises(ValueError):
                    validate_results(results, "smoke")

    def test_rejects_invalid_raw_samples(self):
        for invalid in (float("inf"), float("nan"), 0.0, -1.0, True, "NaN"):
            with self.subTest(invalid=invalid):
                results = smoke_results()
                results[0]["primaryMetric"]["rawData"] = [[invalid]]

                with self.assertRaises(ValueError):
                    validate_results(results, "smoke")

    def test_rejects_missing_cases(self):
        results = smoke_results()[:-1]

        with self.assertRaises(ValueError):
            validate_results(results, "smoke")

    def test_rejects_duplicate_cases(self):
        results = smoke_results()
        results[-1] = copy.deepcopy(results[0])

        with self.assertRaises(ValueError):
            validate_results(results, "smoke")

    def test_rejects_zero_warmup(self):
        results = smoke_results()
        results[0]["warmupIterations"] = 0

        with self.assertRaises(ValueError):
            validate_results(results, "smoke")

    def test_rejects_wrong_units(self):
        results = smoke_results()
        results[0]["primaryMetric"]["scoreUnit"] = "ms/op"

        with self.assertRaises(ValueError):
            validate_results(results, "smoke")

    def test_rejects_empty_samples(self):
        results = smoke_results()
        results[0]["primaryMetric"]["rawData"] = []

        with self.assertRaises(ValueError):
            validate_results(results, "smoke")

    def test_accepts_fixed_profile_with_all_sizes_and_samples(self):
        template = smoke_results()[0]
        results = []
        for count in ("65", "4096", "65536"):
            result = copy.deepcopy(template)
            result["params"]["count"] = count
            result["primaryMetric"]["rawData"] = [[1.0] * 5]
            results.append(result)

        validate_results(results, "fixed")

        self.assertEqual(len(results), 3)

    def test_rejects_smoke_in_place_of_full_measurements(self):
        results = smoke_results()

        with self.assertRaises(ValueError):
            validate_results(results, "main")


if __name__ == "__main__":
    unittest.main()
