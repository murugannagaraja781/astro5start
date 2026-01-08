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

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Params
        partnerId = intent.getStringExtra("partnerId") // Though usually handled by socket
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)

        // Init Views
        remoteView = findViewById(R.id.remote_view)
        localView = findViewById(R.id.local_view)
        tvStatus = findViewById(R.id.tvCallStatus)

        findViewById<ImageButton>(R.id.btnEndCall).setOnClickListener {
            endCall()
        }

        // --- CRITICAL FIX: Ensure Socket is Connected (for Killed App state) ---
        val tokenManager = com.astro5star.app.data.local.TokenManager(this)
        val session = tokenManager.getUserSession()

        SocketManager.init()
        if (session != null) {
            // Register immediately to ensure we are "online" and can emit
            session.userId?.let { uid ->
                SocketManager.registerUser(uid)
                if (SocketManager.getSocket()?.connected() != true) {
                    SocketManager.getSocket()?.connect()
                }
            }
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
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCallLimit()
        } else {
            Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCallLimit() {
        initWebRTC()
        setupSocketListeners()

        if (isInitiator) {
            tvStatus.text = "Calling..."
            createOffer()
        } else {
            tvStatus.text = "Connecting..."

            // DEEP FIX:
            // 1. Ensure we are connected
            // 2. Ensure we are registered
            // 3. ONLY THEN emit answer-session

            val tokenManager = com.astro5star.app.data.local.TokenManager(this)
            val session = tokenManager.getUserSession()
            val myUserId = session?.userId

            if (myUserId != null) {
                // Helper to send answer
                fun sendAnswer() {
                     val payload = JSONObject().apply {
                        put("sessionId", sessionId)
                        put("toUserId", partnerId)
                        put("accept", true)
                    }
                    SocketManager.getSocket()?.emit("answer-session", payload)
                    Log.d(TAG, "Sent answer-session for $sessionId")

                    // ALSO emit session-connect to start the billing timer
                    val connectPayload = JSONObject().apply {
                         put("sessionId", sessionId)
                    }
                    SocketManager.getSocket()?.emit("session-connect", connectPayload)
                    Log.d(TAG, "Sent session-connect for $sessionId")
                }

                if (SocketManager.getSocket()?.connected() == true) {
                     // Check if we need to re-register (if this activity started fresh)
                     SocketManager.registerUser(myUserId) { success ->
                         // Even if already registered, re-registering is safe.
                         // Wait for ack, then answer.
                         runOnUiThread { sendAnswer() }
                     }
                } else {
                    // Wait for connection
                    SocketManager.onConnect {
                         SocketManager.registerUser(myUserId) { success ->
                             runOnUiThread { sendAnswer() }
                         }
                    }
                    SocketManager.getSocket()?.connect() // Force connect
                }
            }
        }
    }

    private fun initWebRTC() {
        eglBase = EglBase.create()

        // Init Factory
        val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        // Init Views
        localView.init(eglBase.eglBaseContext, null)
        remoteView.init(eglBase.eglBaseContext, null)
        localView.setMirror(true)
        localView.setZOrderMediaOverlay(true)

        // Media Streams
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        videoCapturer = createCameraCapturer(Camera2Enumerator(this))

        if (videoCapturer != null) {
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
            videoCapturer!!.startCapture(640, 480, 30)

            localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
            localVideoTrack?.addSink(localView)
        }

        // Create Peer Connection
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        tvStatus.visibility = View.GONE
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        endCall()
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val payload = JSONObject().apply {
                        put("type", "candidate")
                        put("candidate", JSONObject().apply {
                            put("candidate", candidate.sdp)
                            put("sdpMid", candidate.sdpMid)
                            put("sdpMLineIndex", candidate.sdpMLineIndex)
                        })
                    }
                    sendSignal(payload)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    val remoteVideoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        remoteVideoTrack.addSink(remoteView)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })!!

        // Add Tracks
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
                Toast.makeText(this, "Call Ended", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSignal(data: JSONObject) {
        val type = data.optString("type")

        when (type) {
            "offer" -> {
                val sdp = data.optJSONObject("sdp")
                val descriptionStr = sdp?.optString("sdp")
                if (descriptionStr != null) {
                    peerConnection.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, descriptionStr))
                    createAnswer()
                }
            }
            "answer" -> {
                val sdp = data.optJSONObject("sdp")
                val descriptionStr = sdp?.optString("sdp")
                if (descriptionStr != null) {
                    peerConnection.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, descriptionStr))
                }
            }
            "candidate" -> {
                val candidateJson = data.optJSONObject("candidate")
                if (candidateJson != null) {
                    val candidate = IceCandidate(
                        candidateJson.optString("sdpMid"),
                        candidateJson.optInt("sdpMLineIndex"),
                        candidateJson.optString("candidate")
                    )
                    peerConnection.addIceCandidate(candidate)
                }
            }
        }
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                val payload = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", JSONObject().apply {
                        put("type", "offer")
                        put("sdp", desc?.description)
                    })
                }
                sendSignal(payload)
            }
        }, MediaConstraints())
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                val payload = JSONObject().apply {
                    put("type", "answer")
                    put("sdp", JSONObject().apply {
                        put("type", "answer")
                        put("sdp", desc?.description)
                    })
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
        SocketManager.endSession(sessionId)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.off("signal")
        SocketManager.off("session-ended")

        peerConnection.close()
        videoCapturer?.stopCapture()
        localView.release()
        remoteView.release()
        // peerConnectionFactory.dispose() // Sometimes crashes if disposed too earl
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Try front facing first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        // Fallback to back facing
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
