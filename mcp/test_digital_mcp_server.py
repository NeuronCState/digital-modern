import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import digital_mcp_server as server


class DigitalMcpServerTest(unittest.TestCase):
    def test_build_inspect_and_testdata(self):
        design = {
            "elements": [
                {"id": "a", "type": "In", "label": "A", "x": 200, "y": 100},
                {"id": "b", "type": "In", "label": "B", "x": 200, "y": 140},
                {"id": "g", "type": "And", "x": 240, "y": 100},
                {"id": "y", "type": "Out", "label": "Y", "x": 340, "y": 120},
            ],
            "wires": [
                {"from": "a", "to": "g", "input": 0},
                {"from": "b", "to": "g", "input": 1},
                {"from": "g", "to": "y"},
            ],
            "tests": [
                {"inputs": {"A": 0, "B": 0}, "outputs": {"Y": 0}},
                {"inputs": {"A": 1, "B": 1}, "outputs": {"Y": 1}},
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "and.dig"
            result = server.build_circuit(design, path)
            self.assertEqual(result["element_count"], 5)  # includes the generated Testcase
            self.assertEqual(result["wire_count"], 3)
            xml = path.read_text(encoding="utf-8")
            self.assertIn("A B Y", xml)
            self.assertIn("1 1 1", xml)
            self.assertEqual(server.inspect_circuit(path)["wire_count"], 3)

    def test_protocol_initialize_and_tools_list(self):
        self.assertEqual(server.SCHEMA["elements"][2]["type"], "And")
        names = {tool["name"] for tool in server.TOOLS}
        self.assertIn("digital_build_circuit", names)
        self.assertIn("digital_run_tests", names)

    @patch("digital_mcp_server._jar", return_value=Path("mocked.jar"))
    @patch("digital_mcp_server._java", return_value="mocked-java")
    @patch("subprocess.run")
    def test_run_tests(self, mock_run, mock_java, mock_jar):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["java", "CLI", "test"],
            returncode=0,
            stdout="1 passed, 0 failed\n",
            stderr=""
        )

        result = server.run_tests(Path("dummy.dig"), timeout=10, verbose=True)
        self.assertTrue(result["passed"])
        self.assertEqual(result["exit_code"], 0)
        self.assertEqual(result["output"], "1 passed, 0 failed")
        mock_run.assert_called_once()

        mock_run.reset_mock()
        mock_run.side_effect = subprocess.TimeoutExpired(cmd=["java", "CLI", "test"], timeout=10)

        with self.assertRaises(server.ToolError) as context:
            server.run_tests(Path("dummy.dig"), timeout=10, verbose=True)

        self.assertIn("Digital test timed out after 10s", str(context.exception))


if __name__ == "__main__":
    unittest.main()
