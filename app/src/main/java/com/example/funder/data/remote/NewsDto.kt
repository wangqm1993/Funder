package com.example.funder.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 支持图片的新浪财经新闻项。
 */
data class NewsDto(
    @SerializedName("docid") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("wapurl") val wapUrl: String = "",
    @SerializedName("media_name") val source: String = "",
    @SerializedName("ctime") val ctime: String = "",
    @SerializedName("intro") val intro: String = "",
    @SerializedName("images") val images: List<NewsImage> = emptyList(),
    var isHot: Boolean = false // 标记为热门新闻（由客户端逻辑设置）
) {
    val imageUrl: String? get() = images.firstOrNull()?.u?.takeIf { it.isNotEmpty() }
    val linkUrl: String get() = wapUrl.ifEmpty { url }
    
    // 根据图片数量确定的布局模式
    val layoutMode: NewsLayoutMode get() = when {
        images.size >= 3 -> NewsLayoutMode.THREE_IMAGE
        images.size == 1 -> NewsLayoutMode.RIGHT_IMAGE
        else -> NewsLayoutMode.NO_IMAGE
    }
    
    val displayTime: String get() {
        // ctime 是 Unix 时间戳字符串，例如 "1771050213"
        return try {
            val timestamp = ctime.toLongOrNull() ?: return ctime
            val date = java.util.Date(timestamp * 1000)
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
            sdf.format(date)
        } catch (e: Exception) {
            ctime
        }
    }
}

enum class NewsLayoutMode {
    NO_IMAGE,      // 仅文本
    RIGHT_IMAGE,   // 标题 + 右侧小图
    THREE_IMAGE    // 标题 + 下方 3 张图片
}

data class NewsImage(
    @SerializedName("u") val u: String = "",
    @SerializedName("w") val w: Int = 0,
    @SerializedName("h") val h: Int = 0
)

/**
 * 新浪新闻 API 响应包装器。
 */
data class SinaNewsResponse(
    @SerializedName("result") val result: SinaNewsResult? = null
)

data class SinaNewsResult(
    @SerializedName("status") val status: SinaStatus? = null,
    @SerializedName("data") val data: List<NewsDto> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class SinaStatus(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String = ""
)
