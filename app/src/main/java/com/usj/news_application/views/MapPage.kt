package com.usj.news_application.views

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.usj.news_application.R
import com.usj.news_application.models.News
import com.usj.news_application.network.LebaneseNewsScraper
import com.usj.news_application.network.RetrofitInstance
import com.usj.news_application.repositories.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LEBANON_CENTER = LatLng(33.68, 35.58)
private const val LEBANON_ZOOM = 8.4f

class MapPage : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "Live.LB Strike & Security Map",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "LBCI • MTV Lebanon • Live Geolocation",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        NewsMap()
                    }
                }
            }
        }
    }
}

data class NewsLocation(
    val latLng: LatLng,
    val news: News
)

@Composable
fun NewsMap() {
    var newsLocations by remember { mutableStateOf<List<NewsLocation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedNews by remember { mutableStateOf<News?>(null) }
    val repository = remember { NewsRepository(RetrofitInstance.api) }
    val context = LocalContext.current

    val customMarker = remember {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.pin)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 76, 76, false)
        BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LEBANON_CENTER, LEBANON_ZOOM)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val fetched = repository.fetchNews()
                val combined = (fetched + LebaneseNewsScraper.getFallbackLbciNews() + LebaneseNewsScraper.getFallbackMtvNews())
                    .distinctBy { it.title }

                val usedCoordinatesCount = mutableMapOf<String, Int>()
                val mapped = combined.mapNotNull { newsItem ->
                    if (newsItem.mapLocation.isBlank()) return@mapNotNull null
                    val baseLatLng = convertLocationToLatLng(newsItem.mapLocation) ?: return@mapNotNull null

                    val count = usedCoordinatesCount.getOrDefault(newsItem.mapLocation, 0)
                    usedCoordinatesCount[newsItem.mapLocation] = count + 1
                    val latOffset = if (count == 0) 0.0 else (count * 0.007) * if (count % 2 == 0) 1 else -1
                    val lngOffset = if (count == 0) 0.0 else (count * 0.007)

                    NewsLocation(
                        latLng = LatLng(baseLatLng.latitude + latOffset, baseLatLng.longitude + lngOffset),
                        news = newsItem
                    )
                }

                newsLocations = mapped
            } catch (e: Exception) {
                errorMessage = "Failed to load news locations: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center),
                    color = Color(0xFFC62828)
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "",
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.Center)
                )
            }
            else -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false)
                ) {
                    newsLocations.forEach { location ->
                        Marker(
                            state = MarkerState(position = location.latLng),
                            title = "[${location.news.location}] ${location.news.title}",
                            snippet = "Tap for full report & AI translation",
                            icon = customMarker,
                            onClick = {
                                selectedNews = location.news
                                true
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xE61A1D26),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "🚨 ${newsLocations.size} Active Strike / Security Reports Mapped",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                selectedNews?.let { news ->
                    Dialog(onDismissRequest = { selectedNews = null }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC62828), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🚨 ${news.location}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = news.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = news.datetime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = news.detailedDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 6
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { selectedNews = null }) {
                                        Text("Close")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val item = news
                                            selectedNews = null
                                            val intent = Intent(context, NewsDetailPage::class.java).apply {
                                                putExtra("NEWS_ID", item.id)
                                                putExtra("NEWS_TITLE", item.title)
                                                putExtra("NEWS_CONTENT", item.content)
                                                putExtra("NEWS_LOCATION", item.location)
                                                putExtra("NEWS_MAP_LOCATION", item.mapLocation)
                                                putExtra("NEWS_DATETIME", item.datetime)
                                                putExtra("NEWS_DETAILED_DESCRIPTION", item.detailedDescription)
                                                putExtra("NEWS_IMAGE_URL", item.imageUrl)
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFC62828),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Full Article & AI Translate")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun convertLocationToLatLng(location: String): LatLng? {
    return try {
        val parts = location.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDouble()
        val lng = parts[1].trim().toDouble()
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
        LatLng(lat, lng)
    } catch (e: Exception) {
        null
    }
}
