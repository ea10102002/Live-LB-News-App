package com.usj.news_application.repositories

import com.usj.news_application.models.News
import com.usj.news_application.network.LebaneseNewsScraper
import com.usj.news_application.network.NewsApiService
import com.usj.news_application.utils.NewsDateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class NewsRepository(private val apiService: NewsApiService) {

    companion object {
        const val CATEGORY_ALL_LEBANON = "ALL_LEBANON"
        const val CATEGORY_LBCI = "LBCI"
        const val CATEGORY_MTV = "MTV"
        const val CATEGORY_INTERNATIONAL = "INTERNATIONAL"
    }

    suspend fun fetchNewsByCategory(category: String = CATEGORY_ALL_LEBANON): List<News> = withContext(Dispatchers.IO) {
        try {
            when (category) {
                CATEGORY_LBCI -> {
                    LebaneseNewsScraper.fetchLbciNews()
                }
                CATEGORY_MTV -> {
                    LebaneseNewsScraper.fetchMtvNews()
                }
                CATEGORY_INTERNATIONAL -> {
                    val response = apiService.getNews(query = "World OR Europe OR US OR Middle East")
                    val articles = response.articles ?: emptyList()
                    articles
                        .filter { !it.title.isNullOrBlank() && it.title != "[Removed]" }
                        .map { it.toNewsItem() }
                        .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }
                }
                else -> {
                    val lbciDeferred = async { LebaneseNewsScraper.fetchLbciNews() }
                    val mtvDeferred = async { LebaneseNewsScraper.fetchMtvNews() }
                    val apiDeferred = async {
                        try {
                            val response = apiService.getNews(query = "Lebanon OR Beirut")
                            (response.articles ?: emptyList())
                                .filter { article ->
                                    val title = article.title ?: ""
                                    val text = "$title ${article.description ?: ""}".lowercase()
                                    title != "[Removed]" &&
                                            (text.contains("lebanon") || text.contains("lebanese") || text.contains("beirut"))
                                }
                                .map { it.toNewsItem() }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val lbciList = lbciDeferred.await()
                    val mtvList = mtvDeferred.await()
                    val apiList = apiDeferred.await()

                    // Combine all Lebanon sources and strictly sort newest-first
                    (lbciList + mtvList + apiList)
                        .distinctBy { it.title }
                        .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchNews(): List<News> {
        return fetchNewsByCategory(CATEGORY_ALL_LEBANON)
    }
}
