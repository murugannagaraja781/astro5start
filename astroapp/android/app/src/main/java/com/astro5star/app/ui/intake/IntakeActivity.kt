package com.astro5star.app.ui.intake

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import com.astro5star.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.ui.theme.CosmicAppTheme
import io.socket.client.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

class IntakeActivity : ComponentActivity() {

    private var partnerId: String? = null
    private var type: String? = null
    private var partnerName: String? = null
    private var partnerImage: String? = null
    private var isEditMode = false
    private var existingData: JSONObject? = null
    private var targetUserId: String? = null

    private lateinit var tokenManager: TokenManager

    // Lat/Lon state needed for submission/timezone fetch
    // We'll manage these in the Composable state, but need to pass them to API
    // Actually, we can handle everything within the Compose screen logic.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        partnerId = intent.getStringExtra("partnerId")
        type = intent.getStringExtra("type")
        partnerName = intent.getStringExtra("partnerName") ?: "Astrologer"
        partnerImage = intent.getStringExtra("partnerImage")
        isEditMode = intent.getBooleanExtra("isEditMode", false)
        targetUserId = intent.getStringExtra("targetUserId")

        val dataStr = intent.getStringExtra("existingData")
        if (dataStr != null) {
            try { existingData = JSONObject(dataStr) } catch(e: Exception){}
        }

