import React, { useEffect, useState, useCallback } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  StatusBar,
  PermissionsAndroid,
  Platform,
  View,
  ActivityIndicator,
  Text,
  Vibration,
  Linking,
  Alert,
  AppState,
} from 'react-native';
// @ts-ignore
import { WebView } from 'react-native-webview';
// @ts-ignore
// PhonePe SDK import removed
import KeepAwake from 'react-native-keep-awake';
import NetInfo from '@react-native-community/netinfo';
import RNBootSplash from 'react-native-bootsplash';
import notifee, { AndroidImportance } from '@notifee/react-native';
import OfflineNotice from './components/OfflineNotice';

import messaging from '@react-native-firebase/messaging';

// Base64 decode utility for React Native
const base64Decode = (str: string): string => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  let output = '';
  str = str.replace(/[^A-Za-z0-9\+\/\=]/g, '');
  let i = 0;
  while (i < str.length) {
    const enc1 = chars.indexOf(str.charAt(i++));
    const enc2 = chars.indexOf(str.charAt(i++));
    const enc3 = chars.indexOf(str.charAt(i++));
    const enc4 = chars.indexOf(str.charAt(i++));
    const chr1 = (enc1 << 2) | (enc2 >> 4);
    const chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
    const chr3 = ((enc3 & 3) << 6) | enc4;
    output += String.fromCharCode(chr1);
    if (enc3 !== 64) output += String.fromCharCode(chr2);
    if (enc4 !== 64) output += String.fromCharCode(chr3);
  }
  return output;
};

// Background message handler is now in index.js
// DO NOT add setBackgroundMessageHandler here - it must be at the entry point

interface WebViewPermissionRequest {
  grant: (resources: string[]) => void;
  deny: () => void;
  resources: string[];
}

