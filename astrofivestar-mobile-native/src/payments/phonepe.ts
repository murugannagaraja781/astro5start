import PhonePePaymentSDK from 'react-native-phonepe-pg';
import { Linking } from 'react-native';

/**
 * PhonePe Payment Helper
 * Opens native PhonePe app for seamless UPI payment
 */

/**
 * Start PhonePe transaction using the SDK (proper API)
 * @param base64Request - Base64 encoded request from backend
 * @returns Payment result from PhonePe SDK
 */
export async function startPhonePeTransaction(base64Request: string): Promise<{ status: string; error?: string }> {
    try {
        console.log('[PhonePe SDK] Starting transaction...');

        // PhonePePaymentSDK.startTransaction expects an options object:
        // { request: string (base64), appSchema: string | null }
        const result = await PhonePePaymentSDK.startTransaction({
            request: base64Request,
            appSchema: 'astro5star', // Your app's custom URL scheme for iOS
        });

        console.log('[PhonePe SDK] Transaction result:', result);
        return {
            status: result.status || 'UNKNOWN',
            error: result.error,
        };
    } catch (error: any) {
        console.error('[PhonePe SDK] Transaction error:', error);
        return {
            status: 'FAILURE',
            error: error.message || 'Transaction failed',
        };
    }
}

/**
 * Open PhonePe payment URL in browser (fallback method)
 * @param paymentUrl - Payment URL from backend
 */
export async function openPhonePeInBrowser(paymentUrl: string): Promise<boolean> {
    try {
        const canOpen = await Linking.canOpenURL(paymentUrl);
        if (canOpen) {
            await Linking.openURL(paymentUrl);
            return true;
        }
        return false;
    } catch (error) {
        console.error('[PhonePe] Browser open error:', error);
        return false;
    }
}

/**
 * Initialize payment via backend and handle PhonePe flow
 * @param userId - Current user's ID
 * @param amount - Amount in Rupees
 * @param baseUrl - Backend base URL
 * @returns Payment initialization result
 */
export async function initiatePhonePePayment(
    userId: string,
    amount: number,
    baseUrl: string = 'https://astro5star.com'
): Promise<{ success: boolean; transactionId?: string; paymentUrl?: string; error?: string }> {
    try {
        console.log('[PhonePe] Initiating payment:', { userId, amount });

        // Step 1: Create payment on backend
        const response = await fetch(`${baseUrl}/api/phonepe/init`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, amount }),
        });

        const data = await response.json();

        if (!data.ok) {
            console.error('[PhonePe] Backend init failed:', data);
            return { success: false, error: data.error || 'Payment initialization failed' };
        }

        // Get payment URL from response
        const paymentUrl = data.data?.instrumentResponse?.redirectInfo?.url;
        const transactionId = data.transactionId;

        if (!paymentUrl) {
            return { success: false, error: 'No payment URL received' };
        }

        console.log('[PhonePe] Payment URL:', paymentUrl);
        console.log('[PhonePe] Transaction ID:', transactionId);

        return {
            success: true,
            transactionId,
            paymentUrl,
        };

    } catch (error: any) {
        console.error('[PhonePe] Payment initiation error:', error);
        return { success: false, error: error.message || 'Payment failed' };
    }
}

/**
 * Check payment status from backend
 * @param transactionId - The merchant transaction ID
 * @param baseUrl - Backend base URL
 * @returns Payment status
 */
export async function checkPaymentStatus(
    transactionId: string,
    baseUrl: string = 'https://astro5star.com'
): Promise<{ status: string; amount?: number; error?: string }> {
    try {
        const response = await fetch(`${baseUrl}/api/phonepe/status/${transactionId}`);
        const data = await response.json();

        return {
            status: data.status || 'unknown',
            amount: data.amount,
            error: data.error,
        };
    } catch (error: any) {
        console.error('[PhonePe] Status check error:', error);
        return { status: 'error', error: error.message };
    }
}

/**
 * Complete payment flow - call from Add Wallet button
 * Opens PhonePe payment URL in external browser
 */
export async function addMoneyToWallet(
    userId: string,
    amount: number,
    baseUrl: string = 'https://astro5star.com'
): Promise<{ success: boolean; transactionId?: string; error?: string }> {
    // Step 1: Initialize payment
    const initResult = await initiatePhonePePayment(userId, amount, baseUrl);

    if (!initResult.success || !initResult.paymentUrl) {
        return { success: false, error: initResult.error };
    }

    // Step 2: Open payment URL in browser
    const opened = await openPhonePeInBrowser(initResult.paymentUrl);

    if (!opened) {
        return { success: false, error: 'Could not open payment page' };
    }

    return {
        success: true,
        transactionId: initResult.transactionId
    };
}
