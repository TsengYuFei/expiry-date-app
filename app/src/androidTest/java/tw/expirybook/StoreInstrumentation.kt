package tw.expirybook

import android.app.Instrumentation
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.app.NotificationManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.Switch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device-side integration test, no dependency on obsolete android.test APIs. */
class StoreInstrumentation : Instrumentation() {
    private var previewMode=false
    private fun popupClick(label: String) {
        waitForIdleSync(); Thread.sleep(180)
        var node=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first { it.text?.toString()==label || it.contentDescription?.toString()==label }
        while(!node.isClickable && node.parent!=null) node=node.parent
        check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        waitForIdleSync(); Thread.sleep(180)
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); previewMode=arguments?.getString("preview")=="true"; start() }
    override fun onStart() {
        if(previewMode) { designPreview(); return }
        val result=Bundle()
        try {
            targetContext.deleteDatabase("integration-test.db")
            Store(targetContext,"integration-test.db").use { db ->
                check(db.tags().single().id==1L)
                db.saveTag(null,"糖果","糖果",-1); db.saveTag(null,"冰箱","冰箱",-2)
                val candy=db.tags().first { it.name=="糖果" }.id
                val fridge=db.tags().first { it.name=="冰箱" }.id
                val first=db.save(Stuff(name="牛奶",expiry="2026-11-11",created="2026-10-27",tags=setOf(1,fridge)))
                val second=db.save(Stuff(name="牛奶",expiry="2026-11-11",created="2026-10-27",tags=setOf(candy)))
                check(first!=second && db.items().size==2)
                val original=db.items().first { it.id==first }
                db.save(original.copy(note="妹妹的")); check(db.items().first { it.id==first }.note=="妹妹的")
                db.deleteTag(fridge); check(db.items().first { it.id==first }.tags==setOf(1L))
                db.deleteTag(candy); check(db.items().all { it.tags==setOf(1L) })
                db.status(first,1); check(db.items().first { it.id==first }.status==1)
                db.status(first,0)
                val before=db.items(); db.settings(false,7,1200); check(db.items()==before && !db.enabled)
                db.settings(true,3,540); check(db.items()==before)
                db.delivered(original); check(db.items().first { it.id==first }.delivered==Rules.token(original))
                db.save(db.items().first { it.id==first }.copy(note="新備註")); check(db.items().first { it.id==first }.delivered!=null)
                db.save(db.items().first { it.id==first }.copy(days=7)); check(db.items().first { it.id==first }.delivered==null)
                var rejected=false
                try { db.save(original.copy(name=" ")) } catch(_: IllegalArgumentException) { rejected=true }; check(rejected)
                rejected=false; try { db.saveTag(null,"其他","標籤",-1) } catch(_: IllegalArgumentException) { rejected=true }; check(rejected)
                db.saveTag(null,"飲料","飲料",-1)
                db.reorder(db.tags().map { it.id }.reversed()); check(db.tags().last().id==1L)
                db.deleteItem(first); check(db.items().single().id==second)
                result.putString("stream","PASS: SQLite CRUD, duplicate items, multiple tags, tag fallback, settings isolation, delivery tokens, validation and ordering\n")
            }
            targetContext.deleteDatabase("integration-test.db")
            mcpSnapshotExport()
            uiAndNotifications()
            sortingInteractions()
            result.putString("stream",result.getString("stream")+"PASS: Activity add/detail/edit/status/filter, expired export dialog and clipboard, real notification, global mute, inactive suppression, delivery deduplication\nPASS: Tag drag, lift, edge autoscroll, undo, move menu, draft restoration and saved order\n")
            finish(RESULT_OK,result)
        } catch(t: Throwable) { result.putString("stream","FAIL: ${t.stackTraceToString()}"); finish(RESULT_CANCELED,result) }
    }
    private fun clickUi(activity: Activity,label: String) {
        fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
        fun menuItem(value: String) {
            waitForIdleSync(); Thread.sleep(150)
            var node=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(value).first()
            while(!node.isClickable && node.parent!=null) node=node.parent
            check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { "Menu item $value is not clickable" }
        }
        if(label in Rules.statuses) {
            runOnMainSync { check(views(activity.window.decorView).first { it.contentDescription?.toString()=="狀態篩選" }.performClick()) }; menuItem(label)
        } else {
            val accessible=label.removePrefix("＋ ").removePrefix("⚙ ").removePrefix("‹ ")
            runOnMainSync { val view=views(activity.window.decorView).first { it.isShown && it.isClickable && (it.contentDescription?.toString()==accessible || (it is TextView && it.text.toString()==label)) }; check(view.performClick()) }
        }
        waitForIdleSync(); Thread.sleep(150)
    }
    private fun mcpSnapshotExport() {
        fun day(offset: Int): String { val c=Rules.calendar(Rules.today()); c.add(java.util.Calendar.DAY_OF_MONTH,offset); return Rules.date(c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH)+1,c.get(java.util.Calendar.DAY_OF_MONTH)) }
        val tags=listOf(Tag(1,"其他","標籤",android.graphics.Color.parseColor("#2F7554"),0),Tag(2,"冷藏","冰箱",android.graphics.Color.parseColor("#12AB34"),1))
        val items=listOf(
            Stuff(1,"牛奶 \"特價\"",day(-2),day(-10),note="第一行\n第二行",tags=setOf(1,2)),
            Stuff(2,"豆漿",day(0),day(-10)),Stuff(3,"巧克力",day(7),day(-10)),
            Stuff(4,"維他命",day(8),day(-10)),Stuff(5,"已用完",day(-3),day(-10),status=1),Stuff(6,"已丟棄",day(-3),day(-10),status=2)
        )
        val output=McpSnapshot.json(items,tags)
        val json=org.json.JSONObject(output)
        check(json.getInt("schema_version")==1 && json.getJSONArray("items").length()==6)
        check(json.getJSONArray("items").getJSONObject(0).getString("name")=="牛奶 \"特價\"")
        check(json.getJSONArray("items").getJSONObject(0).getString("note")=="第一行\n第二行")
        check(json.getJSONArray("tags").getJSONObject(1).getString("color")=="#12AB34")
        check(json.getJSONArray("items").getJSONObject(4).getString("status")=="used_up")
        check(json.getJSONArray("items").getJSONObject(5).getString("status")=="discarded")
        check(org.json.JSONObject(McpSnapshot.json(emptyList(),emptyList())).getJSONArray("items").length()==0)
        val dir=targetContext.getExternalFilesDir("qa")!!.apply { mkdirs() }
        java.io.File(dir,"mcp-snapshot.json").writeText(output,Charsets.UTF_8)
        java.io.File(dir,"mcp-expected.txt").writeText(Rules.attentionExport(items),Charsets.UTF_8)
    }
    private fun sortingInteractions() {
        val db=Store(targetContext); val original=db.tags().map { it.id }; val created=mutableListOf<Long>()
        val prefix="排序驗收${System.nanoTime()%10000}"
        var activity: Activity?=null
        try {
            repeat(15) { n -> val name="$prefix-${n+1}"; db.saveTag(null,name,TagStyles.icons[n],android.graphics.Color.parseColor(TagStyles.colors[n].second)); created.add(db.tags().first { it.name==name }.id) }
            val baseline=db.tags().map { it.id }
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync()
            fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
            fun click(label: String) { clickUi(activity!!,label) }
            fun list(): TagOrderView {
                if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) return views(activity!!.window.decorView).filterIsInstance<TagOrderView>().single()
                var value: TagOrderView?=null; runOnMainSync { value=views(activity!!.window.decorView).filterIsInstance<TagOrderView>().single() }; return value!!
            }
            fun ids(): List<Long> { var value=emptyList<Long>(); runOnMainSync { value=list().orderedIds }; return value }
            fun center(name: String): Pair<Float,Float> { var value=0f to 0f; runOnMainSync {
                val view=views(activity!!.window.decorView).first { it.contentDescription?.toString()==name && it.isShown }
                val location=IntArray(2); view.getLocationOnScreen(location); value=(location[0]+view.width/2f) to (location[1]+view.height/2f)
            }; return value }
            fun touch(action: Int,x: Float,y: Float,down: Long) {
                val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
                check(uiAutomation.injectInputEvent(event,true)); event.recycle()
            }
            fun screenshot(name: String) { val bitmap=uiAutomation.takeScreenshot()!!; val dir=targetContext.getExternalFilesDir("qa")!!; dir.mkdirs(); java.io.File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            click("設定"); click("標籤管理")
            val firstName=db.tags().first { it.id==created[0] }.name; val secondName=db.tags().first { it.id==created[1] }.name
            val source=center("排序選項 $firstName"); val target=center("排序選項 $secondName"); var down=android.os.SystemClock.uptimeMillis()
            touch(android.view.MotionEvent.ACTION_DOWN,source.first,source.second,down); Thread.sleep(650)
            var lifted=false; runOnMainSync { lifted=(list().findViewHolderForItemId(created[0])?.itemView?.scaleX ?: 1f)>1f }; check(lifted) { "Drag lift was not applied" }
            screenshot("sorting-lift")
            repeat(18) { step -> touch(android.view.MotionEvent.ACTION_MOVE,source.first,source.second+(target.second-source.second+25)*(step+1)/18f,down); Thread.sleep(16) }
            touch(android.view.MotionEvent.ACTION_UP,source.first,target.second+25,down); waitForIdleSync(); Thread.sleep(350)
            check(ids().indexOf(created[0])>baseline.indexOf(created[0])) { "Touch drag did not change order" }; check(db.tags().map { it.id }==baseline)
            click("復原排序"); check(ids()==baseline)
            click("排序選項 $firstName")
            var menuNode=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("移到最下").first()
            while(!menuNode.isClickable && menuNode.parent!=null) menuNode=menuNode.parent
            check(menuNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { "Move menu item could not be clicked" }; waitForIdleSync(); Thread.sleep(500)
            check(ids().last()==created[0]); click("儲存排序"); check(db.tags().last().id==created[0])
            val saved=db.tags().map { it.id }; val edgeName=db.tags().first { it.id==created[1] }.name
            val edgeSource=center("排序選項 $edgeName")
            var bottom=0f; runOnMainSync { val view=list(); val location=IntArray(2); view.getLocationOnScreen(location); bottom=location[1]+view.height-5f }
            down=android.os.SystemClock.uptimeMillis(); touch(android.view.MotionEvent.ACTION_DOWN,edgeSource.first,edgeSource.second,down); Thread.sleep(650)
            repeat(24) { step -> touch(android.view.MotionEvent.ACTION_MOVE,edgeSource.first,edgeSource.second+(bottom-edgeSource.second)*(step+1)/24f,down); Thread.sleep(18) }
            repeat(36) { touch(android.view.MotionEvent.ACTION_MOVE,edgeSource.first,bottom,down); Thread.sleep(40) }
            var scrolled=false; runOnMainSync { scrolled=list().computeVerticalScrollOffset()>0 }; check(scrolled) { "Drag at the bottom edge did not auto-scroll" }
            screenshot("sorting-edge-drag"); touch(android.view.MotionEvent.ACTION_UP,edgeSource.first,bottom,down); waitForIdleSync(); Thread.sleep(350)
            val pending=ids(); check(pending!=saved); check(db.tags().map { it.id }==saved)
            val monitor=addMonitor(MainActivity::class.java.name,null,false); runOnMainSync { activity!!.recreate() }
            activity=monitor.waitForActivityWithTimeout(10000) ?: error("Sorting recreation failed"); removeMonitor(monitor); waitForIdleSync(); check(ids()==pending)
            click("儲存排序"); check(db.tags().map { it.id }==pending)
        } finally {
            activity?.let { a -> runOnMainSync { a.finish() } }
            created.forEach { db.deleteTag(it) }; db.reorder(original); db.close()
        }
    }
    private fun designPreview() {
        Thread.sleep(3000)
        val db=Store(targetContext); val items=mutableListOf<Long>(); val addedTags=mutableListOf<Long>(); var activity: Activity?=null; val result=Bundle(); var success=false
        try {
            fun tag(name: String,icon: String,color: Int): Long {
                db.tags().find { it.name==name }?.let { return it.id }
                db.saveTag(null,name,icon,color); val id=db.tags().first { it.name==name }.id; addedTags.add(id); return id
            }
            fun day(offset: Int): String { val c=Rules.calendar(Rules.today()); c.add(java.util.Calendar.DAY_OF_MONTH,offset); return Rules.date(c.get(java.util.Calendar.YEAR),c.get(java.util.Calendar.MONTH)+1,c.get(java.util.Calendar.DAY_OF_MONTH)) }
            val fridge=tag("冷藏","冰箱",android.graphics.Color.parseColor(TagStyles.colors.first { it.first=="松綠" }.second)); val sweets=tag("零食","糖果",android.graphics.Color.parseColor(TagStyles.colors.first { it.first=="磚紅" }.second)); val daily=tag("日常","日用品",android.graphics.Color.parseColor(TagStyles.colors.first { it.first=="藍色" }.second))
            listOf(Stuff(name="冷萃鮮乳",expiry=day(-3),created=day(-10),note="冰箱第二層",tags=setOf(fridge)),Stuff(name="北海道生巧克力",expiry=day(4),created=day(-7),note="留一份給妹妹",tags=setOf(sweets)),Stuff(name="維他命 D3",expiry=day(19),created=day(-20),tags=setOf(daily))).forEach { items.add(db.save(it)) }
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync()
            fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
            fun click(label: String) { clickUi(activity!!,label) }
            fun snap(name: String) { Thread.sleep(700); val bitmap=uiAutomation.takeScreenshot()!!; val dir=targetContext.getExternalFilesDir("qa")!!; dir.mkdirs(); java.io.File(dir,"design-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            snap("home")
            click("日期篩選"); snap("date-filter"); popupClick("取消")
            runOnMainSync { views(activity!!.window.decorView).filterIsInstance<android.widget.ScrollView>().single().fullScroll(View.FOCUS_DOWN) }; waitForIdleSync(); snap("home-lower")
            runOnMainSync { views(activity!!.window.decorView).filterIsInstance<EditText>().single().setText("找不到的物品") }; waitForIdleSync(); snap("home-empty")
            runOnMainSync {
                check(views(activity!!.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="這裡空空的QQ" })
                views(activity!!.window.decorView).filterIsInstance<EditText>().single().setText("")
            }; waitForIdleSync()
            runOnMainSync { views(activity!!.window.decorView).first { it.contentDescription?.toString()?.startsWith("冷萃鮮乳，")==true }.performClick() }; waitForIdleSync(); snap("detail")
            click("編輯"); snap("edit")
            // Relaunch cleanly after collecting the form, without committing its draft.
            runOnMainSync { activity!!.finish() }
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync()
            click("匯出快過期物品"); snap("export")
            uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("關閉").first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); waitForIdleSync()
            click("設定"); snap("settings"); click("標籤管理"); snap("tags"); click("編輯"); snap("tag-editor"); click("圖示 · 25"); snap("tag-icons"); click("返回"); click("返回"); click("通知管理"); snap("notifications")
            success=true; result.putString("stream","PASS: Design screenshots captured with temporary fixtures\n")
        } catch(t: Throwable) { result.putString("stream",t.stackTraceToString()) }
        finally { items.forEach { db.deleteItem(it) }; addedTags.forEach { db.deleteTag(it) }; db.close(); activity?.let { a -> runOnMainSync { a.finish() } }; finish(if(success) RESULT_OK else RESULT_CANCELED,result) }
    }
    private fun uiAndNotifications() {
        val db=Store(targetContext)
        val originalEnabled=db.enabled; val originalDays=db.setting("days").toInt(); val originalMinute=db.setting("minute").toInt()
        val marker="驗收牛奶${System.nanoTime()%10000}"
        var activity: Activity?=null
        try {
            db.settings(true,3,1200)
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
            fun click(label: String) { clickUi(activity!!,label) }
            fun input(hint: String,value: String) { runOnMainSync { views(activity!!.window.decorView).filterIsInstance<EditText>().first { it.hint?.toString()==hint }.setText(value) }; waitForIdleSync() }
            fun snapshot(name: String) {
                Thread.sleep(400)
                val bitmap=uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
                val directory=targetContext.getExternalFilesDir("qa")!!; directory.mkdirs()
                java.io.File(directory,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
            click("＋ 新增物品")
            runOnMainSync {
                val chosen=views(activity!!.window.decorView).first { it.contentDescription?.toString()=="標籤 其他 已選取" }
                check(chosen.isSelected); check(chosen.performClick())
            }; waitForIdleSync()
            runOnMainSync {
                val unchosen=views(activity!!.window.decorView).first { it.contentDescription?.toString()=="標籤 其他 未選取" }
                check(!unchosen.isSelected); check(unchosen.performClick())
            }; waitForIdleSync()
            runOnMainSync { check(views(activity!!.window.decorView).filterIsInstance<Button>().none { it.text.toString().matches(Regex("[0-9]{2}:[0-9]{2}")) }) { "New item must not expose a time control" } }
            input("物品名稱",marker); input("備註","測試備註"); click("新增")
            val id=db.items().single { it.name==marker }.id
            check(!db.items().single { it.id==id }.reminder)
            check(db.items().single { it.id==id }.minute==1200) { "New item did not inherit app default time" }
            db.settings(true,3,540)
            snapshot("detail")
            click("編輯")
            runOnMainSync { check(views(activity!!.window.decorView).filterIsInstance<Button>().none { it.text.toString().matches(Regex("[0-9]{2}:[0-9]{2}")) }) { "Edit item must not expose a time control" } }
            input("備註","已修改備註")
            val monitor=addMonitor(MainActivity::class.java.name,null,false)
            runOnMainSync { activity!!.recreate() }
            activity=monitor.waitForActivityWithTimeout(10000) ?: error("Activity recreation failed")
            removeMonitor(monitor); waitForIdleSync()
            runOnMainSync { check(views(activity!!.window.decorView).filterIsInstance<EditText>().first { it.hint?.toString()=="備註" }.text.toString()=="已修改備註") }
            snapshot("edit"); click("儲存")
            check(db.items().single { it.id==id }.note=="已修改備註")
            check(db.items().single { it.id==id }.minute==1200) { "Editing overwrote existing reminder time" }
            click("‹ 返回"); input("搜尋名稱或備註",marker); click("用完ㄌ")
            check(db.items().single { it.id==id }.status==1)
            click("已用完"); click("復原"); check(db.items().single { it.id==id }.status==0)
            click("使用中"); snapshot("home-with-item")
            click("日期篩選")
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("篩選起日").isNotEmpty())
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("篩選迄日").isNotEmpty())
            popupClick("套用")
            runOnMainSync { check(views(activity!!.window.decorView).filterIsInstance<EditText>().single().text.toString()==marker) }
            click("日期篩選"); popupClick("清除")
            val original=db.items().single { it.id==id }
            db.save(original.copy(expiry="2000-01-01"))
            input("搜尋名稱或備註","不符合任何物品的搜尋")
            click("匯出快過期物品")
            Thread.sleep(400)
            val dialog=uiAutomation.rootInActiveWindow
            check(dialog.findAccessibilityNodeInfosByText(marker).isNotEmpty()) { "Export must ignore homepage search" }
            fun findCopy(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                if(node.contentDescription?.toString()=="複製快過期物列表") return node
                for(n in 0 until node.childCount) { val child=node.getChild(n) ?: continue; findCopy(child)?.let { return it } }
                return null
            }
            check(findCopy(dialog)!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync()
            runOnMainSync {
                val copied=targetContext.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
                check(copied==Rules.attentionExport(db.items())) { "Clipboard differs from export text" }
            }
            snapshot("expired-export")
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("關閉").first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync(); db.save(original); input("搜尋名稱或備註",marker)
            click("⚙ 設定")
            val exportedFile=java.io.File(targetContext.cacheDir,"mcp-export-test.json")
            exportedFile.writeText("unchanged",Charsets.UTF_8)
            val pickerFilter=android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("application/json") }
            val cancelled=addMonitor(pickerFilter,ActivityResult(Activity.RESULT_CANCELED,null),true)
            try { click("匯出 MCP 資料"); check(exportedFile.readText(Charsets.UTF_8)=="unchanged") } finally { removeMonitor(cancelled) }
            val saved=addMonitor(pickerFilter,ActivityResult(Activity.RESULT_OK,Intent().setData(android.net.Uri.fromFile(exportedFile))),true)
            try {
                click("匯出 MCP 資料")
                val until=android.os.SystemClock.uptimeMillis()+10000
                var exported: org.json.JSONObject?=null
                while(exported==null && android.os.SystemClock.uptimeMillis()<until) { exported=runCatching { org.json.JSONObject(exportedFile.readText(Charsets.UTF_8)) }.getOrNull(); Thread.sleep(100) }
                check(exported!=null) { "MCP document export did not finish" }
                check(exported.getJSONArray("items").length()==db.items().size) { "MCP export must include all statuses and ignore the homepage query" }
                check(exported.getJSONArray("items").let { all -> (0 until all.length()).any { all.getJSONObject(it).getString("name")==marker } })
                check(exported.getJSONArray("tags").length()==db.tags().size)
            } finally { removeMonitor(saved); exportedFile.delete() }
            click("標籤管理"); click("＋ 新增標籤"); input("標籤名稱",marker); click("新增")
            check(db.tags().any { it.name==marker })
            click("編輯"); input("標籤名稱"," "); click("儲存"); check(db.tags().any { it.name==marker })
            input("標籤名稱",marker)
            click("圖示 · 25")
            runOnMainSync { check(views(activity!!.window.decorView).count { it.contentDescription?.toString() in TagStyles.icons.map { name -> "圖示 $name" } }==25) }
            click("圖示 票券"); click("顏色 · 25")
            runOnMainSync { check(views(activity!!.window.decorView).count { it.contentDescription?.toString() in TagStyles.colors.map { (name,_) -> "顏色 $name" } }==25) }
            click("顏色 蜜桃"); click("儲存")
            check(db.tags().single { it.name==marker }.let { it.icon=="票券" && it.color==android.graphics.Color.parseColor(TagStyles.colors.first { it.first=="蜜桃" }.second) })
            snapshot("tags")
            click("‹ 返回"); click("通知管理")
            runOnMainSync {
                val visible=views(activity!!.window.decorView)
                check(visible.any { it.contentDescription?.toString()=="預設提醒時間" })
                check(visible.filterIsInstance<TextView>().none { it.text.toString().contains("手機系統") }) { "Phone-system settings button must be removed" }
                check(visible.count { it.contentDescription?.toString() in Rules.offsetNames.map { value -> "通知日期 $value" } }==7)
            }
            snapshot("notifications")
            db.settings(true,3,540)
            db.save(db.items().single { it.id==id }.copy(reminder=true,expiry=Rules.today(),days=3))
            val notifications=targetContext.getSystemService(NotificationManager::class.java)
            fun refresh() { val latch=CountDownLatch(1); Reminders.refresh(targetContext) { latch.countDown() }; check(latch.await(10,TimeUnit.SECONDS)); Thread.sleep(200) }
            refresh(); check(notifications.activeNotifications.any { it.id==id.toInt() }) { "Reminder not delivered; grant POST_NOTIFICATIONS before running" }
            check(db.items().single { it.id==id }.delivered!=null)
            notifications.cancel(id.toInt()); refresh(); check(notifications.activeNotifications.none { it.id==id.toInt() })
            db.status(id,1); db.save(db.items().single { it.id==id }.copy(days=7)); refresh()
            check(notifications.activeNotifications.none { it.id==id.toInt() })
            db.status(id,0); db.settings(false,3,540); refresh(); check(notifications.activeNotifications.none { it.id==id.toInt() })
            db.settings(true,3,540); refresh(); check(notifications.activeNotifications.any { it.id==id.toInt() })
        } finally {
            db.items().filter { it.name==marker }.forEach { db.deleteItem(it.id); targetContext.getSystemService(NotificationManager::class.java).cancel(it.id.toInt()) }
            db.tags().filter { it.name==marker }.forEach { db.deleteTag(it.id) }
            db.settings(originalEnabled,originalDays,originalMinute); db.close()
            activity?.let { a -> runOnMainSync { a.finish() } }
            Reminders.refresh(targetContext)
        }
    }
    companion object { const val RESULT_OK=-1; const val RESULT_CANCELED=0 }
}
