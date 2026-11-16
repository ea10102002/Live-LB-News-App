package com.usj.news_application.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.models.News
import com.usj.news_application.network.RetrofitInstance
import com.usj.news_application.repositories.NewsRepository
import com.usj.news_application.utils.NewsDateParser
import com.usj.news_application.utils.NewsPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewsPageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NewsRepository(RetrofitInstance.api)
    private val prefsManager = NewsPreferencesManager(application.applicationContext)
    private val dbHelper = DatabaseHelper(application.applicationContext)

    private val _newsList = MutableLiveData<List<News>>()
    val newsList: LiveData<List<News>> get() = _newsList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private var currentCategory: String = NewsRepository.CATEGORY_ALL_LEBANON
    private var activeLoadJob: Job? = null

    init {
        loadNews(NewsRepository.CATEGORY_ALL_LEBANON)
    }

    fun loadNews(
        category: String = currentCategory,
        userEmail: String? = null,
        isSilentRefresh: Boolean = false
    ) {
        currentCategory = category
        activeLoadJob?.cancel()

        if (!isSilentRefresh) {
            _isLoading.value = true
        }
        _error.value = null

        activeLoadJob = viewModelScope.launch {
            try {
                if (category == "SAVED") {
                    val savedList = withContext(Dispatchers.IO) {
                        if (!userEmail.isNullOrEmpty()) {
                            dbHelper.getSavedArticlesForUser(userEmail)
                        } else {
                            emptyList()
                        }
                    }
                    _newsList.value = savedList
                    if (savedList.isEmpty()) {
                        _error.value = "No saved articles yet. Tap the bookmark icon on any article to save it."
                    }
                } else {
                    val fetchedNews = repository.fetchNewsByCategory(category)

                    if (fetchedNews.isNotEmpty()) {
                        val sorted = fetchedNews
                            .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }
                            .toList()
                        _newsList.value = sorted
                        if (category == NewsRepository.CATEGORY_ALL_LEBANON) {
                            withContext(Dispatchers.IO) {
                                prefsManager.saveLastKnownNews(sorted)
                            }
                        }
                    } else if (!isSilentRefresh) {
                        val cached = withContext(Dispatchers.IO) {
                            prefsManager.getLastKnownNews()
                                .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }
                        }
                        if (cached.isNotEmpty()) {
                            _newsList.value = cached
                            _error.value = "Showing offline cached news"
                        } else {
                            _newsList.value = emptyList()
                            _error.value = "No news items found"
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isSilentRefresh) {
                    val cached = withContext(Dispatchers.IO) {
                        prefsManager.getLastKnownNews()
                            .sortedByDescending { NewsDateParser.parseTimestampMillis(it.datetime, it.id) }
                    }
                    if (cached.isNotEmpty()) {
                        _newsList.value = cached
                    } else {
                        _error.value = "Failed to load news: ${e.message}"
                    }
                }
            } finally {
                if (!isSilentRefresh) {
                    _isLoading.value = false
                }
            }
        }
    }
}
