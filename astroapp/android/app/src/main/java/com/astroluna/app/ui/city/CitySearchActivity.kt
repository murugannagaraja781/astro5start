package com.astroluna.app.ui.city

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.astroluna.app.data.remote.nominatim.NominatimResult

class CitySearchActivity : ComponentActivity() {
    private val viewModel: CityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CitySearchScreen(viewModel) { city ->
                        val intent = Intent().apply {
                            putExtra("name", city.address?.getCityName() ?: city.display_name)
                            putExtra("lat", city.lat.toDoubleOrNull() ?: 0.0)
                            putExtra("lon", city.lon.toDoubleOrNull() ?: 0.0)
                            putExtra("display_name", city.display_name)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun CitySearchScreen(viewModel: CityViewModel, onCitySelected: (NominatimResult) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            label = { Text("Search City (India)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(uiState.results) { city ->
                    CityItem(city, onCitySelected)
                    Divider() // Using Divider for compatibility
                }
            }
        }
    }
}

@Composable
fun CityItem(city: NominatimResult, onClick: (NominatimResult) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(city) }
            .padding(12.dp)
    ) {
        Text(text = city.display_name, style = MaterialTheme.typography.bodyLarge)
        Text(text = city.address?.state ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
