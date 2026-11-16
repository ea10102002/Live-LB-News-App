package com.usj.news_application.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.usj.news_application.DatabaseHelper
import com.usj.news_application.R
import com.usj.news_application.adapters.NewsAdapter
import com.usj.news_application.repositories.NewsRepository
import com.usj.news_application.viewmodels.NewsPageViewModel
import com.usj.news_application.workers.NewsNotificationWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NewsPage : Fragment() {
    private val viewModel: NewsPageViewModel by viewModels()
    private lateinit var newsUpdateReceiver: BroadcastReceiver
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: NewsAdapter
    private lateinit var recyclerView: RecyclerView
    private var shouldScrollToTop = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_news_page, container, false)
        dbHelper = DatabaseHelper(requireContext())

        val toolbar: MaterialToolbar = view.findViewById(R.id.topAppBar)

        val userInfoButton: ImageView = toolbar.findViewById(R.id.userInfoButton)
        userInfoButton.setOnClickListener {
            val loggedInEmail = getLoggedInEmail()
            if (loggedInEmail.isNullOrEmpty()) {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            } else {
                startActivity(Intent(requireContext(), AccountInfoActivity::class.java))
            }
        }

        val mapButton: ImageView = toolbar.findViewById(R.id.mapButton)
        mapButton.setOnClickListener {
            startActivity(Intent(requireContext(), MapPage::class.java))
        }

        val themeButton: ImageView = toolbar.findViewById(R.id.themeButton)
        themeButton.setOnClickListener {
            val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            if (isNightMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        val langButton: TextView = toolbar.findViewById(R.id.langButton)
        val currentLang = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"
        langButton.text = if (currentLang == "ar") "EN" else "عربي"
        langButton.setOnClickListener {
            val activeLang = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"
            if (activeLang == "en") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
        }

        tabLayout = view.findViewById(R.id.tabLayout)
        setupTabs()

        recyclerView = view.findViewById(R.id.news_recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        adapter = NewsAdapter(
            isBookmarked = { newsItem ->
                val email = getLoggedInEmail()
                if (!email.isNullOrEmpty()) dbHelper.isArticleSaved(email, newsItem.id) else false
            },
            onBookmarkToggle = { newsItem ->
                val email = getLoggedInEmail()
                if (!email.isNullOrEmpty()) {
                    if (dbHelper.isArticleSaved(email, newsItem.id)) {
                        dbHelper.removeSavedArticle(email, newsItem.id)
                        Toast.makeText(requireContext(), "Removed from Saved Articles", Toast.LENGTH_SHORT).show()
                        if (getSelectedCategoryTag() == "SAVED") {
                            fetchNewsForSelectedTab(isSilent = false)
                        }
                    } else {
                        dbHelper.saveArticleForUser(email, newsItem)
                        Toast.makeText(requireContext(), "Saved to your account bookmarks 🔖", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onItemClick = { selectedNews ->
                val intent = Intent(requireContext(), NewsDetailPage::class.java).apply {
                    putExtra("NEWS_ID", selectedNews.id)
                    putExtra("NEWS_TITLE", selectedNews.title)
                    putExtra("NEWS_CONTENT", selectedNews.content)
                    putExtra("NEWS_LOCATION", selectedNews.location)
                    putExtra("NEWS_MAP_LOCATION", selectedNews.mapLocation)
                    putExtra("NEWS_DATETIME", selectedNews.datetime)
                    putExtra("NEWS_DETAILED_DESCRIPTION", selectedNews.detailedDescription)
                    putExtra("NEWS_IMAGE_URL", selectedNews.imageUrl)
                }
                startActivity(intent)
            }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.layoutManager = layoutManager

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading && adapter.itemCount == 0) View.VISIBLE else View.GONE
            swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.newsList.observe(viewLifecycleOwner) { news ->
            val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
            val wasAtTop = firstVisiblePos <= 1
            adapter.submitList(news) {
                if (shouldScrollToTop || wasAtTop) {
                    recyclerView.scrollToPosition(0)
                    shouldScrollToTop = false
                }
            }
            tvEmptyState.visibility = if (news.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { errMsg ->
            if (!errMsg.isNullOrEmpty()) {
                tvEmptyState.text = errMsg
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            shouldScrollToTop = true
            fetchNewsForSelectedTab(isSilent = false)
        }

        newsUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NewsNotificationWorker.ACTION_NEWS_UPDATED) {
                    fetchNewsForSelectedTab(isSilent = true)
                }
            }
        }
        ContextCompat.registerReceiver(
            requireContext(),
            newsUpdateReceiver,
            IntentFilter(NewsNotificationWorker.ACTION_NEWS_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Live auto-refresh loop: fetches fresh news every 45 seconds while screen is active
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(45_000L)
                    if (getSelectedCategoryTag() != "SAVED") {
                        fetchNewsForSelectedTab(isSilent = true)
                    }
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
        fetchNewsForSelectedTab(isSilent = adapter.itemCount > 0)
    }

    private fun getLoggedInEmail(): String? {
        val prefs = requireContext().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        return prefs.getString("loggedInEmail", null)
    }

    private fun setupTabs() {
        val tabs = listOf(
            getString(R.string.tab_lebanon) to NewsRepository.CATEGORY_ALL_LEBANON,
            getString(R.string.tab_lbci) to NewsRepository.CATEGORY_LBCI,
            getString(R.string.tab_mtv) to NewsRepository.CATEGORY_MTV,
            getString(R.string.tab_international) to NewsRepository.CATEGORY_INTERNATIONAL,
            getString(R.string.tab_saved) to "SAVED"
        )
        tabs.forEach { (title, tag) ->
            tabLayout.addTab(tabLayout.newTab().setText(title).setTag(tag))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                shouldScrollToTop = true
                fetchNewsForSelectedTab(isSilent = false)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                shouldScrollToTop = true
                fetchNewsForSelectedTab(isSilent = false)
            }
        })
    }

    private fun getSelectedCategoryTag(): String {
        val tab = tabLayout.getTabAt(tabLayout.selectedTabPosition)
        return (tab?.tag as? String) ?: NewsRepository.CATEGORY_ALL_LEBANON
    }

    private fun fetchNewsForSelectedTab(isSilent: Boolean = false) {
        val categoryTag = getSelectedCategoryTag()
        viewModel.loadNews(categoryTag, getLoggedInEmail(), isSilentRefresh = isSilent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(newsUpdateReceiver)
    }
}
