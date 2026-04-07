package com.astro5star.app.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.ui.theme.PeacockGreen
import com.astro5star.app.utils.Localization
import org.json.JSONObject
import kotlinx.coroutines.launch

class AstrologerRegistrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SocketManager.init()
        setContent {
            CosmicAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F9FA)
                ) {
                    AstrologerRegistrationScreen(
                        onBack = { finish() },
                        onSubmit = { data ->
                            submitRegistration(data)
                        }
                    )
                }
            }
        }
    }

    private fun submitRegistration(data: JSONObject) {
        SocketManager.submitAstroRegistration(data) { response ->
            lifecycleScope.launch {
                if (response != null && response.optBoolean("ok")) {
                    Toast.makeText(this@AstrologerRegistrationActivity, "Registration Submitted Successfully! Admin will contact you.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val error = response?.optString("error") ?: "Registration failed"
                    Toast.makeText(this@AstrologerRegistrationActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerRegistrationScreen(onBack: () -> Unit, onSubmit: (JSONObject) -> Unit) {
    var realName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var dob by remember { mutableStateOf("") }
    var tob by remember { mutableStateOf("") }
    var pob by remember { mutableStateOf("") }
    var cellNumber1 by remember { mutableStateOf("") }
    var cellNumber2 by remember { mutableStateOf("") }
    var whatsAppNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var aadharNumber by remember { mutableStateOf("") }
    var panNumber by remember { mutableStateOf("") }
    var astrologyExperience by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf("") }
    var upiName by remember { mutableStateOf("") }
    var upiNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }


    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    // Picker States
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    // Default to Tamil as per app pattern (toggle could be added if needed)
    val isTamil = true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.get("join_as_astrologer", isTamil), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { padding ->
        if (showSuccess) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 64.sp)
                    Text(
                        if(isTamil) "பதிவு வெற்றிகரமாக சமர்ப்பிக்கப்பட்டது!" else "Registration Submitted!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PeacockGreen,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        if(isTamil) "உங்கள் விண்ணப்பம் பரிசீலனைக்கு அனுப்பப்பட்டுள்ளது. விரைவில் உங்களை தொடர்பு கொள்வோம்."
                        else "Your application is under review. We will contact you soon.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Localization.get("ok", isTamil))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if(isTamil) "எங்கள் நிபுணர்கள் சமூகத்தில் இணைந்து மக்களுக்கு உதவவும்."
                    else "Join our community of experts and help people find their destiny.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                // Personal Details
                SectionHeader(Localization.get("personal_details", isTamil))

                OutlinedTextField(
                    value = realName,
                    onValueChange = { realName = it },
                    label = { Text(Localization.get("real_name", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(Localization.get("display_name", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Localization.get("gender", isTamil), modifier = Modifier.padding(end = 16.dp))
                    listOf("Male", "Female", "Other").forEach { g ->
                        val translatedG = when(g) {
                            "Male" -> Localization.get("male", isTamil)
                            "Female" -> Localization.get("female", isTamil)
                            else -> Localization.get("other", isTamil)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            RadioButton(selected = gender == g, onClick = { gender = g })
                            Text(translatedG, fontSize = 12.sp)
                        }
                    }
                }

                // Birth Details
                SectionHeader(Localization.get("birth_details", isTamil))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // DOB Field
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text(Localization.get("dob", isTamil)) },
                        placeholder = { Text("DD/MM/YYYY") },
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PeacockGreen,
                            disabledLabelColor = PeacockGreen,
                            disabledPlaceholderColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // TOB Field
                    OutlinedTextField(
                        value = tob,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text(Localization.get("tob", isTamil)) },
                        placeholder = { Text("HH:MM AM/PM") },
                        modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PeacockGreen,
                            disabledLabelColor = PeacockGreen,
                            disabledPlaceholderColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Date Picker Dialog
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = java.util.Date(millis)
                                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                    dob = sdf.format(date)
                                }
                                showDatePicker = false
                            }) {
                                Text("OK", color = PeacockGreen)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel")
                            }
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
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                                cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                tob = sdf.format(cal.time)
                                showTimePicker = false
                            }) {
                                Text("OK", color = PeacockGreen)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel")
                            }
                        },
                        text = {
                            TimePicker(state = timePickerState)
                        }
                    )
                }


                OutlinedTextField(
                    value = pob,
                    onValueChange = { pob = it },
                    label = { Text(Localization.get("pob", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Contact Info
                SectionHeader(Localization.get("contact_info", isTamil))

                OutlinedTextField(
                    value = cellNumber1,
                    onValueChange = { cellNumber1 = it },
                    label = { Text(Localization.get("cell_no_1", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = cellNumber2,
                    onValueChange = { cellNumber2 = it },
                    label = { Text(Localization.get("cell_no_2", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = whatsAppNumber,
                    onValueChange = { whatsAppNumber = it },
                    label = { Text(Localization.get("whatsapp_no", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(Localization.get("email_address", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                )


                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(Localization.get("address", isTamil)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                // Professional Info
                SectionHeader(Localization.get("professional_info", isTamil))

                OutlinedTextField(
                    value = aadharNumber,
                    onValueChange = { aadharNumber = it },
                    label = { Text(Localization.get("aadhar_no", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = panNumber,
                    onValueChange = { panNumber = it },
                    label = { Text(Localization.get("pan_no", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = astrologyExperience,
                    onValueChange = { astrologyExperience = it },
                    label = { Text(Localization.get("experience_years", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                OutlinedTextField(
                    value = profession,
                    onValueChange = { profession = it },
                    label = { Text(Localization.get("profession", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Payment Details
                SectionHeader(Localization.get("payment_details", isTamil))

                OutlinedTextField(
                    value = bankDetails,
                    onValueChange = { bankDetails = it },
                    label = { Text(Localization.get("bank_details", isTamil)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = upiName,
                    onValueChange = { upiName = it },
                    label = { Text(Localization.get("upi_name", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = upiNumber,
                    onValueChange = { upiNumber = it },
                    label = { Text(Localization.get("upi_number", isTamil)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (realName.isNotEmpty() && cellNumber1.isNotEmpty()) {
                            val data = JSONObject().apply {
                                put("realName", realName)
                                put("displayName", displayName)
                                put("gender", gender)
                                put("dob", dob)
                                put("tob", tob)
                                put("pob", pob)
                                put("cellNumber1", cellNumber1)
                                put("cellNumber2", cellNumber2)
                                put("whatsAppNumber", whatsAppNumber)
                                put("email", email)

                                put("address", address)
                                put("aadharNumber", aadharNumber)
                                put("panNumber", panNumber)
                                put("astrologyExperience", astrologyExperience)
                                put("profession", profession)
                                put("bankDetails", bankDetails)
                                put("upiId", upiName)
                                put("upiNumber", upiNumber)
                                put("role", "astrologer")
                            }
                            isLoading = true
                            onSubmit(data)
                        } else {
                            // Toast is handled in activity via callback mostly, but for simplicity:
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(Localization.get("submit_registration", isTamil), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PeacockGreen
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 1.dp, color = PeacockGreen.copy(alpha = 0.2f))
    }
}
