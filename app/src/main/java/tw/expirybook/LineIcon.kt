package tw.expirybook

import android.graphics.*
import android.graphics.drawable.Drawable

/** A single stroke vocabulary for actions and user-selected tag icons. */
class LineIcon(private val name: String, color: Int, private val size: Int) : Drawable() {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=1.45f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun setAlpha(alpha: Int) { pen.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter=filter; invalidateSelf() }
    @Deprecated("Drawable compatibility") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        fun line(x: Float,y: Float,a: Float,b: Float)=canvas.drawLine(x,y,a,b,pen)
        fun path(vararg points: Float) { val p=Path(); p.moveTo(points[0],points[1]); for(i in 2 until points.size step 2) p.lineTo(points[i],points[i+1]); canvas.drawPath(p,pen) }
        fun rect(l: Float,t: Float,r: Float,b: Float,radius: Float=2f)=canvas.drawRoundRect(l,t,r,b,radius,radius,pen)
        fun circle(x: Float,y: Float,r: Float)=canvas.drawCircle(x,y,r,pen)
        when(name) {
            "crest" -> { path(12f,1f,22f,7f,22f,17f,12f,23f,2f,17f,2f,7f,12f,1f); path(8f,7f,16f,7f,16f,9f,8f,15f,8f,17f,16f,17f); line(12f,10f,12f,14f) }
            "leaf" -> { path(5f,19f,13f,11f); val p=Path(); p.moveTo(5f,17f); p.cubicTo(1f,7f,12f,3f,21f,3f); p.cubicTo(20f,13f,17f,22f,7f,19f); canvas.drawPath(p,pen) }
            "alert" -> { path(12f,3f,22f,21f,2f,21f,12f,3f); line(12f,9f,12f,14f); circle(12f,17.5f,.5f) }
            "filter" -> { line(4f,6f,20f,6f); line(7f,12f,17f,12f); line(10f,18f,14f,18f) }
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "search" -> { circle(10.5f,10.5f,6f); line(15f,15f,20f,20f) }
            "settings" -> { line(4f,7f,20f,7f); line(4f,17f,20f,17f); circle(9f,7f,2.5f); circle(16f,17f,2.5f) }
            "export" -> { path(8f,8f,12f,4f,16f,8f); line(12f,4f,12f,15f); path(5f,14f,5f,20f,19f,20f,19f,14f) }
            "calendar" -> { rect(4f,5f,20f,21f); line(4f,10f,20f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f); line(8f,14f,10f,14f); line(14f,14f,16f,14f) }
            "back" -> { path(13f,5f,6f,12f,13f,19f); line(6f,12f,21f,12f) }
            "check" -> path(5f,12f,10f,17f,19f,7f)
            "trash" -> { path(6f,8f,7f,21f,17f,21f,18f,8f); line(4f,5f,20f,5f); rect(9f,2f,15f,5f,1f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
            "edit" -> { path(4f,20f,5f,15f,17f,3f,21f,7f,9f,19f,4f,20f); line(14f,6f,18f,10f) }
            "restore" -> { canvas.drawArc(5f,5f,20f,20f,-140f,290f,false,pen); path(4f,4f,4f,10f,10f,10f) }
            "copy" -> { rect(8f,7f,20f,21f); path(16f,3f,4f,3f,4f,17f) }
            "clock" -> { circle(12f,12f,8.5f); path(12f,7f,12f,12f,16f,14f) }
            "bell" -> { path(4f,17f,6f,14f,6f,9f); canvas.drawArc(6f,3f,18f,15f,180f,180f,false,pen); path(18f,9f,18f,14f,20f,17f,4f,17f); canvas.drawArc(9f,17f,15f,22f,0f,180f,false,pen) }
            "drag" -> { line(7f,8f,17f,8f); line(7f,12f,17f,12f); line(7f,16f,17f,16f) }
            "arrow" -> path(9f,6f,15f,12f,9f,18f)
            "down" -> path(6f,9f,12f,15f,18f,9f)
            "more" -> { circle(5f,12f,.7f); circle(12f,12f,.7f); circle(19f,12f,.7f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "糖果" -> { rect(7f,7f,17f,17f,4f); path(7f,9f,3f,6f,3f,14f,7f,15f); path(17f,9f,21f,6f,21f,14f,17f,15f) }
            "飲料" -> { path(6f,8f,8f,21f,16f,21f,18f,8f,6f,8f); path(12f,13f,14f,3f,19f,3f) }
            "餅乾" -> { circle(12f,12f,8f); circle(9f,9f,.8f); circle(15f,11f,.8f); circle(11f,16f,.8f) }
            "冰箱" -> { rect(5f,3f,19f,21f); line(5f,10f,19f,10f); line(8f,6f,8f,8f); line(8f,13f,8f,17f) }
            "水果" -> { canvas.drawOval(5f,8f,19f,21f,pen); path(12f,9f,12f,5f,16f,3f); canvas.drawOval(7f,3f,12f,7f,pen) }
            "藥品" -> { rect(4f,7f,20f,17f,5f); line(12f,7f,12f,17f) }
            "保養" -> { rect(7f,7f,17f,21f); rect(9f,3f,15f,7f,1f); line(10f,12f,14f,12f) }
            "日用品" -> { path(4f,10f,12f,3f,20f,10f,20f,21f,4f,21f,4f,10f); rect(10f,14f,14f,21f,0f) }
            "蔬菜" -> { canvas.drawOval(7f,3f,18f,18f,pen); path(5f,21f,12f,11f,15f,7f); line(10f,14f,15f,14f) }
            "肉類" -> { canvas.drawOval(4f,5f,20f,19f,pen); canvas.drawOval(8f,8f,16f,16f,pen); line(5f,9f,8f,10f) }
            "魚類" -> { canvas.drawOval(3f,7f,17f,17f,pen); path(17f,12f,22f,7f,22f,17f,17f,12f); circle(7f,11f,.6f); path(11f,7f,13f,4f,15f,8f) }
            "雞蛋" -> { val p=Path(); p.moveTo(12f,3f); p.cubicTo(5f,3f,1f,21f,12f,21f); p.cubicTo(23f,21f,19f,3f,12f,3f); canvas.drawPath(p,pen) }
            "麵包" -> { val p=Path(); p.moveTo(6f,11f); p.cubicTo(-1f,7f,6f,1f,12f,4f); p.cubicTo(18f,1f,25f,7f,18f,11f); p.lineTo(18f,21f); p.lineTo(6f,21f); p.close(); canvas.drawPath(p,pen); line(9f,9f,9f,14f); line(14f,9f,14f,14f) }
            "乳製品" -> { path(7f,3f,15f,3f,19f,9f,19f,21f,5f,21f,5f,9f,7f,3f); path(7f,3f,11f,9f,19f,9f); line(11f,9f,11f,21f) }
            "咖啡" -> { path(4f,8f,4f,16f,6f,19f,14f,19f,16f,16f,16f,8f,4f,8f); canvas.drawArc(13f,8f,22f,15f,-90f,180f,false,pen); line(3f,22f,18f,22f); line(7f,3f,7f,5f); line(12f,2f,12f,5f) }
            "茶葉" -> { val p=Path(); p.moveTo(4f,20f); p.cubicTo(2f,8f,10f,3f,21f,3f); p.cubicTo(21f,14f,14f,22f,4f,20f); canvas.drawPath(p,pen); path(4f,20f,16f,8f); line(10f,14f,10f,8f) }
            "罐頭" -> { canvas.drawOval(5f,3f,19f,8f,pen); path(5f,6f,5f,19f); path(19f,6f,19f,19f); canvas.drawArc(5f,16f,19f,22f,0f,180f,false,pen); line(8f,12f,16f,12f); line(8f,15f,16f,15f) }
            "調味料" -> { rect(8f,2f,16f,6f,1f); path(8f,6f,8f,10f,5f,14f,5f,21f,19f,21f,19f,14f,16f,10f,16f,6f); line(9f,16f,15f,16f) }
            "冷凍" -> { line(12f,2f,12f,22f); line(3.3f,7f,20.7f,17f); line(3.3f,17f,20.7f,7f); path(9f,4f,12f,7f,15f,4f); path(9f,20f,12f,17f,15f,20f); path(3f,11f,7f,10f,7f,6f); path(17f,18f,17f,14f,21f,13f) }
            "清潔" -> { path(7f,3f,17f,3f,17f,7f,13f,7f,13f,10f,18f,15f,18f,21f,5f,21f,5f,15f,9f,10f,9f,7f,7f,7f,7f,3f); line(17f,4f,21f,4f); line(18f,8f,21f,10f) }
            "衣物" -> path(8f,3f,4f,5f,1f,10f,5f,12f,6f,9f,6f,21f,18f,21f,18f,9f,19f,12f,23f,10f,20f,5f,16f,3f,14f,6f,10f,6f,8f,3f)
            "寵物" -> { canvas.drawOval(6f,12f,18f,21f,pen); canvas.drawOval(3f,7f,7f,12f,pen); canvas.drawOval(8f,3f,11f,9f,pen); canvas.drawOval(14f,3f,17f,9f,pen); canvas.drawOval(18f,7f,22f,12f,pen) }
            "文具" -> { path(5f,18f,6f,13f,16f,3f,21f,8f,11f,18f,5f,18f); line(13f,6f,18f,11f); line(4f,22f,20f,22f) }
            "票券" -> { path(3f,6f,21f,6f,21f,10f,18f,12f,21f,14f,21f,18f,3f,18f,3f,14f,6f,12f,3f,10f,3f,6f); line(14f,8f,14f,10f); line(14f,14f,14f,16f) }
            else -> { path(3f,4f,13f,4f,22f,13f,13f,22f,3f,12f,3f,4f); circle(8f,9f,1f) }
        }
        canvas.restore()
    }
}
