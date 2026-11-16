package com.usj.news_application.models

import com.usj.news_application.utils.NewsDateParser
import com.usj.news_application.utils.StrikeLocationDetector

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>?
)

data class Article(
    val source: Source?,
    val author: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val content: String?
) {
    fun toNewsItem(): News {
        val defaultId = url ?: java.util.UUID.randomUUID().toString()
        val defaultTitle = title ?: "No Title"
        val defaultDesc = description ?: ""
        val defaultDetailed = content ?: defaultDesc.ifEmpty { "No detailed description available." }

        val generatedMapLocation = StrikeLocationDetector.detectStrikeCoordinates(
            defaultTitle,
            defaultDesc,
            defaultDetailed
        )

        return News(
            id = defaultId,
            title = defaultTitle,
            content = defaultDesc,
            location = source?.name ?: "International News",
            mapLocation = generatedMapLocation,
            datetime = NewsDateParser.normalizeForDisplay(publishedAt),
            detailedDescription = defaultDetailed,
            imageUrl = urlToImage
        )
    }
}

data class Source(
    val id: String?,
    val name: String?
)
