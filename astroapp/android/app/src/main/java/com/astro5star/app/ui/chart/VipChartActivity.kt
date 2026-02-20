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
    "Ketu" to "கேது", "Ascendant" to "லக்னம்"
)

val planetAbbrTamil = mapOf(
    "Sun" to "சூ", "Moon" to "சந்", "Mars" to "செவ்", "Mercury" to "பு",
    "Jupiter" to "குரு", "Venus" to "சுக்", "Saturn" to "சனி", "Rahu" to "ரா",
    "Ketu" to "கே", "Ascendant" to "ல", "As" to "ல"
)

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
    val subLord: String? = null
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

data class PanchangaValue(val name: String)
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val birthDataStr = intent.getStringExtra("birthData") ?: "{}"
        val birthData = JSONObject(birthDataStr)

        setContent {
            CosmicAppTheme {
                VipChartScreen(birthData) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipChartScreen(birthData: JSONObject, onBack: () -> Unit) {
    var chartState by remember { mutableStateOf<ChartData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = fetchFullChart(birthData)
                chartState = result
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
                        Text(birthData.optString("name", "User"), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                        0 -> ChartsTab(chartState!!, birthData)
                        1 -> PlanetsTab(chartState!!)
                        2 -> DashaListTab(chartState!!.dasha)
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
        SouthIndianGridEnhanced(data.planets, data.houses.ascendantDetails.signName, "Rasi", birthData, data.panchanga.nakshatra?.name ?: "")

        Spacer(Modifier.height(32.dp))

        Text("நவாம்ச கட்டம் (Navamsa - D9)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TraditionalRed)
        Spacer(Modifier.height(8.dp))
        SouthIndianGridEnhanced(data.navamsa?.planets ?: emptyList(), "", "Navamsa", birthData, "")

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SouthIndianGridEnhanced(planets: List<Planet>, ascSign: String, title: String, birthData: JSONObject, starName: String) {
    val signNames = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val gridMap = listOf(11, 0, 1, 2, 10, -1, -1, 3, 9, -1, -1, 4, 8, 7, 6, 5)
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
                                val occupants = mutableListOf<String>()
                                if (signEn == ascSign) occupants.add("As")
                                planets.filter { it.signName == signEn }.forEach { occupants.add(it.name) }

                                 Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    val fontSize = if (occupants.size > 3) 10.sp else 11.sp
                                    val lineHeight = if (occupants.size > 3) 11.sp else 13.sp

                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        occupants.forEach { pName ->
                                            Text(
                                                text = planetAbbrTamil[pName] ?: pName.take(3),
                                                fontSize = fontSize,
                                                fontWeight = FontWeight.Bold,
                                                color = if(pName == "As") Color.Blue else Color.Black,
                                                lineHeight = lineHeight
                                            )
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
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("நவகிரக பாதசாரம் (Navagraha Pathasaram)", fontWeight = FontWeight.Bold, color = TraditionalRed, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
        }

        items(data.planets) { planet ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ParchmentBase),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TraditionalRed.copy(0.2f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = TraditionalRed, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(planetAbbrTamil[planet.name] ?: planet.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(planetTamil[planet.name] ?: planet.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${signTamil[planet.signName] ?: planet.signName} - ${planet.degreeFormatted ?: ""}", fontSize = 12.sp, color = Color.DarkGray)
                        }
                        Text("${planet.house}-ம் வீடு", fontWeight = FontWeight.Bold, color = TraditionalRed)
                    }
                    Divider(Modifier.padding(vertical = 12.dp), color = TraditionalRed.copy(0.1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PlanetDetailSub("நட்சத்திரம்", "${planet.nakshatra} - ${planet.nakshatraPada}")
                        PlanetDetailSub("நட்சத்திர அதிபதி", planet.starLord ?: "N/A")
                        PlanetDetailSub("உப அதிபதி", planet.subLord ?: "N/A")
                    }
                }
            }
        }
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