        setContent {
            CosmicAppTheme {
                IntakeScreen(
                    partnerId = partnerId,
                    partnerName = partnerName!!,
                    partnerImage = partnerImage,
                    callType = type,
                    isEditMode = isEditMode,
                    existingData = existingData,
                    targetUserId = targetUserId,
                    tokenManager = tokenManager,
                    onClose = { finish() },
                    onSessionConnected = { sessionId, callType ->
                        navigateToSession(sessionId, callType)
                    },
                    onUnanswered = {
                        Toast.makeText(this, "Astrologer is busy. Please try again later.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun navigateToSession(sessionId: String, type: String) {
        if (type == "chat") {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("toUserId", partnerId)
                putExtra("toUserName", partnerName)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("partnerId", partnerId)
                putExtra("partnerName", partnerName)
                putExtra("isInitiator", true)
                putExtra("callType", type)
            }
            startActivity(intent)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    partnerId: String?,
    partnerName: String,
    partnerImage: String?,
    callType: String?,
    isEditMode: Boolean,
    existingData: JSONObject?,
    targetUserId: String?,
    tokenManager: TokenManager,
    onClose: () -> Unit,
    onSessionConnected: (String, String) -> Unit,
    onUnanswered: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Form State
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") } // Male, Female

    // Date
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    // Time
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }
    var amPm by remember { mutableStateOf("AM") } // AM or PM
    var unknownTime by remember { mutableStateOf(false) }

    // Place
    var countryName by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var timezone by remember { mutableStateOf<Double?>(null) }

    // Additional
    var occupation by remember { mutableStateOf("") }
    var maritalStatus by remember { mutableStateOf("Single") }
    var topic by remember { mutableStateOf("Career / Job") }

    // Partner
    var includePartner by remember { mutableStateOf(false) }
    var pName by remember { mutableStateOf("") }
    var pCountryName by remember { mutableStateOf("") }
    var pStateName by remember { mutableStateOf("") }
    var pCityName by remember { mutableStateOf("") }
    var pLat by remember { mutableStateOf<Double?>(null) }
    var pLon by remember { mutableStateOf<Double?>(null) }
    var pTz by remember { mutableStateOf<Double?>(null) }
    var pTimezoneId by remember { mutableStateOf<String?>(null) }
    var pDay by remember { mutableStateOf("") }
    var pMonth by remember { mutableStateOf("") }
    var pYear by remember { mutableStateOf("") }
    var pHour by remember { mutableStateOf("") }
    var pMinute by remember { mutableStateOf("") }
    var pAmPm by remember { mutableStateOf("AM") }

    // Logic State
    var isWaiting by remember { mutableStateOf(false) }
    var waitTimeLeft by remember { mutableStateOf(30) }
    var waitingSessionId by remember { mutableStateOf<String?>(null) }

    // State to track which city field triggered search
    var activeCitySearchTarget by remember { mutableStateOf("client") } // "client" or "partner"

    val specificCityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
         if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val d = result.data!!
            val fullName = d.getStringExtra("name") ?: ""
            val cityRes = d.getStringExtra("city") ?: ""
            val stateRes = d.getStringExtra("state") ?: ""
            val countryRes = d.getStringExtra("country") ?: ""
            val tzId = d.getStringExtra("timezoneId")
            val latRes = d.getDoubleExtra("lat", 0.0)
            val lonRes = d.getDoubleExtra("lon", 0.0)

            val parsed = if (cityRes.isBlank() && stateRes.isBlank() && countryRes.isBlank()) {
                parsePlaceName(fullName)
            } else {
                Triple(cityRes, stateRes, countryRes)
            }
            val resolvedCity = parsed.first
            val resolvedState = parsed.second
            val resolvedCountry = parsed.third

            if (activeCitySearchTarget == "client") {
                cityName = resolvedCity
                stateName = resolvedState
                countryName = resolvedCountry
                timezoneId = tzId?.takeIf { it.isNotBlank() }
                latitude = latRes
                longitude = lonRes
                val computed = computeTimezoneOffsetHours(timezoneId, day, month, year, hour, minute)
                if (computed != null) timezone = computed
            } else {
                pCityName = resolvedCity
                pStateName = resolvedState
                pCountryName = resolvedCountry
                pTimezoneId = tzId?.takeIf { it.isNotBlank() }
                pLat = latRes
                pLon = lonRes
                val computed = computeTimezoneOffsetHours(pTimezoneId, pDay, pMonth, pYear, pHour, pMinute)
                if (computed != null) pTz = computed
            }
         }
    }

    val placeName = remember(cityName, stateName, countryName) {
        buildPlaceName(cityName, stateName, countryName)
    }

    val computedTimezone = remember(timezoneId, day, month, year, hour, minute) {
        computeTimezoneOffsetHours(timezoneId, day, month, year, hour, minute)
    }
    val timezoneOffset = computedTimezone ?: timezone
    val timezoneDisplay = timezoneOffset?.let { formatUtcOffset(it) } ?: ""

    val pPlaceName = remember(pCityName, pStateName, pCountryName) {
        buildPlaceName(pCityName, pStateName, pCountryName)
    }

    val partnerComputedTimezone = remember(pTimezoneId, pDay, pMonth, pYear, pHour, pMinute) {
        computeTimezoneOffsetHours(pTimezoneId, pDay, pMonth, pYear, pHour, pMinute)
    }
    val partnerTimezoneOffset = partnerComputedTimezone ?: pTz
    val partnerTimezoneDisplay = partnerTimezoneOffset?.let { formatUtcOffset(it) } ?: ""

    val launchLocationPicker = {
        activeCitySearchTarget = "client"
        val intent = Intent(context, com.astro5star.app.ui.city.CitySearchActivity::class.java)
        specificCityLauncher.launch(intent)
    }

    val launchPartnerLocationPicker = {
        activeCitySearchTarget = "partner"
        val intent = Intent(context, com.astro5star.app.ui.city.CitySearchActivity::class.java)
        specificCityLauncher.launch(intent)
    }

    // Prefill
    LaunchedEffect(Unit) {
        if (existingData != null) {
            val d = existingData!!
            name = d.optString("name")
            val placeRaw = d.optString("city")
            val parsed = parsePlaceName(placeRaw)
            cityName = parsed.first
            stateName = d.optString("state", parsed.second)
            countryName = d.optString("country", parsed.third)
            latitude = d.optDouble("latitude", 0.0).takeIf { it != 0.0 }
            longitude = d.optDouble("longitude", 0.0).takeIf { it != 0.0 }
            timezone = d.optDouble("timezone", 5.5)
            timezoneId = d.optString("timezoneId").takeIf { it.isNotBlank() }

            day = d.optInt("day", 0).toString().takeIf { it != "0" } ?: ""
            month = d.optInt("month", 0).toString().takeIf { it != "0" } ?: ""
            year = d.optInt("year", 0).toString().takeIf { it != "0" } ?: ""
            hour = d.optInt("hour", 0).toString()
            minute = d.optInt("minute", 0).toString()

            gender = d.optString("gender", "Male")
            maritalStatus = d.optString("maritalStatus", "Single")
            occupation = d.optString("occupation", "")
            topic = d.optString("topic", "General")

            val pd = d.optJSONObject("partner")
            if (pd != null) {
                includePartner = true
                pName = pd.optString("name")
                val pPlaceRaw = pd.optString("city")
                val pParsed = parsePlaceName(pPlaceRaw)
                pCityName = pParsed.first
                pStateName = pd.optString("state", pParsed.second)
                pCountryName = pd.optString("country", pParsed.third)
                pDay = pd.optInt("day").toString()
                pMonth = pd.optInt("month").toString()
                pYear = pd.optInt("year").toString()
                pHour = pd.optInt("hour").toString()
                pMinute = pd.optInt("minute").toString()
                pLat = pd.optDouble("latitude", 0.0)
                pLon = pd.optDouble("longitude", 0.0)
                pTz = pd.optDouble("timezone", 5.5)
                pTimezoneId = pd.optString("timezoneId").takeIf { it.isNotBlank() }
            }
        } else {
            // Load Defaults
            val prefs = context.getSharedPreferences("AstroIntakeDefaults", Context.MODE_PRIVATE)
            name = prefs.getString("name", "") ?: ""
            val storedCity = prefs.getString("city", "") ?: ""
            val storedState = prefs.getString("state", "") ?: ""
            val storedCountry = prefs.getString("country", "") ?: ""
            if (storedCity.isBlank() && storedState.isBlank() && storedCountry.isBlank()) {
                val storedPlace = prefs.getString("place", "") ?: ""
                val parsed = parsePlaceName(storedPlace)
                cityName = parsed.first
                stateName = parsed.second
                countryName = parsed.third
            } else {
                cityName = storedCity
                stateName = storedState
                countryName = storedCountry
            }
            latitude = prefs.getFloat("latitude", 0f).toDouble().takeIf { it != 0.0 }
            longitude = prefs.getFloat("longitude", 0f).toDouble().takeIf { it != 0.0 }
            timezone = prefs.getFloat("timezone", 5.5f).toDouble()
            timezoneId = prefs.getString("timezoneId", null)
            day = prefs.getInt("day", 0).toString().takeIf { it != "0" } ?: ""
            month = prefs.getInt("month", 0).toString().takeIf { it != "0" } ?: ""
            year = prefs.getInt("year", 0).toString().takeIf { it != "0" } ?: ""
            hour = prefs.getInt("hour", 0).toString()
            minute = prefs.getInt("minute", 0).toString()
            gender = prefs.getString("gender", "Male") ?: "Male"
            occupation = prefs.getString("occupation", "") ?: ""
            maritalStatus = prefs.getString("maritalStatus", "Single") ?: "Single"
            topic = prefs.getString("topic", "General") ?: "General"
        }

        if (callType == "match") {
            includePartner = true
        }
    }

    // Waiting Timer
    LaunchedEffect(isWaiting) {
        if (isWaiting) {
            waitTimeLeft = 30
            while(waitTimeLeft > 0) {
                delay(1000)
                waitTimeLeft--
            }
            if (isWaiting) {
                isWaiting = false
                onUnanswered()
            }
        }
    }

    // Socket Listener for Wait
    DisposableEffect(Unit) {
        val socket = SocketManager.getSocket()
        val listener: (Array<Any>) -> Unit = { args ->
            val data = args[0] as JSONObject
            val accepted = data.optBoolean("accept", false)
            if (isWaiting) {
                if (accepted) {
                    isWaiting = false
                    val sid = waitingSessionId ?: ""
                    onSessionConnected(sid, callType ?: "chat")
                } else {
                     isWaiting = false
                     // Rejected
                     scope.launch { Toast.makeText(context, "Request Rejected by Astrologer", Toast.LENGTH_LONG).show() }
                     onClose()
                }
            }
        }

        socket?.on("session-answered", listener)

        onDispose {
            socket?.off("session-answered", listener)
        }
    }

    fun validateInput(
        d: String, m: String, y: String, h: String, min: String,
        label: String
    ): Boolean {
        val dayInt = d.toIntOrNull() ?: 0
        val monthInt = m.toIntOrNull() ?: 0
        val yearInt = y.toIntOrNull() ?: 0
        val hourInt = h.toIntOrNull() ?: -1
        val minuteInt = min.toIntOrNull() ?: -1

        if (dayInt < 1 || dayInt > 31) {
            Toast.makeText(context, "$label: Invalid Day (1-31)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (monthInt < 1 || monthInt > 12) {
            Toast.makeText(context, "$label: Invalid Month (1-12)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (yearInt < 1900 || yearInt > 2100) {
            Toast.makeText(context, "$label: Invalid Year (1900-2100)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (hourInt < 1 || hourInt > 12) {
            Toast.makeText(context, "$label: Invalid Hour (1-12)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (minuteInt < 0 || minuteInt > 59) {
            Toast.makeText(context, "$label: Invalid Minute (0-59)", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun convertTo24Hour(hStr: String, amPm: String): Int {
        val h = hStr.toIntOrNull() ?: 0
        return if (amPm == "PM" && h < 12) h + 12
        else if (amPm == "AM" && h == 12) 0
        else h
    }

    fun submit() {
        if (name.isBlank() || cityName.isBlank() || day.isBlank() || month.isBlank() || year.isBlank()) {
            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateInput(day, month, year, hour, minute, "Personal DOB")) return

        val hour24 = convertTo24Hour(hour, amPm)
        val pHour24 = convertTo24Hour(pHour, pAmPm)

        // Validation for Match - Partner details required
        if (callType == "match" || includePartner) {
            if (pName.isBlank()) {
                Toast.makeText(context, "Partner name required", Toast.LENGTH_SHORT).show()
                return
            }
            if (!validateInput(pDay, pMonth, pYear, pHour, pMinute, "Partner DOB")) return
            if (pCityName.isBlank()) {
                Toast.makeText(context, "Partner place required", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val finalTimezone = computeTimezoneOffsetHours(timezoneId, day, month, year, hour24.toString(), minute) ?: timezone ?: 5.5
        val finalPartnerTimezone = computeTimezoneOffsetHours(pTimezoneId, pDay, pMonth, pYear, pHour24.toString(), pMinute) ?: pTz ?: 5.5

        // Save Defaults
        val prefs = context.getSharedPreferences("AstroIntakeDefaults", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", name)
            putString("place", placeName)
            putString("city", cityName)
            putString("state", stateName)
            putString("country", countryName)
            putInt("day", day.toIntOrNull() ?: 0)
            putInt("month", month.toIntOrNull() ?: 0)
            putInt("year", year.toIntOrNull() ?: 0)
            putInt("hour", hour24)
            putInt("minute", minute.toIntOrNull() ?: 0)
            putString("gender", gender)
            putString("occupation", occupation)
            putString("maritalStatus", maritalStatus)
            putString("topic", topic)
            if (latitude != null) putFloat("latitude", latitude!!.toFloat())
            if (longitude != null) putFloat("longitude", longitude!!.toFloat())
            putFloat("timezone", finalTimezone.toFloat())
            if (!timezoneId.isNullOrBlank()) {
                putString("timezoneId", timezoneId)
            } else {
                remove("timezoneId")
            }
            apply()
        }

        var partnerData: JSONObject? = null
        if (includePartner) {
            partnerData = JSONObject().apply {
                 put("name", pName)
                 put("day", pDay.toIntOrNull() ?: 0)
                 put("month", pMonth.toIntOrNull() ?: 0)
                 put("year", pYear.toIntOrNull() ?: 0)
                 put("hour", pHour24)
                 put("minute", pMinute.toIntOrNull() ?: 0)
                 put("city", pPlaceName)
                 put("state", pStateName)
                 put("country", pCountryName)
                 put("latitude", pLat ?: latitude ?: 13.0827)
                 put("longitude", pLon ?: longitude ?: 80.2707)
                 put("timezone", finalPartnerTimezone)
                 if (!pTimezoneId.isNullOrBlank()) put("timezoneId", pTimezoneId)
                 put("gender", if (gender == "Male") "Female" else "Male")
            }
        }

        val birthData = JSONObject().apply {
            put("name", name)
            put("gender", gender)
            put("day", day.toIntOrNull() ?: 0)
            put("month", month.toIntOrNull() ?: 0)
            put("year", year.toIntOrNull() ?: 0)
            put("hour", hour24)
            put("minute", minute.toIntOrNull() ?: 0)
            put("city", placeName)
            put("state", stateName)
            put("country", countryName)
            put("latitude", latitude)
            put("longitude", longitude)
            put("timezone", finalTimezone)
            if (!timezoneId.isNullOrBlank()) put("timezoneId", timezoneId)
            put("maritalStatus", maritalStatus)
            put("occupation", occupation)
            put("topic", topic)
            if (partnerData != null) put("partner", partnerData)
        }

        // Save to API
        val userId = targetUserId ?: tokenManager.getUserSession()?.userId
        if (userId != null) {
              val payload = JSONObject().apply {
                  put("userId", userId)
                  put("intakeData", birthData)
              }
              scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                  try {
                       val gsonReq = com.google.gson.JsonParser.parseString(payload.toString()).asJsonObject
                       com.astro5star.app.data.api.ApiClient.api.saveUserIntake(gsonReq)
                  } catch(e: Exception) { e.printStackTrace() }
              }
        }

        if (isEditMode) {
             val intent = Intent()
             intent.putExtra("birthData", birthData.toString())
             (context as? Activity)?.setResult(Activity.RESULT_OK, intent)
             onClose()
        } else if (callType == "free_horoscope") {
             val intent = Intent(context, com.astro5star.app.ui.chart.VipChartActivity::class.java).apply {
                 putExtra("birthData", birthData.toString())
             }
             context.startActivity(intent)
             (context as? Activity)?.finish()
        } else if (callType == "match") {
             val intent = Intent(context, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java).apply {
                 putExtra("birthData", birthData.toString())
             }
             context.startActivity(intent)
             (context as? Activity)?.finish()
        } else {
            // Initiate Session
             if (partnerId != null && callType != null) {
                 SocketManager.init()
                 SocketManager.requestSession(partnerId, callType, birthData) { response ->
                     if (response?.optBoolean("ok") == true) {
                         waitingSessionId = response.optString("sessionId")
                         scope.launch { isWaiting = true }
                     } else {
                         scope.launch {
                             Toast.makeText(context, response?.optString("error") ?: "Failed", Toast.LENGTH_SHORT).show()
                         }
                     }
                 }
             }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicAppTheme.backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Consultation Details",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Personal Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Personal Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoyalMidnightBlue
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Full Name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PeacockGreen,
                                    focusedLabelColor = PeacockGreen,
                                    cursorColor = PeacockGreen,
                                    focusedTextColor = RoyalMidnightBlue,
                                    unfocusedTextColor = RoyalMidnightBlue
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Gender:", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                                Spacer(Modifier.width(16.dp))
                                RadioButton(
                                    selected = gender == "Male",
                                    onClick = { gender = "Male" },
                                    colors = RadioButtonDefaults.colors(selectedColor = PeacockGreen)
                                )
                                Text("Male", color = RoyalMidnightBlue)
                                Spacer(Modifier.width(16.dp))
                                RadioButton(
                                    selected = gender == "Female",
                                    onClick = { gender = "Female" },
                                    colors = RadioButtonDefaults.colors(selectedColor = PeacockGreen)
                                )
                                Text("Female", color = RoyalMidnightBlue)
                            }

                            Text("Date of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = day,
                                    onValueChange = { if (it.length <= 2) day = it },
                                    label = { Text("DD") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )
                                OutlinedTextField(
                                    value = month,
                                    onValueChange = { if (it.length <= 2) month = it },
                                    label = { Text("MM") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )
                                OutlinedTextField(
                                    value = year,
                                    onValueChange = { if (it.length <= 4) year = it },
                                    label = { Text("YYYY") },
                                    modifier = Modifier.weight(1.5f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )
                            }

                            Text("Time of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = hour,
                                    onValueChange = { if (it.length <= 2) hour = it },
                                    label = { Text("Hour") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )
                                Text(":", fontWeight = FontWeight.Bold, color = RoyalMidnightBlue, fontSize = 20.sp)
                                OutlinedTextField(
                                    value = minute,
                                    onValueChange = { if (it.length <= 2) minute = it },
                                    label = { Text("Min") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )

                                Row(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF0F0F0))
                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                ) {
                                    Text(
                                        "AM",
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { amPm = "AM" }
                                            .background(if (amPm == "AM") PeacockGreen else Color.Transparent)
                                            .padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (amPm == "AM") Color.White else RoyalMidnightBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "PM",
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { amPm = "PM" }
                                            .background(if (amPm == "PM") PeacockGreen else Color.Transparent)
                                            .padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (amPm == "PM") Color.White else RoyalMidnightBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = unknownTime,
                                    onCheckedChange = { unknownTime = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PeacockGreen)
                                )
                                Text("Don't know exact time", color = RoyalMidnightBlue)
                            }

                            Text("Place of Birth", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                            OutlinedTextField(
                                value = countryName,
                                onValueChange = {},
                                label = { Text("Country") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { launchLocationPicker() },
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.Gray,
                                    disabledLabelColor = RoyalMidnightBlue,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = stateName,
                                onValueChange = {},
                                label = { Text("State") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { launchLocationPicker() },
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.Gray,
                                    disabledLabelColor = RoyalMidnightBlue,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                label = { Text("City") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { launchLocationPicker() },
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.Gray,
                                    disabledLabelColor = RoyalMidnightBlue,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            OutlinedTextField(
                                value = timezoneDisplay,
                                onValueChange = {},
                                label = { Text("Timezone") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.Gray,
                                    disabledLabelColor = RoyalMidnightBlue,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            // Optional
                            OutlinedTextField(
                                value = occupation,
                                onValueChange = { occupation = it },
                                label = { Text("Occupation (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PeacockGreen,
                                    focusedLabelColor = PeacockGreen,
                                    cursorColor = PeacockGreen
                                )
                            )

                            SpinnerDropdown(
                                label = "Marital Status",
                                selected = maritalStatus,
                                items = listOf("Single", "Married", "Divorced", "Widowed"),
                                onSelect = { maritalStatus = it }
                            )

                            SpinnerDropdown(
                                label = "Topic of Concern",
                                selected = topic,
                                items = listOf(
                                    "Career / Job",
                                    "Marriage / Relationship",
                                    "Health",
                                    "Finance",
                                    "Legal",
                                    "General"
                                ),
                                onSelect = { topic = it }
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (callType != "match") {
                            Checkbox(
                                checked = includePartner,
                                onCheckedChange = { includePartner = it },
                                colors = CheckboxDefaults.colors(checkedColor = PeacockGreen)
                            )
                            Text(
                                "Include Partner Details",
                                modifier = Modifier.padding(start = 8.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Partner Details",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        }
                    }

                    if (includePartner) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = pName,
                                    onValueChange = { pName = it },
                                    label = { Text("Partner Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PeacockGreen,
                                        focusedLabelColor = PeacockGreen,
                                        cursorColor = PeacockGreen
                                    )
                                )
                                Text("Partner DOB", fontWeight = FontWeight.Bold, color = RoyalMidnightBlue)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pDay,
                                        onValueChange = { if (it.length <= 2) pDay = it },
                                        label = { Text("DD") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PeacockGreen,
                                            focusedLabelColor = PeacockGreen,
                                            cursorColor = PeacockGreen
                                        )
                                    )
                                    OutlinedTextField(
                                        value = pMonth,
                                        onValueChange = { if (it.length <= 2) pMonth = it },
                                        label = { Text("MM") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PeacockGreen,
                                            focusedLabelColor = PeacockGreen,
                                            cursorColor = PeacockGreen
                                        )
                                    )
                                    OutlinedTextField(
                                        value = pYear,
                                        onValueChange = { if (it.length <= 4) pYear = it },
                                        label = { Text("YYYY") },
                                        modifier = Modifier.weight(1.5f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PeacockGreen,
                                            focusedLabelColor = PeacockGreen,
                                            cursorColor = PeacockGreen
                                        )
                                    )
                                }
                                Text("Partner Time", fontWeight = FontWeight.Bold, color = RoyalMidnightBlue)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = pHour,
                                        onValueChange = { if (it.length <= 2) pHour = it },
                                        label = { Text("Hour") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PeacockGreen,
                                            focusedLabelColor = PeacockGreen,
                                            cursorColor = PeacockGreen
                                        )
                                    )
                                    Text(":", fontWeight = FontWeight.Bold, color = RoyalMidnightBlue)
                                    OutlinedTextField(
                                        value = pMinute,
                                        onValueChange = { if (it.length <= 2) pMinute = it },
                                        label = { Text("Min") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PeacockGreen,
                                            focusedLabelColor = PeacockGreen,
                                            cursorColor = PeacockGreen
                                        )
                                    )

                                    Row(
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF0F0F0))
                                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                    ) {
                                        Text(
                                            "AM",
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { pAmPm = "AM" }
                                                .background(if (pAmPm == "AM") PeacockGreen else Color.Transparent)
                                                .padding(12.dp),
                                            textAlign = TextAlign.Center,
                                            color = if (pAmPm == "AM") Color.White else RoyalMidnightBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "PM",
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { pAmPm = "PM" }
                                                .background(if (pAmPm == "PM") PeacockGreen else Color.Transparent)
                                                .padding(12.dp),
                                            textAlign = TextAlign.Center,
                                            color = if (pAmPm == "PM") Color.White else RoyalMidnightBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    "Partner Place of Birth",
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalMidnightBlue
                                )
                                OutlinedTextField(
                                    value = pCountryName,
                                    onValueChange = {},
                                    label = { Text("Country") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchPartnerLocationPicker() },
                                    trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = RoyalMidnightBlue,
                                        disabledBorderColor = Color.Gray,
                                        disabledLabelColor = RoyalMidnightBlue,
                                        disabledContainerColor = Color.Transparent
                                    )
                                )
                                OutlinedTextField(
                                    value = pStateName,
                                    onValueChange = {},
                                    label = { Text("State") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchPartnerLocationPicker() },
                                    trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = RoyalMidnightBlue,
                                        disabledBorderColor = Color.Gray,
                                        disabledLabelColor = RoyalMidnightBlue,
                                        disabledContainerColor = Color.Transparent
                                    )
                                )
                                OutlinedTextField(
                                    value = pCityName,
                                    onValueChange = {},
                                    label = { Text("City") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchPartnerLocationPicker() },
                                    trailingIcon = { Icon(Icons.Default.LocationOn, "Pick", tint = PeacockGreen) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = RoyalMidnightBlue,
                                        disabledBorderColor = Color.Gray,
                                        disabledLabelColor = RoyalMidnightBlue,
                                        disabledContainerColor = Color.Transparent
                                    )
                                )
                                OutlinedTextField(
                                    value = partnerTimezoneDisplay,
                                    onValueChange = {},
                                    label = { Text("Timezone") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = RoyalMidnightBlue,
                                        disabledBorderColor = Color.Gray,
                                        disabledLabelColor = RoyalMidnightBlue,
                                        disabledContainerColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { submit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = PeacockGreen
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen)
                    ) {
                        Text(
                            if (isEditMode) "Update Details" else "Start Consultation",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoyalMidnightBlue
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }

                // Simple Waiting Dialog
                if (isWaiting) {
                    Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(8.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Circular Progress Indicator
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp),
                                    color = PeacockGreen,
                                    strokeWidth = 6.dp
                                )

                                Spacer(Modifier.height(16.dp))

                                // Connecting message
                                Text(
                                    text = "Connecting to $partnerName...",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    color = RoyalMidnightBlue
                                )

                                Spacer(Modifier.height(8.dp))

                                // Timer countdown
                                Text(
                                    text = "${waitTimeLeft}s",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PeacockGreen
                                )

                                Spacer(Modifier.height(16.dp))

                                // Cancel button
                                Button(
                                    onClick = { isWaiting = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                                ) {
                                    Text("Cancel Request", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpinnerDropdown(
    label: String,
    selected: String,
    items: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
            color = RoyalMidnightBlue,
            fontSize = 15.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F0F0)) // Light gray bg
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, "", tint = PeacockGreen) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = RoyalMidnightBlue,
                    disabledBorderColor = Color.Transparent, // Border handled by Box
                    disabledLabelColor = RoyalMidnightBlue,
                    disabledContainerColor = Color.Transparent
                )
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.White)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item, color = RoyalMidnightBlue, fontWeight = FontWeight.Medium) },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun buildPlaceName(city: String, state: String, country: String): String {
    return listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
}

private fun parsePlaceName(place: String): Triple<String, String, String> {
    if (place.isBlank()) return Triple("", "", "")
    val parts = place.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val city = parts.getOrNull(0) ?: ""
    val state = parts.getOrNull(1) ?: ""
    val country = parts.getOrNull(2) ?: ""
    return Triple(city, state, country)
}

private fun computeTimezoneOffsetHours(
    timezoneId: String?,
    day: String,
    month: String,
    year: String,
    hour: String,
    minute: String
): Double? {
    if (timezoneId.isNullOrBlank()) return null
    val tz = TimeZone.getTimeZone(timezoneId)
    if (tz.id == "GMT" && timezoneId != "GMT" && timezoneId != "UTC") return null

    val dayInt = day.toIntOrNull()
    val monthInt = month.toIntOrNull()
    val yearInt = year.toIntOrNull()
    val hourInt = hour.toIntOrNull() ?: 0
    val minuteInt = minute.toIntOrNull() ?: 0

    val offsetMillis = if (dayInt != null && monthInt != null && yearInt != null) {
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, yearInt)
            set(Calendar.MONTH, (monthInt - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, dayInt.coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, hourInt.coerceIn(0, 23))
            set(Calendar.MINUTE, minuteInt.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        tz.getOffset(cal.timeInMillis)
    } else {
        tz.rawOffset
    }

    return offsetMillis / 3600000.0
}

private fun formatUtcOffset(offsetHours: Double): String {
    val totalMinutes = (offsetHours * 60).roundToInt()
    val sign = if (totalMinutes >= 0) "+" else "-"
    val absMinutes = abs(totalMinutes)
    val hours = absMinutes / 60
    val minutes = absMinutes % 60
    return "UTC$sign${"%02d".format(hours)}:${"%02d".format(minutes)}"
}
