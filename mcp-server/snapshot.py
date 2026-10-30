"""Read the Android snapshot contract and apply the app's expiry-list rules."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone, tzinfo
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


class SnapshotError(ValueError):
    """A snapshot is missing, incompatible, or invalid."""


@dataclass(frozen=True)
class Snapshot:
    exported_at: str
    timezone_name: str
    zone: tzinfo
    items: list[dict]
    tags: dict[int, dict]


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise SnapshotError(message)


def _date(value: object, field: str) -> date:
    _require(isinstance(value, str) and bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", value)), f"{field} 必須是 YYYY-MM-DD。")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise SnapshotError(f"{field} 日期無效。") from exc


def _zone(name: str) -> tzinfo:
    # Android also permits user-selected fixed-offset GMT zones.
    offset = re.fullmatch(r"GMT([+-])(\d{2}):(\d{2})", name)
    if offset:
        hours, minutes = int(offset[2]), int(offset[3])
        _require(hours <= 23 and minutes <= 59, "快照時區無效。")
        return timezone(timedelta(minutes=(hours * 60 + minutes) * (1 if offset[1] == "+" else -1)))
    try:
        return ZoneInfo(name)
    except (ZoneInfoNotFoundError, ValueError) as exc:
        raise SnapshotError("不支援的快照時區；請確認已安裝 requirements.txt 並重新匯出。") from exc


def read_snapshot(path: Path) -> Snapshot:
    """Open afresh on every request; never reuse a stale in-memory snapshot."""
    try:
        with path.open("rb") as stream:
            raw = stream.read(20 * 1024 * 1024 + 1)
    except FileNotFoundError as exc:
        raise SnapshotError("找不到資料檔。請從 APP 設定匯出 MCP 資料，放到 --data 指定的位置。") from exc
    except OSError as exc:
        raise SnapshotError("無法讀取資料檔，請確認路徑及檔案權限。") from exc
    _require(len(raw) <= 20 * 1024 * 1024, "資料檔超過 20 MB，請縮減快照或調整伺服器限制。")
    try:
        payload = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise SnapshotError("資料檔不是有效的 UTF-8 JSON，請重新匯出；請勿使用文字列表匯出。") from exc
    _require(isinstance(payload, dict), "快照最外層必須是 JSON 物件。")
    _require(type(payload.get("schema_version")) is int and payload["schema_version"] == 1, "不支援的 schema_version，請使用相容版本的 APP 匯出。")
    _require(payload.get("app") == "有效期限簿", "這不是有效期限簿的 MCP 資料檔。")
    timestamp = payload.get("exported_at")
    _require(isinstance(timestamp, str), "缺少 exported_at 匯出時間。")
    try:
        exported = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
        _require(exported.tzinfo is not None, "exported_at 必須包含時區。")
    except ValueError as exc:
        raise SnapshotError("exported_at 格式無效。") from exc
    zone_name = payload.get("timezone")
    _require(isinstance(zone_name, str) and bool(zone_name), "缺少快照 timezone。")
    zone = _zone(zone_name)
    _require(isinstance(payload.get("items"), list) and isinstance(payload.get("tags"), list), "快照缺少 items 或 tags 陣列。")
    tags: dict[int, dict] = {}
    for tag in payload["tags"]:
        _require(isinstance(tag, dict), "標籤格式無效。")
        tag_id = tag.get("id")
        _require(type(tag_id) is int and tag_id > 0 and tag_id not in tags, "標籤 id 必須是唯一正整數。")
        _require(all(isinstance(tag.get(key), str) for key in ("name", "icon", "color")), "標籤缺少名稱、圖示或顏色。")
        _require(bool(re.fullmatch(r"#[0-9A-Fa-f]{6}", tag["color"])), "標籤顏色格式無效。")
        _require(type(tag.get("sort_order")) is int, "標籤缺少 sort_order。")
        tags[tag_id] = tag
    seen: set[int] = set()
    for item in payload["items"]:
        _require(isinstance(item, dict), "物品格式無效。")
        item_id = item.get("id")
        _require(type(item_id) is int and item_id > 0 and item_id not in seen, "物品 id 必須是唯一正整數。")
        seen.add(item_id)
        _require(isinstance(item.get("name"), str) and bool(item["name"].strip()), "物品名稱不可為空。")
        _require(isinstance(item.get("note"), str), "物品 note 必須是文字。")
        _require(item.get("status") in ("in_use", "used_up", "discarded"), "物品狀態無效。")
        _date(item.get("expiry_date"), "expiry_date")
        _date(item.get("created_date"), "created_date")
        ids = item.get("tag_ids")
        _require(isinstance(ids, list) and all(type(n) is int and n in tags for n in ids), "物品 tag_ids 指向不存在的標籤。")
        _require(len(ids) == len(set(ids)), "物品 tag_ids 不可重複。")
    return Snapshot(timestamp, zone_name, zone, payload["items"], tags)


def query_expiring(snapshot: Snapshot, days_ahead: int = 7, *, today: date | None = None) -> dict:
    if type(days_ahead) is not int or not 0 <= days_ahead <= 365:
        raise SnapshotError("days_ahead 必須是 0 到 365 的整數。")
    as_of = today or datetime.now(snapshot.zone).date()
    cutoff = as_of + timedelta(days=days_ahead)
    selected = sorted(
        (item for item in snapshot.items if item["status"] == "in_use" and _date(item["expiry_date"], "expiry_date") <= cutoff),
        key=lambda item: (item["expiry_date"], item["id"]),
    )
    result_items = []
    lines = []
    for item in selected:
        expiry = date.fromisoformat(item["expiry_date"])
        remaining = (expiry - as_of).days
        roc = f"{expiry.year - 1911}/{expiry.month:02}/{expiry.day:02}"
        result_items.append({
            "id": item["id"], "name": item["name"], "expiry_date": item["expiry_date"],
            "expiry_date_roc": roc, "remaining_days": remaining,
            "expiry_state": "expired" if remaining < 0 else "due_today" if remaining == 0 else "expiring_soon",
            "note": item["note"],
            "tags": [snapshot.tags[n] for n in sorted(item["tag_ids"], key=lambda n: (snapshot.tags[n]["sort_order"], n))],
        })
        flatten = lambda value: re.sub(r"[\r\n]+", " ", value)
        lines.append(f"{flatten(item['name'])}-{roc}-{flatten(item['note'])}")
    return {
        "source": "manual_snapshot", "exported_at": snapshot.exported_at,
        "timezone": snapshot.timezone_name, "as_of": as_of.isoformat(),
        "days_ahead": days_ahead, "cutoff_date": cutoff.isoformat(),
        "total_items_in_snapshot": len(snapshot.items), "count": len(result_items),
        "items": result_items, "text": "\n".join(lines),
        "snapshot_notice": "這是手動匯出快照，並非手機即時資料；手機資料變動後需重新匯出並替換檔案。",
    }
