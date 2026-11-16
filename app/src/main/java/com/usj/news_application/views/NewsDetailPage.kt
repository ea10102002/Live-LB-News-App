package com.usj.news_application.views

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import com.usj.news_application.models.News
import com.usj.news_application.network.GeminiTranslator
import com.usj.news_application.network.LebaneseNewsScraper
import kotlinx.coroutines.launch
import java.lang.Exception

class NewsDetailPage : AppCompatActivity() {

    private var isShowingTranslated = false
    private var cachedTranslation: GeminiTranslator.TranslationResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.news_details_page)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val newsId = intent.getStringExtra("NEWS_ID") ?: intent.getStringExtra("NEWS_TITLE") ?: "id"
        val origTitle = intent.getStringExtra("NEWS_TITLE") ?: "News Details"
        val origContent = intent.getStringExtra("NEWS_CONTENT") ?: ""
        val location = intent.getStringExtra("NEWS_LOCATION") ?: "News"
        val mapLocation = intent.getStringExtra("NEWS_MAP_LOCATION") ?: ""
        val datetime = intent.getStringExtra("NEWS_DATETIME") ?: ""
        var origDetailed = intent.getStringExtra("NEWS_DETAILED_DESCRIPTION") ?: origContent
        var imageUrl = intent.getStringExtra("NEWS_IMAGE_URL")

        supportActionBar?.title = location

        val tvTitle = findViewById<TextView>(R.id.news_title_heading)
        val tvContent = findViewById<TextView>(R.id.news_content)
        val tvDetailed = findViewById<TextView>(R.id.news_detailed_description)
        val tvLocation = findViewById<TextView>(R.id.news_location)
        val tvStrikeBadge = findViewById<TextView>(R.id.tvDetailStrikeBadge)
        val tvDatetime = findViewById<TextView>(R.id.news_datetime)
        val btnTranslateAi = findViewById<MaterialButton>(R.id.btnTranslateAi)
        val btnSaveDetail = findViewById<MaterialButton>(R.id.btnSaveDetail)
        val imageView = findViewById<ImageView>(R.id.news_image)

        fun renderText(titleText: String, summaryText: String, detailedText: String) {
            tvTitle.text = titleText
            tvDetailed.text = detailedText
            val cleanSummary = summaryText.removeSuffix("...").trim()
            if (cleanSummary.isBlank() || detailedText.startsWith(cleanSummary.take(40))) {
                tvContent.visibility = View.GONE
            } else {
                tvContent.visibility = View.VISIBLE
                tvContent.text = summaryText
            }
        }

        renderText(origTitle, origContent, origDetailed)
        tvLocation.text = location

        val badgeBg = tvLocation.background?.mutate() as? GradientDrawable
        val sourceUpper = location.uppercase()
        val badgeColorRes = when {
            sourceUpper.contains("LBCI") -> R.color.lbciBadgeBg
            sourceUpper.contains("MTV") -> R.color.mtvBadgeBg
            else -> R.color.intlBadgeBg
        }
        badgeBg?.setColor(ContextCompat.getColor(this, badgeColorRes))

        if (mapLocation.isNotBlank()) {
            tvStrikeBadge.visibility = View.VISIBLE
            tvStrikeBadge.setOnClickListener {
                startActivity(Intent(this, MapPage::class.java))
            }
        } else {
            tvStrikeBadge.visibility = View.GONE
        }

        val cleanDateTime = datetime.replace('T', ' ').substringBefore("Z")
        tvDatetime.text = cleanDateTime

        fun loadImage(urlToLoad: String?) {
            if (!urlToLoad.isNullOrEmpty()) {
                imageView.visibility = View.VISIBLE
                Picasso.get().load(urlToLoad)
                    .into(imageView, object : Callback {
                        override fun onSuccess() {
                            imageView.visibility = View.VISIBLE
                        }

                        override fun onError(e: Exception?) {
                            imageView.visibility = View.GONE
                        }
                    })
            } else {
                imageView.visibility = View.GONE
            }
        }
        loadImage(imageUrl)

        if (newsId.startsWith("https://www.lbcgroup.tv")) {
            lifecycleScope.launch {
                val (fullBody, fullImg) = LebaneseNewsScraper.fetchLbciFullArticleBody(newsId)
                if (!fullBody.isNullOrBlank()) {
                    origDetailed = fullBody
                    cachedTranslation = null
                    if (!isShowingTranslated) {
                        renderText(origTitle, origContent, origDetailed)
                    }
                }
                if (imageUrl.isNullOrEmpty() && !fullImg.isNullOrEmpty()) {
                    imageUrl = fullImg
                    loadImage(imageUrl)
                }
            }
        }

        val dbHelper = DatabaseHelper(this)
        val loggedInEmail = getSharedPreferences("UserSession", MODE_PRIVATE)
            .getString("loggedInEmail", null)

        fun updateBookmarkButtonUI() {
            val isSaved = if (!loggedInEmail.isNullOrEmpty()) {
                dbHelper.isArticleSaved(loggedInEmail, newsId)
            } else false
            btnSaveDetail.text = if (isSaved) "🔖 Saved" else "🔖 Save"
        }
        updateBookmarkButtonUI()

        btnSaveDetail.setOnClickListener {
            if (loggedInEmail.isNullOrEmpty()) {
                Toast.makeText(this, "Please log in to bookmark articles.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentNewsObj = News(
                id = newsId,
                title = origTitle,
                content = origContent,
                location = location,
                mapLocation = mapLocation,
                datetime = datetime,
                detailedDescription = origDetailed,
                imageUrl = imageUrl
            )
            if (dbHelper.isArticleSaved(loggedInEmail, newsId)) {
                dbHelper.removeSavedArticle(loggedInEmail, newsId)
                Toast.makeText(this, "Removed from Saved Articles", Toast.LENGTH_SHORT).show()
            } else {
                dbHelper.saveArticleForUser(loggedInEmail, currentNewsObj)
                Toast.makeText(this, "Article saved to your account!", Toast.LENGTH_SHORT).show()
            }
            updateBookmarkButtonUI()
        }

        btnTranslateAi.setOnClickListener {
            if (isShowingTranslated) {
                renderText(origTitle, origContent, origDetailed)
                btnTranslateAi.text = getString(R.string.translate_with_ai)
                isShowingTranslated = false
                return@setOnClickListener
            }

            val cached = cachedTranslation
            if (cached != null) {
                renderText(cached.translatedTitle, cached.translatedContent, cached.translatedDetailed)
                btnTranslateAi.text = getString(R.string.show_original)
                isShowingTranslated = true
                return@setOnClickListener
            }

            btnTranslateAi.isEnabled = false
            btnTranslateAi.text = getString(R.string.translating_ai)

            lifecycleScope.launch {
                val result = GeminiTranslator.translateArticle(origTitle, origContent, origDetailed)
                cachedTranslation = result
                renderText(result.translatedTitle, result.translatedContent, result.translatedDetailed)
                btnTranslateAi.text = getString(R.string.show_original)
                btnTranslateAi.isEnabled = true
                isShowingTranslated = true
                Toast.makeText(
                    this@NewsDetailPage,
                    "✨ Full article translated to ${result.targetLanguageLabel} via Gemini AI",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
