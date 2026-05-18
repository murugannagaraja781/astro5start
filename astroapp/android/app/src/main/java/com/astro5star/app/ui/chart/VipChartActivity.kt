package com.astro5star.app.ui.chart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// --- Aesthetic Constants ---
val ParchmentBase = Color(0xFFF4E1C1) // Vintage Paper Color
val ParchmentLight = Color(0xFFFCF5E5)
val TraditionalRed = Color(0xFF8B0000) // Deep Blood Red for borders
val TextGold = Color(0xFFB8860B)
val BorderColor = Color(0xFF8B0000)

// --- Tamil Translation Constants ---
val signTamil = mapOf(
    "Aries" to "மேஷம்", "Taurus" to "ரிஷபம்", "Gemini" to "மிதுனம்", "Cancer" to "கடகம்",
    "Leo" to "சிம்மம்", "Virgo" to "கன்னி", "Libra" to "துலாம்", "Scorpio" to "விருச்சிகம்",
    "Sagittarius" to "தனுசு", "Capricorn" to "மகரம்", "Aquarius" to "கும்பம்", "Pisces" to "மீனம்"
)

val planetTamil = mapOf(
    "Sun" to "சூரியன்", "Moon" to "சந்திரன்", "Mars" to "செவ்வாய்", "Mercury" to "புதன்",
    "Jupiter" to "குரு", "Venus" to "சுக்கிரன்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்னம்", "Mandi" to "மாந்தி"
)

val planetAbbrTamil = mapOf(
    "Sun" to "சூரி", "Moon" to "சந்", "Mars" to "செவ்", "Mercury" to "புத",
    "Jupiter" to "குரு", "Venus" to "சுக்", "Saturn" to "சனி", "Rahu" to "ராகு",
    "Ketu" to "கேது", "Ascendant" to "லக்", "As" to "லக்", "Mandi" to "மாந்தி"
)

val naksTamil = mapOf(
    "Ashwini" to "அஸ்வினி", "Bharani" to "பரணி", "Krittika" to "கார்த்திகை", "Rohini" to "ரோகிணி",
    "Mrigashira" to "மிருகசீரிடம்", "Ardra" to "திருவாதிரை", "Punarvasu" to "புனர்பூசம்", "Pushya" to "பூசம்",
    "Ashlesha" to "ஆயில்யம்", "Magha" to "மகம்", "Purva Phalguni" to "பூரம்", "Uttara Phalguni" to "உத்திரம்",
    "Hasta" to "அஸ்தம்", "Chitra" to "சித்திரை", "Swati" to "சுவாதி", "Vishakha" to "விசாகம்",
    "Anuradha" to "அனுஷம்", "Jyeshtha" to "கேட்டை", "Mula" to "மூலம்", "Purva Ashadha" to "பூராடம்",
    "Uttara Ashadha" to "உத்திராடம்", "Shravana" to "திருவோணம்", "Dhanishta" to "அவிட்டம்", "Shatabhisha" to "சதயம்",
    "Purva Bhadrapada" to "பூரட்டாதி", "Uttara Bhadrapada" to "உத்திரட்டாதி", "Revati" to "ரேவதி"
)

val yogaTamil = mapOf(
    "Vishkumbha" to "விஷ்கம்பம்", "Priti" to "பிரீதி", "Ayushman" to "ஆயுஷ்மான்", "Saubhagya" to "சௌபாக்கியம்",
    "Shobhana" to "சோபனம்", "Atiganda" to "அதிகண்டம்", "Sukarma" to "சுகர்மா", "Dhriti" to "திருதி",
    "Shula" to "சூலம்", "Ganda" to "கண்டம்", "Vriddhi" to "விருத்தி", "Dhruva" to "துருவம்",
    "Vyaghata" to "வியாகாதம்", "Harshana" to "ஹர்ஷணம்", "Vajra" to "வஜ்ரம்", "Siddhi" to "சித்தி",
    "Vyatipata" to "வியதீபாதம்", "Variyan" to "வாரியான்", "Parigha" to "பரிகம்", "Shiva" to "சிவம்",
    "Siddha" to "சித்தம்", "Sadhya" to "சாத்தியம்", "Shubha" to "சுபம்", "Shukla" to "சுக்கிலம்",
    "Brahma" to "பிரம்மா", "Aindra" to "ஐந்திரம்", "Vaidhriti" to "வைதிருதி"
)

