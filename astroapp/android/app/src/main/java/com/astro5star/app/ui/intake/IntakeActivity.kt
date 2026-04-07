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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.ui.theme.CosmicAppTheme
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
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
                    onSessionConnected = { sessionId, callType, birthData ->
                        navigateToSession(sessionId, callType, birthData)
                    },
                    onUnanswered = {
                        Toast.makeText(this, "Astrologer is busy. Please try again later.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun navigateToSession(sessionId: String, type: String, birthData: JSONObject? = null) {
        if (type == "chat") {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("toUserId", partnerId)
                putExtra("toUserName", partnerName)
                putExtra("birthData", birthData?.toString())
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("partnerId", partnerId)
                putExtra("partnerName", partnerName)
                putExtra("isInitiator", true)
                putExtra("callType", type)
                putExtra("birthData", birthData?.toString()) 
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
    onSessionConnected: (String, String, JSONObject?) -> Unit,
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
    var waitTimeLeft by remember { mutableIntStateOf(30) }
    var waitingSessionId by remember { mutableStateOf<String?>(null) }

    // State to track which city field triggered search
    var submittedBirthData by remember { mutableStateOf<JSONObject?>(null) }
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

    // Picker Dialog states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showPartnerDatePicker by remember { mutableStateOf(false) }
    var showPartnerTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (year.isNotEmpty()) {
            Calendar.getInstance().apply {
                set(year.toInt(), (month.toIntOrNull() ?: 1) - 1, day.toIntOrNull() ?: 1)
            }.timeInMillis
        } else System.currentTimeMillis()
    )

    val timePickerState = rememberTimePickerState(
        initialHour = if (hour.isNotEmpty()) convertTo24Hour(hour, amPm) else 12,
        initialMinute = minute.toIntOrNull() ?: 0,
        is24Hour = false
    )

    val pDatePickerState = rememberDatePickerState()
    val pTimePickerState = rememberTimePickerState(is24Hour = false)

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
            
            val h24 = d.optInt("hour", 0)
            amPm = if (h24 >= 12) "PM" else "AM"
            hour = (if (h24 % 12 == 0) 12 else h24 % 12).toString()
            minute = "%02d".format(d.optInt("minute", 0))

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
                
                val ph24 = pd.optInt("hour", 0)
                pAmPm = if (ph24 >= 12) "PM" else "AM"
                pHour = (if (ph24 % 12 == 0) 12 else ph24 % 12).toString()
                pMinute = "%02d".format(pd.optInt("minute", 0))
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
            
            val h24_pref = prefs.getInt("hour", 0)
            amPm = if (h24_pref >= 12) "PM" else "AM"
            hour = (if (h24_pref % 12 == 0) 12 else h24_pref % 12).toString()
            minute = "%02d".format(prefs.getInt("minute", 0))
            
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
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    val accepted = data?.optBoolean("accept", false) ?: false

                    if (isWaiting && data != null) {
                        scope.launch(Dispatchers.Main) {
                            isWaiting = false
                            if (accepted) {
                                val sid = waitingSessionId ?: data.optString("sessionId") ?: ""
                                onSessionConnected(sid, callType ?: "chat", submittedBirthData)
                            } else {
                                Toast.makeText(context, "Request Rejected by Astrologer", Toast.LENGTH_LONG).show()
                                onClose()
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
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
                 submittedBirthData = birthData
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF003300), Color(0xFF1B5E20), Color(0xFF2E7D32))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "பிறப்பு விவரம் (Birth Details)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Astro5Star",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.1f)
                    )
                )
            },
            bottomBar = {
               // Primary Action Button: Centered, compact, glowing
               Box(
                   modifier = Modifier
                       .fillMaxWidth()
                       .navigationBarsPadding()
                       .background(
                           brush = Brush.verticalGradient(
                               colors = listOf(Color.Transparent, Color(0xFF003300).copy(alpha = 0.6f))
                           )
                       )
                       .padding(horizontal = 40.dp, vertical = 12.dp),
                   contentAlignment = Alignment.Center
               ) {
                   Button(
                       onClick = { submit() },
                       modifier = Modifier
                           .fillMaxWidth()
                           .height(54.dp)
                           .shadow(16.dp, RoundedCornerShape(27.dp), spotColor = Color(0xFF2ECC71).copy(alpha = 0.6f)),
                       shape = RoundedCornerShape(27.dp),
                       colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                       contentPadding = PaddingValues(0.dp),
                       border = androidx.compose.foundation.BorderStroke(
                           1.5.dp,
                           Brush.linearGradient(
                               colors = listOf(Color.White.copy(alpha = 0.4f), Color(0xFF69F0AE).copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f))
                           )
                       )
                   ) {
                       Box(
                           modifier = Modifier
                               .fillMaxSize()
                               .background(
                                   Brush.horizontalGradient(
                                       colors = listOf(Color(0xFF1B8A4A), Color(0xFF2ECC71), Color(0xFF1B8A4A))
                                   )
                               ),
                           contentAlignment = Alignment.Center
                       ) {
                           Row(verticalAlignment = Alignment.CenterVertically) {
                               Icon(
                                   Icons.Default.AutoAwesome,
                                   contentDescription = null,
                                   tint = Color.White,
                                   modifier = Modifier.size(18.dp)
                               )
                               Spacer(Modifier.width(10.dp))
                               Text(
                                   if (isEditMode) "Update Details" else "Start Consultation ✨",
                                   fontSize = 16.sp,
                                   fontWeight = FontWeight.Bold,
                                   color = Color.White
                               )
                           }
                       }
                   }
               }
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Form Card Container
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Full Name
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Full Name") },
                                placeholder = { Text("Enter your full name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PeacockGreen,
                                    focusedLabelColor = PeacockGreen,
                                    cursorColor = PeacockGreen
                                )
                            )

                            // Gender Selection (Clean modern toggle style)
                            Text("Gender", fontWeight = FontWeight.SemiBold, color = RoyalMidnightBlue)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFF5F5F5))
                                    .padding(4.dp)
                            ) {
                                val isMale = gender == "Male"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isMale) PeacockGreen else Color.Transparent)
                                        .clickable { gender = "Male" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Male", color = if (isMale) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (!isMale) PeacockGreen else Color.Transparent)
                                        .clickable { gender = "Female" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Female", color = if (!isMale) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Date of Birth
                            val dateStr = if (day.isNotEmpty()) "$day / $month / $year" else "Select Date"
                            OutlinedTextField(
                                value = dateStr,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Date of Birth") },
                                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                                trailingIcon = { Icon(painterResource(android.R.drawable.ic_menu_my_calendar), null, tint = PeacockGreen, modifier = Modifier.clickable { showDatePicker = true }) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.LightGray,
                                    disabledLabelColor = Color.Gray,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            // Time of Birth
                            val timeStr = if (hour.isNotEmpty()) "$hour:$minute $amPm" else "Select Time"
                            OutlinedTextField(
                                value = timeStr,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Time of Birth") },
                                modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                                trailingIcon = { Icon(painterResource(android.R.drawable.ic_menu_recent_history), null, tint = PeacockGreen, modifier = Modifier.clickable { showTimePicker = true }) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.LightGray,
                                    disabledLabelColor = Color.Gray,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = unknownTime,
                                    onCheckedChange = { unknownTime = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PeacockGreen)
                                )
                                Text("Don't know exact time", color = RoyalMidnightBlue, fontSize = 14.sp)
                            }

                            // Place of Birth
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Place of Birth") },
                                modifier = Modifier.fillMaxWidth().clickable { launchLocationPicker() },
                                trailingIcon = { Icon(Icons.Default.LocationOn, "Location", tint = PeacockGreen) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = RoyalMidnightBlue,
                                    disabledBorderColor = Color.LightGray,
                                    disabledLabelColor = Color.Gray,
                                    disabledContainerColor = Color.Transparent
                                )
                            )

                            // Optional Topic of Concern
                            SpinnerDropdown(
                                label = "Topic of Concern",
                                selected = topic,
                                items = listOf("General", "Marriage", "Career", "Finance", "Health", "Education", "Legal"),
                                onSelect = { topic = it }
                            )

                            // Partners toggle
                            if (callType != "match") {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    Checkbox(
                                        checked = includePartner,
                                        onCheckedChange = { includePartner = it },
                                        colors = CheckboxDefaults.colors(checkedColor = PeacockGreen)
                                    )
                                    Text("Include Partner Details", color = RoyalMidnightBlue, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    if (includePartner) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Partner Details", fontWeight = FontWeight.Bold, color = RoyalMidnightBlue)
                                OutlinedTextField(
                                    value = pName,
                                    onValueChange = { pName = it },
                                    label = { Text("Partner Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PeacockGreen)
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val dateStr = if (pDay.isNotEmpty()) "$pDay/$pMonth/$pYear" else "Select Date"
                                    OutlinedTextField(
                                        value = dateStr,
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = false,
                                        label = { Text("DOB") },
                                        modifier = Modifier.weight(1f).clickable { showPartnerDatePicker = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = RoyalMidnightBlue)
                                    )
                                    val timeStr = if (pHour.isNotEmpty()) "$pHour:$pMinute $pAmPm" else "Select Time"
                                    OutlinedTextField(
                                        value = timeStr,
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = false,
                                        label = { Text("TOB") },
                                        modifier = Modifier.weight(1f).clickable { showPartnerTimePicker = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = RoyalMidnightBlue)
                                    )
                                }

                                OutlinedTextField(
                                    value = pCityName,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Place of Birth") },
                                    modifier = Modifier.fillMaxWidth().clickable { launchPartnerLocationPicker() },
                                    trailingIcon = { Icon(Icons.Default.LocationOn, null, tint = PeacockGreen) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = RoyalMidnightBlue)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(100.dp)) // Padding for sticky button
                }

                // Date Picker Dialog
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    val cal = Calendar.getInstance().apply { timeInMillis = it }
                                    day = cal.get(Calendar.DAY_OF_MONTH).toString()
                                    month = (cal.get(Calendar.MONTH) + 1).toString()
                                    year = cal.get(Calendar.YEAR).toString()
                                }
                                showDatePicker = false
                            }) { Text("OK", color = PeacockGreen) }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // Time Picker Dialog
                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val h = timePickerState.hour
                                amPm = if (h >= 12) "PM" else "AM"
                                hour = (if (h % 12 == 0) 12 else h % 12).toString()
                                minute = "%02d".format(timePickerState.minute)
                                showTimePicker = false
                            }) { Text("OK", color = PeacockGreen) }
                        },
                        text = {
                            TimePicker(state = timePickerState)
                        }
                    )
                }

                // Partner Date Picker Dialog
                if (showPartnerDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showPartnerDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pDatePickerState.selectedDateMillis?.let {
                                    val cal = Calendar.getInstance().apply { timeInMillis = it }
                                    pDay = cal.get(Calendar.DAY_OF_MONTH).toString()
                                    pMonth = (cal.get(Calendar.MONTH) + 1).toString()
                                    pYear = cal.get(Calendar.YEAR).toString()
                                }
                                showPartnerDatePicker = false
                            }) { Text("OK", color = PeacockGreen) }
                        }
                    ) {
                        DatePicker(state = pDatePickerState)
                    }
                }

                // Partner Time Picker Dialog
                if (showPartnerTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showPartnerTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val h = pTimePickerState.hour
                                pAmPm = if (h >= 12) "PM" else "AM"
                                pHour = (if (h % 12 == 0) 12 else h % 12).toString()
                                pMinute = "%02d".format(pTimePickerState.minute)
                                showPartnerTimePicker = false
                            }) { Text("OK", color = PeacockGreen) }
                        },
                        text = {
                            TimePicker(state = pTimePickerState)
                        }
                    )
                }

                // Vibrant Green Booking Confirmed Dialog
                if (isWaiting) {
                    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val mName = months.getOrElse(month.toIntOrNull() ?: 0) { "" }
                    val displayDate = "$day $mName, $year"
                    val displayTime = "$hour:$minute $amPm $timezoneDisplay"
                    val sessionType = if (callType == "match") "Relationship Matching" else "Full Natal Chart Reading"

                    Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 20.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = Color(0xFF69F0AE),
                                    ambientColor = Color(0xFF2ECC71)
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(1.dp, Color(0xFF69F0AE).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Celestial Icon Placeholder (Glow Effect)
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .shadow(10.dp, CircleShape, spotColor = Color(0xFF69F0AE))
                                        .background(Color(0xFF2ECC71).copy(alpha = 0.2f), CircleShape)
                                        .border(2.dp, Color(0xFF69F0AE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Cosmic",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(Modifier.height(24.dp))

                                Text(
                                    "Your cosmic journey\nbegins soon!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "The stars have aligned for your session. Get ready to uncover your destiny in the emerald sky.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                Spacer(Modifier.height(24.dp))

                                // Details Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "SESSION TYPE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            sessionType,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF69F0AE) // Mint
                                        )
                                        Icon(Icons.Default.AutoFixHigh, "", tint = Color(0xFF69F0AE), modifier = Modifier.size(20.dp))
                                    }

                                    Divider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = Color.White.copy(alpha = 0.1f)
                                    )

                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("DATE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                            Text(displayDate, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("TIME", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                            Text(displayTime, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Connecting...
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF69F0AE),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Connecting... ${waitTimeLeft}s",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = { isWaiting = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black.copy(alpha = 0.3f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Cancel Request", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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

private fun convertTo24Hour(hour: String, amPm: String): Int {
    val h = hour.toIntOrNull() ?: 12
    return when {
        amPm.equals("PM", ignoreCase = true) && h < 12 -> h + 12
        amPm.equals("AM", ignoreCase = true) && h == 12 -> 0
        else -> h
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
