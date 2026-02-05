package com.astro5star.app.ui.academy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

class AcademyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                AcademyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = ApiClient.api.getAcademyVideos()
                if (response.isSuccessful && response.body() != null) {
                    val root = JSONObject(response.body().toString())
                    val arr = root.getJSONArray("videos")
                    val list = mutableListOf<VideoItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(VideoItem(
                            title = obj.getString("title"),
                            url = obj.getString("youtubeUrl"),
                            category = obj.optString("category", "General")
                        ))
                    }
                    videos = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Astro Academy", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (videos.isEmpty()) {
                Text("No videos found.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(videos) { video ->
                        VideoCard(video)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                context.startActivity(intent)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(48.dp), tint = Color.Red)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(video.title, style = MaterialTheme.typography.titleMedium)
                Text(video.category, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

data class VideoItem(val title: String, val url: String, val category: String)
