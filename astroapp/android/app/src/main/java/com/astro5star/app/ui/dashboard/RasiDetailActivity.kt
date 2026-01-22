package com.astro5star.app.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RasiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rasiName = intent.getStringExtra("rasiName") ?: "Unknown"
        val rasiId = intent.getIntExtra("rasiId", 1)
        val rasiIcon = intent.getIntExtra("rasiIcon", R.drawable.ic_rasi_aries_premium)
        val rasiColorInt = intent.getIntExtra("rasiColor", android.graphics.Color.YELLOW)
        val rasiColor = Color(rasiColorInt)

        setContent {
            RasiDetailScreen(
                rasiName = rasiName,
                rasiId = rasiId,
                rasiIcon = rasiIcon,
                rasiColor = rasiColor,
                onBack = { finish() }
            )
        }
    }
}

// Colors from Design System
val DeepBlueBg = Color(0xFF1E2749)
val CardDark = Color(0xFF2C3E50)
val GoldText = Color(0xFFD4AF37)
val SoftWhite = Color(0xFFE0E0E0)
val LuckyGreen = Color(0xFF059669) // Matching web color

@Composable
fun RasiDetailScreen(rasiName: String, rasiId: Int, rasiIcon: Int, rasiColor: Color, onBack: () -> Unit) {
    var prediction by remember { mutableStateOf("Loading horoscope for today...") }
    var luckyNumber by remember { mutableStateOf<String?>(null) }
    var luckyColorText by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch Logic
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                // Use backend API which handles 5:05 AM logic and daily updates
                val request = Request.Builder()
                    .url("${Constants.SERVER_URL}/api/horoscope/rasi-palan")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "[]"
                    var dataArray: org.json.JSONArray? = null

                    try {
                         dataArray = org.json.JSONArray(jsonStr)
                    } catch (e: Exception) {
                        try {
                             // Try object
                             val obj = org.json.JSONObject(jsonStr)
                             if (obj.has("data")) dataArray = obj.getJSONArray("data")
                        } catch (e2: Exception) {
                            // ignore
                        }
                    }

                    if (dataArray != null && dataArray.length() > 0) {
                         // Find by rasi name filtering
                         // Map standard names to what might be in JSON (usually case-insensitive check)
                         var foundData: JSONObject? = null

                         for (i in 0 until dataArray.length()) {
                             val obj = dataArray.getJSONObject(i)
                             // Check common keys for name
                             val nameInJson = if (obj.has("sign_name")) obj.getString("sign_name")
                                              else if (obj.has("sign")) obj.getString("sign")
                                              else if (obj.has("rasi")) obj.getString("rasi")
                                              else ""

                             // Check ID as fallback
                             val idInJson = if (obj.has("sign_id")) obj.optInt("sign_id", -1) else -1

                             if (nameInJson.equals(rasiName, ignoreCase = true) || idInJson == rasiId) {
                                 foundData = obj
                                 break
                             }
                         }

                         // Fallback to index if name search failed
                         if (foundData == null) {
                             val index = rasiId - 1
                             if (index >= 0 && index < dataArray.length()) {
                                 foundData = dataArray.getJSONObject(index)
                             }
                         }

                         if (foundData != null) {
                             var text = ""
                             // Try getting Tamil prediction specific keys first
                             if (foundData.has("prediction_ta")) text = foundData.getString("prediction_ta")
                             else if (foundData.has("forecast_ta")) text = foundData.getString("forecast_ta")
                             else if (foundData.has("prediction")) text = foundData.getString("prediction")
                             else if (foundData.has("content")) text = foundData.getString("content")
                             else if (foundData.has("description")) text = foundData.getString("description")
                             else text = foundData.toString()

                             // Clean up Markdown
                             text = text.replace("###", "").trim()

                             // Extract Lucky Details
                             val lNum = if (foundData.has("lucky_number")) foundData.getString("lucky_number") else null
                             val lCol = if (foundData.has("lucky_color_ta")) foundData.getString("lucky_color_ta")
                                        else if (foundData.has("lucky_color")) foundData.getString("lucky_color") else null

                             withContext(Dispatchers.Main) {
                                 prediction = text
                                 luckyNumber = lNum
                                 luckyColorText = lCol
                                 isLoading = false
                             }
                         } else {
                             withContext(Dispatchers.Main) {
                                  errorMsg = "Rasi data not found for $rasiName"
                                  isLoading = false
                             }
                         }
                    } else {
                         withContext(Dispatchers.Main) {
                              errorMsg = "Empty data received"
                              isLoading = false
                         }
                    }
                } else {
                     withContext(Dispatchers.Main) {
                         errorMsg = "Failed to fetch data from API: ${response.code}"
                         isLoading = false
                     }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = "Error: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(
                colors = listOf(Color(0xFF2C3E50), Color(0xFF000000))
            ))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                 // Background Pattern - Using simple Box with alpha as placeholder
                 Box(modifier = Modifier.fillMaxSize().background(Color.Black).alpha(0.2f))

                 // Back Button
                 IconButton(
                     onClick = onBack,
                     modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                 ) {
                     Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                 }

                 // Rasi Icon & Name
                 Column(
                     modifier = Modifier.align(Alignment.Center),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Image(
                         painter = painterResource(id = rasiIcon),
                         contentDescription = null,
                         modifier = Modifier.size(100.dp),
                         colorFilter = ColorFilter.tint(rasiColor)
                     )
                     Spacer(modifier = Modifier.height(16.dp))
                     Text(
                         text = rasiName,
                         fontSize = 28.sp,
                         fontWeight = FontWeight.Bold,
                         color = SoftWhite
                     )
                 }
            }

            // Content Card
            Card(
                 shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                 colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2749)),
                 modifier = Modifier.fillMaxWidth().offset(y = (-20).dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Today's Prediction",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldText
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = GoldText, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (errorMsg != null) {
                         Text(
                            text = errorMsg!!,
                            fontSize = 16.sp,
                            color = Color.Red
                        )
                    } else {
                        Text(
                            text = prediction,
                            fontSize = 16.sp,
                            color = SoftWhite,
                            lineHeight = 24.sp
                        )

                        // Lucky Details Section
                        if (luckyNumber != null || luckyColorText != null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Divider(color = Color.Gray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (luckyNumber != null) {
                                    Text(
                                        text = "Lucky Number: ",
                                        fontSize = 14.sp,
                                        color = LuckyGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = luckyNumber!!,
                                        fontSize = 14.sp,
                                        color = SoftWhite
                                    )
                                }

                                if (luckyNumber != null && luckyColorText != null) {
                                     Text(
                                        text = "  |  ",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }

                                if (luckyColorText != null) {
                                    Text(
                                        text = "Lucky Color: ",
                                        fontSize = 14.sp,
                                        color = LuckyGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = luckyColorText!!,
                                        fontSize = 14.sp,
                                        color = SoftWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
