"""Generate explicitly labelled synthetic data for a repeatable class demonstration."""
import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo


def demo_payload() -> dict:
    today = datetime.now(ZoneInfo("Asia/Taipei")).date()
    items = []
    for n, (name, offset, status) in enumerate([
        ("示範牛奶", -2, "in_use"), ("示範豆漿", 0, "in_use"),
        ("示範巧克力", 7, "in_use"), ("示範維他命", 8, "in_use"),
        ("示範已用完物品", -3, "used_up"), ("示範已丟棄物品", -3, "discarded"),
    ], 1):
        items.append({"id": n, "name": name, "expiry_date": (today + timedelta(days=offset)).isoformat(), "created_date": today.isoformat(), "status": status, "note": "示範資料，並非手機內容", "tag_ids": [1], "reminder": {"enabled": False, "days_before": 3, "minute_of_day": 540}})
    return {"schema_version": 1, "app": "有效期限簿", "exported_at": datetime.now(timezone.utc).isoformat(), "timezone": "Asia/Taipei", "tags": [{"id": 1, "name": "其他", "icon": "標籤", "color": "#2F7554", "sort_order": 0}], "items": items}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="建立六筆示範資料（不讀取手機、不覆蓋現有檔案）")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data" / "items.demo.json")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(demo_payload(), output, ensure_ascii=False, indent=2)
