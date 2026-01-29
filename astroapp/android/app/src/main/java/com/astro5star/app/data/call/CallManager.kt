package com.astro5star.app.data.call

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.Constants
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList

/**
 * Singleton CallManager to keep WebRTC connections alive even if Activity is destroyed.
 */
object CallManager {
    private const val TAG = "CallManager"

    // Context - Use Application Context ONLY
    private var appContext: Context? = null

    // WebRTC Components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    // Call State
    var isCallActive: Boolean = false
        private set
    var callType: String = "video"
    var sessionId: String? = null
    var partnerId: String? = null
    var isInitiator: Boolean = false

    // Remote Track to attach when UI becomes available
    private var remoteVideoTrack: VideoTrack? = null

    // ICE Handlings
    private val pendingIceCandidates = LinkedList<IceCandidate>()

    // UI Callbacks
    interface CallEvents {
        fun onCallEnded(reason: String?)
        fun onRemoteStreamAdded(videoTrack: VideoTrack)
        fun onStatusChanged(status: String) // e.g. "Connecting", "Connected"
    }

    private var eventsListener: CallEvents? = null

    // ICE Servers
    private val iceServers by lazy {
         listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:turn.astro5star.com:3478?transport=udp")
                .setUsername("webrtcuser").setPassword("strongpassword123").createIceServer(),
            PeerConnection.IceServer.builder("turns:turn.astro5star.com:5349")
                .setUsername("webrtcuser").setPassword("strongpassword123").createIceServer()
        )
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        eglBase = EglBase.create()

        val options = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .createPeerConnectionFactory()

