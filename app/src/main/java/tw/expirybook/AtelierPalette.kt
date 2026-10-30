package tw.expirybook

import android.graphics.Color

/** White stationery surfaces, a green action color, and unmodified category colors. */
object AtelierPalette {
    val paper=Color.rgb(255,255,255)
    val surface=Color.rgb(239,247,241)
    val ink=Color.rgb(36,77,57)
    val muted=Color.rgb(96,123,108)
    val sage=Color.rgb(47,117,84)
    val mint=Color.rgb(220,238,225)
    val rose=Color.rgb(182,140,135)
    val line=Color.rgb(211,230,217)
    fun mix(color: Int,base: Int,amount: Float): Int=Color.rgb(
        (Color.red(base)+(Color.red(color)-Color.red(base))*amount).toInt(),
        (Color.green(base)+(Color.green(color)-Color.green(base))*amount).toInt(),
        (Color.blue(base)+(Color.blue(color)-Color.blue(base))*amount).toInt())
    fun pigment(color: Int)=color
    fun tint(color: Int,amount: Float=.17f)=mix(pigment(color),paper,amount)
}
