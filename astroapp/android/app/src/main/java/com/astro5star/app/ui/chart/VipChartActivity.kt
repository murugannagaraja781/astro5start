package com.astro5star.app.ui.chart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.gson.Gson

// --- Data Models ---
data class ChartResponse(val success: Boolean, val data: ChartData)
data class ChartData(
    val planets: List<Planet>,
    val houses: HouseData,
    val panchanga: Panchanga,
    val dasha: Dasha,
    val transits: List<Transit>,
    val navamsa: NavamsaData? = null
)
data class NavamsaData(val planets: Map<String, String>)
data class Planet(
    val name: String,
    val signName: String,
    val house: Int,
    val nakshatra: String,
    val nakshatraPada: Int,
    val degreeFormatted: String,
    val signLord: String,
    val starLord: String,
    val subLord: String
)
data class HouseData(val ascendantDetails: AscendantDetails)
data class AscendantDetails(val signName: String, val degreeFormatted: String, val signLord: String)
data class Panchanga(val tithi: String, val nakshatra: String, val yoga: String, val karana: String)
data class Dasha(
    val mahadashaName: String,
    val bhuktiName: String,
    val antaramName: String,
    val remainingYearsInCurrentDasha: Double,
    val endsAt: String
)
data class Transit(val name: String, val signName: String, val isRetrograde: Boolean)

// --- Theme Colors ---
val NeumorphicBg = Color(0xFFF0F2F5)
val NeumorphicGreen = Color(0xFF4CAF50)
val DarkGreen = Color(0xFF2E7D32)
val NeumorphicWhite = Color.White

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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = fetchFullChart(birthData)
                if (result != null) {
                    chartState = result
                } else {
                    errorMessage = "Unable to fetch astrological data."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Celestial Analysis", color = DarkGreen, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeumorphicWhite)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NeumorphicBg)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = NeumorphicGreen)
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else if (chartState != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { AscendantCard(chartState!!.houses.ascendantDetails) }
                    item { PanchangaCard(chartState!!.panchanga) }

                    item { SectionTitle("Rasi Chart") }
                    item { SouthIndianGrid(chartState!!.planets, chartState!!.houses.ascendantDetails.signName) }

                    if (chartState!!.navamsa != null) {
                        item { SectionTitle("Navamsa Chart") }
                        item { NavamsaGrid(chartState!!.navamsa!!) }
                    }

                    item { SectionTitle("Planetary Positions") }
                    items(chartState!!.planets) { planet -> PlanetCard(planet) }
                    item { SectionTitle("Current Dasha Period") }
                    item { DashaCard(chartState!!.dasha) }
                    item { SectionTitle("Planetary Transits (Gocharam)") }
                    items(chartState!!.transits) { transit -> TransitCard(transit) }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = DarkGreen,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun AscendantCard(asc: AscendantDetails) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("🌟 ASCENDANT (LAGNA)", color = DarkGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(asc.signName, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(12.dp))
                Text(asc.degreeFormatted, color = Color.Gray, fontSize = 16.sp)
            }
            Text("Sign Lord: ${asc.signLord}", color = NeumorphicGreen, fontSize = 14.sp)
        }
    }
}
@Composable
fun PlanetCard(p: Planet) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(p.name, color = DarkGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(p.degreeFormatted, color = Color.Gray, fontSize = 14.sp)
            }
            Text("${p.signName} (House ${p.house})", color = Color.DarkGray, fontSize = 14.sp)
            Text("Nakshatra: ${p.nakshatra} (Pada ${p.nakshatraPada})", color = Color.Gray, fontSize = 12.sp)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = NeumorphicBg)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LordsItem("Sign L", p.signLord)
                LordsItem("Star L", p.starLord)
                LordsItem("Sub L", p.subLord)
            }
        }
    }
}

