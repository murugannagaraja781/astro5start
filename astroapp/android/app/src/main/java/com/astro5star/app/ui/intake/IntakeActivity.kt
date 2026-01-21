package com.astro5star.app.ui.intake

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.astro5star.app.R
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.ui.call.CallActivity

import com.astro5star.app.ui.auth.GoldAccent
import com.astro5star.app.ui.auth.TextWhite
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class IntakeActivity : ComponentActivity() {

    private var partnerId: String? = null
    private var type: String? = null
    private var partnerName: String? = null
    private var partnerImage: String? = null
    private var isEditMode = false
    private var existingData: JSONObject? = null

    // City Search State Holders (MutableState handled in Compose, but receiving result needs Activity reference)
    private var lastCitySearchResolve: ((name: String, lat: Double, lon: Double, tz: Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        partnerId = intent.getStringExtra("partnerId")
        type = intent.getStringExtra("type")
        partnerName = intent.getStringExtra("partnerName")
        partnerImage = intent.getStringExtra("partnerImage")
        isEditMode = intent.getBooleanExtra("isEditMode", false)

        val dataStr = intent.getStringExtra("existingData")
        if (dataStr != null) {
            try { existingData = JSONObject(dataStr) } catch(_: Exception){}
        } else {
            // Auto-fill: Load from SharedPreferences
            val prefs = getSharedPreferences("IntakePrefs", MODE_PRIVATE)
            val lastData = prefs.getString("last_intake_data", null)
            if (lastData != null) {
                try { existingData = JSONObject(lastData) } catch(_: Exception){}
            }
        }

        setContent {
             // User Requested Blue Gradient Background
             val blueGradient = Brush.verticalGradient(
                 colors = listOf(
                     Color(0xFF4348f8), // User Blue 1
                     Color(0xFF5f60f3)  // User Blue 2
                 )
             )

             Box(modifier = Modifier.fillMaxSize().background(blueGradient)) {
                 IntakeScreen(
                     partnerName = partnerName ?: "Astrologer",
                     isEditMode = isEditMode,
                     existingData = existingData,
                     onCitySearch = { callback ->
                         lastCitySearchResolve = callback
                         val intent = Intent(this@IntakeActivity, com.astro5star.app.ui.city.CitySearchActivity::class.java)
                         startActivityForResult(intent, 1001)
                     },
                     onBack = { finish() },
                     onSubmit = { birthData ->
                         submitConsultation(birthData)
                     }
                 )

                 // Waiting Overlay
                 if (isWaiting.value) {
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .background(Color.Black.copy(alpha = 0.7f))
                             .clickable(enabled = true) {}, // Block clicks
                         contentAlignment = Alignment.Center
                     ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(color = GoldAccent)
                             Spacer(modifier = Modifier.height(16.dp))
                             Text("Waiting for Astrologer...", color = TextWhite, fontWeight = FontWeight.Bold)
                             Spacer(modifier = Modifier.height(8.dp))
                             Text("${timeRemaining.value}s", color = GoldAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                         }
                     }
                 }
             }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val name = data.getStringExtra("name") ?: ""
            val lat = data.getDoubleExtra("lat", 0.0)
            val lon = data.getDoubleExtra("lon", 0.0)
            // Fetch Timezone or standard 5.5
            // ideally we fetch timezone here async, strict refactor implies we keep logic simple for now
            val tz = 5.5 // Default IST for MVP or fetch via API if needed same as old logic

            lastCitySearchResolve?.invoke(name, lat, lon, tz)
        }
    }

    // State for Waiting Dialog
    private val isWaiting = mutableStateOf(false)
    private val timeRemaining = mutableStateOf(30)
    private var waitingTimer: android.os.CountDownTimer? = null

    override fun onDestroy() {
        super.onDestroy()
        waitingTimer?.cancel()
    }

    private fun startWaitingTimer() {
        waitingTimer?.cancel()
        timeRemaining.value = 30
        waitingTimer = object : android.os.CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining.value = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                isWaiting.value = false
                Toast.makeText(this@IntakeActivity, "Astrologer is busy, please try again later.", Toast.LENGTH_LONG).show()
                SocketManager.endSession(null) // Cancel request on server if needed
            }
        }.start()
    }

    private fun submitConsultation(birthData: JSONObject) {
        if (isEditMode) {
             val resultIntent = Intent()
             resultIntent.putExtra("birthData", birthData.toString())
             setResult(RESULT_OK, resultIntent)
             finish()
        } else {
             val prefs = getSharedPreferences("IntakePrefs", MODE_PRIVATE)
             prefs.edit().putString("last_intake_data", birthData.toString()).apply()

             if (partnerId == null || type == null) return

             // Show Loading Immediately (Connection phase)
             isWaiting.value = true
             timeRemaining.value = 30

             // FIX: Initialize socket and REGISTER user before requesting session
             // This ensures server has socket mapping to send back session-answered
             SocketManager.init()

             val tokenManager = com.astro5star.app.data.local.TokenManager(this)
             val userId = tokenManager.getUserSession()?.userId

             if (userId == null) {
                 Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                 isWaiting.value = false
                 return
             }

             // FIX: Register user on socket BEFORE requesting session
             SocketManager.registerUser(userId) { registered ->
                 runOnUiThread {
                     if (registered) {
                         android.util.Log.d("IntakeActivity", "User registered: $userId, now requesting session")
                         // Now request session after registration complete
                         SocketManager.requestSession(partnerId!!, type!!, birthData) { response ->
                             runOnUiThread {
                                 if (response?.optBoolean("ok") == true) {
                                     val sessionId = response.optString("sessionId")
                                     startWaitingTimer()
                                     waitForAnswer(sessionId)
                                 } else {
                                     isWaiting.value = false
                                     val error = response?.optString("error") ?: "Connection Failed"
                                     Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                                 }
                             }
                         }
                     } else {
                         isWaiting.value = false
                         Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                     }
                 }
             }
        }
    }

    private fun waitForAnswer(sessionId: String) {
        // Remove previous listener to avoid stacking
        SocketManager.off("session-answered")

        SocketManager.onSessionAnswered { data ->
             runOnUiThread {
                 val accepted = data.optBoolean("accept", false)
                 waitingTimer?.cancel()
                 isWaiting.value = false

                 if (accepted) {
                     val intent: Intent
                     if (type == "chat") {
                         intent = Intent(this, ChatActivity::class.java).apply {
                             putExtra("sessionId", sessionId)
                             putExtra("toUserId", partnerId)
                             putExtra("toUserName", partnerName)
                         }
                     } else {
                         intent = Intent(this, CallActivity::class.java).apply {
                             putExtra("sessionId", sessionId)
                             putExtra("partnerId", partnerId)
                             putExtra("partnerName", partnerName) // FIX: Added missing partnerName
                             putExtra("isInitiator", true)
                             putExtra("type", type)
                             putExtra("callType", type) // FIX: Also pass as callType for compatibility
                             putExtra("partnerImage", partnerImage)
                         }
                     }
                     startActivity(intent)
                     finish()
                 } else {
                     Toast.makeText(this, "Request Rejected", Toast.LENGTH_LONG).show()
                 }
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    partnerName: String,
    isEditMode: Boolean,
    existingData: JSONObject?,
    onCitySearch: ((String, Double, Double, Double) -> Unit) -> Unit,
    onBack: () -> Unit,
    onSubmit: (JSONObject) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Form State
    var name by remember { mutableStateOf(existingData?.optString("name") ?: "") }
    var gender by remember { mutableStateOf(existingData?.optString("gender", "Male") ?: "Male") }

    // Date
    val calendar = Calendar.getInstance()
    var day by remember { mutableStateOf(existingData?.optInt("day") ?: calendar.get(Calendar.DAY_OF_MONTH)) }
    var month by remember { mutableStateOf(existingData?.optInt("month") ?: (calendar.get(Calendar.MONTH) + 1)) }
    var year by remember { mutableStateOf(existingData?.optInt("year") ?: calendar.get(Calendar.YEAR)) }
    var hour by remember { mutableStateOf(existingData?.optInt("hour") ?: calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(existingData?.optInt("minute") ?: calendar.get(Calendar.MINUTE)) }

    var place by remember { mutableStateOf(existingData?.optString("city") ?: "") }
    var lat by remember { mutableDoubleStateOf(existingData?.optDouble("latitude") ?: 0.0) }
    var lon by remember { mutableDoubleStateOf(existingData?.optDouble("longitude") ?: 0.0) }
    var tz by remember { mutableDoubleStateOf(existingData?.optDouble("timezone", 5.5) ?: 5.5) }

    val maritalOptions = listOf("Single", "Married", "Divorced", "Widowed")
    var maritalStatus by remember { mutableStateOf(existingData?.optString("maritalStatus") ?: "Single") }

    val topicOptions = listOf("Career / Job", "Marriage / Relationship", "Health", "Finance", "Legal", "General")
    var topic by remember { mutableStateOf(existingData?.optString("topic") ?: "Career / Job") }

    var occupation by remember { mutableStateOf(existingData?.optString("occupation") ?: "") }

    // Dropdown States
    var maritalExpanded by remember { mutableStateOf(false) }
    var topicExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
             CenterAlignedTopAppBar(
                 title = { Text("Consultation Form", color = TextWhite, fontWeight = FontWeight.Bold) },
                 navigationIcon = {
                     IconButton(onClick = onBack) {
                         Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
                     }
                 },
                 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
             )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Partner Header Info
            Text(
                text = "Consulting with $partnerName",
                color = TextWhite.copy(alpha=0.8f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Transparent Card Form
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)), // User Requested Transparency
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha=0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Text("Birth Details", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = transparentTextFieldColors()
                    )

                    // Gender
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gender: ", color = TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        listOf("Male", "Female").forEach { g ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = (gender == g),
                                    onClick = { gender = g },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldAccent, unselectedColor = TextWhite)
                                )
                                Text(g, color = TextWhite)
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                    }

                    // Date Selection
                    OutlinedTextField(
                         value = "$day-$month-$year",
                         onValueChange = {},
                         readOnly = true,
                         label = { Text("Date of Birth") },
                         trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = GoldAccent) },
                         modifier = Modifier.fillMaxWidth().clickable {
                             DatePickerDialog(context, { _, y, m, d ->
                                 year = y; month = m + 1; day = d
                             }, year, month-1, day).show()
                         },
                         enabled = false, // Disable typing, handle click onBox
                         colors = transparentTextFieldColorsBottomLine() // Custom visual
                    )
                     // Because enabled=false captures clicks poorly on TF, we wrap/overlay or use a Clickable Box:
                     Box(modifier = Modifier.fillMaxWidth().height(56.dp).clickable {
                           DatePickerDialog(context, { _, y, m, d ->
                                 year = y; month = m + 1; day = d
                             }, year, month-1, day).show()
                     }) { /* Overlay transparent click handler over the TF if needed, or structured better */ }


                    // Time Selection
                    // Combining TF readOnly with click
                    Box {
                         OutlinedTextField(
                             value = String.format("%02d:%02d", hour, minute),
                             onValueChange = {},
                             readOnly = true,
                             label = { Text("Time of Birth") },
                             trailingIcon = { Icon(Icons.Default.Schedule, null, tint = GoldAccent) },
                             modifier = Modifier.fillMaxWidth(),
                             colors = transparentTextFieldColors()
                         )
                         Box(modifier = Modifier.matchParentSize().clickable {
                             TimePickerDialog(context, { _, h, m ->
                                 hour = h; minute = m
                             }, hour, minute, false).show()
                         })
                    }

                    // Place Selection
                    Box {
                        OutlinedTextField(
                             value = place,
                             onValueChange = {},
                             readOnly = true,
                             label = { Text("Place of Birth") },
                             trailingIcon = { Icon(Icons.Default.LocationOn, null, tint = GoldAccent) },
                             modifier = Modifier.fillMaxWidth(),
                             colors = transparentTextFieldColors()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable {
                             onCitySearch { n, la, lo, t ->
                                 place = n
                                 lat = la
                                 lon = lo
                                 tz = t
                             }
                        })
                    }

                    // Marital Status Dropdown
                    Box {
                        OutlinedTextField(
                            value = maritalStatus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Marital Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = maritalExpanded) },
                             modifier = Modifier.fillMaxWidth(),
                             colors = transparentTextFieldColors()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { maritalExpanded = true })
                        DropdownMenu(
                            expanded = maritalExpanded,
                            onDismissRequest = { maritalExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            maritalOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = TextWhite) },
                                    onClick = { maritalStatus = option; maritalExpanded = false }
                                )
                            }
                        }
                    }

                    // Occupation
                    OutlinedTextField(
                        value = occupation,
                        onValueChange = { occupation = it },
                        label = { Text("Occupation (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = transparentTextFieldColors()
                    )

                    // Topic
                    Box {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Topic of Concern") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicExpanded) },
                             modifier = Modifier.fillMaxWidth(),
                             colors = transparentTextFieldColors()
                        )
                         Box(modifier = Modifier.matchParentSize().clickable { topicExpanded = true })
                        DropdownMenu(
                            expanded = topicExpanded,
                            onDismissRequest = { topicExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            topicOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = TextWhite) },
                                    onClick = { topic = option; topicExpanded = false }
                                )
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connect Button
            Button(
                onClick = {
                    if (name.isBlank() || place.isBlank()) {
                        Toast.makeText(context, "Please fill Name and Place", Toast.LENGTH_SHORT).show()
                    } else {
                        val json = JSONObject().apply {
                            put("name", name)
                            put("gender", gender)
                            put("day", day)
                            put("month", month)
                            put("year", year)
                            put("hour", hour)
                            put("minute", minute)
                            put("city", place)
                            put("latitude", lat)
                            put("longitude", lon)
                            put("timezone", tz)
                            put("maritalStatus", maritalStatus)
                            put("occupation", occupation)
                            put("topic", topic)
                        }
                        onSubmit(json)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if(isEditMode) "Updates Details" else "Start Consultation",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black // Black text on Gold button
                )
            }
        }
    }
}

@Composable
fun transparentTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GoldAccent,
    unfocusedBorderColor = GoldAccent.copy(alpha=0.5f),
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    cursorColor = GoldAccent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedLabelColor = GoldAccent,
    unfocusedLabelColor = TextWhite.copy(alpha=0.7f)
)

@Composable
fun transparentTextFieldColorsBottomLine() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    // Add logic if we want underlines, but using box outline for consistency is safer
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
     focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
