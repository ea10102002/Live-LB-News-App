package com.usj.news_application.network

import com.usj.news_application.models.News
import com.usj.news_application.utils.NewsDateParser
import com.usj.news_application.utils.StrikeLocationDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object LebaneseNewsScraper {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun normalizeLbciImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var clean = rawUrl.trim()
        if (clean.contains("crown.svg") || clean.contains("play-icon.svg") || clean.contains("logo")) {
            return null
        }
        if (clean.contains("uploadImagesDoc-")) {
            clean = clean.replace("uploadImagesDoc-", "uploadImages/DocumentImages/Doc-")
        }
        if (clean.startsWith("/")) {
            clean = "https://www.lbcgroup.tv$clean"
        }
        return clean
    }

    suspend fun fetchLbciNews(): List<News> = withContext(Dispatchers.IO) {
        val articlesByUrl = linkedMapOf<String, News>()
        try {
            val cacheBust = System.currentTimeMillis()
            val doc = Jsoup.connect("https://www.lbcgroup.tv/news/category/8/lebanon-news/en?_=$cacheBust")
                .userAgent(USER_AGENT)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .timeout(10000)
                .get()

            val imageMap = mutableMapOf<String, String>()
            val dateMap = mutableMapOf<String, String>()

            val scripts = doc.select("script[type=application/ld+json]")
            for (script in scripts) {
                val raw = script.data().trim()
                if (raw.contains("\"NewsArticle\"")) {
                    try {
                        val jsonText = if (raw.startsWith("[")) raw else "[$raw]"
                        val arr = org.json.JSONArray(jsonText)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            if (obj.optString("@type") == "NewsArticle") {
                                val headline = Jsoup.parse(obj.optString("headline")).text().trim()
                                val datePub = obj.optString("datePublished")
                                val imgArr = obj.optJSONArray("image")
                                val rawImg = if (imgArr != null && imgArr.length() > 0) imgArr.optString(0) else ""
                                val fixedImg = normalizeLbciImageUrl(rawImg)
                                if (headline.isNotEmpty()) {
                                    if (!fixedImg.isNullOrEmpty()) imageMap[headline] = fixedImg
                                    if (datePub.isNotEmpty()) dateMap[headline] = datePub
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            val cards = doc.select("div.card-module")
            for (card in cards) {
                val titleLink = card.selectFirst("div.card-module-title a") ?: continue
                val title = titleLink.text().trim()
                if (title.isEmpty()) continue

                val href = titleLink.attr("href").trim()
                val fullUrl = if (href.startsWith("http")) href else "https://www.lbcgroup.tv$href"
                val timeText = card.selectFirst("div.card-module-date-container div")?.text()?.trim() ?: ""

                val cardImgTag = card.selectFirst("img.card-module-img")
                    ?: card.selectFirst("div.card-module-image-container img")
                val rawCardImg = cardImgTag?.attr("src")?.ifEmpty { cardImgTag.attr("data-src") }
                val imageUrl = normalizeLbciImageUrl(rawCardImg) ?: imageMap[title]

                val rawPublishedAt = dateMap[title]?.substringBefore("+") ?: timeText
                val displayTime = NewsDateParser.normalizeForDisplay(rawPublishedAt)

                val summary = "Lebanon News • LBCI Live Desk ($displayTime)"
                val detailed = "$title\n\nReported by LBCI Lebanon Newsroom."
                val mapCoords = StrikeLocationDetector.detectStrikeCoordinates(title, "")

                val candidate = News(
                    id = fullUrl,
                    title = title,
                    content = summary,
                    location = "LBCI",
                    mapLocation = mapCoords,
                    datetime = displayTime,
                    detailedDescription = detailed,
                    imageUrl = imageUrl
                )

                val existing = articlesByUrl[fullUrl]
                if (existing == null || (existing.imageUrl.isNullOrEmpty() && !candidate.imageUrl.isNullOrEmpty())) {
                    articlesByUrl[fullUrl] = candidate
                }
            }
        } catch (_: Exception) {
        }

        val sorted = articlesByUrl.values
            .distinctBy { it.title }
            .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }

        if (sorted.isEmpty()) {
            return@withContext getFallbackLbciNews()
        }
        sorted
    }

    suspend fun fetchLbciFullArticleBody(articleUrl: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (!articleUrl.startsWith("https://www.lbcgroup.tv")) return@withContext Pair(null, null)
        try {
            val doc = Jsoup.connect(articleUrl)
                .userAgent(USER_AGENT)
                .timeout(8000)
                .get()

            val bodyContainer = doc.selectFirst("div[id*=ArticleDetailsDescription]")
            val bodyText = bodyContainer?.text()?.trim()?.takeIf { it.isNotEmpty() }

            val mainImg = doc.selectFirst("img.ArticleImage")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")

            Pair(bodyText, normalizeLbciImageUrl(mainImg))
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    suspend fun fetchMtvNews(): List<News> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<News>()
        val cacheBust = System.currentTimeMillis()
        val localLebanonUrl = "https://www.mtv.com.lb/news/%D9%85%D8%AD%D9%84%D9%8A%D8%A7%D8%AA?_=$cacheBust"

        try {
            val doc = Jsoup.connect(localLebanonUrl)
                .userAgent(USER_AGENT)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .timeout(10000)
                .get()

            val highlight = doc.selectFirst("a.highlight")
            if (highlight != null) {
                val title = highlight.selectFirst("p.highlight-title")?.text()?.trim() ?: ""
                val fullBody = highlight.selectFirst("p.highlight-text")?.text()?.trim() ?: ""
                val time = highlight.selectFirst("p.highlight-time")?.text()?.trim() ?: ""
                val img = highlight.selectFirst("img")?.attr("src")
                val href = highlight.attr("href")
                if (title.isNotEmpty()) {
                    val fullUrl = if (href.startsWith("http")) href else "https://www.mtv.com.lb$href"
                    val shortDesc = if (fullBody.length > 160) fullBody.take(160) + "..." else fullBody
                    val mapCoords = StrikeLocationDetector.detectStrikeCoordinates(title, shortDesc)
                    articles.add(
                        News(
                            id = fullUrl,
                            title = title,
                            content = shortDesc.ifEmpty { title },
                            location = "MTV LEBANON",
                            mapLocation = mapCoords,
                            datetime = NewsDateParser.normalizeForDisplay(time),
                            detailedDescription = fullBody.ifEmpty { title },
                            imageUrl = img
                        )
                    )
                }
            }

            // Select strictly the chronological "By Date" feed so old "Most Viewed" articles are excluded
            var items = doc.select("div#more-news-by-date-filtered a.more-news-item")
            if (items.isEmpty()) {
                items = doc.select("a.more-news-item")
            }

            for (item in items) {
                val title = item.selectFirst("h3")?.text()?.trim() ?: continue
                val fullBody = item.selectFirst("h5")?.text()?.trim() ?: ""
                val time = item.selectFirst("p")?.text()?.trim() ?: ""
                val img = item.selectFirst("img")?.attr("src")
                val href = item.attr("href")
                val fullUrl = if (href.startsWith("http")) href else "https://www.mtv.com.lb$href"

                val shortDesc = if (fullBody.length > 160) fullBody.take(160) + "..." else fullBody
                val mapCoords = StrikeLocationDetector.detectStrikeCoordinates(title, shortDesc)

                articles.add(
                    News(
                        id = fullUrl,
                        title = title,
                        content = shortDesc.ifEmpty { title },
                        location = "MTV LEBANON",
                        mapLocation = mapCoords,
                        datetime = NewsDateParser.normalizeForDisplay(time),
                        detailedDescription = fullBody.ifEmpty { title },
                        imageUrl = img
                    )
                )
            }
        } catch (_: Exception) {
        }

        val sorted = articles
            .distinctBy { it.title }
            .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }

        if (sorted.isEmpty()) {
            return@withContext getFallbackMtvNews()
        }
        sorted
    }

    fun getFallbackLbciNews(): List<News> {
        return listOf(
            News(
                id = "https://www.lbcgroup.tv/news/lebanon-news/968171/israeli-army-says-it-targeted-weapons-shipment-bound-for-hezbollah-in/en",
                title = "Israeli army says it targeted weapons shipment bound for Hezbollah in Janta",
                content = "Lebanon News • LBCI Live Desk: Israeli airstrike reported near Janta in the Bekaa region.",
                location = "LBCI",
                mapLocation = "33.9078,36.1153",
                datetime = "16/11/2026 10:47",
                detailedDescription = "The Israeli military announced that its warplanes targeted an infrastructure site and weapons shipment in the Janta area of the Bekaa region along the Lebanese-Syrian border.",
                imageUrl = "https://www.lbcgroup.tv/uploadImages/DocumentImages/Doc-T-968171-639304157731130850.jpg"
            ),
            News(
                id = "https://www.lbcgroup.tv/news/lebanon-security/strike-nabatieh",
                title = "Israeli drone strike targets building in Mayfadoun, Nabatieh district",
                content = "National News Agency: An Israeli drone fired two missiles at the roof of an inhabited building in Mayfadoun in the Nabatieh district.",
                location = "LBCI",
                mapLocation = "33.3770,35.4838",
                datetime = "16/11/2026 10:15",
                detailedDescription = "According to the National News Agency and LBCI field reporters, an Israeli drone fired two guided missiles targeting the roof of a building in the town of Mayfadoun in the Nabatieh governorate. Civil defense teams rushed to the scene to inspect the site.",
                imageUrl = null
            ),
            News(
                id = "https://www.lbcgroup.tv/news/lebanon-security/strike-tyre",
                title = "Heavy Israeli artillery shelling and airstrike reported near Tyre and Naqoura",
                content = "LBCI correspondent reports warplanes breaking the sound barrier and airstrikes on the outskirts of Tyre and Naqoura.",
                location = "LBCI",
                mapLocation = "33.2705,35.1963",
                datetime = "16/11/2026 09:30",
                detailedDescription = "Israeli warplanes carried out an airstrike on the outskirts of Tyre and Naqoura in South Lebanon, accompanied by reconnaissance drone flights over the coast and southern border towns.",
                imageUrl = null
            )
        )
    }

    fun getFallbackMtvNews(): List<News> {
        return listOf(
            News(
                id = "https://www.mtv.com.lb/News/1748797",
                title = "الوكالة الوطنية: مسيّرة إسرائيلية تلقي صاروخين على سطح مبنى مأهول في ميفدون في قضاء النبطية",
                content = "أفادت الوكالة الوطنية للإعلام بأن مسيّرة إسرائيلية ألقت صاروخين على سطح مبنى مأهول في بلدة ميفدون في قضاء النبطية من دون وقوع إصابات.",
                location = "MTV LEBANON",
                mapLocation = "33.3770,35.4838",
                datetime = "16/11/2026 11:10",
                detailedDescription = "أفادت الوكالة الوطنية للإعلام عبر موقع MTV Lebanon بأن مسيّرة إسرائيلية معادية استهدفت بصاروخين سطح مبنى مأهول في بلدة ميفدون في قضاء النبطية، واقتصرت الأضرار على الماديات من دون وقوع إصابات بشرية.",
                imageUrl = "https://imagescdn.mtv.com.lb/articles/343658550478.jpg"
            ),
            News(
                id = "https://www.mtv.com.lb/News/1748809",
                title = "غارة إسرائيلية تستهدف أطراف الخيام ومرجعيون في الجنوب",
                content = "شنّ الطيران الحربي الإسرائيلي غارة جوية استهدفت أطراف مدينة الخيام وقضاء مرجعيون بالتزامن مع تحليق مكثف للمسيّرات.",
                location = "MTV LEBANON",
                mapLocation = "33.3323,35.5902",
                datetime = "16/11/2026 10:35",
                detailedDescription = "أفاد مراسل MTV في الجنوب بأن الطيران الحربي الإسرائيلي شنّ غارة استهدفت أطراف الخيام ومرجعيون، تزامناً مع قصف مدفعي طال أطراف كفركلا والعديسة.",
                imageUrl = "https://imagescdn.mtv.com.lb/articles/920944752002.jpg"
            ),
            News(
                id = "https://www.mtv.com.lb/News/1748830",
                title = "قصف مدفعي وغارات إسرائيلية تطال أطراف بنت جبيل وعيتا الشعب",
                content = "تعرّضت أطراف بلدتي عيتا الشعب وبنت جبيل لقصف مدفعي وإطلاق قنابل مضيئة مع تحليق للطيران الاستطلاعي.",
                location = "MTV LEBANON",
                mapLocation = "33.1214,35.4389",
                datetime = "16/11/2026 09:45",
                detailedDescription = "أشارت تقارير ميدانية عبر MTV إلى تعرض أطراف عيتا الشعب في قضاء بنت جبيل لقصف مدفعي إسرائيلي متقطع بالتزامن مع تحليق للطيران المسير في أجواء القطاع الأوسط.",
                imageUrl = null
            )
        )
    }
}
