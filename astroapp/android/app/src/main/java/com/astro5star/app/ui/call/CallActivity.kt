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

    // Queue for ICE candidates received before remote description is set
    private val pendingIceCandidates = LinkedList<IceCandidate>()

    // CORRECT TURN Configuration for Mobile Networks
    // Priority: TURNS (443 TLS) first → TURN (3478 UDP) fallback
    // User Provided ICE Config
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
            findViewById<TextView>(R.id.tvAudioTime)?.text = timeStr
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
        val btnChat = findViewById<ImageButton>(R.id.btnChat)
        // btnBack removed from XML for simplified Split Screen UI

        btnEndCall.setOnClickListener { endCall() }

        btnChat.setOnClickListener {
             val intent = android.content.Intent(this, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("toUserId", partnerId)
                putExtra("toUserName", partnerName)
            }
            startActivity(intent)
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

            // Hide Split Screen Video Container
            findViewById<View>(R.id.videoSplitLayout)?.visibility = View.GONE
            findViewById<View>(R.id.remoteVideoCard)?.visibility = View.GONE
            findViewById<View>(R.id.localVideoCard)?.visibility = View.GONE

            localView.visibility = View.GONE
            remoteView.visibility = View.GONE
            findViewById<View>(android.R.id.content).setBackgroundColor(android.graphics.Color.BLACK)

            // Switch to Audio UI Layout
            findViewById<View>(R.id.controlPanel).visibility = View.GONE
            findViewById<View>(R.id.tvCallStatus).visibility = View.GONE // Use tvAudioStatus instead

            val audioLayout = findViewById<View>(R.id.audioLayout)
            audioLayout.visibility = View.VISIBLE

            findViewById<TextView>(R.id.tvAudioName).text = partnerName ?: "Unknown"

            configureAudioSettings()

            // Bind New Audio Buttons
            val btnAudioMic = findViewById<ImageButton>(R.id.btnAudioMic)
            val btnAudioSpeaker = findViewById<ImageButton>(R.id.btnAudioSpeaker)
            val btnAudioEndCall = findViewById<android.widget.Button>(R.id.btnAudioEndCall)

            btnAudioEndCall.setOnClickListener { endCall() }

            btnAudioSpeaker.setOnClickListener {
                toggleSpeaker()
                // Update specific button visual if needed (toggleSpeaker updates old button alpha, we might need to sync or just rely on toast)
                btnAudioSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
            }

            btnAudioMic.setOnClickListener {
                isMuted = !isMuted
                localAudioTrack?.setEnabled(!isMuted)
                btnAudioMic.alpha = if (isMuted) 0.5f else 1.0f
                Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
                // Sync old button state just in case
                btnMic.alpha = if (isMuted) 0.5f else 1.0f
            }

            // Sync initial state
            btnAudioSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f

            btnVideo.setOnClickListener {
                toggleSpeaker()
            }
        } else {
            // Video Call: Use for Camera Toggle
            configureAudioSettings()

            btnVideo.setOnClickListener {
                val enabled = localVideoTrack?.enabled() ?: true
                localVideoTrack?.setEnabled(!enabled)
                btnVideo.alpha = if (!enabled) 1.0f else 0.5f
                Toast.makeText(this, if (!enabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // --- CRITICAL FIX: Ensure Socket is Connected (for Killed App state) ---
        val tokenManager = com.astro5star.app.data.local.TokenManager(this)
        val session = tokenManager.getUserSession()

        try {
            SocketManager.init()
            if (session != null) {
                session.userId?.let { uid ->
                    SocketManager.registerUser(uid)
                    if (SocketManager.getSocket()?.connected() != true) {
                        SocketManager.getSocket()?.connect()
                    }
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

    private fun toggleSpeaker() {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val btn = findViewById<ImageButton>(R.id.btnVideo)
        if (isSpeakerOn) {
            // Turn off speaker
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            btn.alpha = 0.5f // Visual Feedback
            Toast.makeText(this, "Speaker Off", Toast.LENGTH_SHORT).show()
        } else {
            // Turn on speaker
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            btn.alpha = 1.0f // Visual Feedback
            Toast.makeText(this, "Speaker On", Toast.LENGTH_SHORT).show()
        }
        isSpeakerOn = !isSpeakerOn
    }

    private fun configureAudioSettings() {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION

        // Default behavior: Video -> Speaker, Audio -> Earpiece
        if (callType == "video") {
            audioManager.isSpeakerphoneOn = true
            isSpeakerOn = true
        } else {
            audioManager.isSpeakerphoneOn = false
            isSpeakerOn = false
        }
    }

    private fun checkPermissions(): Boolean {
        // For audio call, we theoretically don't need CAMERA, but for simplicity we keep it or check conditional
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        return if (callType == "audio") hasAudio else (hasAudio && hasCamera)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Simply retry startCallLimit if mostly granted. detailed check omitted for brevity.
        if (requestCode == PERMISSION_REQ_CODE) {
             startCallLimit()
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

            val tokenManager = com.astro5star.app.data.local.TokenManager(this)
            val session = tokenManager.getUserSession()
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
            videoCapturer = createCameraCapturer(Camera2Enumerator(this))
            if (videoCapturer != null) {
                val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)

                // Start camera capture with higher resolution for better quality
                videoCapturer!!.startCapture(1280, 720, 30)
                Log.d(TAG, "Camera capturer started: 1280x720 @30fps")

                localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                localVideoTrack?.setEnabled(true)  // Ensure video track is enabled
                localVideoTrack?.addSink(localView)
                Log.d(TAG, "Local video track created and added to local view")
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

        SocketManager.onSessionEnded {
            runOnUiThread {
                Log.d(TAG, "onSessionEnded: Received session-ended event")
                Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
                if (!isFinishing) {
                    finish()
                }
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
            "bye" -> {
                Log.d(TAG, "Received BYE signal. Ending call.")
                Toast.makeText(this, "Partner ended the call", Toast.LENGTH_SHORT).show()
                if (!isFinishing) {
                    finish()
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
        Log.d(TAG, "endCall: Initiating manual disconnect")
        stopBackgroundService()

        // 1. Send Explicit "bye" signal to peer (Redundancy)
        try {
            val signalData = JSONObject().apply {
                put("type", "bye")
            }
            val payload = JSONObject().apply {
                put("toUserId", partnerId)
                put("signal", signalData)
            }
            sendSignal(payload)
            Log.d(TAG, "Sent BYE signal to $partnerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send BYE signal", e)
        }

        // 2. Notify Server to End Session (Billing)
        SocketManager.endSession(sessionId)

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable) // Stop Timer
        SocketManager.off("signal")
        SocketManager.off("session-ended")
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
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("SdpObserver", "Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("SdpObserver", "Set Failure: $p0") }
    }
}
