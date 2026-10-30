package tw.expirybook

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Versioned interchange format for the local, read-only MCP companion. */
object McpSnapshot {
    fun json(items: List<Stuff>,tags: List<Tag>,now: Date=Date(),zone: TimeZone=TimeZone.getDefault()): String {
        val timestamp=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC") }.format(now)
        return JSONObject().apply {
            put("schema_version",1)
            put("app","有效期限簿")
            put("exported_at",timestamp)
            put("timezone",zone.id)
            put("tags",JSONArray().apply { tags.forEach { tag -> put(JSONObject().apply {
                put("id",tag.id); put("name",tag.name); put("icon",tag.icon)
                put("color",String.format(Locale.US,"#%06X",tag.color and 0xFFFFFF)); put("sort_order",tag.order)
            }) } })
            put("items",JSONArray().apply { items.sortedBy { it.id }.forEach { item -> put(JSONObject().apply {
                put("id",item.id); put("name",item.name); put("expiry_date",item.expiry); put("created_date",item.created)
                put("status",listOf("in_use","used_up","discarded")[item.status]); put("note",item.note)
                put("tag_ids",JSONArray(item.tags.sorted()))
                put("reminder",JSONObject().apply { put("enabled",item.reminder); put("days_before",item.days); put("minute_of_day",item.minute) })
            }) } })
        }.toString(2)
    }
}
