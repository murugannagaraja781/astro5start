# Astro5Star - Chat Feature Documentation

இந்த ஆவணத்தில் Astro5Star ஆப்பில் உள்ள Chat வசதி எப்படி வேலை செய்கிறது, அதன் டிசைன் மற்றும் முக்கிய அம்சங்கள் (Features) பற்றிய முழு விவரங்களும் விளக்கப்பட்டுள்ளன.

---

## 1. Core Architecture (கட்டமைப்பு)

Chat வசதியானது **Real-time Communication** (நிகழ்நேர தொடர்பு) அடிப்படையில் இயங்குகிறது. 
* **Frontend (Android):** Jetpack Compose மூலம் டிசைன் செய்யப்பட்டுள்ளது (`ChatActivity.kt`).
* **Backend Connection:** `Socket.io` பயன்படுத்தப்பட்டு சர்வரோடு (Node.js/SocketHandler) இணைக்கப்பட்டுள்ளது. இதன் மூலம் மெசேஜ்கள் தாமதமின்றி உடனுக்குடன் அனுப்பவும் பெறவும் முடிகிறது.

---

## 2. Key Features (முக்கிய அம்சங்கள்)

### A. Role-Based UI (பயனர் அடிப்படை)
ஆப்பில் இரண்டு விதமான பயனர்கள் உள்ளனர். இருவருக்கும் ஏற்றவாறு UI மாறும்:
* **Astrologer (ஜோதிடர்):** வாடிக்கையாளரின் ஜாதகத்தை (Rasi Chart) பார்க்கும் வசதி, செஷன் முடிந்தவுடன் எவ்வளவு சம்பாதித்தோம் (Earned) என்ற விவரம் காட்டப்படும்.
* **Client (வாடிக்கையாளர்):** ஜோதிடரிடம் கேள்வி கேட்பது, பேலன்ஸ் குறைவதை கண்காணிப்பது மற்றும் செஷன் முடிந்தவுடன் எவ்வளவு பணம் கழிக்கப்பட்டது (Deducted) என்ற விவரம் காட்டப்படும்.

### B. Text & Reply System
* **Real-time Typing:** வாடிக்கையாளர் அல்லது ஜோதிடர் டைப் செய்யும்போது `Typing...` என்று எதிர்முனையில் காண்பிக்கும்.
* **Swipe to Reply:** மெசேஜை வலதுபுறமாக ஸ்வைப் (Swipe) செய்தால், குறிப்பிட்ட மெசேஜுக்கு நேரடியாக Reply செய்ய முடியும் (WhatsApp போன்ற வசதி).
* **Smart Send Button:** டெக்ஸ்ட் டைப் செய்தால் மட்டுமே Send பட்டன் வேலை செய்யும் படி வடிவமைக்கப்பட்டுள்ளது. 

### C. Voice & Media Sharing (வாய்ஸ் மற்றும் ஃபைல் பகிர்தல்)
* **Voice Messaging & Audio Playback (வாய்ஸ் மெசேஜ் மற்றும் ப்ளேபேக்):** 
  * **UI Design:** WhatsApp-ல் உள்ளதை போன்றே UI டிசைன் செய்யப்பட்டுள்ளது. Play/Pause பட்டன் மற்றும் ஆடியோ எவ்வளவு நேரம் ஓடியுள்ளது என்பதைக் காட்டும் Waveform/Progress Bar (`LinearProgressIndicator`) உள்ளது.
  * **Playback Logic:** ஆடியோவை ப்ளே செய்ய `ChatAudioPlayer.kt` என்ற பிரத்யேக கிளாஸ் பயன்படுத்தப்படுகிறது. இது `MediaPlayer`-ஐ அடிப்படையாகக் கொண்டு செயல்படுகிறது. ஒரு வாய்ஸ் மெசேஜில் Play பட்டனை அழுத்தினால், அந்த URL-ஐ MediaPlayer ஸ்ட்ரீம் (Stream) செய்யும். வேறு ஒரு ஆடியோவை ப்ளே செய்தால், முந்தைய ஆடியோ தானாகவே நிறுத்தப்படும் (Pause).
  * **Constraint:** 2 செகண்டுகளுக்குக் குறைவான வாய்ஸ் ரெக்கார்ட் செய்யப்பட்டால் ஆட்டோமேட்டிக்காக ரத்து செய்யப்படும் (Empty audio-ஐ தடுக்க).
* **Image & File Sharing (இமேஜ் மற்றும் ஃபைல் பகிர்தல்):** 
  * **Image View Logic:** சாட்டில் வரும் படங்களை (Images) லோடு செய்ய `Coil` (SubcomposeAsyncImage) பயன்படுத்தப்படுகிறது. இதனால் படங்கள் வேகமாக லோடு ஆகும். 
  * **Full-Screen View:** சாட்டில் உள்ள இமேஜை க்ளிக் செய்தால், அது `FullScreenImageActivity`-க்கு URL உடன் அனுப்பப்பட்டு முழுத்திரையில் (Full-screen) காண்பிக்கப்படும். அங்கிருந்து படத்தை டவுன்லோட் செய்யவும் வசதி உள்ளது.
  * **File View:** PDF அல்லது மற்ற ஃபைல்களை அனுப்பலாம். அதை க்ளிக் செய்தால் போனில் உள்ள பிரவுசர் (Browser) அல்லது பொருத்தமான ஆப் மூலம் ஃபைல் ஓபன் ஆகும்.

### D. Chat Controls (கட்டுப்பாடுகள்)
* **Status Updates:** ஜோதிடர் "ஜாதகத்தை பகுப்பாய்வு செய்கிறேன்" (Analysing chart) என்று ஒரு அப்டேட்டை அனுப்பினால், அது வாடிக்கையாளருக்கு தனி நிறத்தில் Highlight ஆகிக் காண்பிக்கும்.
* **Copy & Share:** மெசேஜை லாங் பிரஸ் (Long Press) செய்தால் Copy Text, Reply மற்றும் Share செய்யும் ஆப்ஷன்கள் வரும்.

