"""Hardware-free regression coverage for the physical iOS test runner."""

import os
from pathlib import Path
import plistlib
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone


FIXTURE_COMMAND = r'''
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys

arguments = sys.argv[1:]
command = Path(sys.argv[0]).name
root = Path(os.environ["IOS_RUNNER_FIXTURE"])
case = os.environ.get("IOS_RUNNER_CASE", "success")
device = "synthetic-ios-device"
identifier = "11111111-2222-3333-4444-555555555555"
bundle = "test.kompact.nativetests"

def output_json(value):
    value = json.dumps({"result": value})
    if "--json-output" in arguments:
        destination = arguments[arguments.index("--json-output") + 1]
        if destination != "-":
            Path(destination).write_text(value)
            return
    print(value)

if command == "file":
    print("fixture: Mach-O 64-bit executable arm64")
elif command == "vtool":
    print("platform IOS\nminos 15.0")
elif command == "security":
    if arguments[0] == "cms":
        profile = Path(arguments[arguments.index("-i") + 1])
        if "-o" in arguments:
            shutil.copyfile(profile, arguments[arguments.index("-o") + 1])
        else:
            print(profile.read_text())
    else:
        print('  1) ' + hashlib.sha1(b"public fixture certificate").hexdigest().upper() + ' "Fixture signing identity"')
elif command == "xcodebuild":
    print("Xcode 27.1\nBuild version fixture")
elif command == "codesign":
    pass
elif command == "xcrun":
    if arguments[0] == "lldb":
        joined = " ".join(arguments)
        if identifier not in joined or "device process attach --pid 321" not in joined:
            sys.exit("Fixture rejected an ambiguous debugger target")
        if "continue_tests 321" not in joined:
            sys.exit("Fixture requires the initial-stop-aware debugger command")
        (root / "resume").write_text("resumed")
        if case == "debugger-failure":
            sys.exit("Private fixture debugger detail")
        print("Kompact Native test process exited with status 0.")
    elif arguments[1:3] == ["list", "devices"]:
        output_json({"devices": [{
            "identifier": identifier,
            "hardwareProperties": {"udid": device, "platform": "iOS"},
            "deviceProperties": {"name": "Private fixture name", "osVersionNumber": "18.7.8", "developerModeStatus": "enabled"},
            "connectionProperties": {"pairingState": "paired", "tunnelState": "connected"},
        }]})
    elif arguments[2:4] == ["install", "app"]:
        output_json({"installedApplications": [{
            "bundleID": bundle, "launchServicesIdentifier": "fixture-installation-token",
            "installationURL": "file:///fixture/KompactNativeTests.app/",
        }]})
    elif arguments[2:4] == ["info", "processes"]:
        processes = [{
            "processIdentifier": 999, "executable": "file:///fixture/Unrelated.app/KompactNativeTests",
        }]
        if (root / "launched").exists():
            processes.append({
                "processIdentifier": 321,
                "executable": "file:///fixture/KompactNativeTests.app/KompactNativeTests",
            })
        output_json({"runningProcesses": processes})
    elif arguments[2:4] == ["process", "launch"]:
        if "--start-stopped" not in arguments:
            print("App terminated due to signal 9.")
            sys.exit(1)
        (root / "launched").touch()
        if (root / "resume").read_text() != "resumed":
            sys.exit("Fixture rejected execution without debugger attachment")
        print("[==========] Running 404 tests.")
        if case == "native-failure":
            print("[  FAILED  ] 1 test.")
            sys.exit(1)
        if case != "missing-summary":
            print("[  PASSED  ] 404 tests.")
    else:
        sys.exit("Unexpected fixture device command")
else:
    sys.exit("Unexpected fixture tool")
'''


class DeviceRunnerTest(unittest.TestCase):
    def run_device_runner(self, case="success"):
        with tempfile.TemporaryDirectory(prefix="kompact-ios-runner-test-") as temporary:
            root = Path(temporary)
            tools = root / "tools"
            tools.mkdir()
            fixture = tools / "fixture"
            fixture.write_text(f"#!{sys.executable}\n" + FIXTURE_COMMAND)
            fixture.chmod(0o755)
            for name in ("file", "vtool", "security", "xcodebuild", "codesign", "xcrun"):
                (tools / name).symlink_to(fixture.name)
            binary = root / "test.kexe"
            binary.write_bytes(b"synthetic test executable")
            profile = root / "development.mobileprovision"
            profile.write_bytes(plistlib.dumps({
                "TeamIdentifier": ["TESTTEAM01"],
                "Entitlements": {
                    "application-identifier": "TESTTEAM01.test.kompact.nativetests",
                    "get-task-allow": True,
                },
                "ExpirationDate": (datetime.now(timezone.utc) + timedelta(days=1)).replace(tzinfo=None),
                "ProvisionedDevices": ["synthetic-ios-device"],
                "DeveloperCertificates": [b"public fixture certificate"],
            }))
            os.mkfifo(root / "resume")
            environment = {
                **os.environ, "PATH": str(tools) + os.pathsep + os.environ["PATH"],
                "IOS_RUNNER_FIXTURE": str(root), "KOMPACT_IOS_DEVICE_UDID": "",
                "TMPDIR": str(root), "IOS_RUNNER_CASE": case,
            }
            runner = Path(__file__).with_name("test-ios-device.sh")

            result = subprocess.run(
                ["bash", str(runner), str(binary), str(profile)],
                capture_output=True, text=True, timeout=30, env=environment,
            )

            self.assertNotIn("Private fixture name", result.stdout + result.stderr)
            for private_value in (
                "synthetic-ios-device", "11111111-2222-3333-4444-555555555555",
                "TESTTEAM01", "test.kompact.nativetests",
            ):
                self.assertNotIn(private_value, result.stdout + result.stderr)
            self.assertEqual([], list(root.glob("kompact-ios-device-tests.*")))
            return result

    def test_standalone_suite_outlives_launch_watchdog_with_exact_debugger_target(self):
        result = self.run_device_runner()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("[  PASSED  ] 404 tests.", result.stdout)

    def test_debugger_failure_rejects_a_passing_console(self):
        result = self.run_device_runner("debugger-failure")

        self.assertEqual(1, result.returncode)
        self.assertIn("Native test debugger failed", result.stdout)
        self.assertNotIn("Private fixture debugger detail", result.stdout + result.stderr)

    def test_native_failure_rejects_a_successful_debugger(self):
        result = self.run_device_runner("native-failure")

        self.assertEqual(1, result.returncode)
        self.assertIn("[  FAILED  ] 1 test.", result.stdout)

    def test_missing_summary_rejects_successful_process_exits(self):
        result = self.run_device_runner("missing-summary")

        self.assertEqual(1, result.returncode)
        self.assertIn("did not report a passing Kotlin/Native test summary", result.stderr)


if __name__ == "__main__":
    unittest.main()
