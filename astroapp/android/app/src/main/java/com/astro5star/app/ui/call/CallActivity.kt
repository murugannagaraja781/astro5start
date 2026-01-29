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
import com.astro5star.app.data.call.CallManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.AuthResponse
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class CallActivity : AppCompatActivity(), CallManager.CallEvents {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_CODE = 101
    }

    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var tvStatus: TextView
    private lateinit var tvCallDuration: TextView

    private var partnerId: String? = null
    private var partnerName: String? = null
    private var sessionId: String? = null
    private var isInitiator = false
    private var callType: String = "video"
    private var clientBirthData: JSONObject? = null

    private lateinit var tokenManager: TokenManager
    private var session: AuthResponse? = null

    // Timer
    private var callDurationSeconds = 0
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            val timeStr = String.format("%02d:%02d", minutes, seconds)
            tvCallDuration.text = timeStr
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Initialize CallManager immediately
        CallManager.init(applicationContext)
        CallManager.setListener(this)

        // Params
        partnerId = intent.getStringExtra("partnerId")
        partnerName = intent.getStringExtra("partnerName") ?: partnerId
        sessionId = intent.getStringExtra("sessionId")
        isInitiator = intent.getBooleanExtra("isInitiator", false)
        val rawType = intent.getStringExtra("type") ?: intent.getStringExtra("callType") ?: "video"
        callType = if (rawType.lowercase() == "audio" || rawType.lowercase() == "voice") "audio" else "video"

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
        tvCallDuration = findViewById(R.id.tvCallDuration)

        val tvRemoteName = findViewById<TextView>(R.id.tvRemoteName)
        tvRemoteName.text = partnerName ?: "Unknown"
        tvCallDuration.text = "00:00"

        val btnEndCall = findViewById<ImageButton>(R.id.btnEndCall)
        val btnMic = findViewById<ImageButton>(R.id.btnMic)
        val btnVideo = findViewById<ImageButton>(R.id.btnVideo)
        val btnRasi = findViewById<ImageButton>(R.id.btnRasi)
        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        tokenManager = TokenManager(this)
        session = tokenManager.getUserSession()
        val role = session?.role

        // Visibility logic
        if (role == "astrologer") {
            btnRasi.visibility = View.VISIBLE
            btnEdit.visibility = View.VISIBLE
        } else {
            btnRasi.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
        }

        btnEndCall.setOnClickListener { endCallByUser() }
        btnBack.setOnClickListener {
            // Back button minimizes app (like Home), doesn't end call
            moveTaskToBack(true)
        }

        btnRasi.setOnClickListener {
             if (clientBirthData != null) {
                 val intent = android.content.Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                 intent.putExtra("birthData", clientBirthData.toString())
                 startActivity(intent)
             } else {
                 showRasiChart()
                 if (isInitiator) Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
             }
        }

        btnEdit.setOnClickListener {
             val intent = android.content.Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
             intent.putExtra("isEditMode", true)
             intent.putExtra("existingData", clientBirthData?.toString())
             editIntakeLauncher.launch(intent)
        }

        // Mic Toggle
        var isMuted = false
        btnMic.setOnClickListener {
            isMuted = !isMuted
            CallManager.toggleMic(isMuted)
            btnMic.alpha = if (isMuted) 0.5f else 1.0f
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        // Speaker/Video Toggle
        if (callType == "audio") {
            btnVideo.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            localView.visibility = View.GONE
            remoteView.visibility = View.GONE
            findViewById<View>(android.R.id.content).setBackgroundColor(android.graphics.Color.BLACK)

            setSpeakerphoneOn(false)
            var isSpeakerOn = false
            btnVideo.setOnClickListener {
                isSpeakerOn = !isSpeakerOn
                setSpeakerphoneOn(isSpeakerOn)
                btnVideo.alpha = if (isSpeakerOn) 1.0f else 0.5f
                Toast.makeText(this, if (isSpeakerOn) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Video Call
            setSpeakerphoneOn(true)
            var isCameraOn = true
            btnVideo.setOnClickListener {
                isCameraOn = !isCameraOn
                CallManager.toggleCamera(isCameraOn)
                btnVideo.alpha = if (!isCameraOn) 0.5f else 1.0f
                Toast.makeText(this, if (isCameraOn) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // Ensure Socket
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

        // Check Permissions
        if (checkPermissions()) {
            checkAndStartCall()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQ_CODE
            )
        }
    }

    // --- Call Logic Delegated to CallManager ---

    private fun checkAndStartCall() {
        if (CallManager.isCallActive) {
            Log.d(TAG, "Call already active in Manager. Attaching views.")
            // Call is already running (e.g., renewed activity), just attach views
            CallManager.attachLocalView(localView)
            CallManager.attachRemoteView(remoteView)

            // Start Timer if not running? We don't have exact sync yet, but let's start it
            timerHandler.removeCallbacks(timerRunnable)
            timerHandler.postDelayed(timerRunnable, 1000)

        } else {
            Log.d(TAG, "Starting new call via Manager")
            CallManager.startCall(partnerId!!, sessionId!!, isInitiator, callType)
            CallManager.attachLocalView(localView)

            // Start Foreground Service
            startBackgroundService()

            startTimer()
        }
    }

    private fun startTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun endCallByUser() {
        CallManager.endCall() // Manager sends end-session
        stopBackgroundService()
        finish()
    }

    // --- CallEvents Overrides ---

    override fun onCallEnded(reason: String?) {
        runOnUiThread {
            Toast.makeText(this, "Call Ended: $reason", Toast.LENGTH_SHORT).show()
            stopBackgroundService()
            finish()
        }
    }

    override fun onRemoteStreamAdded(videoTrack: VideoTrack) {
        runOnUiThread {
            Log.d(TAG, "onRemoteStreamAdded: Attaching to remoteView")
            CallManager.attachRemoteView(remoteView)

            if (callType == "video") {
                remoteView.visibility = View.VISIBLE
            }
        }
    }

    override fun onStatusChanged(status: String) {
        runOnUiThread {
            tvStatus.text = status
            if (status.contains("Connected")) {
                 tvStatus.postDelayed({ tvStatus.visibility = View.GONE }, 2000)
            } else {
                tvStatus.visibility = View.VISIBLE
            }
        }
    }

    // --- Lifecycle ---

    override fun onResume() {
        super.onResume()
        CallManager.setListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        CallManager.setListener(null)
        // DO NOT endCall() here unless you are sure;
        // endCallByUser() handles the explicit end.
        // If system kills activity (swipe), call might survive in Service + Manager if process lives.
        // BUT if process dies, everything dies.
        // ForegroundService helps keep process alive.

        CallManager.detachViews(localView, remoteView)
        localView.release()
        remoteView.release()
    }

    // --- Helpers ---

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
    }

    private fun checkPermissions(): Boolean {
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
                         allGranted = false; break
                     }
                 }
             } else allGranted = false

             if (allGranted) checkAndStartCall()
             else {
                 Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
                 finish()
             }
        }
    }

    private fun showRasiChart() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rasi Chart")
            .setMessage("Chart visualization.")
            .setPositiveButton("Close", null)
            .show()
    }
}