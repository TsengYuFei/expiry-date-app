package tw.expirybook

object TagStyles {
    val icons=listOf("標籤","糖果","飲料","餅乾","冰箱","水果","藥品","保養","日用品",
        "蔬菜","肉類","魚類","雞蛋","麵包","乳製品","咖啡","茶葉","罐頭","調味料","冷凍","清潔","衣物","寵物","文具","票券")
    val colors=listOf("白色" to "#FFFFFF", "石墨" to "#404040", "灰色" to "#A6A6A6", "深藍" to "#203864", "藍色" to "#4472C4",
        "紅色" to "#C00000", "橘色" to "#ED7D31", "黃色" to "#FFC000", "綠色" to "#548235", "紫色" to "#7030A0",
        "粉紅色" to "#FF66A3", "珊瑚" to "#FF6F61", "磚紅" to "#A63D40", "蜜桃" to "#F4B183", "杏色" to "#FFE699",
        "萊姆" to "#92D050", "橄欖" to "#808000", "松綠" to "#008050", "薄荷" to "#66CC99", "青色" to "#008C95",
        "湖水" to "#00B0F0", "靛藍" to "#3F51B5", "薰衣草" to "#B4A7D6", "玫瑰" to "#C0508B", "棕色" to "#996633")
    fun lightText(color: Int): Boolean {
        fun linear(channel: Int): Double { val v=channel/255.0; return if(v<=.04045) v/12.92 else Math.pow((v+.055)/1.055,2.4) }
        val luminance=.2126*linear((color shr 16) and 255)+.7152*linear((color shr 8) and 255)+.0722*linear(color and 255)
        return luminance<.179
    }
}
