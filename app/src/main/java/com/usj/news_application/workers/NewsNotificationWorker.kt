package com.usj.news_application.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.usj.news_application.models.News
import com.usj.news_application.network.RetrofitInstance
import com.usj.news_application.repositories.NewsRepository
import com.usj.news_application.utils.NewsPreferencesManager
import com.usj.news_application.views.NewsDetailPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NewsNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val ACTION_NEWS_UPDATED = "com.usj.news_application.NEWS_UPDATED"
        private const val KEY_FORCE_DEMO = "FORCE_DEMO_NOTIFICATION"

        fun schedulePeriodicWork(context: Context) {
            val newsWorkRequest = PeriodicWorkRequestBuilder<NewsNotificationWorker>(
                15,
                TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "CheckNewNews",
                ExistingPeriodicWorkPolicy.KEEP,
                newsWorkRequest
            )
        }

        fun triggerImmediateCheck(context: Context, forceDemoAlert: Boolean = false) {
            val data = workDataOf(KEY_FORCE_DEMO to forceDemoAlert)
            val oneTimeRequest = OneTimeWorkRequestBuilder<NewsNotificationWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateNewsCheck",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferencesManager = NewsPreferencesManager(applicationContext)
        val repository = NewsRepository(RetrofitInstance.api)
        val forceDemo = inputData.getBoolean(KEY_FORCE_DEMO, false)

        try {
            val latestNews = repository.fetchNews()
            val lastKnownNews = preferencesManager.getLastKnownNews()
            val newNewsItems = findNewNewsItems(lastKnownNews, latestNews)

            if (newNewsItems.isNotEmpty()) {
                val intent = Intent(ACTION_NEWS_UPDATED)
                applicationContext.sendBroadcast(intent)

                val itemsToNotify = newNewsItems.sortedByDescending { it.mapLocation.isNotEmpty() }.take(2)
                itemsToNotify.forEach { newsItem ->
                    sendNewsNotification(newsItem)
                }

                preferencesManager.saveLastKnownNews(latestNews)
            } else if (forceDemo && latestNews.isNotEmpty()) {
                val topAlert = latestNews.firstOrNull { it.mapLocation.isNotEmpty() } ?: latestNews.first()
                sendNewsNotification(topAlert)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun findNewNewsItems(oldNews: List<News>, newNews: List<News>): List<News> {
        return newNews.filter { newItem ->
            oldNews.none { it.id == newItem.id }
        }
    }

    private fun sendNewsNotification(news: News) {
        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "NEW_NEWS_CHANNEL",
                "Live.LB Breaking & Strike Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Live.LB breaking news and strike notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val detailIntent = Intent(applicationContext, NewsDetailPage::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NEWS_ID", news.id)
            putExtra("NEWS_TITLE", news.title)
            putExtra("NEWS_CONTENT", news.content)
            putExtra("NEWS_LOCATION", news.location)
            putExtra("NEWS_MAP_LOCATION", news.mapLocation)
            putExtra("NEWS_DATETIME", news.datetime)
            putExtra("NEWS_DETAILED_DESCRIPTION", news.detailedDescription)
            putExtra("NEWS_IMAGE_URL", news.imageUrl)
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            news.id.hashCode(),
            detailIntent,
            pendingFlags
        )

        val isStrike = news.mapLocation.isNotEmpty()
        val titlePrefix = if (isStrike) "🚨 Strike Alert (${news.location})" else "📰 Breaking (${news.location})"

        val notification = NotificationCompat.Builder(applicationContext, "NEW_NEWS_CHANNEL")
            .setContentTitle("$titlePrefix: ${news.title}")
            .setContentText(news.content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(news.content)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .build()

        val notificationId = news.id.hashCode()
        notificationManager.notify(notificationId, notification)
    }
}
