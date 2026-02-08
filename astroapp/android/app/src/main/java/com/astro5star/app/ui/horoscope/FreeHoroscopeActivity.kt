package com.astro5star.app.ui.horoscope

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import java.util.*

class FreeHoroscopeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CosmicAppTheme {
                FreeHoroscopeScreen(
                    onBackClick = { finish() },
                    onGenerateChart = { data -> generateRasiChart(data) }
                )
            }
        }
    }

    private fun generateRasiChart(data: BirthData) {
        lifecycleScope.launch {
            try {
                // Prepare payload
                val payload = JsonObject().apply {
                    addProperty("name", data.name)
                    addProperty("dob", data.dob)
                    addProperty("time", data.time)
                    addProperty("country", data.country)
                    addProperty("state", data.state)
                    addProperty("city", data.city)
                    addProperty("birthPlace", data.birthPlace)
                    addProperty("timezone", data.timezone)
                    addProperty("latitude", data.latitude)
                    addProperty("longitude", data.longitude)
                }

                // Call API to generate chart
                val response = ApiClient.api.generateRasiChart(payload)

                if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                    val chartData = response.body()?.get("chart")?.asJsonObject

                    runOnUiThread {
                        Toast.makeText(this@FreeHoroscopeActivity, "Chart Generated Successfully!", Toast.LENGTH_SHORT).show()
                        // TODO: Navigate to chart display screen with chartData
                        finish()
                    }
                } else {
                    val error = response.body()?.get("error")?.asString ?: "Failed to generate chart"
                    runOnUiThread {
                        Toast.makeText(this@FreeHoroscopeActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@FreeHoroscopeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

data class BirthData(
    val name: String,
    val dob: String,
    val time: String,
    val country: String,
    val state: String,
    val city: String,
    val birthPlace: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeHoroscopeScreen(
    onBackClick: () -> Unit,
    onGenerateChart: (BirthData) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var birthPlace by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Green Claymorphism Theme
    val clayShape = RoundedCornerShape(20.dp)
    val clayGreen = Color(0xFF2ECC71)
    val clayLightGreen = Color(0xFF58D68D)
    val clayDarkGreen = Color(0xFF27AE60)
    val clayBg = Color(0xFFE8F5E9)
    val clayWhite = Color(0xFFF1F8F4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F5E9),
                        Color(0xFFC8E6C9),
                        Color(0xFFA5D6A7)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Free Horoscope",
                            color = clayDarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = clayDarkGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = clayShape,
                            ambientColor = clayGreen.copy(alpha = 0.2f),
                            spotColor = clayGreen.copy(alpha = 0.2f)
                        )
                        .clip(clayShape)
                        .background(clayWhite)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = clayShape
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter Your Birth Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = clayDarkGreen
                        )
                        Text(
                            text = "Fill in all the details to generate your personalized Rasi chart",
                            style = MaterialTheme.typography.bodyMedium,
                            color = clayDarkGreen.copy(alpha = 0.7f)
                        )
                    }
                }

                // Form Fields Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = clayShape,
                            ambientColor = clayGreen.copy(alpha = 0.2f),
                            spotColor = clayGreen.copy(alpha = 0.2f)
                        )
                        .clip(clayShape)
                        .background(clayWhite)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = clayShape
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Date of Birth
                        OutlinedTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = { Text("Date of Birth (DD/MM/YYYY)", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("01/01/1990", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Time of Birth
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Time of Birth (HH:MM)", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("14:30", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Country
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Country", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("India", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // State
                        OutlinedTextField(
                            value = state,
                            onValueChange = { state = it },
                            label = { Text("State", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Tamil Nadu", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // City
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Chennai", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Birth Place
                        OutlinedTextField(
                            value = birthPlace,
                            onValueChange = { birthPlace = it },
                            label = { Text("Birth Place", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Hospital/Home Address", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Timezone
                        OutlinedTextField(
                            value = timezone,
                            onValueChange = { timezone = it },
                            label = { Text("Timezone", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Asia/Kolkata", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Latitude
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text("Latitude", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("13.0827", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )

                        // Longitude
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text("Longitude", color = clayDarkGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("80.2707", color = clayDarkGreen.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = clayGreen,
                                unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                focusedContainerColor = clayBg,
                                unfocusedContainerColor = clayBg,
                                focusedTextColor = clayDarkGreen,
                                unfocusedTextColor = clayDarkGreen,
                                cursorColor = clayGreen
                            )
                        )
                    }
                }

                // Generate Button
                Button(
                    onClick = {
                        if (validateInputs(name, dob, time, country, state, city, birthPlace, timezone, latitude, longitude)) {
                            isLoading = true
                            val birthData = BirthData(
                                name = name,
                                dob = dob,
                                time = time,
                                country = country,
                                state = state,
                                city = city,
                                birthPlace = birthPlace,
                                timezone = timezone,
                                latitude = latitude.toDoubleOrNull() ?: 0.0,
                                longitude = longitude.toDoubleOrNull() ?: 0.0
                            )
                            onGenerateChart(birthData)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = clayGreen.copy(alpha = 0.4f),
                            spotColor = clayGreen.copy(alpha = 0.4f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = clayGreen,
                        contentColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            "Generate Rasi Chart",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun validateInputs(
    name: String,
    dob: String,
    time: String,
    country: String,
    state: String,
    city: String,
    birthPlace: String,
    timezone: String,
    latitude: String,
    longitude: String
): Boolean {
    return name.isNotBlank() &&
            dob.isNotBlank() &&
            time.isNotBlank() &&
            country.isNotBlank() &&
            state.isNotBlank() &&
            city.isNotBlank() &&
            birthPlace.isNotBlank() &&
            timezone.isNotBlank() &&
            latitude.toDoubleOrNull() != null &&
            longitude.toDoubleOrNull() != null
}
