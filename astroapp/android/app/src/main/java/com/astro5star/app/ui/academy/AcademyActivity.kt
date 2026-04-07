package com.astro5star.app.ui.academy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    var showComingSoon by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = ApiClient.api.getAcademyVideos()
                if (response.isSuccessful && response.body() != null) {
                    val root = JSONObject(response.body().toString())
                    val arr = root.optJSONArray("videos")
                    if (arr != null && arr.length() > 0) {
                        val list = mutableListOf<VideoItem>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(VideoItem(
                                title = obj.optString("title", "Video"),
                                url = obj.optString("youtubeUrl", ""),
                                category = obj.optString("category", "General")
                            ))
                        }
                        videos = list
                    } else {
                        showComingSoon = true
                    }
                } else {
                    showComingSoon = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showComingSoon = true
            } finally {
                isLoading = false
            }
        }
    }

    // Coming Soon Dialog
    if (showComingSoon) {
        AlertDialog(
            onDismissRequest = {
                showComingSoon = false
                onBack()
            },
            icon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF6200EE),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Astro Academy",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF6200EE)
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🚀 விரைவில் வருகிறது!",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ஜோதிட பாடங்கள் மற்றும் வீடியோக்கள் விரைவில் கிடைக்கும்.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showComingSoon = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedVideo != null) "📺 ${selectedVideo?.title}" else "🎓 Astro Academy", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedVideo != null) selectedVideo = null else onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedVideo != null) {
                VideoPlayer(selectedVideo!!.url)
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (videos.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos) { video ->
                        VideoCard(video) { selectedVideo = it }
                    }
                }
            } else {
                Text(
                    "வீடியோக்கள் எதுவும் இல்லை\nNo videos available",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun VideoPlayer(url: String) {
    val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
    val context = LocalContext.current

    if (isYouTube) {
        val videoId = remember(url) {
            val uri = Uri.parse(url)
            when {
                url.contains("youtube.com/embed/") -> url.split("embed/")[1].split("?")[0]
                url.contains("youtube.com/shorts/") -> url.split("shorts/")[1].split("?")[0]
                uri.host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
                uri.host?.contains("youtu.be") == true -> uri.pathSegments.firstOrNull()
                else -> {
                    val regex = "([a-zA-Z0-9_-]{11})".toRegex()
                    regex.find(url)?.value
                }
            }
        } ?: ""

        // Fix for Error 153: Must provide a proper origin/referer
        val htmlData = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>body { margin: 0; padding: 0; background-color: #000; display: flex; align-items: center; justify-content: center; height: 100vh; }</style>
            </head>
            <body>
                <iframe
                    width="100%"
                    height="100%"
                    src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&showinfo=0&enablejsapi=1&origin=https://astro5star.com"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen
                    referrerpolicy="strict-origin-when-cross-origin">
                </iframe>
            </body>
            </html>
        """.trimIndent()

        android.util.Log.d("VideoPlayer", "Loading Video ID: $videoId with Referer Fix")

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        // Set a clean User-Agent to avoid mobile-detection issues
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            android.util.Log.d("VideoPlayer", "Finished loading internal page")
                        }
                    }
                    webChromeClient = WebChromeClient()

                    // Crucial: Use the site's base URL to provide a valid 'Referer'
                    loadDataWithBaseURL("https://astro5star.com", htmlData, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val fullUrl = if (url.startsWith("http")) url else {
            val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL.removeSuffix("/")
            val cleanPath = if (url.startsWith("/")) url else "/$url"
            "$baseUrl$cleanPath"
        }

        android.util.Log.d("VideoPlayer", "Playing direct URL: $fullUrl")

        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val uri = Uri.parse(fullUrl)
                    setVideoURI(uri)
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)

                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }

                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("VideoPlayer", "Error playing direct video: $what, $extra")
                        Toast.makeText(ctx, "Unable to play video ($what)", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: (VideoItem) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(video) }
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color.Red.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Red,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = video.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

data class VideoItem(val title: String, val url: String, val category: String)
