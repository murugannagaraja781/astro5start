import messaging from '@react-native-firebase/messaging';
import {Platform, PermissionsAndroid} from 'react-native';

/**
 * Request notification permission from the user
 * Call this ONCE after login
 */
export async function requestNotificationPermission(): Promise<boolean> {
  // Request Android 13+ POST_NOTIFICATIONS permission
  if (Platform.OS === 'android' && Platform.Version >= 33) {
    try {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
      );
      if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
        console.log('POST_NOTIFICATIONS permission denied');
      }
    } catch (err) {
      console.warn('Error requesting POST_NOTIFICATIONS permission:', err);
    }
  }

  // Request Firebase messaging permission
  const authStatus = await messaging().requestPermission();
  const enabled =
    authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
    authStatus === messaging.AuthorizationStatus.PROVISIONAL;

  console.log('Notification permission status:', authStatus, enabled ? 'ENABLED' : 'DISABLED');
  return enabled;
}

/**
 * Get the FCM device token
 * This token is what the backend needs to send push notifications
 */
export async function getFcmToken(): Promise<string | null> {
  try {
    const token = await messaging().getToken();
    console.log('FCM TOKEN:', token);
    return token;
  } catch (error) {
    console.error('Error getting FCM token:', error);
    return null;
  }
}

/**
 * Save FCM token to backend after successful login
 * @param userId - The logged-in user's ID
 * @param token - The FCM device token
 */
export async function saveFcmTokenToBackend(
  userId: string,
  token: string,
): Promise<boolean> {
  try {
    const response = await fetch('https://astro5star.com/api/save-fcm', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        userId,
        fcmToken: token,
      }),
    });

    if (!response.ok) {
      console.error('Failed to save FCM token:', await response.text());
      return false;
    }

    console.log('FCM token saved successfully');
    return true;
  } catch (error) {
    console.error('Error saving FCM token to backend:', error);
    return false;
  }
}

/**
 * Complete FCM setup flow - call after OTP login success
 */
export async function setupNotificationsAfterLogin(userId: string): Promise<void> {
  const permissionGranted = await requestNotificationPermission();
  
  if (permissionGranted) {
    const token = await getFcmToken();
    if (token) {
      await saveFcmTokenToBackend(userId, token);
    }
  }
}
