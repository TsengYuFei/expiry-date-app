"""Real MCP stdio smoke client; it is not an LLM agent."""
import argparse
import json
import sys
from pathlib import Path

import anyio
from mcp import Client
from mcp.client.stdio import StdioServerParameters


async def run(data: Path, days: int, expected_text: Path | None) -> None:
    server = StdioServerParameters(command=sys.executable, args=[str(Path(__file__).with_name("server.py")), "--data", str(data.resolve())], env={"PYTHONUTF8": "1"})
    async with Client(server) as client:
        tools = await client.list_tools()
        assert [tool.name for tool in tools.tools] == ["get_expiring_items"]
        result = await client.call_tool("get_expiring_items", {"days_ahead": days})
        if result.is_error:
            print(result.model_dump_json(indent=2), file=sys.stderr)
            raise SystemExit(2)
        payload = result.structured_content
        assert isinstance(payload, dict)
        if expected_text is not None:
            assert payload["text"] == expected_text.read_text(encoding="utf-8"), "MCP output differs from Android Rules.attentionExport"
        print(json.dumps({"tool": "get_expiring_items", "result": payload}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="透過 MCP 初始化、列工具並呼叫查詢；不使用模型或 API key")
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--days-ahead", type=int, default=7)
    parser.add_argument("--expected-text", type=Path, help="驗證用：比對 Android 產生的同一天預期文字")
    args = parser.parse_args()
    anyio.run(run, args.data, args.days_ahead, args.expected_text)
