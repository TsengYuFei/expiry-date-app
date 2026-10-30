package tw.expirybook

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.*
import android.view.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var filter=Filter()
    private var page="home"
    private var opened=0L
    private var captureDraft: (() -> Bundle)?=null
    private var feedback: Toast?=null
    private val green=AtelierPalette.sage
    private val ink=AtelierPalette.ink
    private val muted=AtelierPalette.muted
    private val paper=AtelierPalette.paper
    private val surface=AtelierPalette.surface
    private val border=AtelierPalette.line
    private val rust=AtelierPalette.rose
    private var tagDraft: LongArray?=null
    private var tagOrder: TagOrderView?=null
    private var tagSavedIds=emptyList<Long>()
    private lateinit var pageScroll: ScrollView
    private lateinit var headerBar: LinearLayout
    private val colors=TagStyles.colors
    private val icons=TagStyles.icons
    private val handler=Handler(Looper.getMainLooper())
    private var shownDate=Rules.today()
    private val dateRefresh=object: Runnable { override fun run() { if(page=="home" && shownDate!=Rules.today()) { shownDate=Rules.today(); home() }; handler.postDelayed(this,60000) } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store=Store(this)
        if(state!=null) filter=Filter(state.getString("query", ""),state.getLong("tag",-1).takeIf { it>=0 },state.getInt("status"),state.getString("from"),state.getString("to"))
        if(Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        tagDraft=state?.getLongArray("tagDraft")
        home()
        if(state==null) openIntent(intent) else {
            val id=state.getLong("opened",0)
            when(state.getString("page")) {
                "detail" -> detail(id)
                "edit" -> edit(store.items().find { it.id==id },state.getBundle("draft"))
                "settings" -> settingsMenu()
                "notifications" -> notifications(state.getBundle("draft"))
                "tags" -> tagsPage()
                "tagEdit" -> tagEditor(store.tags().find { it.id==id },state.getBundle("draft"))
                "date" -> dateFilter()
            }
        }
    }
    override fun onResume() { super.onResume(); Reminders.refresh(this); handler.postDelayed(dateRefresh,60000) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(dateRefresh) }
    override fun onDestroy() { store.close(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); if(page=="tags") tagDraft=tagOrder?.orderedIds?.toLongArray(); out.putLongArray("tagDraft",tagDraft); out.putString("query",filter.query); out.putLong("tag",filter.tag ?: -1); out.putInt("status",filter.status); out.putString("from",filter.from); out.putString("to",filter.to); out.putString("page",page); out.putLong("opened",opened); out.putBundle("draft",captureDraft?.invoke()) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openIntent(intent) }
    private fun openIntent(intent: Intent) { val id=intent.getLongExtra("item",0); if(id!=0L && store.items().any { it.id==id }) detail(id) }
    // API 33+ uses the native OnBackInvokedDispatcher registered above; this handles API 24–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Platform back compatibility") override fun onBackPressed() { navigateBack() }
    private fun navigateBack() {
        when(page) { "home" -> finish(); "edit" -> confirm("放棄尚未儲存的修改？") { if(opened==0L) home() else detail(opened) }; "tags" -> leaveTags(); "notifications" -> settingsMenu(); "tagEdit" -> confirm("放棄尚未儲存的修改？") { tagsPage() }; else -> home() }
    }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun text(value: String,size: Float=16f,bold: Boolean=false)=TextView(this).apply { text=value; textSize=size; setTextColor(ink); typeface=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",Typeface.NORMAL); setPadding(0,dp(5),0,dp(5)); includeFontPadding=false }
    private fun shape(color: Int,radius: Int=10,stroke: Int?=null)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); if(stroke!=null) setStroke(dp(1),stroke) }
    private fun wash(color: Int,amount: Float=.2f)=AtelierPalette.tint(color,amount)
    private fun pigment(color: Int)=AtelierPalette.pigment(color)
    private fun onColor(color: Int)=if(TagStyles.lightText(color)) Color.WHITE else ink
    private fun panel(fill: Int=paper,edge: Int?=null)=GreenPanelDrawable(resources.displayMetrics.density,fill,edge)
    private fun rule(body: LinearLayout) { body.addView(View(this).apply { background=GreenPanelDrawable(resources.displayMetrics.density,Color.TRANSPARENT,divider=true); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(-1,dp(12))) }
    private fun glyph(name: String,color: Int=ink,size: Int=22)=LineIcon(name,color,dp(size))
    private fun iconButton(icon: String,label: String,color: Int=ink,action: ()->Unit)=ImageButton(this).apply {
        setImageDrawable(glyph(icon,color)); contentDescription=label; tooltipTextCompat(label)
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x188FA394),shape(Color.TRANSPARENT,24),null)
        setPadding(dp(12),dp(12),dp(12),dp(12)); minimumWidth=dp(48); minimumHeight=dp(48)
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)); setOnClickListener { action() }
    }
    private fun View.tooltipTextCompat(label: String) { if(Build.VERSION.SDK_INT>=26) tooltipText=label }
    private fun spacer(body: LinearLayout,height: Int=16) { body.addView(View(this),LinearLayout.LayoutParams(1,dp(height))) }
    private fun section(body: LinearLayout,label: String) { spacer(body,12); body.addView(text(label,12f,true).apply { setTextColor(muted); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(when { label.contains("時間")->"clock"; (label.contains("日期") || label=="有效期限")->"calendar"; label=="物品名稱"->"日用品"; label=="分類"->"標籤"; label=="備註"->"edit"; else->"bell" },green,14),null,null,null); compoundDrawablePadding=dp(6) }); spacer(body,6) }
    private fun button(value: String,action: ()->Unit)=Button(this).apply {
        text=value; contentDescription=value; isAllCaps=false; textSize=14f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        val primary=value in listOf("新增","儲存","套用","儲存排序","選擇")
        setTextColor(if(primary) Color.WHITE else if(value=="刪除") rust else ink)
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x188FA394),shape(if(primary) green else paper,8,if(primary) null else border),null)
        elevation=0f; stateListAnimator=null; minHeight=dp(48); minimumHeight=dp(48); minWidth=dp(72); setPadding(dp(18),dp(9),dp(18),dp(9))
        layoutParams=LinearLayout.LayoutParams(-2,-2).apply { topMargin=dp(5); bottomMargin=dp(5) }; setOnClickListener { action() }
    }
    private fun pinAction(label: String,leading: View?=null,action: ()->Unit) {
        val footer=row().apply { setPadding(0,dp(8),0,dp(4)); setBackgroundColor(surface) }
        if(leading!=null) footer.addView(leading)
        footer.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        footer.addView(button(label,action),LinearLayout.LayoutParams(dp(132),dp(48))); root.addView(footer)
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun field(label: String,initial: String="",multi: Boolean=false)=EditText(this).apply {
        hint=label; contentDescription=label; setText(initial); textSize=if(label=="物品名稱" || label=="標籤名稱") 26f else 16f
        setTextColor(ink); setHintTextColor(muted); setSingleLine(!multi)
        inputType=if(multi) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_CLASS_TEXT
        if(multi) minLines=2
        background=shape(paper,14,border)
        setPadding(dp(12),dp(12),dp(12),dp(12)); minHeight=dp(54)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6); bottomMargin=dp(12) }
    }
    private fun styleSwitch(view: Switch) { view.setTextColor(ink); view.textSize=16f; view.background=shape(surface,14); view.setPadding(dp(4),dp(8),dp(4),dp(8)); view.minHeight=dp(52); view.layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4); bottomMargin=dp(4) } }
    private fun menuRow(icon: String,label: String,action: ()->Unit): LinearLayout = row().apply {
        setPadding(0,dp(14),0,dp(14)); minimumHeight=dp(70)
        addView(ImageView(this@MainActivity).apply { setImageDrawable(glyph(icon,ink,21)); background=shape(surface,24); setPadding(dp(12),dp(12),dp(12),dp(12)) },LinearLayout.LayoutParams(dp(46),dp(46)))
        addView(text(label,16f).apply { setPadding(dp(14),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(glyph("arrow",muted,16)) },LinearLayout.LayoutParams(dp(26),dp(26)))
        isClickable=true; contentDescription=label; setOnClickListener { action() }; layoutParams=LinearLayout.LayoutParams(-1,-2)
    }
    private fun offsetPicker(initial: Int,change: (Int)->Unit): LinearLayout {
        val area=column(); var selected=initial
        fun draw() {
            area.removeAllViews()
            Rules.offsets.indices.toList().chunked(4).forEach { group -> val r=row(); group.forEach { index ->
                val value=Rules.offsets[index]
                r.addView(button(Rules.offsetNames[index]) { selected=value; change(value); draw() }.apply {
                    background=shape(if(value==selected) AtelierPalette.mint else surface,24); setTextColor(if(value==selected) green else ink); textSize=12f; minWidth=0; minimumWidth=0; setPadding(dp(3),dp(8),dp(3),dp(8)); isSelected=value==selected; contentDescription="通知日期 ${Rules.offsetNames[index]}"
                },LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(0,dp(4),dp(5),dp(4)) })
            }; if(group.size<4) r.addView(View(this),LinearLayout.LayoutParams(0,1,(4-group.size).toFloat())); area.addView(r) }
        }; draw(); return area
    }
    private fun enableTree(view: View,enabled: Boolean) { view.isEnabled=enabled; if(view is ViewGroup) for(index in 0 until view.childCount) enableTree(view.getChildAt(index),enabled) }
    private fun start(name: String,title: String,back: ()->Unit={home()}): LinearLayout {
        page=name; captureDraft=null; setContentView(R.layout.activity_main); root=findViewById(R.id.root)
        root.setBackgroundColor(surface)
        root.setOnApplyWindowInsetsListener { v,insets -> val s=insets.systemWindowInsetTop; v.setPadding(dp(16),dp(6)+s,dp(16),dp(6)+insets.systemWindowInsetBottom); insets }
        root.requestApplyInsets()
        headerBar=row(); if(name!="home") headerBar.addView(iconButton("back","返回",action=back))
        if(name=="home") headerBar.addView(ImageView(this).apply { setImageDrawable(glyph("leaf",green,26)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(28),dp(38)).apply { rightMargin=dp(10) })
        headerBar.addView(text(title,if(name=="home") 20f else 21f,false).apply { letterSpacing=.01f },LinearLayout.LayoutParams(0,-2,1f)); root.addView(headerBar)
        val body=column().apply { setPadding(0,dp(10),0,dp(12)) }; pageScroll=ScrollView(this); pageScroll.isFillViewport=true; pageScroll.isVerticalScrollBarEnabled=false; pageScroll.addView(body); root.addView(pageScroll,LinearLayout.LayoutParams(-1,0,1f)); return body
    }
    private fun toast(s: String) { feedback?.cancel(); feedback=Toast.makeText(this,s,Toast.LENGTH_SHORT).also { it.show() } }
    private fun attempt(block: ()->Unit) { try { block() } catch(e: IllegalArgumentException) { toast(e.message ?: "資料有誤") } }
    private fun confirm(title: String,positive: String="確定",action: ()->Unit) { AlertDialog.Builder(this).setTitle(title).setNegativeButton("取消",null).setPositiveButton(positive) { _,_->action() }.show() }
    private fun saveAction(block: ()->Unit) { attempt { block(); Reminders.refresh(this) } }
    private fun tagText(tag: Tag)=tag.name
    private fun tagChip(tag: Tag,selected: Boolean=false,action: ()->Unit): Button = button(tag.name,action).apply {
        val foreground=if(selected) Color.WHITE else ink
        setTextColor(foreground); textSize=12f; isSelected=selected
        setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(tag.icon,if(selected) Color.WHITE else tag.color,16),null,glyph(if(selected) "check" else "plus",foreground,15),null); compoundDrawablePadding=dp(6)
        background=shape(if(selected) green else paper,12,if(selected) green else border)
        minHeight=dp(48); minimumHeight=dp(48); setPadding(dp(10),dp(7),dp(10),dp(7)); minWidth=0; minimumWidth=0
        contentDescription="標籤 ${tag.name} ${if(selected) "已選取" else "未選取"}"
        accessibilityDelegate=object: View.AccessibilityDelegate() { override fun onInitializeAccessibilityNodeInfo(host: View,info: android.view.accessibility.AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host,info); info.isCheckable=true; info.isChecked=selected } }
    }
    private fun chips(tags: List<Tag>,selected: Set<Long>,change: (Long)->Unit): LinearLayout {
        val wrap=column()
        tags.chunked(2).forEach { group -> val r=row(); group.forEach { t -> r.addView(tagChip(t,t.id in selected) { change(t.id) },LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(0,dp(4),dp(6),dp(4)) }) }; if(group.size<2) r.addView(View(this),LinearLayout.LayoutParams(0,1,(2-group.size).toFloat())); wrap.addView(r) }; return wrap
    }
    private fun home() {
        start("home","有效期限簿")
        root.removeView(pageScroll)
        val content=column()
        val homeScroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(content) }
        headerBar.addView(iconButton("settings","設定",green) { settingsMenu() })
        val inventory=store.items(); val today=Rules.today()
        val active=inventory.count { it.status==0 }; val attention=Rules.attentionCount(inventory,today)
        val amber=Color.rgb(133,69,8); val amberFill=Color.rgb(247,193,86); val expired=Color.rgb(151,38,48)
        val summary=row().apply { gravity=Gravity.CENTER_VERTICAL; background=panel(green); setPadding(dp(14),dp(10),dp(14),dp(10)) }
        fun metric(label: String,value: Int,icon: String,description: String): LinearLayout {
            val block=column()
            block.addView(text(label,11f).apply { setTextColor(Color.WHITE); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(icon,Color.WHITE,15),null,null,null); compoundDrawablePadding=dp(6) })
            block.addView(text(value.toString(),30f,true).apply { setTextColor(Color.WHITE); contentDescription=description })
            return block
        }
        summary.addView(metric("使用中",active,"leaf","使用中的物品 $active 件"),LinearLayout.LayoutParams(0,-2,1f))
        summary.addView(View(this).apply { setBackgroundColor(0x558CD3AB) },LinearLayout.LayoutParams(dp(1),dp(40)).apply { leftMargin=dp(12); rightMargin=dp(16) })
        val urgency=metric("即將＋已過期",attention,"clock","即將及已過期的使用中物品 $attention 件"); val urgentNumber=urgency.getChildAt(1); urgency.removeView(urgentNumber); val urgentActions=row(); urgentActions.addView(urgentNumber,LinearLayout.LayoutParams(0,-2,1f)); urgentActions.addView(iconButton("export","匯出快過期物品",Color.WHITE) { exportExpired() },LinearLayout.LayoutParams(dp(44),dp(44))); urgency.addView(urgentActions); summary.addView(urgency,LinearLayout.LayoutParams(0,-2,1f)); content.addView(summary,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6); bottomMargin=dp(10) })
        val searchRow=row()
        val search=field("搜尋名稱或備註",filter.query).apply { textSize=14f; setPadding(dp(12),dp(10),dp(12),dp(10)); background=shape(paper,14,border); minHeight=dp(48); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("search",green,18),null,null,null); compoundDrawablePadding=dp(8) }
        searchRow.addView(search,LinearLayout.LayoutParams(0,dp(48),1f).apply { rightMargin=dp(6) }); searchRow.addView(iconButton("calendar","日期篩選",green) { dateFilter() }); content.addView(searchRow)
        val allTags=store.tags(); if(filter.tag!=null && allTags.none { it.id==filter.tag }) filter=filter.copy(tag=null)
        val categories=row().apply { setPadding(0,dp(10),0,dp(8)) }
        val all=button("全部") { filter=filter.copy(tag=null); home() }.apply { background=shape(if(filter.tag==null) green else surface,8); setTextColor(if(filter.tag==null) Color.WHITE else ink); minWidth=0; minimumWidth=0 }
        categories.addView(all,LinearLayout.LayoutParams(-2,dp(44)).apply { rightMargin=dp(6) })
        allTags.forEach { tag -> categories.addView(tagChip(tag,filter.tag==tag.id) { filter=filter.copy(tag=if(filter.tag==tag.id) null else tag.id); home() }.apply { contentDescription="標籤 ${tag.name} ${if(filter.tag==tag.id) "已選取" else "未選取"}" },LinearLayout.LayoutParams(-2,dp(44)).apply { rightMargin=dp(6) }) }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(categories) },LinearLayout.LayoutParams(-1,-2))
        val bar=row()
        val status=button(Rules.statuses[filter.status]) {}.apply {
            contentDescription="狀態篩選"; background=shape(Color.TRANSPARENT,0); setPadding(0,0,dp(8),0); gravity=Gravity.CENTER_VERTICAL or Gravity.START; textSize=13f
            setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("filter",green,16),null,glyph("down",muted,14),null); compoundDrawablePadding=dp(6)
            setOnClickListener { PopupMenu(this@MainActivity,this).apply { Rules.statuses.forEachIndexed { i,label -> menu.add(0,i,0,label) }; setOnMenuItemClickListener { filter=filter.copy(status=it.itemId); home(); true }; show() } }
        }; bar.addView(status,LinearLayout.LayoutParams(-2,dp(44)))
        content.addView(bar)
        val cards=column()
        if(filter.from!=null || filter.to!=null) cards.addView(text("${filter.from?.let(Rules::roc) ?: "不限"} — ${filter.to?.let(Rules::roc) ?: "不限"}",11f).apply { setTextColor(muted) })
        val list=column(); cards.addView(list)
        content.addView(cards)
        root.addView(homeScroll,LinearLayout.LayoutParams(-1,0,1f))
        fun render() {
            list.removeAllViews(); val items=Rules.filter(store.items(),filter)
            if(items.isEmpty()) { list.addView(ImageView(this).apply { setImageResource(R.drawable.illustration_garden); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(-1,dp(120))); list.addView(text("這裡空空的QQ",14f).apply { setTextColor(muted); gravity=Gravity.CENTER }) }
            items.forEach { item ->
                val days=Rules.remaining(item.expiry,today); val phase=Rules.expiryPhase(item.expiry,today)
                val stateColor=when(phase) { 2->expired; 1->amber; else->green }
                val stateFill=when(phase) { 2->Color.rgb(183,48,60); 1->amberFill; else->Color.rgb(237,246,239) }
                val fg=stateColor
                val entry=column().apply { background=panel(paper,border); setPadding(dp(12),dp(10),dp(12),dp(2)); isClickable=true; setOnClickListener { detail(item.id) }; contentDescription="${item.name}，${Rules.expiryText(item.expiry)}，查看資訊" }
                val main=row().apply { gravity=Gravity.TOP; isBaselineAligned=false }
                val tags=allTags.filter { it.id in item.tags }; val cover=tags.firstOrNull { it.id!=1L } ?: tags.firstOrNull()
                main.addView(ImageView(this).apply { background=shape(wash(cover?.color ?: green,.15f),12); setImageDrawable(glyph(cover?.icon ?: "日用品",cover?.color ?: green,23)); setPadding(dp(10),dp(10),dp(10),dp(10)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(44),dp(44)).apply { rightMargin=dp(10); topMargin=dp(3) })
                val info=column()
                info.addView(text(item.name,16f,true).apply { setPadding(0,0,0,dp(5)) })
                info.addView(text(Rules.roc(item.expiry),10f).apply { setTextColor(muted); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("calendar",muted,12),null,null,null); compoundDrawablePadding=dp(4) })
                main.addView(info,LinearLayout.LayoutParams(0,-2,1f))
                val badgeColor=if(phase==2) Color.WHITE else stateColor
                val badge=column().apply { gravity=Gravity.CENTER; background=shape(stateFill,12); setPadding(dp(5),dp(5),dp(5),dp(5)); contentDescription=when(phase) { 2->"已過期"; 1->"即將過期"; else->"未過期" } }
                badge.addView(text(when(phase) { 2->"已過期"; 1->"即將過期"; else->"未過期" },9f).apply { setTextColor(badgeColor); gravity=Gravity.CENTER; setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(when(phase) { 2->"alert"; 1->"clock"; else->"leaf" },badgeColor,11),null,null,null); compoundDrawablePadding=dp(3); setPadding(0,0,0,0) })
                badge.addView(text(if(days==0) "今" else kotlin.math.abs(days).toString(),if(kotlin.math.abs(days).toString().length>3) 18f else 27f).apply { setTextColor(badgeColor); gravity=Gravity.CENTER; setPadding(0,dp(2),0,0) })
                badge.addView(text(if(days<0) "天已過期" else if(days==0) "天到期" else "天後到期",9f).apply { setTextColor(badgeColor); gravity=Gravity.CENTER; setPadding(0,0,0,0) })
                main.addView(badge,LinearLayout.LayoutParams(dp(74),-2).apply { leftMargin=dp(8) }); entry.addView(main)
                val tagRow=row().apply { setPadding(0,dp(5),0,dp(5)) }
                tags.forEach { tag -> tagRow.addView(text(tag.name,10f).apply { setTextColor(ink); background=shape(wash(tag.color,.12f),6); setPadding(dp(6),dp(3),dp(6),dp(3)); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(tag.icon,tag.color,12),null,null,null); compoundDrawablePadding=dp(4) },LinearLayout.LayoutParams(-2,-2).apply { rightMargin=dp(5) }) }
                entry.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tagRow) },LinearLayout.LayoutParams(-1,-2)); rule(entry)
                val actions=row()
                fun action(label: String,icon: String,color: Int,status: Int) { actions.addView(button(label) { changeStatus(item,status) }.apply { background=shape(paper,0); setTextColor(color); textSize=12f; minWidth=0; minimumWidth=0; setPadding(dp(4),dp(8),dp(4),dp(8)); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(icon,color,17),null,null,null); compoundDrawablePadding=dp(5) },LinearLayout.LayoutParams(0,dp(44),1f)) }
                if(item.status==0) { action("用完ㄌ","check",green,1); actions.addView(View(this).apply { setBackgroundColor(border) },LinearLayout.LayoutParams(dp(1),dp(16))); action("丟掉ㄌ","trash",expired,2) } else action("復原","restore",green,0)
                entry.addView(actions); list.addView(entry,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            }
        }
        search.addTextChangedListener(object: TextWatcher { override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}; override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { filter=filter.copy(query=s.toString()); render() }; override fun afterTextChanged(s: Editable?) {} }); render()
        val nav=row().apply { setPadding(0,dp(8),0,0); gravity=Gravity.CENTER }
        nav.addView(button("新增物品") { edit(null) }.apply { setTextColor(Color.WHITE); background=shape(green,14); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("plus",Color.WHITE,20),null,null,null); compoundDrawablePadding=dp(8) },LinearLayout.LayoutParams(-1,dp(48))); root.addView(nav)
    }
    private fun exportExpired() {
        val exported=Rules.attentionExport(store.items())
        val body=column().apply { setPadding(dp(24),dp(18),dp(24),dp(8)); setBackgroundColor(paper) }
        val header=row()
        header.addView(text("快過期ㄉ東東",21f,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(iconButton("copy","複製快過期物列表",green) {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("快過期ㄉ東東",exported))
                toast("已複製快過期物列表")
        }.apply { isEnabled=exported.isNotEmpty(); alpha=if(isEnabled) 1f else .4f })
        body.addView(header)
        val list=text(exported.ifEmpty { "目前沒有即將或已過期物品" },14f).apply { setTextIsSelectable(true); setLineSpacing(dp(8).toFloat(),1f) }
        body.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,dp(minOf((exported.lines().size*48).coerceIn(80,300),resources.configuration.screenHeightDp/2))))
        AlertDialog.Builder(this).setView(body).setPositiveButton("關閉",null).show().window?.apply { setBackgroundDrawable(shape(paper,32)); setGravity(Gravity.BOTTOM); setLayout(-1,-2); setDimAmount(.2f) }
    }
    private fun changeStatus(item: Stuff,status: Int) { store.status(item.id,status); toast(when(status) {1->"用完${item.name}了";2->"丟掉${item.name}了";else->"${item.name}已復原"}); Reminders.refresh(this); home() }
    private fun pickDate(initial: String, done: (String)->Unit) {
        val cal=Rules.calendar(initial)
        val dialog=Dialog(this); val box=column().apply { setPadding(dp(20),dp(16),dp(20),dp(16)) }
        box.addView(text("選擇日期",20f,true))
        val r=row()
        val year=NumberPicker(this).apply { minValue=1; maxValue=289; value=cal.get(java.util.Calendar.YEAR)-1911; wrapSelectorWheel=false }
        val month=NumberPicker(this).apply { minValue=1; maxValue=12; value=cal.get(java.util.Calendar.MONTH)+1 }
        val day=NumberPicker(this).apply { minValue=1; maxValue=31; value=cal.get(java.util.Calendar.DAY_OF_MONTH) }
        fun updateDays() { val c=java.util.GregorianCalendar(year.value+1911,month.value-1,1); day.maxValue=c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) }
        year.setOnValueChangedListener { _,_,_->updateDays() }; month.setOnValueChangedListener { _,_,_->updateDays() }; updateDays()
        listOf(year to "年",month to "月",day to "日").forEach { (picker,label) -> val c=column(); c.addView(text(label,12f).apply { setTextColor(muted); gravity=Gravity.CENTER }); c.addView(picker); r.addView(c,LinearLayout.LayoutParams(0,-2,1f)) }; box.addView(r)
        val actions=row(); actions.addView(button("取消") { dialog.dismiss() },LinearLayout.LayoutParams(0,dp(48),1f).apply { rightMargin=dp(12) })
        actions.addView(button("選擇") { year.clearFocus(); month.clearFocus(); day.clearFocus(); done(Rules.date(year.value+1911,month.value,day.value)); dialog.dismiss() },LinearLayout.LayoutParams(0,dp(48),1f)); box.addView(actions)
        dialog.setContentView(box); dialog.show(); dialog.window?.apply { setBackgroundDrawable(shape(paper,32)); setGravity(Gravity.CENTER); setLayout(dp(minOf(360,resources.configuration.screenWidthDp-32)),-2); setDimAmount(.25f) }
    }
    private fun dateFilter() {
        var from=filter.from; var to=filter.to
        val body=column().apply { setPadding(dp(20),dp(16),dp(20),dp(12)) }
        val heading=text("日期篩選",20f,true).apply { setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("calendar",green,22),null,null,null); compoundDrawablePadding=dp(10) }; body.addView(heading)
        val a=button("") {}.apply { contentDescription="篩選起日"; background=shape(surface,12,border); textSize=17f }
        val b=button("") {}.apply { contentDescription="篩選迄日"; background=shape(surface,12,border); textSize=17f }
        fun labels() { a.text=from?.let(Rules::roc) ?: "不限"; b.text=to?.let(Rules::roc) ?: "不限" }
        fun dateRow(label: String,view: Button,clear: ()->Unit) { val r=row(); r.addView(text(label,12f).apply { setTextColor(muted) },LinearLayout.LayoutParams(dp(28),-2)); r.addView(view,LinearLayout.LayoutParams(0,dp(52),1f)); r.addView(iconButton("close","清除${if(label=="從") "起日" else "迄日"}",muted,clear)); body.addView(r,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) }) }
        a.setOnClickListener { pickDate(from ?: Rules.today()) { from=it; labels() } }; b.setOnClickListener { pickDate(to ?: Rules.today()) { to=it; labels() } }
        dateRow("從",a) { from=null; labels() }; dateRow("至",b) { to=null; labels() }; labels()
        val error=text("",12f).apply { setTextColor(rust); visibility=View.GONE }; body.addView(error)
        val dialog=AlertDialog.Builder(this).setView(body).setNegativeButton("取消",null).setNeutralButton("清除") { _,_->filter=filter.copy(from=null,to=null); home() }.setPositiveButton("套用",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(from!=null && to!=null && from!!>to!!) { error.text="起日不可晚於迄日"; error.visibility=View.VISIBLE }
            else { filter=filter.copy(from=from,to=to); dialog.dismiss(); home() }
        } }
        dialog.show(); dialog.window?.apply { setBackgroundDrawable(shape(paper,20)); setLayout(dp(minOf(360,resources.configuration.screenWidthDp-32)),-2); setDimAmount(.25f) }
    }
    private fun detail(id: Long) {
        val item=store.items().find { it.id==id } ?: return home(); opened=id
        val body=start("detail","物品資訊")
        headerBar.addView(iconButton("edit","編輯",muted) { edit(item) }); headerBar.addView(iconButton("trash","刪除",rust) { deleteItem(item) })
        val tags=store.tags().filter { it.id in item.tags }; val color=(tags.firstOrNull { it.id!=1L } ?: tags.firstOrNull())?.color ?: green
        val date=item.expiry.split('-'); val days=Rules.remaining(item.expiry)
        val portrait=row().apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(20),0,dp(24)) }
        val stamp=column().apply { gravity=Gravity.CENTER; background=panel(paper,border); setPadding(dp(10),dp(16),dp(10),dp(16)) }
        stamp.addView(text("${date[0].toInt()-1911} / ${date[1]}",11f).apply { gravity=Gravity.CENTER; setTextColor(muted) })
        stamp.addView(text(date[2],48f).apply { typeface=Typeface.create("serif",Typeface.NORMAL); gravity=Gravity.CENTER })
        portrait.addView(stamp,LinearLayout.LayoutParams(dp(104),-2).apply { rightMargin=dp(20) })
        val title=column(); title.addView(text(item.name,27f,false)); title.addView(text(Rules.expiryText(item.expiry),13f).apply { setTextColor(if(days<0) rust else muted) }); portrait.addView(title,LinearLayout.LayoutParams(0,-2,1f)); body.addView(portrait)
        body.addView(chips(tags,emptySet()) {}.apply { for(i in 0 until childCount) { val r=getChildAt(i) as ViewGroup; for(j in 0 until r.childCount) { r.getChildAt(j).isClickable=false; r.getChildAt(j).isFocusable=false } } })
        spacer(body,24); rule(body)
        val state=row(); state.addView(text("狀態",12f).apply { setTextColor(muted) },LinearLayout.LayoutParams(0,-2,1f)); state.addView(text(Rules.statuses[item.status],15f)); body.addView(state)
        val created=row(); created.addView(text("建立日期",12f).apply { setTextColor(muted) },LinearLayout.LayoutParams(0,-2,1f)); created.addView(text(Rules.roc(item.created),15f)); body.addView(created)
        val expiry=row(); expiry.addView(text("有效期限",12f).apply { setTextColor(muted) },LinearLayout.LayoutParams(0,-2,1f)); expiry.addView(text(Rules.roc(item.expiry),15f)); body.addView(expiry)
        spacer(body,14); rule(body)
        if(item.note.isNotBlank()) { section(body,"備註"); body.addView(text(item.note,17f).apply { setLineSpacing(dp(5).toFloat(),1f) }) }
        section(body,"提醒"); body.addView(text(if(item.reminder) "${Rules.offsetNames[Rules.offsets.indexOf(item.days)]} · ${time(item.minute)}" else "未開啟",16f))
        if(!store.enabled) body.addView(text("全域通知已暫停",12f).apply { setTextColor(muted) })
    }
    private fun deleteItem(item: Stuff) { confirm("確定刪除「${item.name}」？此操作無法復原。","刪除") {
        store.deleteItem(item.id); getSystemService(NotificationManager::class.java).cancel(item.id.toInt()); Reminders.refresh(this); toast("刪除成功"); home()
    } }
    private fun edit(item: Stuff?,draft: Bundle?=null) {
        opened=item?.id ?: 0
        val body=start("edit",if(item==null) "新增物品" else "編輯物品") { navigateBack() }
        section(body,"物品名稱"); val name=field("物品名稱",draft?.getString("name") ?: item?.name ?: "").apply { textSize=20f }; body.addView(name)
        var expiry=draft?.getString("expiry") ?: item?.expiry ?: Rules.today()
        section(body,"有效期限")
        val date=button(Rules.roc(expiry)) {}.apply { contentDescription="有效期限"; textSize=20f; background=shape(surface,8,border); setCompoundDrawablesRelativeWithIntrinsicBounds(glyph("calendar",ink,20),null,null,null); compoundDrawablePadding=dp(18); gravity=Gravity.CENTER_VERTICAL or Gravity.START; layoutParams=LinearLayout.LayoutParams(-1,dp(64)).apply { topMargin=dp(8); bottomMargin=dp(12) } }
        date.setOnClickListener { pickDate(expiry) { expiry=it; date.text=Rules.roc(it) } }; body.addView(date)
        section(body,"分類")
        val selected=(draft?.getLongArray("tags")?.toSet() ?: item?.tags ?: setOf(1L)).toMutableSet()
        val tagArea=column(); body.addView(tagArea)
        fun drawTags() { tagArea.removeAllViews(); tagArea.addView(chips(store.tags(),selected) { if(!selected.add(it)) selected.remove(it); drawTags() }) }; drawTags()
        section(body,"備註"); val note=field("備註",draft?.getString("note") ?: item?.note ?: "",true); body.addView(note)
        val enabled=Switch(this).apply { text="到期提醒"; contentDescription="開啟此物品提醒"; isChecked=draft?.getBoolean("reminder") ?: item?.reminder ?: false; isEnabled=store.enabled }; styleSwitch(enabled); body.addView(enabled)
        var days=draft?.getInt("days") ?: item?.days ?: store.setting("days").toInt()
        // Time is immutable in an item form. Existing items retain their snapshot;
        // a new item inherits the app default without exposing a time control.
        val minute=item?.minute ?: draft?.getInt("minute") ?: store.setting("minute").toInt()
        val dates=offsetPicker(days) { days=it }; body.addView(dates)
        fun enabledUi() { val active=store.enabled && enabled.isChecked; enableTree(dates,active); dates.alpha=if(active) 1f else .4f }
        enabled.setOnCheckedChangeListener { _,_->enabledUi() }; enabledUi()
        if(!store.enabled) body.addView(text("全域通知已暫停",12f).apply { setTextColor(muted) })
        pinAction(if(item==null) "新增" else "儲存",if(item!=null) iconButton("trash","刪除",rust) { deleteItem(item) } else null) { saveAction {
            require(name.text.isNotBlank()) { "請輸入物品名稱" }
            val next=Stuff(item?.id ?: 0,name.text.toString(),expiry,item?.created ?: Rules.today(),item?.status ?: 0,note.text.toString(),enabled.isChecked,days,minute,selected)
            val id=store.save(next); toast(if(item==null) "成功新增物品" else "修改成功")
            if(enabled.isChecked && store.enabled) requestNotifications(); detail(id)
        } }
        captureDraft={ Bundle().apply { putString("name",name.text.toString()); putString("expiry",expiry); putString("note",note.text.toString()); putBoolean("reminder",enabled.isChecked); putInt("days",days); putInt("minute",minute); putLongArray("tags",selected.toLongArray()) } }
    }
    private fun settingsMenu() {
        val body=start("settings","設定")
        fun entry(label: String,detail: String,icon: String,action: ()->Unit) {
            val line=row().apply { background=panel(paper,border); setPadding(dp(14),dp(14),dp(12),dp(14)); contentDescription=label; isClickable=true; setOnClickListener { action() } }
            line.addView(ImageView(this).apply { background=shape(AtelierPalette.mint,12); setImageDrawable(glyph(icon,green,24)); setPadding(dp(11),dp(11),dp(11),dp(11)) },LinearLayout.LayoutParams(dp(46),dp(46)))
            val words=column(); words.addView(text(label,16f,true)); words.addView(text(detail,11f).apply { setTextColor(muted) }); line.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(12) }); line.addView(ImageView(this).apply { setImageDrawable(glyph("arrow",green,18)) },LinearLayout.LayoutParams(dp(24),dp(36)))
            body.addView(line,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        entry("標籤管理","${store.tags().size} 個標籤","標籤") { tagsPage() }; entry("通知管理",if(store.enabled) "通知已開啟" else "通知已暫停","bell") { notifications() }
        entry("匯出 MCP 資料","全部物品與標籤 · JSON","export") { exportMcpData() }
    }
    private fun exportMcpData() {
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="application/json"; putExtra(Intent.EXTRA_TITLE,"items.json")
            },2001)
        } catch(_: ActivityNotFoundException) { toast("找不到檔案儲存工具") }
    }
    @Deprecated("Platform Activity result compatibility")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=2001 || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return toast("未取得儲存位置，請重試")
        val context=applicationContext
        toast("正在匯出 MCP 資料…")
        Thread({
            val success=runCatching {
                val json=Store(context).use { source ->
                    val db=source.readableDatabase
                    db.beginTransaction()
                    try { McpSnapshot.json(source.items(),source.tags()) } finally { db.endTransaction() }
                }
                val stream=context.contentResolver.openOutputStream(uri,"wt") ?: error("無法寫入檔案")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }.isSuccess
            runOnUiThread { if(!isDestroyed) toast(if(success) "MCP 資料已匯出" else "匯出失敗，請重試或選擇其他位置") }
        },"mcp-snapshot-export").start()
    }
    private fun time(minute: Int)="%02d:%02d".format(minute/60,minute%60)
    private fun notifications(draft: Bundle?=null) {
        val body=start("notifications","通知管理") { settingsMenu() }
        val enabled=Switch(this).apply { text="允許通知"; isChecked=draft?.getBoolean("enabled") ?: store.enabled }; styleSwitch(enabled); body.addView(enabled); rule(body)
        var days=draft?.getInt("days") ?: store.setting("days").toInt(); var minute=draft?.getInt("minute") ?: store.setting("minute").toInt()
        section(body,"預設通知時間")
        val clock=button(time(minute)) {}.apply { contentDescription="預設提醒時間"; textSize=34f; background=shape(AtelierPalette.mint,14); setPadding(0,dp(8),0,dp(8)); layoutParams=LinearLayout.LayoutParams(-1,dp(72)); gravity=Gravity.CENTER }
        clock.setOnClickListener { TimePickerDialog(this,{_,h,m -> minute=h*60+m; clock.text=time(minute)},minute/60,minute%60,true).show() }; body.addView(clock); rule(body)
        section(body,"預設通知日期")
        val dates=offsetPicker(days) { days=it }; body.addView(dates)
        body.addView(text("套用於新增物品",12f).apply { setTextColor(muted); setPadding(0,dp(16),0,0) })
        fun update() { enableTree(dates,enabled.isChecked); clock.isEnabled=enabled.isChecked; dates.alpha=if(enabled.isChecked) 1f else .4f; clock.alpha=dates.alpha }; update(); enabled.setOnCheckedChangeListener { _,_->update() }
        pinAction("儲存") { store.settings(enabled.isChecked,days,minute); Reminders.refresh(this); if(enabled.isChecked) requestNotifications(); toast("設定已儲存"); settingsMenu() }
        captureDraft={ Bundle().apply { putBoolean("enabled",enabled.isChecked); putInt("days",days); putInt("minute",minute) } }
    }
    private fun requestNotifications() { if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100) }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,results: IntArray) { super.onRequestPermissionsResult(requestCode,permissions,results); Reminders.refresh(this) }
    private fun tagsPage() {
        start("tags","標籤管理") { leaveTags() }
        root.removeView(pageScroll)
        headerBar.addView(iconButton("plus","新增標籤") { tagDraft=tagOrder?.orderedIds?.toLongArray(); tagEditor(null) })
        root.addView(text("長按拖曳 · 點選握把可逐格移動",12f).apply { setTextColor(muted); setPadding(dp(4),dp(14),0,dp(10)) })
        val saved=store.tags(); tagSavedIds=saved.map { it.id }
        val byId=saved.associateBy { it.id }
        val draft=(tagDraft?.toList() ?: tagSavedIds).filter { it in byId }.distinct()
        val ordered=(draft+tagSavedIds.filter { it !in draft }).map { byId.getValue(it) }
        val footer=row().apply { setPadding(0,dp(8),0,dp(4)) }
        val save=button("儲存排序") {
            tagOrder?.let { store.reorder(it.orderedIds) }; tagDraft=null; toast("排序已儲存"); tagsPage()
        }
        fun updateState() {
            val dirty=tagOrder?.orderedIds!=tagSavedIds
            save.isEnabled=dirty; save.alpha=if(dirty) 1f else .4f
        }
        val list=TagOrderView(this,ordered,{ tag -> tagDraft=tagOrder?.orderedIds?.toLongArray(); tagEditor(tag) }) {
            tagDraft=tagOrder?.orderedIds?.toLongArray(); updateState()
        }
        tagOrder=list; root.addView(list,LinearLayout.LayoutParams(-1,0,1f)); updateState()
        footer.addView(iconButton("restore","復原排序",muted) { tagDraft=null; tagsPage() })
        footer.addView(View(this),LinearLayout.LayoutParams(0,1,1f)); footer.addView(save,LinearLayout.LayoutParams(dp(132),dp(48))); root.addView(footer)
    }
    private fun leaveTags() {
        if(tagOrder?.orderedIds!=tagSavedIds) confirm("放棄尚未儲存的排序？") { tagDraft=null; settingsMenu() }
        else { tagDraft=null; settingsMenu() }
    }
    private fun tagEditor(tag: Tag?,draft: Bundle?=null) {
        opened=tag?.id ?: 0
        val body=start("tagEdit",if(tag==null) "新增標籤" else "編輯標籤") { tagsPage() }
        val name=field("標籤名稱",draft?.getString("name") ?: tag?.name ?: ""); body.addView(name)
        var icon=draft?.getString("icon") ?: tag?.icon ?: "標籤"; var color=draft?.getInt("color") ?: tag?.color ?: Color.parseColor(colors[0].second)
        val preview=row().apply { setPadding(0,dp(20),0,dp(24)) }
        val mark=ImageView(this); preview.addView(mark,LinearLayout.LayoutParams(dp(76),dp(76)).apply { rightMargin=dp(18) })
        val sample=text("",23f); preview.addView(sample,LinearLayout.LayoutParams(0,-2,1f)); body.addView(preview)
        fun updatePreview() { sample.text=name.text.toString().ifBlank { "標籤" }; mark.background=shape(wash(color,.55f),38); mark.setPadding(dp(21),dp(21),dp(21),dp(21)); mark.setImageDrawable(glyph(icon,ink,34)) }
        val tabs=row(); body.addView(tabs)
        val iconArea=column(); val swatches=column(); body.addView(iconArea); body.addView(swatches)
        val colorTab=button("顏色 · 25") {}; val iconTab=button("圖示 · 25") {}
        fun selectTab(showIcons: Boolean) {
            iconArea.visibility=if(showIcons) View.VISIBLE else View.GONE; swatches.visibility=if(showIcons) View.GONE else View.VISIBLE
            listOf(colorTab to !showIcons,iconTab to showIcons).forEach { (view,selected) ->
                view.background=android.graphics.drawable.LayerDrawable(arrayOf(shape(paper,0),shape(if(selected) green else border,0))).apply { setLayerHeight(1,dp(if(selected) 2 else 1)); setLayerGravity(1,Gravity.BOTTOM) }
                view.setTextColor(if(selected) ink else muted); view.isSelected=selected
            }
        }
        colorTab.setOnClickListener { selectTab(false) }; iconTab.setOnClickListener { selectTab(true) }
        tabs.addView(colorTab,LinearLayout.LayoutParams(0,dp(48),1f).apply { rightMargin=dp(12) }); tabs.addView(iconTab,LinearLayout.LayoutParams(0,dp(48),1f))
        fun cell(r: LinearLayout,view: View) { val frame=FrameLayout(this); frame.addView(view,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER)); r.addView(frame,LinearLayout.LayoutParams(0,dp(60),1f)) }
        fun drawIcons() {
            iconArea.removeAllViews(); spacer(iconArea,14)
            icons.chunked(5).forEach { group -> val r=row(); group.forEach { choice -> cell(r,iconButton(choice,"圖示 $choice",ink) { icon=choice; updatePreview(); drawIcons() }.apply { background=shape(if(icon==choice) wash(color,.5f) else paper,24,if(icon==choice) pigment(color) else border); isSelected=icon==choice }) }; iconArea.addView(r) }
        }; drawIcons()
        fun drawColors() {
            swatches.removeAllViews(); spacer(swatches,14)
            colors.chunked(5).forEach { group -> val r=row(); group.forEach { (label,hex) -> val c=Color.parseColor(hex)
                cell(r,iconButton("check","顏色 $label",ink) { color=c; updatePreview(); drawColors(); drawIcons() }.apply { setImageDrawable(if(color==c) glyph("check",if(TagStyles.lightText(c)) Color.WHITE else ink,20) else null); background=shape(c,24,border); isSelected=color==c })
            }; swatches.addView(r) }
        }; drawColors(); updatePreview(); selectTab(false)
        name.addTextChangedListener(object: TextWatcher { override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}; override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { updatePreview() }; override fun afterTextChanged(s: Editable?) {} })
        pinAction(if(tag==null) "新增" else "儲存",if(tag!=null) iconButton("trash","刪除",rust) { confirm("刪除「${tag.name}」？沒有其他標籤的物品會改為「其他」。","刪除") { store.deleteTag(tag.id); toast("刪除成功"); tagsPage() } } else null) {
            attempt { store.saveTag(tag?.id,name.text.toString(),icon,color); toast(if(tag==null) "成功新增標籤" else "修改成功"); tagsPage() }
        }
        captureDraft={ Bundle().apply { putString("name",name.text.toString()); putString("icon",icon); putInt("color",color) } }
    }
}
