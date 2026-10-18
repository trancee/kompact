"""Boundary validation for the internal physical-device test driver."""

import subprocess
import unittest
from unittest.mock import patch

import ios_device_tests


class DeviceMetadataTest(unittest.TestCase):
    def test_invalid_device_json_has_redacted_error(self):
        with patch.object(ios_device_tests, "checked_command", return_value="private invalid response"):
            with self.assertRaisesRegex(ios_device_tests.DeviceTestError, "invalid metadata"):
                ios_device_tests.device_result([], "Fixture inspection")

    def test_process_selection_uses_exact_installed_executable(self):
        processes = {"runningProcesses": [
            {"executable": "file:///fixture/Other.app/KompactNativeTests", "processIdentifier": 999},
            {"executable": "file:///fixture/KompactNativeTests.app/KompactNativeTests", "processIdentifier": 321},
        ]}
        executable = ios_device_tests.device_path("file:///fixture/KompactNativeTests.app/KompactNativeTests")

        with patch.object(ios_device_tests, "device_result", return_value=processes):
            selected = ios_device_tests.matching_processes("fixture-device", executable)

        self.assertEqual({321}, selected)

    def test_boolean_process_identifier_is_rejected(self):
        processes = {"runningProcesses": [
            {"executable": "/fixture/KompactNativeTests", "processIdentifier": True},
        ]}
        executable = ios_device_tests.device_path("/fixture/KompactNativeTests")

        with patch.object(ios_device_tests, "device_result", return_value=processes):
            with self.assertRaisesRegex(ios_device_tests.DeviceTestError, "identifier is invalid"):
                ios_device_tests.matching_processes("fixture-device", executable)

    def test_command_failure_does_not_expose_output(self):
        completed = subprocess.CompletedProcess(["fixture"], 1, "private device metadata", "private signing metadata")

        with patch.object(ios_device_tests.subprocess, "run", return_value=completed):
            with self.assertRaises(ios_device_tests.DeviceTestError) as failure:
                ios_device_tests.checked_command(["fixture"], "Fixture inspection")

        self.assertNotIn("metadata", str(failure.exception))
        self.assertIn("exit 1", str(failure.exception))

    def test_command_timeout_does_not_expose_arguments(self):
        timeout = subprocess.TimeoutExpired(["fixture", "private-device"], 1)

        with patch.object(ios_device_tests.subprocess, "run", side_effect=timeout):
            with self.assertRaises(ios_device_tests.DeviceTestError) as failure:
                ios_device_tests.checked_command(["fixture", "private-device"], "Fixture inspection")

        self.assertNotIn("private-device", str(failure.exception))
        self.assertIn("timed out", str(failure.exception))

    def test_invalid_device_paths_fail_with_redacted_error(self):
        for value in (
            None, "relative/path", "https://fixture/path", "file://remote/path",
            "file://[broken", "file:///fixture/../Other.app", "file:///fixture/%00",
        ):
            with self.subTest(value=value):
                with self.assertRaises(ios_device_tests.DeviceTestError):
                    ios_device_tests.device_path(value)


if __name__ == "__main__":
    unittest.main()