function App(): React.JSX.Element {
  const [hasPermissions, setHasPermissions] = useState(false);
  const [keepScreenAwake, setKeepScreenAwake] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(true);
  const [fcmToken, setFcmToken] = useState<string | null>(null);
  const [isPaymentLoading, setIsPaymentLoading] = useState(false);

  const [initialUrl, setInitialUrl] = useState('https://astro5star.com');
  const webviewRef = React.useRef<WebView>(null);

  // Helper function to handle external URLs (UPI/PhonePe intents)
  const handleExternalUrl = useCallback(async (url: string): Promise<boolean> => {
    // Handle UPI/PhonePe schemes directly
    if (
      url.startsWith('phonepe://') ||
      url.startsWith('tez://') ||
      url.startsWith('paytmmp://') ||
      url.startsWith('upi://')
    ) {
      try {
        const canOpen = await Linking.canOpenURL(url);
        if (canOpen) {
          await Linking.openURL(url);
          return true;
        } else {
          console.log('Cannot open URL, no app available:', url);
          Alert.alert('App Not Found', 'Please install the UPI payment app.');
        }
      } catch (e) {
        console.log('Cannot open UPI URL:', e);
      }
      return false;
    }

    // Handle intent:// URLs (Android specific)
    if (url.startsWith('intent://')) {
      try {
        // Parse intent URL to extract scheme
        // Format: intent://path#Intent;scheme=upi;package=com.phonepe.app;...;end
        const schemeMatch = url.match(/scheme=([^;]+)/);
        const packageMatch = url.match(/package=([^;]+)/);

        if (schemeMatch) {
          const scheme = schemeMatch[1];
          // Build native URL from intent
          const pathStart = url.indexOf('://') + 3;
          const pathEnd = url.indexOf('#Intent');
          const path = pathEnd > -1 ? url.substring(pathStart, pathEnd) : url.substring(pathStart);
          const nativeUrl = `${scheme}://${path}`;

          console.log('Converted intent URL to:', nativeUrl);

          const canOpen = await Linking.canOpenURL(nativeUrl);
          if (canOpen) {
            await Linking.openURL(nativeUrl);
            return true;
          } else {
            // Try opening with just the scheme
            const basicUrl = `${scheme}://pay`;
            const canOpenBasic = await Linking.canOpenURL(basicUrl);
            if (canOpenBasic) {
              await Linking.openURL(url); // Try original intent URL
              return true;
            }
            Alert.alert('App Not Found', `Cannot open ${scheme} app. Please install it.`);
          }
        }
      } catch (e) {
        console.log('Intent URL handling failed:', e);
      }
      return false;
    }

    return false;
  }, []);

  useEffect(() => {
    const setupServices = async () => {
      // Init PhonePe SDK
      // PhonePe SDK Removed check browser flow
      // PhonePePaymentSDK.init(...) legacy code removed

      // 0. Check if opened from Notification (Quit State)
      const initialMsg = await messaging().getInitialNotification();
      if (initialMsg?.data?.callId) {
        console.log(
          'App opened from QUIT state via Call Notification:',
          initialMsg.data.callId,
        );
        setInitialUrl(
          `https://astro5star.com?incomingCallId=${initialMsg.data.callId}`,
        );
      }

      // 1. Request Permissions
      if (Platform.OS === 'android') {
        try {
          // Android 13+ Notification Permission
          if (Platform.Version >= 33) {
            await PermissionsAndroid.request(
              PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
            );
          }

          const granted = await PermissionsAndroid.requestMultiple([
            PermissionsAndroid.PERMISSIONS.CAMERA,
            PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
            PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE,
            PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES,
          ]);

          if (
            granted['android.permission.CAMERA'] ===
            PermissionsAndroid.RESULTS.GRANTED &&
            granted['android.permission.RECORD_AUDIO'] ===
            PermissionsAndroid.RESULTS.GRANTED
          ) {
            setHasPermissions(true);
          } else {
            console.log('Camera permission denied');
            setHasPermissions(true); // Allow anyway, but video might fail
          }
        } catch (err) {
          console.warn(err);
          setHasPermissions(true);
        }
      } else {
        setHasPermissions(true);
      }
      RNBootSplash.hide({ fade: true });

      // 2. Setup FCM
      try {
        const authStatus = await messaging().requestPermission();
        const enabled =
          authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
          authStatus === messaging.AuthorizationStatus.PROVISIONAL;

        if (enabled) {
          const token = await messaging().getToken();
          console.log('FCM Token:', token);
          setFcmToken(token);
        }
      } catch (error) {
        console.error('FCM Error:', error);
      }

      // 3. Start Forensic Service (Keep Alive)
      const channelId = await notifee.createChannel({
        id: 'persistent_service',
        name: 'Background Service',
        importance: AndroidImportance.HIGH,
      });

      await notifee.displayNotification({
        title: 'Astro 5 Star is Running',
        body: 'Ready for incoming calls.',
        android: {
          channelId,
          asForegroundService: true,
          ongoing: true,
          pressAction: { id: 'default' },
        },
      });
    };

    setupServices();

    // Foreground Message Listener
    const unsubscribe = messaging().onMessage(async remoteMessage => {
      console.log('A new FCM message arrived!', remoteMessage);
      // Show Local Notification (Simulate incoming call screen)
      // For now, Notifee handles this via the data payload if configured,
      // or we can explicitly display a "Heads Up" notification here.

      await notifee.displayNotification({
        title: remoteMessage.notification?.title || 'Incoming Call',
        body: remoteMessage.notification?.body || 'Tap to answer',
        android: {
          channelId: 'persistent_service', // Use existing channel
          importance: AndroidImportance.HIGH,
          pressAction: { id: 'default' },
          fullScreenAction: { id: 'default', launchActivity: 'default' }, // Attempt to wake
        },
      });

      Vibration.vibrate([0, 500, 200, 500]); // Vibrate
    });

    const netUnsub = NetInfo.addEventListener(state => {
      setIsConnected(state.isConnected);
    });

    // Deep Link Handler
    const handleDeepLink = (event: { url: string }) => {
      console.log('Deep Link received:', event.url);
      if (
        event.url.includes('payment_status') ||
        event.url.includes('payment-status') ||
        event.url.includes('astro5star')
      ) {
        // Parse status
        const isSuccess = event.url.includes('status=success');

        if (isSuccess) {
          Alert.alert("Payment Successful", "Your wallet has been recharged.");
          webviewRef.current?.reload();
        } else if (event.url.includes('status=failed')) {
          Alert.alert("Payment Failed", "Transaction could not be completed.");
        }

        // Also inject JS just in case
        const refreshJS = `
          (function() {
            if (window.fetchTransactionHistory) window.fetchTransactionHistory();
            if (window.socket && window.state && window.state.me) {
               window.socket.emit('get-wallet', { userId: window.state.me.userId });
            }
          })();
          true;
        `;
        webviewRef.current?.injectJavaScript(refreshJS);
      }
    };
    const linkingUnsub = Linking.addEventListener('url', handleDeepLink);

    // Check if app was launched by deep link
    Linking.getInitialURL().then(url => {
      if (url) {
        handleDeepLink({ url });
      }
    });

    // Handle notification tap when app is in background
    const notificationOpenedUnsub = messaging().onNotificationOpenedApp(remoteMessage => {
      console.log('Notification caused app to open from background:', remoteMessage);
      if (remoteMessage.data?.callId) {
        const { callId, fromUserId, type } = remoteMessage.data;
        console.log('Navigating to call:', { callId, fromUserId, type });
        // Navigate to incoming call screen via WebView
        webviewRef.current?.injectJavaScript(`
          window.location.href = 'https://astro5star.com?incomingCallId=${callId}';
          true;
        `);
      }
    });

    return () => {
      unsubscribe();
      netUnsub();
      linkingUnsub.remove();
      notificationOpenedUnsub();
    };
  }, []);

  if (!hasPermissions) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#0000ff" />
        <Text>Initializing...</Text>
      </View>
    );
  }

  if (isConnected === false) {
    return (
      <OfflineNotice
        onRetry={() => {
          NetInfo.fetch().then(state => setIsConnected(state.isConnected));
        }}
      />
    );
  }

  // Inject Token into WebView
  const injectedJS = `
    window.localStorage.setItem('fcmToken', '${fcmToken || ''}');
    console.log("FCM Token Injected:", '${fcmToken || ''}');
    true;
  `;

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <WebView
        ref={webviewRef}
        source={{ uri: initialUrl }}
        style={styles.webview}
        injectedJavaScript={injectedJS}
        javaScriptEnabled={true}
        domStorageEnabled={true}
        // Cookie & Content Settings (FIX for payment page loading)
        thirdPartyCookiesEnabled={true}
        sharedCookiesEnabled={Platform.OS === 'ios'}
        mixedContentMode="always"
        cacheEnabled={true}
        allowFileAccess={true}
        allowFileAccessFromFileURLs={true}
        allowUniversalAccessFromFileURLs={true}
        allowsInlineMediaPlayback={true}
        mediaPlaybackRequiresUserAction={false}
        startInLoadingState={true}
        renderLoading={() => (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#5B21B6" />
          </View>
        )}
        mediaCapturePermissionGrantType="grant"
        originWhitelist={['*']}
        // @ts-ignore
        onPermissionRequest={(request: WebViewPermissionRequest) => {
          request.grant(request.resources);
        }}
        onShouldStartLoadWithRequest={(request: { url: string }) => {
          const url = request.url;
          // Handle External Payment Apps (iOS primarily, but also Android fallback)
          if (
            url.startsWith('phonepe://') ||
            url.startsWith('tez://') ||
            url.startsWith('paytmmp://') ||
            url.startsWith('upi://') ||
            url.startsWith('intent://')
          ) {
            handleExternalUrl(url);
            return false; // Prevent WebView from loading this
          }
          return true;
        }}
        // Android-specific navigation handler for intents
        onNavigationStateChange={(navState: { url: string }) => {
          const url = navState.url;
          if (
            url.startsWith('phonepe://') ||
            url.startsWith('tez://') ||
            url.startsWith('paytmmp://') ||
            url.startsWith('upi://') ||
            url.startsWith('intent://')
          ) {
            handleExternalUrl(url);
          }
        }}
        onMessage={(event: { nativeEvent: { data: string } }) => {
          try {
            const data = JSON.parse(event.nativeEvent.data);
            if (data.type === 'VIBRATE') Vibration.vibrate([0, 1000, 500, 1000]);
            if (data.type === 'KEEP_AWAKE') setKeepScreenAwake(!!data.enable);
            if (data.type === 'OPEN_EXTERNAL') Linking.openURL(data.url).catch(() => { });

            // ============ ROBUST PAYMENT FLOW WITH FALLBACKS ============
            // Priority: 1) UPI Intent (opens GPay/PhonePe) → 2) External Browser → 3) Error

            // Method 1: Open UPI Intent directly (Best - opens GPay/PhonePe/Paytm)
            if (data.type === 'OPEN_UPI_INTENT' && data.intentUrl) {
              setIsPaymentLoading(false);
              console.log('Opening UPI Intent:', data.intentUrl);

              Linking.openURL(data.intentUrl)
                .then(() => {
                  console.log('UPI app opened successfully');
                })
                .catch((err) => {
                  console.log('UPI Intent failed, trying browser:', err);
                  // Fallback to browser if UPI intent fails
                  if (data.paymentUrl) {
                    Linking.openURL(data.paymentUrl).catch(() => {
                      Alert.alert('Payment Error', 'No UPI app found. Please install GPay or PhonePe.');
                    });
                  } else {
                    Alert.alert('Payment Error', 'No UPI app found. Please install GPay or PhonePe.');
                  }
                });
            }

            // Method 2: Open Payment URL in external browser
            if (data.type === 'OPEN_PAYMENT_URL' && data.url) {
              setIsPaymentLoading(false);
              console.log('Opening Payment URL in browser:', data.url);

              Linking.openURL(data.url).catch((err) => {
                console.log('Failed to open payment URL:', err);
                Alert.alert('Error', 'Could not open payment page');
              });
            }

            // Legacy UPI_PAY handler (for backward compatibility)
            if (data.type === 'UPI_PAY') {
              setIsPaymentLoading(true);
              console.log('Legacy UPI_PAY received - should use OPEN_UPI_INTENT or OPEN_PAYMENT_URL');
              setTimeout(() => setIsPaymentLoading(false), 2000);
            }
          } catch (e) { }
        }}
      />
      {/* Payment Loading Overlay */}
      {isPaymentLoading && (
        <View style={styles.paymentLoadingOverlay}>
          <View style={styles.paymentLoadingCard}>
            <ActivityIndicator size="large" color="#5B21B6" />
            <Text style={styles.paymentLoadingText}>Processing Payment...</Text>
            <Text style={styles.paymentLoadingSubtext}>Please wait, do not close the app</Text>
          </View>
        </View>
      )}
      {keepScreenAwake && <KeepAwake />}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  webview: {
    flex: 1,
  },
  loadingContainer: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'white',
  },
  paymentLoadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  paymentLoadingCard: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 10,
  },
  paymentLoadingText: {
    marginTop: 15,
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1F2937',
  },
  paymentLoadingSubtext: {
    marginTop: 5,
    fontSize: 13,
    color: '#6B7280',
  },
});

export default App;

