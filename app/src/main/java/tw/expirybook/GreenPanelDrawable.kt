package tw.expirybook

import android.graphics.*
import android.graphics.drawable.Drawable

/** Compact green panels with a subtle layered arc motif. */
class GreenPanelDrawable(private val density: Float,private val fill: Int,private val edge: Int?=null,private val divider: Boolean=false) : Drawable() {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setAlpha(alpha: Int) { pen.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter=filter; invalidateSelf() }
    @Deprecated("Drawable compatibility") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(density,density)
        val w=bounds.width()/density; val h=bounds.height()/density
        pen.style=Paint.Style.FILL; pen.color=fill
        if(!divider) {
            canvas.drawRoundRect(0f,0f,w,h,16f,16f,pen)
            val clip=Path().apply { addRoundRect(0f,0f,w,h,16f,16f,Path.Direction.CW) }; canvas.clipPath(clip)
            if(edge!=null) { pen.style=Paint.Style.STROKE; pen.strokeWidth=.7f; pen.color=edge; canvas.drawRoundRect(.5f,.5f,w-.5f,h-.5f,16f,16f,pen) }
            else { pen.style=Paint.Style.STROKE; pen.strokeWidth=12f; pen.color=0x148ED5AB; canvas.drawCircle(w+8f,h+8f,64f,pen); canvas.drawCircle(w+8f,h+8f,88f,pen) }
        } else { pen.style=Paint.Style.STROKE; pen.strokeWidth=.6f; pen.color=Color.rgb(220,234,224); canvas.drawLine(0f,h/2,w,h/2,pen) }
        canvas.restore()
    }
}
