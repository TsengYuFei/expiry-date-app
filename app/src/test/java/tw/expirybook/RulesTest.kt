package tw.expirybook

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class RulesTest {
    @Test fun expiryStatesAndActiveAttentionCounts() {
        val today="2026-10-29"
        assertEquals(2,Rules.expiryPhase("2026-10-28",today))
        assertEquals(1,Rules.expiryPhase(today,today))
        assertEquals(1,Rules.expiryPhase("2026-11-05",today))
        assertEquals(0,Rules.expiryPhase("2026-11-06",today))
        val samples=listOf(Stuff(name="過期",expiry="2026-10-28",created=today),Stuff(name="即將",expiry="2026-11-05",created=today),Stuff(name="充裕",expiry="2026-11-06",created=today),Stuff(name="用完",expiry="2026-10-01",created=today,status=1),Stuff(name="丟棄",expiry="2026-10-01",created=today,status=2))
        assertEquals(2,Rules.attentionCount(samples,today))
    }
    @Test fun tagCatalogHas25DistinctOptionsAndReadableText() {
        assertEquals(25,TagStyles.icons.size); assertEquals(25,TagStyles.icons.distinct().size)
        assertEquals(25,TagStyles.colors.size); assertEquals(25,TagStyles.colors.map { it.second }.distinct().size)
        assertTrue(TagStyles.lightText(0xFF000000.toInt())); assertFalse(TagStyles.lightText(0xFFFFFFFF.toInt()))
    }
    private val items=listOf(
        Stuff(1,"巧克力","2026-09-01","2026-01-01",note="我的",tags=setOf(2)),
        Stuff(2,"巧克力牛奶","2026-10-31","2026-01-01",tags=setOf(3)),
        Stuff(3,"果凍","2027-01-25","2026-01-01",status=1,note="妹妹的",tags=setOf(3)),
        Stuff(4,"糖果","2027-12-31","2026-01-01",note="我的",tags=setOf(2)),
        Stuff(5,"巧克比","2026-09-30","2026-01-01",status=1,note="牛奶口味",tags=setOf(1)))
    private fun check(expected: List<Long>,filter: Filter) = assertEquals(expected,Rules.filter(items,filter).map { it.id })
    @Test fun specificationSearchCases() {
        check(listOf(1,2),Filter(query="巧克力"))
        check(listOf(1,4),Filter(query="我的"))
        check(listOf(2),Filter(query="牛奶"))
        check(emptyList(),Filter(query="蘋果牛奶"))
        check(listOf(2),Filter(tag=3))
        check(listOf(1,4),Filter(tag=2))
        check(listOf(1,2,4),Filter())
        check(emptyList(),Filter(tag=4))
        check(listOf(5,3),Filter(status=1))
        check(emptyList(),Filter(status=2))
        check(listOf(1,2),Filter(from="2026-09-01",to="2026-12-31"))
        check(listOf(4),Filter(from="2027-01-25"))
        check(emptyList(),Filter(to="2026-07-31"))
        check(listOf(3),Filter(tag=3,status=1))
        check(emptyList(),Filter(tag=1,from="2027-01-01"))
        check(listOf(5),Filter(query="牛奶",status=1,from="2026-01-25",to="2026-10-05"))
        check(emptyList(),Filter(tag=2,to="2026-07-01"))
        check(listOf(1,4),Filter(query="我的",tag=2,from="2026-09-01"))
        check(emptyList(),Filter(query="牛奶",tag=3,status=1,from="2026-01-25",to="2026-10-05"))
    }
    @Test fun multipleTagsMatchAnySelectedSingleTag() {
        val item=items[0].copy(tags=setOf(1,2,3))
        for(tag in item.tags) assertEquals(listOf(item),Rules.filter(listOf(item),Filter(tag=tag)))
    }
    @Test fun attentionExportIncludesTodayAndSevenDaysExcludesEightDaysAndInactive() {
        val today="2026-10-31"
        val expired=Stuff(1,"牛奶","2026-10-01",today,note="第一行\r\n第二行")
        val dueToday=Stuff(2,"豆漿",today,today)
        val inSevenDays=Stuff(3,"巧克力","2026-11-07",today,note="我的")
        val inEightDays=Stuff(4,"果凍","2026-11-08",today)
        val finished=expired.copy(id=5,status=1)
        val discarded=expired.copy(id=6,status=2)
        val samples=listOf(inEightDays,discarded,dueToday,inSevenDays,finished,expired)
        assertEquals("牛奶-115/10/01-第一行 第二行\n豆漿-115/10/31-\n巧克力-115/11/07-我的",Rules.attentionExport(samples,today))
        assertEquals(3,Rules.attentionCount(samples,today))
        assertEquals("",Rules.attentionExport(listOf(inEightDays,finished,discarded),today))
    }
    @Test fun dateBoundariesAndRoc() {
        assertEquals("115/10/31",Rules.roc("2026-10-31"))
        assertEquals(0,Rules.remaining("2026-10-31","2026-10-31"))
        assertEquals(-1,Rules.remaining("2026-10-30","2026-10-31"))
        assertEquals(1,Rules.remaining("2028-03-01","2028-02-29"))
        check(listOf(2),Filter(from="2026-10-31",to="2026-10-31"))
    }
    @Test fun reminderSubtractsCalendarDaysAcrossYearBoundary() {
        val previous=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
            val item=items[0].copy(expiry="2027-01-01",days=3,minute=1200)
            val due=Rules.calendar("2026-12-29").apply { set(java.util.Calendar.HOUR_OF_DAY,20) }.timeInMillis
            assertEquals(due,Rules.due(item))
            assertNotEquals(Rules.token(item),Rules.token(item.copy(minute=540)))
            assertEquals(Rules.token(item),Rules.token(item.copy(status=1)))
        } finally { TimeZone.setDefault(previous) }
    }
}
