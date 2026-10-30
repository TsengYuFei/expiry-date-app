package tw.expirybook

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

data class Tag(val id: Long, val name: String, val icon: String, val color: Int, val order: Int)
data class Stuff(val id: Long = 0, val name: String, val expiry: String, val created: String,
    val status: Int = 0, val note: String = "", val reminder: Boolean = false,
    val days: Int = 3, val minute: Int = 540, val tags: Set<Long> = setOf(1L), val delivered: String? = null)
data class Filter(val query: String = "", val tag: Long? = null, val status: Int = 0,
    val from: String? = null, val to: String? = null)

object Rules {
    const val soonDays=7
    fun expiryPhase(expiry: String,today: String=today()): Int = remaining(expiry,today).let { when { it<0->2; it<=soonDays->1; else->0 } }
    fun attentionCount(items: List<Stuff>,today: String=today()) = items.count { it.status==0 && expiryPhase(it.expiry,today)!=0 }
    val offsets = listOf(0, 1, 2, 3, 5, 7, 14)
    val offsetNames = listOf("當天", "一天前", "兩天前", "三天前", "五天前", "一周前", "兩周前")
    val statuses = listOf("使用中", "已用完", "已丟棄")
    fun date(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(java.util.Locale.US, year, month, day)
    fun today(): String = Calendar.getInstance().let { date(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    fun calendar(value: String, zone: TimeZone = TimeZone.getDefault()): Calendar {
        val p = value.split('-').map(String::toInt)
        return GregorianCalendar(zone).apply { clear(); isLenient = false; set(p[0], p[1] - 1, p[2]); timeInMillis }
    }
    fun roc(value: String): String = value.split('-').let { "${it[0].toInt() - 1911}/${it[1]}/${it[2]}" }
    fun remaining(expiry: String, today: String = today()): Int =
        ((calendar(expiry, TimeZone.getTimeZone("UTC")).timeInMillis - calendar(today, TimeZone.getTimeZone("UTC")).timeInMillis) / 86400000).toInt()
    fun expiryText(expiry: String): String = remaining(expiry).let { when { it < 0 -> "已過期 ${-it} 天"; it == 0 -> "今天到期"; else -> "還有 $it 天到期" } }
    fun due(item: Stuff): Long = calendar(item.expiry).apply {
        add(Calendar.DAY_OF_MONTH, -item.days); set(Calendar.HOUR_OF_DAY, item.minute / 60); set(Calendar.MINUTE, item.minute % 60)
    }.timeInMillis
    fun token(item: Stuff) = "${item.expiry}/${item.days}/${item.minute}"
    fun attentionExport(items: List<Stuff>, today: String = today()): String = items
        .filter { it.status == 0 && expiryPhase(it.expiry,today) != 0 }
        .sortedWith(compareBy<Stuff> { it.expiry }.thenBy { it.id })
        .joinToString("\n") { item ->
            fun singleLine(value: String) = value.replace(Regex("[\\r\\n]+"), " ")
            "${singleLine(item.name)}-${roc(item.expiry)}-${singleLine(item.note)}"
        }
    fun filter(items: List<Stuff>, f: Filter) = items.filter {
        it.status == f.status && (f.tag == null || f.tag in it.tags) &&
            (it.name.contains(f.query.trim(), true) || it.note.contains(f.query.trim(), true)) &&
            (f.from == null || it.expiry >= f.from) && (f.to == null || it.expiry <= f.to)
    }.sortedWith(compareBy<Stuff> { it.expiry }.thenBy { it.id })
}
