package tw.expirybook

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.recyclerview.widget.*
import kotlin.math.*

/** Stable rows, continuous insertion animation and native edge scrolling while dragging. */
class TagOrderView @JvmOverloads constructor(context: Context, tags: List<Tag> = emptyList(), private val edit: (Tag)->Unit = {},
    private val changed: ()->Unit = {}) : RecyclerView(context) {
    private val order=tags.toMutableList()
    val orderedIds: List<Long> get()=order.map { it.id }
    private val entryOrder=tags.toList()
    private val accent=AtelierPalette.sage
    private val labelColor=AtelierPalette.ink
    private val subdued=AtelierPalette.muted
    private var draggingId: Long?=null
    private fun dp(n: Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun tile(active: Boolean=false)=GradientDrawable().apply { setColor(if(active) AtelierPalette.tint(accent,.3f) else AtelierPalette.paper); cornerRadius=dp(18).toFloat(); if(active) setStroke(dp(1),accent) }
    private val rows=object: Adapter<Holder>() {
        init { setHasStableIds(true) }
        override fun getItemCount()=order.size
        override fun getItemId(position: Int)=order[position].id
        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int)=Holder()
        override fun onBindViewHolder(holder: Holder,position: Int) { holder.bind(order[position],position) }
        override fun onBindViewHolder(holder: Holder,position: Int,payloads: MutableList<Any>) {
            if(payloads.isEmpty()) holder.bind(order[position],position) else holder.bindPosition(position)
        }
    }
    private val touch=ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,0) {
        override fun isItemViewSwipeEnabled()=false
        override fun getMoveThreshold(viewHolder: ViewHolder)=.18f
        override fun getBoundingBoxMargin()=dp(16)
        override fun onMove(recycler: RecyclerView,source: ViewHolder,target: ViewHolder): Boolean {
            val from=source.bindingAdapterPosition; val to=target.bindingAdapterPosition
            if(from==NO_POSITION || to==NO_POSITION || from==to) return false
            move(from,to); source.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); return true
        }
        override fun onSwiped(viewHolder: ViewHolder,direction: Int) {}
        override fun onSelectedChanged(holder: ViewHolder?,actionState: Int) {
            super.onSelectedChanged(holder,actionState)
            if(actionState==ItemTouchHelper.ACTION_STATE_DRAG && holder!=null) {
                draggingId=holder.itemId; holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                holder.itemView.background=tile(true)
                holder.itemView.animate().scaleX(1.025f).scaleY(1.025f).translationZ(dp(12).toFloat()).setDuration(140).start()
            }
        }
        override fun clearView(recycler: RecyclerView,holder: ViewHolder) {
            super.clearView(recycler,holder); draggingId=null
            holder.itemView.background=tile()
            holder.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(170).start()
        }
        override fun interpolateOutOfBoundsScroll(recycler: RecyclerView,viewSize: Int,out: Int,total: Int,elapsed: Long): Int {
            val ramp=(.25f+elapsed/1200f).coerceAtMost(1f)
            val distance=(abs(out).toFloat()/dp(80)).coerceAtMost(1f)
            return (sign(out.toFloat())*dp(2+(distance*6).roundToInt())*ramp).roundToInt().let { if(it==0) if(out<0) -1 else 1 else it }
        }
    })
    init {
        contentDescription="標籤排序清單"
        layoutManager=LinearLayoutManager(context)
        adapter=rows
        itemAnimator=DefaultItemAnimator().apply { moveDuration=190; addDuration=140; removeDuration=140; supportsChangeAnimations=false }
        setPadding(dp(4),dp(8),dp(4),dp(8)); clipToPadding=false; isVerticalScrollBarEnabled=false
        addItemDecoration(object: ItemDecoration() {
            private val pen=android.graphics.Paint().apply { color=AtelierPalette.line; strokeWidth=dp(1).toFloat() }
            override fun onDraw(canvas: android.graphics.Canvas,parent: RecyclerView,state: State) {
                for(i in 0 until parent.childCount) { val child=parent.getChildAt(i); val y=child.bottom+child.translationY+dp(4); canvas.drawLine(dp(12).toFloat(),y,width-dp(12).toFloat(),y,pen) }
            }
        })
        touch.attachToRecyclerView(this)
    }
    fun resetOrder() { if(orderedIds==entryOrder.map { it.id }) return; order.clear(); order.addAll(entryOrder); rows.notifyDataSetChanged(); changed(); announceForAccessibility("已復原排序") }
    private fun move(from: Int,to: Int) {
        if(from !in order.indices || to !in order.indices || from==to) return
        val item=order.removeAt(from); order.add(to,item); rows.notifyItemMoved(from,to)
        rows.notifyItemRangeChanged(min(from,to),abs(from-to)+1,"position"); changed()
    }
    private fun moveTo(holder: Holder,destination: Int) {
        val from=holder.bindingAdapterPosition
        if(from==NO_POSITION) return
        val to=destination.coerceIn(0,order.lastIndex); if(from==to) return
        val name=order[from].name; move(from,to); smoothScrollToPosition(to)
        announceForAccessibility("$name，移至第 ${to+1} 位")
    }
    private fun options(holder: Holder,anchor: View) {
        val p=holder.bindingAdapterPosition; if(p==NO_POSITION) return
        PopupMenu(context,anchor).apply {
            menu.add(0,1,0,"上移").isEnabled=p>0
            menu.add(0,2,1,"下移").isEnabled=p<order.lastIndex
            menu.add(0,3,2,"移到最上").isEnabled=p>0
            menu.add(0,4,3,"移到最下").isEnabled=p<order.lastIndex
            setOnMenuItemClickListener { item ->
                val current=holder.bindingAdapterPosition
                if(current!=NO_POSITION) moveTo(holder,when(item.itemId) { 1->current-1; 2->current+1; 3->0; else->order.lastIndex }); true
            }; show()
        }
    }
    private fun icon(name: String,label: String,color: Int=labelColor)=ImageButton(context).apply {
        setImageDrawable(LineIcon(name,color,dp(21))); contentDescription=label
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x188FA394),null,null)
        setPadding(dp(13),dp(13),dp(13),dp(13)); layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
        if(android.os.Build.VERSION.SDK_INT>=26) tooltipText=label
    }
    inner class Holder : ViewHolder(LinearLayout(context).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(12),dp(4),dp(12)); background=tile()
        layoutParams=LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { bottomMargin=dp(10) }
    }) {
        val ordinal=TextView(context).apply { textSize=11f; typeface=android.graphics.Typeface.MONOSPACE; setTextColor(subdued); gravity=Gravity.CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val symbol=ImageView(context)
        private val title=TextView(context).apply { textSize=16f; setTextColor(labelColor); setPadding(dp(10),0,dp(4),0) }
        private val editButton=icon("edit","編輯")
        private val handle=icon("drag","拖曳排序",subdued)
        init {
            val r=itemView as LinearLayout
            r.addView(ordinal,LinearLayout.LayoutParams(dp(23),-2)); r.addView(symbol,LinearLayout.LayoutParams(dp(32),dp(40)))
            r.addView(title,LinearLayout.LayoutParams(0,-2,1f)); r.addView(editButton); r.addView(handle)
            editButton.setOnClickListener { val p=bindingAdapterPosition; if(p!=NO_POSITION) edit(order[p]) }
            handle.setOnClickListener { options(this,handle) }
            handle.setOnLongClickListener { if(bindingAdapterPosition!=NO_POSITION) touch.startDrag(this); true }
            itemView.accessibilityDelegate=object: View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View,info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host,info)
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.move_tag_up,"上移"))
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.move_tag_down,"下移"))
                }
                override fun performAccessibilityAction(host: View,action: Int,args: android.os.Bundle?): Boolean {
                    val p=bindingAdapterPosition
                    if(p!=NO_POSITION && action==R.id.move_tag_up) { moveTo(this@Holder,p-1); return true }
                    if(p!=NO_POSITION && action==R.id.move_tag_down) { moveTo(this@Holder,p+1); return true }
                    return super.performAccessibilityAction(host,action,args)
                }
            }
        }
        fun bind(tag: Tag,position: Int) {
            ordinal.text=String.format(java.util.Locale.TAIWAN,"%02d",position+1); title.text=tag.name
            symbol.setImageDrawable(LineIcon(tag.icon,labelColor,dp(22))); symbol.background=GradientDrawable().apply { setColor(AtelierPalette.tint(tag.color,.45f)); cornerRadius=dp(20).toFloat() }; symbol.setPadding(dp(5),dp(7),dp(5),dp(7))
            editButton.visibility=if(tag.id==1L) View.INVISIBLE else View.VISIBLE
            editButton.isEnabled=tag.id!=1L; editButton.isClickable=tag.id!=1L
            handle.contentDescription="排序選項 ${tag.name}"; if(android.os.Build.VERSION.SDK_INT>=26) handle.tooltipText="點選移動／長按拖曳"
            itemView.contentDescription="${tag.name}，第 ${position+1} 位"; itemView.background=tile(tag.id==draggingId)
        }
        fun bindPosition(position: Int) {
            ordinal.text=String.format(java.util.Locale.TAIWAN,"%02d",position+1)
            itemView.contentDescription="${order[position].name}，第 ${position+1} 位"
        }
    }
}