        Log.d(TAG, "CallManager Initialized")
    }

    fun setListener(listener: CallEvents?) {
        this.eventsListener = listener
        // If we already have a remote track, notify immediately
        if (remoteVideoTrack != null && listener != null) {
            listener.onRemoteStreamAdded(remoteVideoTrack!!)
            Log.d(TAG, "Notified listener of existing remote track")
        }
    }

    fun startCall(pId: String, sId: String, initiator: Boolean, type: String) {
        if (isCallActive) {
            Log.w(TAG, "Call already active, ignoring startCall")
            return
        }

        partnerId = pId
        sessionId = sId
        isInitiator = initiator
        callType = type
        isCallActive = true

        Log.d(TAG, "Starting Call: sid=$sId, type=$type, init=$initiator")

        initPeerConnection()
        initPeerConnection()
        setupSocketListeners()

        // CRITICAL FIX: reliable connection
        // If we are NOT the initiator (we are answering), we must tell the server
        // so it can trigger the "session-answered" event for the caller (Web Client).
        if (!initiator) {
            SocketManager.answerSessionNative(sId, true, type)
        }


        // Setup Media (Camera/Audio)
        setupMedia()
    }

    private fun setupMedia() {
        if (peerConnectionFactory == null) return

        val audioSource = peerConnectionFactory!!.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("101", audioSource)

        if (callType == "video") {
            // Video Logic
             // Try Camera2 first, then Camera1
            videoCapturer = try {
                createCameraCapturer(Camera2Enumerator(appContext))
            } catch (e: Exception) {
                Log.e(TAG, "Camera2Enumerator failed, trying Camera1Enumerator", e)
                try {
                    createCameraCapturer(Camera1Enumerator(true))
                } catch (e1: Exception) {
                    null
                }
            }

            if (videoCapturer != null) {
                try {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext)
                    val videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)

                    videoCapturer!!.startCapture(640, 480, 30)
                    Log.d(TAG, "Camera started")

                    localVideoTrack = peerConnectionFactory!!.createVideoTrack("100", videoSource)
                    localVideoTrack?.setEnabled(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera", e)
                }
            }
        }

        // Add Tracks to PeerConnection
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("mediaStream")) }
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("mediaStream")) }
    }

    private fun initPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState: $newState")
                 if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                    eventsListener?.onStatusChanged("Connected")
                } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED || newState == PeerConnection.IceConnectionState.FAILED) {
                    eventsListener?.onStatusChanged("Connection Unstable/Failed")
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                 if (candidate != null) {
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
                if (stream != null && stream.videoTracks.isNotEmpty() && callType == "video") {
                    val track = stream.videoTracks[0]
                    Log.d(TAG, "onAddStream: Found video track")
                    remoteVideoTrack = track
                    eventsListener?.onRemoteStreamAdded(track)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack && callType == "video") {
                    Log.d(TAG, "onTrack: Found video track")
                    remoteVideoTrack = track
                    eventsListener?.onRemoteStreamAdded(track)
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    // --- Socket Handling ---

    fun setupSocketListeners() {
        SocketManager.onSignal { data ->
            handleSignal(data)
        }

        // Billing Start trigger
        SocketManager.onBillingStarted { startTime ->
            Log.d(TAG, "Billing Started. Initiator: $isInitiator")
             eventsListener?.onStatusChanged("🔴 Billing Active")
             // Initiator starts handshake
             if (isInitiator) {
                 createOffer()
             }
        }

        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            stopCallInternal()
            eventsListener?.onCallEnded(reason)
        }

        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             stopCallInternal()
             eventsListener?.onCallEnded("connection_lost")
        }
    }

    private fun handleSignal(data: JSONObject) {
        if (peerConnection == null) return

        val signal = data.optJSONObject("signal") ?: data
        var type = signal.optString("type")
         if (type.isEmpty() && signal.has("candidate")) {
            type = "candidate"
        }

        when (type) {
            "offer" -> {
                val sdpObj = signal.optJSONObject("sdp")
                val descriptionStr = sdpObj?.optString("sdp") ?: signal.optString("sdp")

                if (descriptionStr.isNotEmpty()) {
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            createAnswer()
                            drainRemoteCandidates()
                        }
                    }, SessionDescription(SessionDescription.Type.OFFER, descriptionStr))
                }
            }
            "answer" -> {
                val sdpObj = signal.optJSONObject("sdp")
                val descriptionStr = sdpObj?.optString("sdp") ?: signal.optString("sdp")
                 if (descriptionStr.isNotEmpty()) {
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
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
                    if (peerConnection?.remoteDescription == null) {
                        pendingIceCandidates.add(candidate)
                    } else {
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
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
        }, constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
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
        }, constraints)
    }

    private fun sendSignal(payload: JSONObject) {
        payload.put("sessionId", sessionId)
        SocketManager.getSocket()?.emit("signal", payload)
    }

    private fun drainRemoteCandidates() {
        if (pendingIceCandidates.isNotEmpty()) {
            for (candidate in pendingIceCandidates) {
                peerConnection?.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    // --- UI Attachments ---

    fun attachLocalView(view: SurfaceViewRenderer) {
        if (localVideoTrack == null) {
            Log.w(TAG, "attachLocalView: localVideoTrack is null")
            return
        }
        if (eglBase != null) {
             view.init(eglBase!!.eglBaseContext, null)
             view.setEnableHardwareScaler(true)
             view.setMirror(true)
             view.setZOrderMediaOverlay(true)
             view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

             localVideoTrack?.addSink(view)
             Log.d(TAG, "Attached Local View")
        }
    }

    fun attachRemoteView(view: SurfaceViewRenderer) {
        if (remoteVideoTrack == null) {
             Log.d(TAG, "attachRemoteView: Remote track not yet available")
             return
        }
        if (eglBase != null) {
             view.init(eglBase!!.eglBaseContext, null)
             view.setEnableHardwareScaler(true)
             view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

             remoteVideoTrack?.addSink(view)
             Log.d(TAG, "Attached Remote View")
        }
    }

    fun detachViews(localView: SurfaceViewRenderer, remoteView: SurfaceViewRenderer) {
        // We only remove sink, we DO NOT release the views because the Activity releases them
        // Better yet, just remove sinks
        localVideoTrack?.removeSink(localView)
        remoteVideoTrack?.removeSink(remoteView)
    }

    // --- Control ---

    fun endCall() {
        // Emit end session
        SocketManager.endSession(sessionId)
        stopCallInternal()
    }

    private fun stopCallInternal() {
         isCallActive = false

         // Remove listeners
         SocketManager.off("signal")
         SocketManager.off("billing-started")

         try {
             videoCapturer?.stopCapture()
             videoCapturer?.dispose()
             videoCapturer = null

             localVideoTrack?.dispose() // Clean up tracks
             localAudioTrack?.dispose()

             peerConnection?.close()
             peerConnection = null
         } catch (e: Exception) {
             e.printStackTrace()
         }

         remoteVideoTrack = null
         sessionId = null
         partnerId = null

         // Clean pending
         pendingIceCandidates.clear()

         Log.d(TAG, "Call Stopped/Cleaned up")
    }

    fun toggleMic(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleCamera(isEnabled: Boolean) {
        localVideoTrack?.setEnabled(isEnabled)
    }

    fun getEglBaseContext(): EglBase.Context? {
        return eglBase?.eglBaseContext
    }

    // --- Helpers ---

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Front
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        // Back
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    // Simple observer helper
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("SimpleSdpObserver", "CreateFailure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("SimpleSdpObserver", "SetFailure: $p0") }
    }
}
