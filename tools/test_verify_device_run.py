import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class DeviceRunVerificationTest(unittest.TestCase):
    def verify(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "instrumentation.txt"
            path.write_text(text)
            return subprocess.run([sys.executable, str(Path(__file__).with_name("verify_device_run.py")), str(path)],
                                  capture_output=True).returncode

    def test_requires_both_tests(self):
        self.assertNotEqual(0, self.verify("OK (1 test)"))
        self.assertNotEqual(0, self.verify("OK (0 tests)"))
        self.assertNotEqual(0, self.verify("OK (2 tests)"))
        self.assertEqual(0, self.verify("OK (5 tests)"))

    def test_rejects_failure_even_with_success_summary(self):
        for failure in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=crash"):
            self.assertNotEqual(0, self.verify("OK (5 tests)\n" + failure))


if __name__ == "__main__":
    unittest.main()