val karanaTamil = mapOf(
    "Bava" to "பவம்", "Balava" to "பாலவம்", "Kaulava" to "கௌலவம்", "Taitila" to "சைதிலம்",
    "Gara" to "கரசை", "Vanija" to "வணிசை", "Vishti" to "பத்திரை", "Shakuni" to "சகுனி",
    "Chatushpada" to "சதுஷ்பாதம்", "Naga" to "நாகவம்", "Kimstughna" to "கிமிஸ்துக்கினம்"
)

fun calculateAge(year: Int, month: Int, day: Int): Int {
    val dob = java.util.Calendar.getInstance().apply { set(year, month - 1, day) }
    val today = java.util.Calendar.getInstance()
    var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
    if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) age--
    return age
}

// --- Updated Data Models ---
data class ChartResponse(val success: Boolean, val data: ChartData)
data class ChartData(
    val planets: List<Planet>,
    val houses: HouseData,
    val panchanga: Panchanga,
    val dasha: List<DashaPeriod>,
    val transits: List<Transit>,
    val tamilDate: TamilDate?,
    val kpSignificators: KPSignificators? = null,
    val navamsa: NavamsaData? = null
)

data class Planet(
    val name: String,
    val signName: String,
    val signIndex: Int,
    val house: Int,
    val nakshatra: String,
    val nakshatraPada: Int,
    val degreeFormatted: String? = null,
    val signLord: String? = null,
    val starLord: String? = null,
    val subLord: String? = null,
    val dignity: String? = null,
    val isRetrograde: Boolean = false,
    val isCombust: Boolean = false
)

data class HouseData(
    val cusps: List<Double>,
    val details: List<HouseDetail>,
    val ascendantDetails: HouseDetail
)

data class HouseDetail(
    val signName: String,
    val signAbbr: String? = null,
    val nakshatra: String? = null,
    val nakshatraPada: Int = 1,
    val signLord: String? = null,
    val starLord: String? = null,
    val subLord: String? = null,
    val degreeFormatted: String? = null
)

data class Panchanga(
    val tithi: PanchangaValue? = null,
    val nakshatra: PanchangaValue? = null,
    val yoga: PanchangaValue? = null,
    val karana: PanchangaValue? = null,
    val vara: PanchangaValue? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val moonSign: String? = null,
    val sunSign: String? = null
)

data class PanchangaValue(
    val name: String,
    val name_ta: String? = null,
    val index: Int = 0
)
data class DashaPeriod(
    val lord: String,
    val start: String,
    val end: String,
    val level: Int,
    val subPeriods: List<DashaPeriod>? = null
)
data class Transit(val name: String, val signName: String, val isRetrograde: Boolean)
data class TamilDate(val day: Int, val month: String, val year: String)
data class NavamsaData(val planets: List<Planet>? = null)
data class KPSignificators(val planetView: List<KPPlanet>?, val houseView: List<KPHouse>?)
data class KPPlanet(val name: String, val levelA: List<Int>, val levelB: List<Int>, val levelC: List<Int>, val levelD: List<Int>)
data class KPHouse(val house: Int, val level1: List<String>, val level2: List<String>, val level3: List<String>, val level4: List<String>, val lord: String)

class VipChartActivity : ComponentActivity() {
    private var toUserId: String? = null
    private var sessionId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val birthDataStr = intent.getStringExtra("birthData") ?: "{}"
        val birthData = JSONObject(birthDataStr)
        toUserId = intent.getStringExtra("toUserId")
        sessionId = intent.getStringExtra("sessionId")

        // Emit status update if we have session info
        if (!toUserId.isNullOrEmpty() && !sessionId.isNullOrEmpty()) {
            com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("status-update", JSONObject().apply {
                put("toUserId", toUserId)
                put("status", "analysing_chart")
                put("sessionId", sessionId)
            })
        }

