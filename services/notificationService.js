// services/notificationService.js
const { sendFcmV1Push } = require('./fcmService');
const { sendSmsNotification } = require('./otpService');
const User = require('../models/User');

const notifyFollowersOfOnlineStatus = async (astrologerId) => {
    try {
        const astrologer = await User.findOne({ userId: astrologerId }).select('userId name followers image').lean();
        if (!astrologer || !astrologer.followers || astrologer.followers.length === 0) return;

        const { formatImageUrl } = require('../utils/formatImage');
        const astroImg = formatImageUrl(astrologer.image, astrologer.name);

        console.log(`[Notification] Notifying ${astrologer.followers.length} followers of ${astrologer.name}'s online status.`);

        const followers = await User.find({ userId: { $in: astrologer.followers } }).select('userId fcmToken phone').lean();
        
        // Proper Tamil Notification for SMS
        const smsMessage = `🌟 Astro 5 Star: உங்களுக்குப் பிடித்தமான ஜோதிடர் ${astrologer.name} இப்போது ஆன்லைனில் வந்துள்ளார். உடனே அவரிடம் பேச ஆப்பை திறக்கவும்!`;

        for (const follower of followers) {
            // 1. Send Push
            if (follower.fcmToken) {
                const notification = {
                    title: "🌟 ஜோதிடர் ஆன்லைனில் உள்ளார்!",
                    body: `உங்களுக்குப் பிடித்தமான ஜோதிடர் ${astrologer.name} இப்போது ஆன்லைனில் உங்கள் அழைப்பிற்காகக் காத்திருக்கிறார். உடனே அவரிடம் ஆலோசனை பெறவும்!`,
                    image: astroImg
                };
                const data = { 
                    type: 'ASTRO_ONLINE', 
                    astrologerId: astrologer.userId, 
                    astrologerName: astrologer.name,
                    image: astroImg
                };
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
        const Appointment = require('../models/Appointment');
        const astrologer = await User.findOne({ userId: astrologerId }).select('userId name image').lean();
        if (!astrologer) return;

        const { formatImageUrl } = require('../utils/formatImage');
        const astroImg = formatImageUrl(astrologer.image, astrologer.name);

        // Using Appointment model for queue instead of old Waitlist model for live consistency
        const pending = await Appointment.find({ astrologerId, status: 'waiting' }).lean();
        if (pending.length === 0) return;

        console.log(`[Waitlist] Notifying ${pending.length} waiting users for ${astrologer.name}`);

        const clientIds = pending.map(p => p.clientId);
        const clients = await User.find({ userId: { $in: clientIds } }).select('userId fcmToken phone').lean();
        
        const smsMessage = `🔔 Astro 5 Star: ஜோதிடர் ${astrologer.name} இப்போது ஆன்லைனில் வந்துள்ளார். உங்கள் காத்திருப்பு முடிந்தது, உடனே அவரிடம் பேசவும்!`;

        for (const client of clients) {
            // 1. Send Push
            if (client.fcmToken) {
                const notification = {
                    title: "🔔 ஜோதிடர் முன்பதிவு!",
                    body: `ஜோதிடர் ${astrologer.name} இப்போது ஆன்லைனில் வந்துள்ளார். ஏற்கனவே காத்திருப்புப் பட்டியலில் இருக்கும் நீங்கள், உடனே அவரிடம் ஆலோசனை பெறலாம்!`,
                    image: astroImg
                };
                const data = { type: 'ASTRO_AVAILABLE', astrologerId: astrologer.userId, image: astroImg };
                sendFcmV1Push(client.fcmToken, data, notification).catch(e => {});
            }

            // 2. Send SMS
            if (client.phone) {
                sendSmsNotification(client.phone, smsMessage);
            }
        }

    } catch (err) {
        console.error('[NotificationService] Waitlist error:', err);
    }
};

module.exports = { notifyFollowersOfOnlineStatus, notifyWaitlistUsers };
