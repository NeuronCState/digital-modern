import json
import tempfile
import unittest
from unittest.mock import patch, Mock
from pathlib import Path

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

    def test_build_circuit_validation_errors(self):
        output = Path("dummy.dig")
        with self.assertRaisesRegex(server.ToolError, "design must be an object"):
            server.build_circuit([], output)

        with self.assertRaisesRegex(server.ToolError, "design.elements and design.wires must be arrays"):
            server.build_circuit({"elements": {}, "wires": []}, output)

        with self.assertRaisesRegex(server.ToolError, "each element needs a unique non-empty id"):
            server.build_circuit({"elements": [{"type": "In"}], "wires": []}, output)

        with self.assertRaisesRegex(server.ToolError, "each element needs a unique non-empty id"):
            server.build_circuit({"elements": [{"id": "a", "type": "In"}, {"id": "a", "type": "Out"}], "wires": []}, output)

        with self.assertRaisesRegex(server.ToolError, "invalid element type"):
            server.build_circuit({"elements": [{"id": "a", "type": "In valid"}], "wires": []}, output)

        with self.assertRaisesRegex(server.ToolError, "each wire must be an object"):
            server.build_circuit({"elements": [{"id": "a", "type": "In"}], "wires": ["not a dict"]}, output)

        with self.assertRaisesRegex(server.ToolError, "each wire needs from/to or p1/p2"):
            server.build_circuit({"elements": [{"id": "a", "type": "In"}], "wires": [{"from": "a"}]}, output)

    def test_input_path_errors(self):
        with self.assertRaisesRegex(server.ToolError, "path must be a non-empty string"):
            server._input_path("")
        with self.assertRaisesRegex(server.ToolError, "path must be a non-empty string"):
            server._input_path(None)

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nonexistent.dig"
            with self.assertRaisesRegex(server.ToolError, "file does not exist"):
                server._input_path(str(path))

            wrong_suffix = Path(directory) / "wrong.txt"
            wrong_suffix.touch()
            with self.assertRaisesRegex(server.ToolError, "expected a .dig file"):
                server._input_path(str(wrong_suffix), ".dig")

    def test_inspect_circuit_invalid_xml(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "malformed.dig"
            path.write_text("<circuit><unclosed_tag>", encoding="utf-8")
            with self.assertRaises(server.ToolError) as ctx:
                server.inspect_circuit(path)
            self.assertIn("invalid Digital XML", str(ctx.exception))

    def test_protocol_initialize_and_tools_list(self):
        self.assertEqual(server.SCHEMA["elements"][2]["type"], "And")
        names = {tool["name"] for tool in server.TOOLS}
        self.assertIn("digital_build_circuit", names)
        self.assertIn("digital_run_tests", names)

    @patch("subprocess.run")
    @patch("digital_mcp_server._jar")
    @patch("digital_mcp_server._java")
    def test_render_svg_success(self, mock_java, mock_jar, mock_run):
        mock_java.return_value = "java"
        mock_jar.return_value = Path("Digital.jar")
        mock_run.return_value = Mock(returncode=0)
        path = Path("test.dig")
        result = server.render_svg(path, 10)

        mock_run.assert_called_once()
        command = mock_run.call_args[0][0]
        self.assertEqual(command[-4:], ["-dig", "test.dig", "-svg", "test.dig.svg"])
        self.assertEqual(result["svg_path"], "test.dig.svg")
        self.assertEqual(result["command"], command)

    @patch("subprocess.run")
    @patch("digital_mcp_server._jar")
    @patch("digital_mcp_server._java")
    def test_render_svg_timeout(self, mock_java, mock_jar, mock_run):
        import subprocess
        mock_java.return_value = "java"
        mock_jar.return_value = Path("Digital.jar")
        mock_run.side_effect = subprocess.TimeoutExpired(cmd="dummy", timeout=10)
        path = Path("test.dig")

        with self.assertRaises(server.ToolError) as context:
            server.render_svg(path, 10)

        self.assertIn("Digital SVG export timed out after 10s", str(context.exception))

    @patch("subprocess.run")
    @patch("digital_mcp_server._jar")
    @patch("digital_mcp_server._java")
    def test_render_svg_failure(self, mock_java, mock_jar, mock_run):
        mock_java.return_value = "java"
        mock_jar.return_value = Path("Digital.jar")
        mock_run.return_value = Mock(returncode=1, stdout="Error info\n", stderr="More info")
        path = Path("test.dig")

        with self.assertRaises(server.ToolError) as context:
            server.render_svg(path, 10)

        self.assertIn("Error info", str(context.exception))
        self.assertIn("More info", str(context.exception))


if __name__ == "__main__":
    unittest.main()