        setContent {
            CosmicAppTheme {
                VipChartScreen(birthData) { finish() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear status update
        if (!toUserId.isNullOrEmpty() && !sessionId.isNullOrEmpty()) {
            com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("status-update", JSONObject().apply {
                put("toUserId", toUserId)
                put("status", "")
                put("sessionId", sessionId)
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipChartScreen(birthData: JSONObject, onBack: () -> Unit) {
    var chartState by remember { mutableStateOf<ChartData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                val result = fetchFullChart(birthData)
                if (result != null) {
                    chartState = result
                } else {
                    errorMessage = "Failed to fetch chart data. This often happens if the server connection is unstable. Please try again."
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ராசி & நவாம்ச கட்டங்கள்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TraditionalRed)
                        val gender = when(birthData.optString("gender").lowercase()) {
                            "male" -> "ஆண் (Male)"
                            "female" -> "பெண் (Female)"
                            else -> birthData.optString("gender", "")
                        }
                        val age = calculateAge(birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day"))
                        Text("${birthData.optString("name", "User")} | $gender | Age: $age", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TraditionalRed) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ParchmentLight)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(ParchmentLight)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TraditionalRed)
                }
            } else if (chartState != null) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ParchmentLight,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = TraditionalRed
                        )
                    }
                ) {
                    val tabs = listOf("கட்டங்கள்", "நவகிரக பாதசாரம்", "தசா புக்தி விபரங்கள்")
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    fontWeight = if(selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if(selectedTab == index) TraditionalRed else Color.Gray
                                )
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> chartState?.let { ChartsTab(it, birthData) }
                        1 -> chartState?.let { PlanetsTab(it) }
                        2 -> chartState?.let { DashaListTab(it.dasha) }
                    }
                }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Unknown Error",
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { onBack() },
                            colors = ButtonDefaults.buttonColors(containerColor = TraditionalRed)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChartsTab(data: ChartData, birthData: JSONObject) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

        Text("ராசி கட்டம் (Rasi Chart)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TraditionalRed)
        Spacer(Modifier.height(8.dp))
        SouthIndianGridEnhanced(data.planets, data.houses.ascendantDetails, "Rasi", birthData, data.panchanga.nakshatra?.name ?: "")

        Spacer(Modifier.height(32.dp))
        Text("பஞ்சாங்கம் (Panchangam)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TraditionalRed)
        Spacer(Modifier.height(8.dp))
        PanchangaPanel(data.panchanga)

        data.navamsa?.planets?.let { navPlanets ->
            Spacer(Modifier.height(32.dp))
            Text("நவாம்ச கட்டம் (Navamsa Chart)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TraditionalRed)
            Spacer(Modifier.height(8.dp))
            SouthIndianGridEnhanced(navPlanets, null, "Navamsa", birthData, "")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun PanchangaPanel(p: Panchanga) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, TraditionalRed.copy(0.2f))
    ) {
        Column(Modifier.padding(16.dp)) {
            PanchangaRow("வாரம்", p.vara?.name ?: "--")
            PanchangaRow("திதி", p.tithi?.name_ta ?: p.tithi?.name ?: "--")
            PanchangaRow("நட்சத்திரம்", naksTamil[p.nakshatra?.name] ?: p.nakshatra?.name ?: "--")
            PanchangaRow("யோகம்", yogaTamil[p.yoga?.name] ?: p.yoga?.name ?: "--")
            PanchangaRow("கரணம்", karanaTamil[p.karana?.name] ?: p.karana?.name ?: "--")
        }
    }
}

@Composable
fun PanchangaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(90.dp) // Fixed width for alignment
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SouthIndianGridEnhanced(planets: List<Planet>, ascDetails: HouseDetail?, title: String, birthData: JSONObject, starName: String) {
    val signNames = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val gridMap = listOf(11, 0, 1, 2, 10, -1, -1, 3, 9, -1, -1, 4, 8, 7, 6, 5)
    val ascSign = ascDetails?.signName ?: ""
    val ascIdx = if (ascSign.isNotEmpty()) signNames.indexOf(ascSign) else -1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(ParchmentBase, RoundedCornerShape(4.dp))
            .border(3.dp, TraditionalRed, RoundedCornerShape(4.dp))
    ) {
        // Decorative Borders for boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 4
            val cellH = h / 4

            // Vertical lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(TraditionalRed, Offset(i * cellW, 0f), Offset(i * cellW, cellH), strokeWidth = 1.dp.toPx())
                    drawLine(TraditionalRed, Offset(i * cellW, 3 * cellH), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(TraditionalRed, Offset(i * cellW, 0f), Offset(i * cellW, h), strokeWidth = 1.dp.toPx())
                }
            }

            // Horizontal lines
            for (i in 1..3) {
                if (i == 2) {
                    // Skip center 2x2
                    drawLine(TraditionalRed, Offset(0f, i * cellH), Offset(cellW, i * cellH), strokeWidth = 1.dp.toPx())
                    drawLine(TraditionalRed, Offset(3 * cellW, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                } else {
                    drawLine(TraditionalRed, Offset(0f, i * cellH), Offset(w, i * cellH), strokeWidth = 1.dp.toPx())
                }
            }

            // Central Area Decor (Pillar-like / Unified Center)
            val centralPadding = 2.dp.toPx()
            val rectPath = Path().apply {
                moveTo(cellW + centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, cellH + centralPadding)
                lineTo(3 * cellW - centralPadding, 3 * cellH - centralPadding)
                lineTo(cellW + centralPadding, 3 * cellH - centralPadding)
                close()
            }

            // Draw a subtle background for the center "pillar" area
            drawPath(
                path = rectPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF9C4).copy(alpha = 0.5f), Color(0xFFFBC02D).copy(alpha = 0.1f)))
            )

            // Central Border (Thicker)
            drawPath(rectPath, TraditionalRed, style = Stroke(width = 2.4.dp.toPx()))
        }

