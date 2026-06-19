package com.astro5star.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Parses dynamic birth data into a 12-house map.
 * Expects keys like "Aries", "Mesham", "1", or similar standard names.
 */
fun parseRasiChart(rasiData: Map<String, List<String>>): Map<Int, List<String>> {
    val mappedChart = mutableMapOf<Int, List<String>>()
    
    val signMap = mapOf(
        1 to listOf("Aries", "Mesham", "1"),
        2 to listOf("Taurus", "Rishabam", "Vrishabha", "2"),
        3 to listOf("Gemini", "Midhunam", "Mithuna", "3"),
        4 to listOf("Cancer", "Kadagam", "Karka", "4"),
        5 to listOf("Leo", "Simmam", "Simha", "5"),
        6 to listOf("Virgo", "Kanni", "Kanya", "6"),
        7 to listOf("Libra", "Thulaam", "Tula", "7"),
        8 to listOf("Scorpio", "Viruchigam", "Vrishchika", "8"),
        9 to listOf("Sagittarius", "Dhanusu", "Dhanu", "9"),
        10 to listOf("Capricorn", "Magaram", "Makara", "10"),
        11 to listOf("Aquarius", "Kumbam", "Kumbha", "11"),
        12 to listOf("Pisces", "Meenam", "Meena", "12")
    )

    for ((houseNum, aliases) in signMap) {
        val planets = mutableListOf<String>()
        for (alias in aliases) {
            rasiData[alias]?.let { planets.addAll(it) }
            rasiData[alias.lowercase()]?.let { planets.addAll(it) }
            rasiData[alias.uppercase()]?.let { planets.addAll(it) }
        }
        mappedChart[houseNum] = planets.distinct()
    }
    
    return mappedChart
}

@Composable
fun RasiKattam(
    rasiData: Map<String, List<String>>,
    title: String = "Rasi Chart"
) {
    val parsedChart = parseRasiChart(rasiData)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.White)
            .border(1.dp, Color.Black)
    ) {
        // Row 1: 12 (Pisces), 1 (Aries), 2 (Taurus), 3 (Gemini)
        Row(modifier = Modifier.fillMaxWidth()) {
            RasiBox(weight = 1f, planets = parsedChart[12], houseName = "Meenam")
            RasiBox(weight = 1f, planets = parsedChart[1], houseName = "Mesham")
            RasiBox(weight = 1f, planets = parsedChart[2], houseName = "Rishabam")
            RasiBox(weight = 1f, planets = parsedChart[3], houseName = "Midhunam")
        }
        // Row 2: 11 (Aquarius) | Center Empty | 4 (Cancer)
        Row(modifier = Modifier.fillMaxWidth()) {
            RasiBox(weight = 1f, planets = parsedChart[11], houseName = "Kumbam")
            CenterBox(weight = 2f, title = title)
            RasiBox(weight = 1f, planets = parsedChart[4], houseName = "Kadagam")
        }
        // Row 3: 10 (Capricorn) | Center Empty | 5 (Leo)
        Row(modifier = Modifier.fillMaxWidth()) {
            RasiBox(weight = 1f, planets = parsedChart[10], houseName = "Magaram")
            EmptyBox(weight = 2f)
            RasiBox(weight = 1f, planets = parsedChart[5], houseName = "Simmam")
        }
        // Row 4: 9 (Sagittarius), 8 (Scorpio), 7 (Libra), 6 (Virgo)
        Row(modifier = Modifier.fillMaxWidth()) {
            RasiBox(weight = 1f, planets = parsedChart[9], houseName = "Dhanusu")
            RasiBox(weight = 1f, planets = parsedChart[8], houseName = "Viruchigam")
            RasiBox(weight = 1f, planets = parsedChart[7], houseName = "Thulaam")
            RasiBox(weight = 1f, planets = parsedChart[6], houseName = "Kanni")
        }
    }
}

@Composable
private fun RowScope.RasiBox(weight: Float, planets: List<String>?, houseName: String) {
    Box(
        modifier = Modifier
            .weight(weight)
            .aspectRatio(1f)
            .border(0.5.dp, Color.Black)
            .background(Color(0xFFFFFDF0))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = houseName.take(3).uppercase(),
                fontSize = 8.sp,
                color = Color.LightGray,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (!planets.isNullOrEmpty()) {
                Text(
                    text = planets.joinToString("\n"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun RowScope.CenterBox(weight: Float, title: String) {
    Box(
        modifier = Modifier
            .weight(weight)
            .aspectRatio(2f)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD84315), // Deep Orange
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.EmptyBox(weight: Float) {
    Box(
        modifier = Modifier
            .weight(weight)
            .aspectRatio(2f)
            .background(Color.White)
    )
}
