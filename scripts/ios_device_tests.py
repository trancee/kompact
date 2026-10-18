"""Launch the signed Native test host under its development debugger."""

from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeoutError
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import signal
import subprocess
import sys
import time
from urllib.parse import unquote, urlparse
import uuid


TIMEOUT_SECONDS = 900
SUCCESS = "Kompact Native test process exited with status 0."


class DeviceTestError(Exception):
    """A device-test failure whose message contains no private device metadata."""


def checked_command(arguments, phase, timeout=60):
    try:
        completed = subprocess.run(
            arguments, capture_output=True, text=True, timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        raise DeviceTestError(f"{phase} timed out; private diagnostics withheld.") from None
    if completed.returncode:
        raise DeviceTestError(f"{phase} failed (exit {completed.returncode}); private diagnostics withheld.")
    return completed.stdout


def device_result(arguments, phase, timeout=60):
    text = checked_command(
        ["xcrun", "devicectl", *arguments, "--quiet", "--json-output", "-"], phase, timeout,
    )
    try:
        result = json.loads(text)["result"]
        if not isinstance(result, dict):
            raise ValueError
        return result
    except (KeyError, ValueError, TypeError):
        raise DeviceTestError(f"{phase} returned invalid metadata; private diagnostics withheld.") from None


def device_path(value):
    if not isinstance(value, str):
        raise DeviceTestError("Device executable metadata is missing.")
    try:
        parsed = urlparse(value)
    except ValueError:
        raise DeviceTestError("Device executable metadata is invalid.") from None
    if parsed.scheme not in ("", "file") or parsed.netloc or not parsed.path.startswith("/"):
        raise DeviceTestError("Device executable metadata is invalid.")
    path = PurePosixPath(unquote(parsed.path))
    if ".." in path.parts or "\0" in str(path):
        raise DeviceTestError("Device executable metadata is invalid.")
    return path


def matching_processes(device, executable):
    result = device_result(
        ["device", "info", "processes", "--device", device], "Test-process inspection",
    )
    processes = result.get("runningProcesses")
    if not isinstance(processes, list):
        raise DeviceTestError("Test-process inspection returned no process list.")
    matches = set()
    for process in processes:
        if not isinstance(process, dict):
            raise DeviceTestError("Test-process inspection returned an invalid process.")
        if not process.get("executable") or device_path(process["executable"]) != executable:
            continue
        pid = process.get("processIdentifier")
        if type(pid) is not int or not 0 < pid < 2**31:
            raise DeviceTestError("The test-host process identifier is invalid.")
        matches.add(pid)
    return matches


def terminate_owned_process(process):
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass


def print_test_output(text):
    for line in text.splitlines():
        if line.startswith(("[", "Native ")):
            print(line)


def continue_tests(debugger, command, result, internal_dict):
    import lldb

    if not re.fullmatch(r"[1-9][0-9]*", command.strip()):
        result.SetError("An exact test-host process identifier is required.")
        return
    expected_pid = int(command)
    process = debugger.GetSelectedTarget().GetProcess()
    if not process.IsValid() or process.GetProcessID() != expected_pid:
        result.SetError("The debugger is not attached to the expected test host.")
        return
    debugger.SetAsync(True)
    deadline = time.monotonic() + 60
    resumed = False
    initial_stop_id = None
    while time.monotonic() < deadline:
        state = process.GetState()
        if state == lldb.eStateExited:
            if resumed and process.GetExitStatus() == 0:
                result.AppendMessage(SUCCESS)
            else:
                result.SetError("The Native test process exited without a successful debugger-controlled run.")
            return
        if state in (lldb.eStateInvalid, lldb.eStateCrashed, lldb.eStateDetached):
            result.SetError("The Native test process became invalid, crashed, or detached.")
            return
        if state == lldb.eStateStopped:
            if resumed and process.GetStopID() == initial_stop_id:
                time.sleep(0.1)
                continue
            thread = process.GetSelectedThread()
            if (
                resumed
                or thread.GetStopReason() != lldb.eStopReasonSignal
                or thread.GetStopReasonDataAtIndex(0) != signal.SIGSTOP
            ):
                result.SetError("The Native test process stopped unexpectedly.")
                return
            resumed = True
            initial_stop_id = process.GetStopID()
            # CoreDevice attach returns before this initial SIGSTOP is delivered.
            if process.Continue().Fail():
                result.SetError("The Native test process could not resume from its initial debugger stop.")
                return
            deadline = time.monotonic() + TIMEOUT_SECONDS
        else:
            time.sleep(0.1)
    result.SetError("The Native test process did not reach its initial debugger stop or exit.")


def __lldb_init_module(debugger, internal_dict):
    debugger.HandleCommand(f"command script add -f {__name__}.continue_tests continue_tests")


def run_tests(device, identifier, bundle, app):
    try:
        identifier = str(uuid.UUID(identifier))
    except ValueError:
        raise DeviceTestError("The selected CoreDevice identifier is invalid.") from None
    if not device or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]*", bundle):
        raise DeviceTestError("The selected test-host identity is invalid.")
    if not Path(app).is_dir() or Path(app).name != "KompactNativeTests.app":
        raise DeviceTestError("The signed test-host app is missing.")
    installation = device_result(
        ["device", "install", "app", "--device", device, app], "Signed test-host installation", 180,
    )
    applications = installation.get("installedApplications")
    if not isinstance(applications, list):
        raise DeviceTestError("Installation returned no application records.")
    installed = [
        entry for entry in applications
        if isinstance(entry, dict) and entry.get("bundleID") == bundle
    ]
    if len(installed) != 1:
        raise DeviceTestError("Installation did not identify exactly one launchable test host.")
    executable = device_path(installed[0].get("installationURL")) / "KompactNativeTests"
    previous = matching_processes(device, executable)
    print("Installed the signed test host. Running Kotlin/Native tests on the device.", flush=True)
    with ThreadPoolExecutor(max_workers=1) as executor:
        console = subprocess.Popen(
            ["xcrun", "devicectl", "device", "process", "launch", "--device", device,
             "--terminate-existing", "--start-stopped", "--console", bundle],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True,
        )
        output = executor.submit(console.communicate)
        debugger = None
        console_reported = False
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                candidates = matching_processes(device, executable) - previous
                if len(candidates) > 1:
                    raise DeviceTestError("The newly launched test-host process is ambiguous.")
                if len(candidates) == 1:
                    pid = candidates.pop()
                    break
                if output.done():
                    raise DeviceTestError("The test host exited before debugger attachment.")
                time.sleep(0.1)
            else:
                raise DeviceTestError("The newly launched test-host process was not observed.")
            debugger = subprocess.Popen(
                ["xcrun", "lldb", "--no-lldbinit", "--batch",
                 "-o", "device select " + identifier,
                 "-o", f"device process attach --pid {pid}",
                 "-o", "command script import " + shlex.quote(str(Path(__file__).resolve())),
                 "-o", f"continue_tests {pid}"],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True,
            )
            try:
                debug_output, _ = debugger.communicate(timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                raise DeviceTestError("The Native test debugger timed out; private diagnostics withheld.") from None
            try:
                console_output, _ = output.result(timeout=30)
            except FutureTimeoutError:
                raise DeviceTestError("The Native test console did not close after debugger exit.") from None
            print_test_output(console_output)
            console_reported = True
            if debugger.returncode or SUCCESS not in debug_output:
                raise DeviceTestError("The Native test debugger failed; private diagnostics withheld.")
            if console.returncode:
                raise DeviceTestError(f"The Native test console failed (exit {console.returncode}).")
            print(SUCCESS)
        finally:
            for process in (debugger, console):
                if process is not None:
                    terminate_owned_process(process)
            if debugger is not None:
                try:
                    debugger.communicate(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(debugger.pid, signal.SIGKILL)
                    debugger.communicate()
            try:
                output.result(timeout=5)
            except FutureTimeoutError:
                os.killpg(console.pid, signal.SIGKILL)
                output.result()
            if not console_reported:
                print_test_output(output.result()[0])


def main():
    if len(sys.argv) != 5:
        print("Usage: ios_device_tests.py <device> <CoreDevice identifier> <bundle> <signed app>", file=sys.stderr)
        return 2
    try:
        run_tests(*sys.argv[1:])
    except DeviceTestError as error:
        print(str(error), file=sys.stderr)
        return 1
    except OSError:
        print("The device-test tool could not run; private diagnostics withheld.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
