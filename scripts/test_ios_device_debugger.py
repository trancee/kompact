"""Asynchronous debugger lifecycle coverage without Apple tooling."""

import signal
import sys
import unittest
from unittest.mock import Mock, patch

import ios_device_tests


class DebuggerContinuationTest(unittest.TestCase):
    def setUp(self):
        self.lldb = Mock(
            eStateExited=10, eStateInvalid=0, eStateCrashed=8, eStateDetached=9,
            eStateStopped=5, eStopReasonSignal=5,
        )
        self.module_patch = patch.dict(sys.modules, {"lldb": self.lldb})
        self.module_patch.start()
        self.addCleanup(self.module_patch.stop)
        self.debugger = Mock()
        self.process = self.debugger.GetSelectedTarget.return_value.GetProcess.return_value
        self.process.IsValid.return_value = True
        self.process.GetProcessID.return_value = 321
        self.process.GetExitStatus.return_value = 0
        self.process.Continue.return_value.Fail.return_value = False
        self.thread = self.process.GetSelectedThread.return_value
        self.thread.GetStopReason.return_value = self.lldb.eStopReasonSignal
        self.thread.GetStopReasonDataAtIndex.return_value = signal.SIGSTOP
        self.result = Mock()

    def test_waits_for_asynchronous_initial_stop_before_continuing(self):
        self.process.GetState.side_effect = [2, 3, 5, 10]

        with patch.object(ios_device_tests.time, "sleep"):
            ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.process.Continue.assert_called_once_with()
        self.result.SetError.assert_not_called()
        self.result.AppendMessage.assert_called_once_with(ios_device_tests.SUCCESS)

    def test_rejects_another_process(self):
        self.process.GetProcessID.return_value = 999

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.process.Continue.assert_not_called()
        self.result.SetError.assert_called_once()

    def test_rejects_invalid_process_identifier(self):
        ios_device_tests.continue_tests(self.debugger, "321; continue", self.result, {})

        self.process.GetState.assert_not_called()
        self.result.SetError.assert_called_once()

    def test_does_not_continue_after_an_unexpected_signal(self):
        self.process.GetState.return_value = 5
        self.thread.GetStopReasonDataAtIndex.return_value = signal.SIGABRT

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.process.Continue.assert_not_called()
        self.result.SetError.assert_called_once()

    def test_does_not_continue_a_second_stop(self):
        self.process.GetState.side_effect = [5, 5]
        self.process.GetStopID.side_effect = [1, 2]

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.process.Continue.assert_called_once_with()
        self.result.SetError.assert_called_once()
        self.result.AppendMessage.assert_not_called()

    def test_waits_for_cached_initial_stop_to_transition_after_async_continue(self):
        self.process.GetState.side_effect = [5, 5, 6, 10]
        self.process.GetStopID.return_value = 1

        with patch.object(ios_device_tests.time, "sleep"):
            ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.debugger.SetAsync.assert_called_once_with(True)
        self.process.Continue.assert_called_once_with()
        self.result.SetError.assert_not_called()
        self.result.AppendMessage.assert_called_once_with(ios_device_tests.SUCCESS)

    def test_rejects_unsuccessful_exit(self):
        self.process.GetState.side_effect = [5, 10]
        self.process.GetExitStatus.return_value = 1

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.result.SetError.assert_called_once()
        self.result.AppendMessage.assert_not_called()

    def test_rejects_exit_without_debugger_control(self):
        self.process.GetState.return_value = 10

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.result.SetError.assert_called_once()
        self.process.Continue.assert_not_called()

    def test_reports_initial_stop_deadline(self):
        with patch.object(ios_device_tests.time, "monotonic", side_effect=[0, 61]):
            ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.result.SetError.assert_called_once()
        self.process.Continue.assert_not_called()

    def test_successful_execution_is_not_limited_to_the_attachment_deadline(self):
        self.process.GetState.side_effect = [5, 10]

        with patch.object(ios_device_tests.time, "monotonic", side_effect=[0, 0, 61, 61]):
            ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.result.SetError.assert_not_called()
        self.result.AppendMessage.assert_called_once_with(ios_device_tests.SUCCESS)

    def test_resume_failure_does_not_report_success(self):
        self.process.GetState.return_value = 5
        self.process.Continue.return_value.Fail.return_value = True

        ios_device_tests.continue_tests(self.debugger, "321", self.result, {})

        self.result.SetError.assert_called_once()
        self.result.AppendMessage.assert_not_called()

    def test_crash_detachment_and_invalid_state_fail_closed(self):
        for state in (0, 8, 9):
            with self.subTest(state=state):
                self.process.GetState.return_value = state
                result = Mock()

                ios_device_tests.continue_tests(self.debugger, "321", result, {})

                result.SetError.assert_called_once()
                result.AppendMessage.assert_not_called()


if __name__ == "__main__":
    unittest.main()