        // Contents
        Column(Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val pos = row * 4 + col
                        val signIdx = gridMap[pos]

                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (signIdx != -1) {
                                 val signEn = signNames[signIdx]
                                 val occupants = mutableListOf<Pair<String, String>>()
                                 if (signEn == ascSign) occupants.add("As" to (ascDetails?.degreeFormatted ?: ""))
                                 planets.filter { it.signName == signEn }.forEach { 
                                     occupants.add(it.name to (it.degreeFormatted ?: "")) 
                                 }

                                 Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    val fontSize = if (occupants.size > 3) 10.sp else 11.sp
                                    val lineHeight = if (occupants.size > 3) 11.sp else 13.sp

                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                         occupants.forEach { (pName, deg) ->
                                            val pAbbr = planetAbbrTamil[pName] ?: pName.take(3)
                                            val degreeShort = deg.take(5) // Just Deg:Min (e.g. 10:24)
                                            
                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = pAbbr,
                                                    fontSize = fontSize,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if(pName == "As") Color.Blue else Color.Black,
                                                    lineHeight = lineHeight
                                                )
                                                if (degreeShort.isNotEmpty()) {
                                                    Text(
                                                        text = " $degreeShort",
                                                        fontSize = (fontSize.value - 2).sp,
                                                        color = Color.DarkGray,
                                                        lineHeight = lineHeight
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                            } else if (pos == 5) {
                                // Central Info Display (Spans 2x2 area 5,6,9,10 but we use box 5 as anchor)
                                Box(modifier = Modifier.fillMaxSize().offset(x = 0.dp), contentAlignment = Alignment.Center) {
                                    // Spanning 2 cells
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay central text over the 2x2 hole
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dob = "${birthData.optInt("day")}-${getMonthName(birthData.optInt("month"))}-${birthData.optInt("year")}"
                val tob = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

                Text(dob, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(tob, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(2.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TraditionalRed)
                if (starName.isNotEmpty()) {
                    Text(starName, fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        }

    }
}

fun getMonthName(m: Int): String = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m]

@Composable
fun PlanetsTab(data: ChartData) {
    val planetsWithAsc = remember(data.planets, data.houses.ascendantDetails) {
        val list = mutableListOf<Planet>()
        val asc = data.houses.ascendantDetails
        list.add(Planet(
            name = "Ascendant",
            signName = asc.signName,
            signIndex = 0,
            house = 1,
            nakshatra = asc.nakshatra ?: "",
            nakshatraPada = asc.nakshatraPada,
            degreeFormatted = asc.degreeFormatted,
            signLord = asc.signLord,
            starLord = asc.starLord,
            subLord = asc.subLord,
            dignity = "--"
        ))
        list.addAll(data.planets)
        list
    }

    val tableBorderColor = Color(0xFF2E7D32).copy(alpha = 0.5f)

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Table Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tableBorderColor)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E7D32))
                    .padding(vertical = 1.dp), // Tiny padding for border effect
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerStyle = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )

                TableCell(text = "கிரகம்", weight = 1.2f, style = headerStyle)
                TableCell(text = "பாகை", weight = 1.8f, style = headerStyle)
                TableCell(text = "நட்சத்திரம்", weight = 2f, style = headerStyle)
                TableCell(text = "பாதம்", weight = 0.8f, style = headerStyle)
                TableCell(text = "ந அ", weight = 1.2f, style = headerStyle)
                TableCell(text = "நிலை", weight = 1f, style = headerStyle)
            }

            // Rows
            planetsWithAsc.forEachIndexed { index, planet ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color.White else Color(0xFFF1F8E9))
                        .border(0.5.dp, tableBorderColor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val baseStyle = MaterialTheme.typography.bodySmall.copy(
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Planet Name (Red)
                    val pAbbr = planetAbbrTamil[planet.name] ?: planet.name.take(3)
                    val pSuffix = buildString {
                        if (planet.isRetrograde) append(" (வ)")
                        if (planet.isCombust) append(" (அ)")
                    }
                    val planetDisplayName = pAbbr + pSuffix

                    TableCell(
                        text = planetDisplayName,
                        weight = 1.2f,
                        style = baseStyle.copy(color = Color.Red),
                        isFirst = true
                    )

                    // Degree (Blue) - Formatting 1:11:05
                    val degreeOnly = planet.degreeFormatted ?: "00:00:00"
                    TableCell(text = degreeOnly, weight = 1.8f, style = baseStyle.copy(color = Color(0xFF1565C0)))

                    // Nakshatra (Blue)
                    TableCell(text = naksTamil[planet.nakshatra] ?: planet.nakshatra, weight = 2f, style = baseStyle.copy(color = Color(0xFF1565C0)))

                    // Pada (Blue)
                    TableCell(text = "${planet.nakshatraPada}", weight = 0.8f, style = baseStyle.copy(color = Color(0xFF1565C0)))

                    // Star Lord (Blue)
                    TableCell(text = planetAbbrTamil[planet.starLord] ?: planet.starLord ?: "--", weight = 1.2f, style = baseStyle.copy(color = Color(0xFF1565C0)))

                    // Status (Blue)
                    TableCell(text = planet.dignity ?: "--", weight = 1f, style = baseStyle.copy(color = Color(0xFF1565C0)))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "* வ - வக்ரம் (Retrograde), அ - அஸ்தமனம் (Combust)",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            "* ந அ - நட்சத்திர அதிபதி",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    style: TextStyle,
    isFirst: Boolean = false
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(45.dp) // Fixed height for alignment
            .border(0.5.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = style,
            modifier = Modifier.padding(2.dp)
        )
    }
}

@Composable
fun PlanetDetailSub(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DashaListTab(mahadashas: List<DashaPeriod>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().background(TraditionalRed).padding(16.dp)) {
                Text("விம்ஷோத்தரி தசா புக்தி விபரங்கள்", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        items(mahadashas) { md ->
            DashaNodeInternal(md)
        }
    }
}

@Composable
fun DashaNodeInternal(period: DashaPeriod) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = !period.subPeriods.isNullOrEmpty()
    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val isCurrent = todayStr >= period.start.take(10) && todayStr <= period.end.take(10)

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrent) Color(0xFFFFF9C4) else Color.Transparent)
                .clickable(enabled = hasSub) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val levelIndent = (period.level - 1) * 20
            Spacer(Modifier.width(levelIndent.dp))

