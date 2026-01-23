# WebRTC Code Quality & Stability Check

## 1. Overview
The user reported that "call and video call not working properly". We have reverted to commit `e23a74a`. This document analyzes the current state of WebRTC implementation in `CallActivity.kt` and `SocketManager.kt` to identify potential stability and quality issues.

## 2. Issues Identified

### A. Signaling Robustness (`CallActivity.kt`)
1.  **Renegotiation Handling**: The `onRenegotiationNeeded()` callback is **empty** (Line 542).
    *   *Risk*: If network conditions change or if we attempt to upgrade from Audio to Video (mid-call), the connection may fail or stay in the old state.
    *   *Fix*: Implement `createOffer()` inside `onRenegotiationNeeded`.

2.  **ICE Candidate Queuing**:
    *   Candidates received before `remoteDescription` are queued in `pendingIceCandidates`.
    *   *Issue*: `drainRemoteCandidates()` is called in `onSetSuccess` of `setRemoteDescription`. This is correct.
    *   *Check*: Ensure `drainRemoteCandidates` is actually robust. It iterates and adds. Seems okay, but threading safety should be verified (it's called from callback, usually signaling thread, but `addIceCandidate` is thread-safe).

3.  **Session Connectivity (Initiator/Receiver)**:
    *   The `startCallLimit` function logic for `isInitiator` vs Receiver (lines 376-425) seems slightly asymmetric regarding `session-connect`.
    *   **Initiator**: Emits `session-connect`, then `createOffer`.
    *   **Receiver**: Emits `answer-session`, THEN `session-connect`.
    *   *Risk*: Race conditions if the server expects `session-connect` before routing signals.

### B. Audio/Video Management
1.  **Audio Routing**:
    *   `configureAudioSettings` sets `MODE_IN_COMMUNICATION`.
    *   Audio Call defaults to `isSpeakerphoneOn = false` (Earpiece).
    *   Video Call defaults to `isSpeakerphoneOn = true` (Speaker).
    *   *Reference*: This is standard behavior. However, on some Android versions (12+), audio focus handling might need more explicit `AudioFocusRequest`.

2.  **Video View Z-Ordering**:
    *   Line 449: `localView.setZOrderMediaOverlay(true)`.
    *   Line 441: `remoteView` (SurfaceViewRenderer).
    *   *Issue*: Sometimes `SurfaceViewRenderer` can result in black screens if not properly initialized or if layout overlaps are weird. The XML layout has `remote_view` at the bottom and `localVideoCard` (containing `local_view`) on top. This looks correct.

3.  **Camera Capturer**:
    *   `createCameraCapturer` (Line 698) tries Front then Back.
    *   *Quality Check*: It uses `1280x720 @ 30fps`. This is high quality but might be heavy for poor networks.
    *   *Suggestion*: Adaptive resolution or fallback to VGA (640x480) if connection is poor? (Advanced, maybe not needed for "check").

### C. Resource Management
1.  **Memory Leaks**: `onDestroy` releases `peerConnection`, `videoCapturer`, `localView`, `remoteView`. This looks correct.
2.  **Socket Listeners**: `setupSocketListeners` attaches listeners. `onDestroy` removes specific listeners.
    *   *Potential Issue*: `SocketManager.off("signal")` removes ALL signal listeners. If other parts of the app use "signal" (unlikely), they would break. For `CallActivity`, this is fine.

## 3. Recommended Fixes (Immediate)

1.  **Implement `onRenegotiationNeeded`**:
    ```kotlin
    override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded")
        // If we are the initiator (or maybe just polite peer logic), we should renegotiate.
        // Simple approach: just create offer again.
        if (isInitiator) {
            createOffer()
        }
    }
    ```

2.  **Enhance Connection State Handling**:
    In `onIceConnectionChange`:
    *   `DISCONNECTED`: Currently shows toast. Should we try to restart ICE?
    *   `FAILED`: Ends call.

3.  **Fix "Wait for Socket" Logic in `onCreate`**:
    The block at lines 301-313 attempts to connect socket if needed.
    *   *Issue*: It's a bit "fire and forget". If socket takes 5 seconds to connect, `startCallLimit` (called by permissions result) might run before socket is ready, or `setupSocketListeners` might handle events late.
    *   *Fix*: Ensure `startCallLimit` waits for `socket.connected()` state properly (it has some logic for this in lines 411/416, but verify it covers all paths).

4.  **Hardware Acceleration**:
    `eglBase` creation and usage looks correct.

## 4. Proposed Implementation Plan
I will perform the fixes to ensure "proper" operation:
1.  **Add Renegotiation Support**: Ensure stability if network glitches.
2.  **Review Audio Focus**: Ensure audio is routed correctly.
3.  **Verify Socket Ready State**: Ensure we don't try to signal before socket is connected.
