// services/notificationService.js
const { sendFcmV1Push } = require('./fcmService');
const { sendSmsNotification } = require('./otpService');
const User = require('../models/User');

const notifyFollowersOfOnlineStatus = async (astrologerId) => {
    try {
        const astrologer = await User.findOne({ userId: astrologerId }).select('userId name followers').lean();
        if (!astrologer || !astrologer.followers || astrologer.followers.length === 0) return;

        console.log(`[Notification] Notifying ${astrologer.followers.length} followers of ${astrologer.name}'s online status.`);

        const followers = await User.find({ userId: { $in: astrologer.followers } }).select('userId fcmToken phone').lean();
        
        const smsMessage = `🌟 Astro 5 Star: ${astrologer.name} is now ONLINE. Tap to connect!`;

        for (const follower of followers) {
            // 1. Send Push
            if (follower.fcmToken) {
                const notification = {
                    title: "🌟 Astrologer Online!",
                    body: `${astrologer.name} is now online and available for consultation. Tap to connect!`
                };
                const data = { type: 'ASTRO_ONLINE', astrologerId: astrologer.userId, astrologerName: astrologer.name };
                sendFcmV1Push(follower.fcmToken, data, notification).catch(() => {});
            }

            // 2. Send SMS
            if (follower.phone) {
                sendSmsNotification(follower.phone, smsMessage);
            }
        }

    } catch (err) {
        console.error('[NotificationService] Error:', err);
    }
};

const notifyWaitlistUsers = async (astrologerId) => {
    try {
        const Waitlist = require('../models/Waitlist');
        const astrologer = await User.findOne({ userId: astrologerId }).select('userId name').lean();
        if (!astrologer) return;

        const pending = await Waitlist.find({ astrologerId, status: 'pending' }).lean();
        if (pending.length === 0) return;

        console.log(`[Waitlist] Notifying ${pending.length} waitlisted users for ${astrologer.name}`);

        const clientIds = pending.map(p => p.clientId);
        const clients = await User.find({ userId: { $in: clientIds } }).select('userId fcmToken phone').lean();
        
        const smsMessage = `🔔 Astro 5 Star: ${astrologer.name} is now ONLINE. Your wait is over, connect now!`;

        for (const client of clients) {
            // 1. Send Push
            if (client.fcmToken) {
                const notification = {
                    title: "🔔 Astrologer Available!",
                    body: `${astrologer.name} is now online and available for your consultation. Connect now!`
                };
                const data = { type: 'ASTRO_AVAILABLE', astrologerId: astrologer.userId };
                sendFcmV1Push(client.fcmToken, data, notification).catch(e => {});
            }

            // 2. Send SMS
            if (client.phone) {
                sendSmsNotification(client.phone, smsMessage);
            }
        }

        // Mark as notified
        await Waitlist.updateMany(
            { astrologerId, status: 'pending' },
            { status: 'notified', notifiedAt: new Date() }
        );

    } catch (err) {
        console.error('[NotificationService] Waitlist error:', err);
    }
};

module.exports = { notifyFollowersOfOnlineStatus, notifyWaitlistUsers };
