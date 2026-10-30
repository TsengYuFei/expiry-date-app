import copy
import json
import sys
import tempfile
import unittest
from datetime import date, datetime, timezone
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from snapshot import SnapshotError, query_expiring, read_snapshot


def payload() -> dict:
    return {"schema_version": 1, "app": "有效期限簿", "exported_at": "2026-10-01T00:00:00Z", "timezone": "Asia/Taipei", "tags": [{"id": 1, "name": "冷藏", "icon": "冰箱", "color": "#12AB34", "sort_order": 0}], "items": [
        {"id": n, "name": name, "expiry_date": expiry, "created_date": "2026-10-01", "status": status, "note": note, "tag_ids": [1]}
        for n, name, expiry, status, note in [
            (1, '牛奶 "特價"', "2026-10-29", "in_use", "第一行\r\n第二行"),
            (2, "豆漿", "2026-10-30", "in_use", ""),
            (3, "巧克力", "2026-11-06", "in_use", "我的"),
            (4, "維他命", "2026-11-07", "in_use", ""),
            (5, "用完", "2026-10-20", "used_up", ""),
            (6, "丟棄", "2026-10-20", "discarded", ""),
        ]
    ]}


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "items.json"
        self.write(payload())

    def write(self, data):
        self.path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    def test_boundaries_statuses_unicode_and_text(self):
        result = query_expiring(read_snapshot(self.path), 7, today=date(2026, 10, 30))
        self.assertEqual([1, 2, 3], [i["id"] for i in result["items"]])
        self.assertEqual([-1, 0, 7], [i["remaining_days"] for i in result["items"]])
        self.assertEqual(["expired", "due_today", "expiring_soon"], [i["expiry_state"] for i in result["items"]])
        self.assertEqual('牛奶 "特價"-115/10/29-第一行 第二行\n豆漿-115/10/30-\n巧克力-115/11/06-我的', result["text"])
        self.assertEqual("冷藏", result["items"][0]["tags"][0]["name"])
        self.assertEqual(6, result["total_items_in_snapshot"])
        self.assertEqual(2, query_expiring(read_snapshot(self.path), 0, today=date(2026, 10, 30))["count"])

    def test_replacement_is_read_again_and_source_not_modified(self):
        before = self.path.read_bytes()
        query_expiring(read_snapshot(self.path), today=date(2026, 10, 30))
        self.assertEqual(before, self.path.read_bytes())
        updated = payload(); updated["items"][0]["status"] = "used_up"
        self.write(updated)
        self.assertEqual(2, query_expiring(read_snapshot(self.path), today=date(2026, 10, 30))["count"])

    def test_query_day_uses_snapshot_zone_not_export_day_or_pc_zone(self):
        snapshot = read_snapshot(self.path)
        class Clock:
            @staticmethod
            def now(zone):
                return datetime(2026, 10, 30, 16, 5, tzinfo=timezone.utc).astimezone(zone)
        with patch("snapshot.datetime", Clock):
            result = query_expiring(snapshot)
        self.assertEqual("2026-10-31", result["as_of"])
        self.assertEqual(4, result["count"])
        self.assertEqual("2026-10-01T00:00:00Z", result["exported_at"])

    def test_custom_android_fixed_offset_zone(self):
        data = payload(); data["timezone"] = "GMT+08:00"; self.write(data)
        self.assertEqual(28800, read_snapshot(self.path).zone.utcoffset(None).total_seconds())

    def test_empty_snapshot_and_sorting_ties(self):
        data = payload(); data["items"] = []; self.write(data)
        self.assertEqual("", query_expiring(read_snapshot(self.path))["text"])
        data = payload(); first = copy.deepcopy(data["items"][0]); first["id"] = 9
        data["items"].insert(0, first); self.write(data)
        self.assertEqual([1, 9, 2, 3], [i["id"] for i in query_expiring(read_snapshot(self.path), today=date(2026, 10, 30))["items"]])

    def test_invalid_parameters(self):
        for days in (-1, 366, True, "7", 1.5):
            with self.subTest(days=days), self.assertRaises(SnapshotError):
                query_expiring(read_snapshot(self.path), days)

    def test_missing_corrupt_and_unsupported_files(self):
        self.path.unlink()
        with self.assertRaisesRegex(SnapshotError, "找不到"):
            read_snapshot(self.path)
        self.path.write_text("{", encoding="utf-8")
        with self.assertRaisesRegex(SnapshotError, "JSON"):
            read_snapshot(self.path)
        for key, value in [("schema_version", 2), ("schema_version", True), ("timezone", "No/Such_Zone"), ("exported_at", "yesterday"), ("app", "other")]:
            data = payload(); data[key] = value; self.write(data)
            with self.subTest(key=key, value=value), self.assertRaises(SnapshotError):
                read_snapshot(self.path)

    def test_invalid_item_fields_and_references(self):
        for key, value in [("expiry_date", "2026-02-30"), ("expiry_date", "2026-1-1"), ("status", "unknown"), ("tag_ids", [99]), ("tag_ids", [1, 1]), ("id", True), ("note", None)]:
            data = payload(); data["items"][0][key] = value; self.write(data)
            with self.subTest(key=key), self.assertRaises(SnapshotError):
                read_snapshot(self.path)
        data = payload(); data["items"].append(data["items"][0]); self.write(data)
        with self.assertRaises(SnapshotError):
            read_snapshot(self.path)


if __name__ == "__main__":
    unittest.main()