            // Icon/Prefix based on level
            val iconColor = when(period.level) {
                1 -> TraditionalRed
                2 -> Color(0xFF2E7D32)
                3 -> Color(0xFF1976D2)
                else -> Color.DarkGray
            }

            Box(Modifier.size(32.dp).background(iconColor.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Text(planetAbbrTamil[period.lord] ?: period.lord.take(2), color = iconColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "${planetTamil[period.lord] ?: period.lord} " + when(period.level) {
                        1 -> "மகா தசை"
                        2 -> "புக்தி"
                        3 -> "ஆந்தரம்"
                        4 -> "பிரத்யந்தரம்"
                        else -> "சிக்ஷ்ம"
                    },
                    fontWeight = if(period.level == 1) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if(period.level == 1) 16.sp else 14.sp
                )
                Text("${period.start.take(10).replace("-", ".")} - ${period.end.take(10).replace("-", ".")}", fontSize = 11.sp, color = Color.Gray)
            }

            if (hasSub) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        if (expanded && hasSub) {
            period.subPeriods?.forEach { child ->
                DashaNodeInternal(child)
            }
            Divider(Modifier.padding(start = ((period.level) * 20).dp), color = Color.Gray.copy(0.1f))
        }
        if (period.level == 1) {
            Divider(color = Color.LightGray.copy(0.4f))
        }
    }
}

private suspend fun fetchFullChart(birthData: JSONObject): ChartData? = withContext(Dispatchers.IO) {
    try {
        val payload = com.google.gson.JsonObject().apply {
            addProperty("date", String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day")))
            addProperty("time", String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute")))
            addProperty("lat", birthData.optDouble("latitude"))
            addProperty("lng", birthData.optDouble("longitude"))
            addProperty("timezone", birthData.optDouble("timezone", 5.5))
        }

        val response = com.astro5star.app.data.api.ApiClient.api.getRasiEngBirthChart(payload)
        if (response.isSuccessful && response.body() != null) {
            val chartResponse = Gson().fromJson(response.body().toString(), ChartResponse::class.java)
            if (chartResponse.success) return@withContext chartResponse.data
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