@Composable
fun LordsItem(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = DarkGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DashaCard(dasha: Dasha) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("⏳ CURRENT DASHA", color = DarkGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text("${dasha.mahadashaName} - ${dasha.bhuktiName}", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Antaram: ${dasha.antaramName}", color = Color.DarkGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = 0.5f,
                modifier = Modifier.fillMaxWidth().height(8.dp).shadow(2.dp, RoundedCornerShape(4.dp)),
                color = NeumorphicGreen,
                trackColor = NeumorphicBg
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ends: ${dasha.endsAt.take(10)} (${String.format("%.2f", dasha.remainingYearsInCurrentDasha)} Years left)", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun TransitCard(t: Transit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(t.name, color = Color.Black, fontWeight = FontWeight.Bold)
                if (t.isRetrograde) Text("Retrograde", color = Color(0xFFFF5252), fontSize = 10.sp)
            }
            Text(t.signName, color = DarkGreen, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PanchangaCard(p: Panchanga) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PanchangaItem("Tithi", p.tithi)
            PanchangaItem("Nakshatra", p.nakshatra)
            PanchangaItem("Yoga", p.yoga)
            PanchangaItem("Karana", p.karana)
        }
    }
}

@Composable
fun PanchangaItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = DarkGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SouthIndianGrid(planets: List<Planet>, ascSign: String) {
    val signMap = listOf(
        "Pisces", "Aries", "Taurus", "Gemini",
        "Aquarius", "", "", "Cancer",
        "Capricorn", "", "", "Leo",
        "Sagittarius", "Scorpio", "Libra", "Virgo"
    )

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(modifier = Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val sign = signMap[index]

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, NeumorphicBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sign.isNotEmpty()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(sign.take(3), fontSize = 10.sp, color = DarkGreen.copy(alpha = 0.5f))
                                    val inSign = mutableListOf<String>()
                                    if (sign == ascSign) inSign.add("Asc")
                                    planets.filter { it.signName == sign }.forEach { inSign.add(it.name.take(2)) }

                                    inSign.chunked(2).forEach { pair ->
                                        Text(pair.joinToString(" "), fontSize = 12.sp, color = DarkGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (index == 5) {
                                Text("🕉️", fontSize = 24.sp, color = NeumorphicGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavamsaGrid(navamsa: NavamsaData) {
    val signMap = listOf(
        "Pisces", "Aries", "Taurus", "Gemini",
        "Aquarius", "", "", "Cancer",
        "Capricorn", "", "", "Leo",
        "Sagittarius", "Scorpio", "Libra", "Virgo"
    )

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicWhite)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..3) {
                Row(modifier = Modifier.weight(1f)) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val sign = signMap[index]

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, NeumorphicBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sign.isNotEmpty()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(sign.take(3), fontSize = 10.sp, color = DarkGreen.copy(alpha = 0.5f))
                                    val inSign = navamsa.planets.filter { it.value == sign }.map { it.key.take(2) }

                                    inSign.chunked(2).forEach { pair ->
                                        Text(pair.joinToString(" "), fontSize = 12.sp, color = DarkGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (index == 6) {
                                Text("Navamsa", fontSize = 12.sp, color = NeumorphicGreen.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Network Helper ---
private suspend fun fetchFullChart(birthData: JSONObject): ChartData? = withContext(Dispatchers.IO) {
    try {
        val dateStr = String.format("%04d-%02d-%02d", birthData.optInt("year"), birthData.optInt("month"), birthData.optInt("day"))
        val timeStr = String.format("%02d:%02d", birthData.optInt("hour"), birthData.optInt("minute"))

        val payload = com.google.gson.JsonObject().apply {
            addProperty("date", dateStr)
            addProperty("time", timeStr)
            addProperty("lat", birthData.optDouble("latitude"))
            addProperty("lng", birthData.optDouble("longitude"))
            addProperty("timezone", birthData.optDouble("timezone", 5.5))
        }

        val api = com.astro5star.app.data.api.ApiClient.api
        val response = api.getRasiEngBirthChart(payload)

        if (response.isSuccessful && response.body() != null) {
            val gson = Gson()
            val chartResponse = gson.fromJson(response.body().toString(), ChartResponse::class.java)
            if (chartResponse.success) {
                return@withContext chartResponse.data
            }
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
