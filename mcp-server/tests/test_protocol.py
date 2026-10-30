"""Exercise initialization, tools/list and tools/call through a real stdio process."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

import anyio
from mcp import Client
from mcp.client.stdio import StdioServerParameters

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from make_demo_snapshot import demo_payload


class ProtocolTests(unittest.IsolatedAsyncioTestCase):
    async def test_stdio_discovery_query_reload_errors_and_recovery(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "items.json"
            data = demo_payload()
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            server = StdioServerParameters(command=sys.executable, args=[str(Path(__file__).resolve().parents[1] / "server.py"), "--data", str(path)], env={"PYTHONUTF8": "1"})
            with anyio.fail_after(40):
                async with Client(server) as client:
                    listed = await client.list_tools()
                    self.assertEqual(["get_expiring_items"], [t.name for t in listed.tools])
                    tool = listed.tools[0]
                    self.assertTrue(tool.annotations.read_only_hint)
                    self.assertFalse(tool.annotations.open_world_hint)
                    self.assertEqual(365, tool.input_schema["properties"]["days_ahead"]["maximum"])
                    before = path.read_bytes()
                    result = await client.call_tool("get_expiring_items", {})
                    self.assertFalse(result.is_error)
                    self.assertEqual(3, result.structured_content["count"])
                    self.assertEqual(before, path.read_bytes())
                    zero = await client.call_tool("get_expiring_items", {"days_ahead": 0})
                    self.assertEqual(2, zero.structured_content["count"])
                    data["items"][0]["status"] = "used_up"
                    updated = path.with_suffix(".new")
                    updated.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
                    updated.replace(path)
                    result = await client.call_tool("get_expiring_items", {"days_ahead": 7})
                    self.assertEqual(2, result.structured_content["count"])
                    for arguments in ({"days_ahead": -1}, {"days_ahead": True}, {"days_ahead": 366}, {"days_ahead": "7"}):
                        invalid = await client.call_tool("get_expiring_items", arguments)
                        self.assertTrue(invalid.is_error)
                    path.write_text("broken json", encoding="utf-8")
                    broken = await client.call_tool("get_expiring_items", {})
                    self.assertTrue(broken.is_error)
                    self.assertIn("JSON", str(broken.content))
                    path.unlink()
                    missing = await client.call_tool("get_expiring_items", {})
                    self.assertTrue(missing.is_error)
                    path.write_text(json.dumps(demo_payload(), ensure_ascii=False), encoding="utf-8")
                    recovered = await client.call_tool("get_expiring_items", {})
                    self.assertFalse(recovered.is_error)
                    self.assertEqual(3, recovered.structured_content["count"])


if __name__ == "__main__":
    unittest.main()