---

## 3. Session & Timer Management (செஷன் நிர்வாகம்)

* **Timer Display:** Chat நடக்கும் போது மேலே (Top Bar) எவ்வளவு நேரம் ஆகியுள்ளது என்ற டைமர் ஓடிக்கொண்டிருக்கும்.
* **Low Balance Warning:** வாடிக்கையாளரின் வாலட் பேலன்ஸ் குறைந்தால், `showLowBalanceDialog` மூலம் எச்சரிக்கை (Warning) காட்டப்படும்.
* **End Chat:** Chat-ஐ முடிப்பதற்கு `END` பட்டன் உள்ளது. வாடிக்கையாளர்கள் பேக் பட்டனை (Back Button) அழுத்தி தவறுதலாக வெளியேறுவதைத் தடுக்க அது முடக்கப்பட்டுள்ளது (Disabled).

---

## 4. Post-Chat Summary (செஷன் முடிவடைந்த பின்)

Chat செஷன் முடிந்தவுடன் `ChatModernSummaryDialog` என்ற பாப்-அப் (Pop-up) ஓபன் ஆகும். இதில்:
1. **Duration:** Chat செய்த மொத்த நேரம்.
2. **Amount:** ஜோதிடராக இருந்தால் எவ்வளவு சம்பாதித்தார் (Earned), வாடிக்கையாளராக இருந்தால் எவ்வளவு பணம் கழிக்கப்பட்டது (Deducted) என்ற விவரம்.
3. **Birth Details:** வாடிக்கையாளரின் பிறந்த விவரங்கள் (Birth Data / Partner Details) போன்றவை காட்டப்படும்.
4. **Go Back Home:** இதை கிளிக் செய்தால் மீண்டும் முகப்புப் பக்கத்திற்கு (Home Screen) சென்றுவிடும்.

---

## 5. UI/UX Design Elements (டிசைன் விவரங்கள்)

* **Material 3 & Jetpack Compose:** முற்றிலும் நவீனமான Compose UI பயன்படுத்தப்பட்டுள்ளது.
* **Chat Bubbles:** 
  * ஜோதிடர் அனுப்பும் மெசேஜ்: Pink நிறம் (`#FFD1DC`)
  * வாடிக்கையாளர் அனுப்பும் மெசேஜ்: Violet நிறம் (`#E1BEE7`)
* **Read Receipts:** மெசேஜ் சென்றுவிட்டதா (Single Tick), டெலிவரி ஆகிவிட்டதா (Double Tick) மற்றும் படித்துவிட்டார்களா (Blue Tick / Colored Tick) என்பதை அறியும் வசதி உள்ளது.

---

> [!TIP]
> **Performance Notes:** 
> படங்களை லோடு செய்வதற்கு `Coil` லைப்ரரி பயன்படுத்தப்பட்டுள்ளது. பேக்ரவுண்ட் வேலைகளுக்கு `Kotlin Coroutines` சிறப்பாகப் பயன்படுத்தப்பட்டுள்ளன.

---

## 6. Sample Code Snippets (முக்கிய குறியீடுகள்)

### 1. Sending a Message (Socket.io)
மெசேஜ்களை Socket மூலம் அனுப்பும் முறை:
```kotlin
val payload = JSONObject().apply {
    put("toUserId", toUserId)
    put("sessionId", sessionId)
    put("messageId", UUID.randomUUID().toString())
    put("timestamp", System.currentTimeMillis())
    put("content", JSONObject().put("text", finalText))
}
viewModel.sendMessage(payload) // This emits "chat-message" to the server
```

### 2. Voice Message 2-Second Rule
குறைந்தபட்சம் 2 வினாடிகள் (seconds) ஆடியோ ரெக்கார்ட் செய்திருக்க வேண்டும் என்பதற்கான செக்கிங்:
```kotlin
onStopRecording = {
    val duration = recordingSeconds
    recordingTimer?.removeCallbacks(recordingRunnable)
    recordingTimer = null
    val file = voiceRecorder.stopRecording()
    
    if (duration < 2) {
        // If less than 2 seconds, delete file and warn user
        if (file != null && file.exists()) file.delete()
        Toast.makeText(context, "Voice message must be at least 2 seconds", Toast.LENGTH_SHORT).show()
    } else if (file != null && file.exists()) {
        // Valid recording, upload to server
        handleMediaUpload(directFile = file)
    }
}
```

### 3. Smart Send Button Logic (Jetpack Compose)
டெக்ஸ்ட் டைப் செய்யும் போது மட்டுமே Send பட்டன் வேலை செய்ய வேண்டும், இல்லையென்றால் Mic பட்டன் வேலை செய்ய வேண்டும் என்பதற்கான லாஜிக்:
```kotlin
Box(
    modifier = Modifier
        .pointerInput(text.isBlank()) { 
            // Only capture tap gestures if text is empty (for recording voice)
            if (text.isBlank()) {
                detectTapGestures(
                    onPress = {
                        onStartRecording()
                        try { awaitRelease() } 
                        finally { onStopRecording() }
                    }
                )
            }
        }
        .clickable(enabled = text.isNotBlank() && !isRecording) {
            // Click listener for sending text message
            if (text.isNotBlank() && !isRecording) onSend()
        }
) {
    Icon(
        imageVector = if (text.isNotBlank() && !isRecording) Icons.Default.Send 
                      else if (isRecording) Icons.Default.Mic 
                      else Icons.Default.MicNone,
        contentDescription = "Action"
    )
}
```
