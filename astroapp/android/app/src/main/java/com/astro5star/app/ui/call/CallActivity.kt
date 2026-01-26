package com.astro5star.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.astro5star.app.R
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.AuthResponse
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList

class CallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_CODE = 101
    }

    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var tvStatus: TextView

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var eglBase: EglBase

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var isInitiator = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var clientBirthData: JSONObject? = null

    private lateinit var tokenManager: TokenManager
    private var session: AuthResponse? = null

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     // Emit update to server/astrologer
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    // Queue for ICE candidates received before remote description is set
    private val pendingIceCandidates = LinkedList<IceCandidate>()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:turn.astro5star.com:3478?transport=udp")
            .setUsername("webrtcuser").setPassword("strongpassword123").createIceServer(),
        PeerConnection.IceServer.builder("turns:turn.astro5star.com:5349")
            .setUsername("webrtcuser").setPassword("strongpassword123").createIceServer()
    )

    private var isMuted = false
    private var isSpeakerOn = false
    private var callType: String = "video" // Default to video

    private var partnerName: String? = null
    private var callDurationSeconds = 0
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            val timeStr = String.format("%02d:%02d", minutes, seconds)
            findViewById<TextView>(R.id.tvCallDuration).text = timeStr
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Params
        partnerId = intent.getStringExtra("partnerId")
        partnerName = intent.getStringExtra("partnerName") ?: partnerId
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)
        callType = intent.getStringExtra("type") ?: intent.getStringExtra("callType") ?: "video"

        val birthDataStr = intent.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) clientBirthData = obj
             } catch (e: Exception) { e.printStackTrace() }
        }

        // Init Views
        remoteView = findViewById(R.id.remote_view)
        localView = findViewById(R.id.local_view)
        tvStatus = findViewById(R.id.tvCallStatus)

        val tvRemoteName = findViewById<TextView>(R.id.tvRemoteName)
        val tvCallDuration = findViewById<TextView>(R.id.tvCallDuration)
        tvRemoteName.text = partnerName ?: "Unknown"
        tvCallDuration.text = "00:00"

        val btnEndCall = findViewById<ImageButton>(R.id.btnEndCall)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnVideo = findViewById<ImageButton>(R.id.btnVideo)
        val btnRasi = findViewById<ImageButton>(R.id.btnRasi)
        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Role-based UI Visibility
        // Role-based UI Visibility
        tokenManager = TokenManager(this)
        session = tokenManager.getUserSession()
        val role = session?.role

        if (role == "astrologer") {
            btnRasi.visibility = View.VISIBLE
            // Astrologer can also see Edit button to view/edit client details if needed
            btnEdit.visibility = View.VISIBLE
        } else {
            // Client: Can only see Edit button (to update their own intake)
            btnRasi.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
        }

        btnEndCall.setOnClickListener { endCall() }
        btnBack.setOnClickListener { endCall() }

        btnRasi.setOnClickListener {
             // Show Rasi Chart using ChartDisplayActivity
             if (clientBirthData != null) {
                 val intent = android.content.Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                 intent.putExtra("birthData", clientBirthData.toString())
                 startActivity(intent)
             } else {
                 showRasiChart() // Fallback if no data, or show standard message
                 // Ideally we should try to fetch it if missing, but for now fallback dialog or toast
                 if (isInitiator) Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
             }
        }

        btnEdit.setOnClickListener {
             // Open Intake Activity for editing
             val intent = android.content.Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
             intent.putExtra("isEditMode", true)
             intent.putExtra("existingData", clientBirthData?.toString())
             editIntakeLauncher.launch(intent)
        }

        // Start Timer
        timerHandler.postDelayed(timerRunnable, 1000)

        // Mic Toggle
        btnMic.setOnClickListener {
            isMuted = !isMuted
            localAudioTrack?.setEnabled(!isMuted)
            btnMic.alpha = if (isMuted) 0.5f else 1.0f
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        // Speaker/Video Toggle
        if (callType == "audio") {
            // Audio Call: Use this button for Speaker Toggle
            btnVideo.setImageResource(android.R.drawable.ic_lock_silent_mode_off) // Generic Speaker Icon
            localView.visibility = View.GONE
            // Keep remote view GONE for audio, or maybe show avatar?
            // For full screen audio UI redesign, we might want a placeholder image in remoteView or just black
            // Activity layout is black bg, so should be fine.
            remoteView.visibility = View.GONE
            findViewById<View>(android.R.id.content).setBackgroundColor(android.graphics.Color.BLACK)

            setSpeakerphoneOn(false)

            btnVideo.setOnClickListener {
                isSpeakerOn = !isSpeakerOn
                setSpeakerphoneOn(isSpeakerOn)
                btnVideo.alpha = if (isSpeakerOn) 1.0f else 0.5f
                Toast.makeText(this, if (isSpeakerOn) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Video Call: Use for Camera Toggle
            setSpeakerphoneOn(true)

            btnVideo.setOnClickListener {
                val enabled = localVideoTrack?.enabled() ?: true
                localVideoTrack?.setEnabled(!enabled)
                btnVideo.alpha = if (!enabled) 1.0f else 0.5f
                Toast.makeText(this, if (!enabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // --- CRITICAL FIX: Ensure Socket is Connected (for Killed App state) ---
        // --- CRITICAL FIX: Ensure Socket is Connected (for Killed App state) ---
        // tokenManager and session already initialized above

        try {
            SocketManager.init()
            session?.userId?.let { uid ->
                SocketManager.registerUser(uid)
                if (SocketManager.getSocket()?.connected() != true) {
                    SocketManager.getSocket()?.connect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket init failed", e)
        }
        // -----------------------------------------------------------------------

        // Check Permissions
        if (checkPermissions()) {
            startCallLimit()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQ_CODE
            )
        }

        startBackgroundService()
    }

    private fun startBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astro5star.app.CallForegroundService::class.java).apply {
            action = "ACTION_START_CALL"
            putExtra("partnerName", partnerName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astro5star.app.CallForegroundService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        startService(serviceIntent)
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
        isSpeakerOn = on
    }

    private fun checkPermissions(): Boolean {
        // For audio call, we theoretically don't need CAMERA, but for simplicity we keep it or check conditional
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        return if (callType == "audio") hasAudio else (hasAudio && hasCamera)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQ_CODE) {
             var allGranted = true
             if (grantResults.isNotEmpty()) {
                 for (result in grantResults) {
                     if (result != PackageManager.PERMISSION_GRANTED) {
                         allGranted = false
                         break
                     }
                 }
             } else {
                 allGranted = false
             }

             if (allGranted) {
                 startCallLimit()
             } else {
                 Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
                 finish() // End call if permissions denied
             }
        }
    }

    private fun startCallLimit() {
        initWebRTC()
        setupSocketListeners()

        if (isInitiator) {
            tvStatus.text = "Calling..."

            // FIX: Initiator MUST also send session-connect to start billing
            val connectPayload = JSONObject().apply {
                 put("sessionId", sessionId)
            }
            SocketManager.getSocket()?.emit("session-connect", connectPayload)
            Log.d(TAG, "Sent session-connect (Initiator) for $sessionId")

            createOffer()
        } else {
            tvStatus.text = "Connecting..."

            val myUserId = session?.userId

            if (myUserId != null) {
                fun sendAnswer() {
                     val payload = JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", partnerId)
                         put("accept", true)
                     }
                    SocketManager.getSocket()?.emit("answer-session", payload)
                    Log.d(TAG, "Sent answer-session for $sessionId")

                    val connectPayload = JSONObject().apply {
                         put("sessionId", sessionId)
                    }
                    SocketManager.getSocket()?.emit("session-connect", connectPayload)
                    Log.d(TAG, "Sent session-connect for $sessionId")
                }

                if (SocketManager.getSocket()?.connected() == true) {
                     SocketManager.registerUser(myUserId) { success ->
                         runOnUiThread { sendAnswer() }
                     }
                } else {
                    SocketManager.onConnect {
                         SocketManager.registerUser(myUserId) { success ->
                             runOnUiThread { sendAnswer() }
                         }
                    }
                    SocketManager.getSocket()?.connect()
                }
            }
        }
    }

    private fun initWebRTC() {
        eglBase = EglBase.create()

        val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        // Only init Video if Video Call
        if (callType == "video") {
            // Initialize remote view (full screen)
            remoteView.init(eglBase.eglBaseContext, null)
            remoteView.setEnableHardwareScaler(true)
            remoteView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            // Initialize local view (PIP - self view)
            localView.init(eglBase.eglBaseContext, null)
            localView.setEnableHardwareScaler(true)
            localView.setMirror(true)  // Mirror for selfie camera
            localView.setZOrderMediaOverlay(true)  // Show above remote view
            localView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            // Make sure local view is visible
            localView.visibility = View.VISIBLE
            Log.d(TAG, "Video views initialized successfully")
        }

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        if (callType == "video") {
            // Try Camera2 first, then Camera1
            videoCapturer = try {
                createCameraCapturer(Camera2Enumerator(this))
            } catch (e: Exception) {
                Log.e(TAG, "Camera2Enumerator failed, trying Camera1Enumerator", e)
                try {
                    createCameraCapturer(Camera1Enumerator(true))
                } catch (e1: Exception) {
                    Log.e(TAG, "Camera1Enumerator failed too", e1)
                    null
                }
            }

            if (videoCapturer != null) {
                try {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)

                    // Use 640x480 (VGA) which is guaranteed to be supported.
                    // 720p (1280x720) causes failures on many front cameras.
                    videoCapturer!!.startCapture(640, 480, 30)
                    Log.d(TAG, "Camera capturer started: 640x480 @30fps")

                    localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                    localVideoTrack?.setEnabled(true)  // Ensure video track is enabled
                    localVideoTrack?.addSink(localView)
                    Log.d(TAG, "Local video track created and added to local view")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera capture", e)
                    Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "ERROR: Could not create camera capturer!")
                Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $p0")
            }
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $newState")
                runOnUiThread {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED -> tvStatus.visibility = View.GONE
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            // Don't end immediately, allow re-negotiation or timeout
                            Toast.makeText(this@CallActivity, "Connection Unstable", Toast.LENGTH_SHORT).show()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            // Only end on FAILURE
                            Toast.makeText(this@CallActivity, "Connection Failed", Toast.LENGTH_SHORT).show()
                            endCall()
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $p0")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    // WRAPPED SIGNAL
                    val signalData = JSONObject().apply {
                         put("type", "candidate")
                         put("candidate", JSONObject().apply {
                             put("candidate", candidate.sdp)
                             put("sdpMid", candidate.sdpMid)
                             put("sdpMLineIndex", candidate.sdpMLineIndex)
                         })
                    }
                    val payload = JSONObject().apply {
                        put("toUserId", partnerId)
                        put("signal", signalData)
                    }
                    sendSignal(payload)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.videoTracks?.size} video tracks")
                if (stream != null && stream.videoTracks.isNotEmpty() && callType == "video") {
                    val remoteVideoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        remoteVideoTrack.addSink(remoteView)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
                // Implement renegotiation if supported
            }
        })!!

        localAudioTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
        localVideoTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
    }


    private fun setupSocketListeners() {
        SocketManager.onSignal { data ->
            runOnUiThread {
                handleSignal(data)
            }
        }

        SocketManager.getSocket()?.on("client-birth-chart") { args ->
            try {
                 val data = args[0] as JSONObject
                 val bData = data.optJSONObject("birthData")
                 if (bData != null) {
                     clientBirthData = bData
                     runOnUiThread {
                         Toast.makeText(this@CallActivity, "Client Data Updated", Toast.LENGTH_SHORT).show()
                     }
                 }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Billing Started - Show indicator when billing begins
        SocketManager.onBillingStarted { startTime ->
            runOnUiThread {
                val billingIndicator = findViewById<TextView>(R.id.tvCallStatus)
                billingIndicator?.text = "🔴 Billing Active"
                billingIndicator?.visibility = View.VISIBLE
                billingIndicator?.setTextColor(android.graphics.Color.parseColor("#EF4444"))

                // Auto-hide after 3 seconds
                billingIndicator?.postDelayed({
                    billingIndicator.visibility = View.GONE
                }, 3000)

                Log.d(TAG, "Billing started - Session is now billable")
            }
        }

        // Session Ended with Summary - Show deducted/earned amounts
        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            runOnUiThread {
                // Stop timer
                timerHandler.removeCallbacks(timerRunnable)

                // Format duration
                val minutes = duration / 60
                val seconds = duration % 60
                val durationStr = String.format("%02d:%02d", minutes, seconds)

                // Show summary dialog
                val message = when {
                    session?.role == "astrologer" -> {
                        "Duration: $durationStr\n\nYou earned: ₹${String.format("%.2f", earned)}"
                    }
                    reason == "insufficient_funds" -> {
                        "Call ended due to insufficient balance.\n\nDuration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                    }
                    else -> {
                        "Duration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                    }
                }

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(if (reason == "insufficient_funds") "⚠️ Low Balance" else "📞 Call Summary")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }

        // Safety: End call if socket disconnects (internet lost)
        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 Toast.makeText(this, "Connection Lost - Call Ended", Toast.LENGTH_SHORT).show()
                 finish()
             }
        }
    }

    private fun drainRemoteCandidates() {
        if (pendingIceCandidates.isNotEmpty()) {
            Log.d(TAG, "Draining ${pendingIceCandidates.size} remote candidates")
            for (candidate in pendingIceCandidates) {
                peerConnection.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    private fun handleSignal(data: JSONObject) {
        val signal = data.optJSONObject("signal") ?: data // Check if wrapped in signal, else fallback
        var type = signal.optString("type")

        // Fallback: If type is empty but 'candidate' exists, treat as candidate
        if (type.isEmpty() && signal.has("candidate")) {
            type = "candidate"
        }

        Log.d(TAG, "Received signal: $type")

        when (type) {
            "offer" -> {
                val sdp = signal.optJSONObject("sdp")
                val descriptionStr = sdp?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty()) {
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote Offer Set. Creating Answer.")
                            createAnswer()
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.OFFER, descriptionStr))
                }
            }
            "answer" -> {
                val sdp = signal.optJSONObject("sdp")
                val descriptionStr = sdp?.optString("sdp") ?: signal.optString("sdp")
                if (descriptionStr.isNotEmpty()) {
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote Answer Set.")
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.ANSWER, descriptionStr))
                }
            }
            "candidate" -> {
                val candidateJson = signal.optJSONObject("candidate") ?: signal
                val sdpMid = candidateJson.optString("sdpMid")
                val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex", -1)
                val sdp = candidateJson.optString("candidate")

                if (sdp.isNotEmpty() && sdpMLineIndex != -1) {
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)

                    // QUEUE IF REMOTE DESCRIPTION IS NULL
                    if (peerConnection.remoteDescription == null) {
                         Log.d(TAG, "Queuing ICE candidate (RemoteDesc is null)")
                         pendingIceCandidates.add(candidate)
                    } else {
                         peerConnection.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                // WRAPPED SIGNAL
                val signalData = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc?.description)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
            }
        }, MediaConstraints())
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                // WRAPPED SIGNAL
                val signalData = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", desc?.description)
                }
                val payload = JSONObject().apply {
                    put("toUserId", partnerId)
                    put("signal", signalData)
                }
                sendSignal(payload)
            }
        }, MediaConstraints())
    }

    private fun sendSignal(data: JSONObject) {
        data.put("sessionId", sessionId)
        SocketManager.emitSignal(data)
    }

    private fun endCall() {
        stopBackgroundService()
        SocketManager.endSession(sessionId)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable) // Stop Timer
        SocketManager.off("signal")
        SocketManager.off("session-ended")
        SocketManager.off("billing-started")
        SocketManager.off("client-birth-chart")
        SocketManager.getSocket()?.off(io.socket.client.Socket.EVENT_DISCONNECT)
        try {
            peerConnection.close()
            videoCapturer?.stopCapture()
            localView.release()
            remoteView.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying", e)
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        Log.d(TAG, "Looking for front facing camera from ${deviceNames.size} devices")

        // 1. Try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating capturer for front facing device: $deviceName")
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // 2. Fallback to back facing camera if front not found (rare but possible on some devices)
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating capturer for back facing device: $deviceName")
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        Log.e(TAG, "No camera found!")
        return null
    }

    private fun showRasiChart() {
        // Simple North/South chart placeholder or use RasiDetailDialog logic
        // For now, showing a simple dialog with the user's Rasi if available
        // Or specific chart viewer. Given user asked for "Rasi Chart Icon", likely expects the chart image.
        // We will show a dialog with Rasi Grid similar to Home Screen but simplified.

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rasi Chart")
            .setMessage("Chart visualization would appear here.\n(Implementation Pending: Needs Chart Rendering Logic)")
            .setPositiveButton("Close", null)
            .create()
        dialog.show()
    }

    private fun showIntakeDialog() {
        // Dialog with Name, DOB, TOB, POB inputs
        val view = layoutInflater.inflate(R.layout.dialog_intake_edit, null)
        val etName = view.findViewById<android.widget.EditText>(R.id.etName)
        val etDob = view.findViewById<android.widget.EditText>(R.id.etDob)
        val etTob = view.findViewById<android.widget.EditText>(R.id.etTob)
        val etPob = view.findViewById<android.widget.EditText>(R.id.etPob)

        // Pre-fill if we have data (requires storing birthData in Activity)
        // For now, empty or standard.

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Consultation Details")
            .setView(view)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString()
                val dob = etDob.text.toString()
                val tob = etTob.text.toString()
                val pob = etPob.text.toString()

                val formData = JSONObject().apply {
                    put("name", name)
                    put("dob", dob)
                    put("tob", tob)
                    put("pob", pob)
                    put("updatedAt", System.currentTimeMillis())
                }

                if (sessionId != null) {
                    val payload = JSONObject().apply {
                        put("sessionId", sessionId)
                        put("formData", formData)
                    }
                    SocketManager.getSocket()?.emit("update-intake", payload)
                    Toast.makeText(this, "Details updated & sent to Astrologer", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("SdpObserver", "Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("SdpObserver", "Set Failure: $p0") }
    }
}
