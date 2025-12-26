/**
 * @format
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

// Firebase imports for background message handling
import messaging from '@react-native-firebase/messaging';
import notifee, { AndroidImportance } from '@notifee/react-native';

/**
 * Background Message Handler
 * CRITICAL: This MUST be registered in index.js (outside of any React component)
 * Handles FCM messages when app is in background or killed state
 */
messaging().setBackgroundMessageHandler(async remoteMessage => {
    console.log('[Background] FCM Message received:', JSON.stringify(remoteMessage));

    // Create notification channel for Android (required for Android 8+)
    const channelId = await notifee.createChannel({
        id: 'calls',
        name: 'Incoming Calls',
        importance: AndroidImportance.HIGH,
        sound: 'default',
        vibration: true,
    });

    // Display local notification using Notifee
    await notifee.displayNotification({
        title: remoteMessage.notification?.title || 'Incoming Call',
        body: remoteMessage.notification?.body || 'Tap to answer',
        android: {
            channelId,
            importance: AndroidImportance.HIGH,
            pressAction: { id: 'default' },
            fullScreenAction: {
                id: 'default',
                launchActivity: 'default',
            },
            // Use data payload from FCM
            ...(remoteMessage.data && { data: remoteMessage.data }),
        },
        data: remoteMessage.data,
    });
});

// Register main application component
AppRegistry.registerComponent(appName, () => App);
