const Appointment = require('../models/Appointment');
const User = require('../models/User');
const { v4: uuidv4 } = require('uuid');
const { sendFcmV1Push } = require('../services/fcmService');

const joinQueue = async (req, res) => {
    try {
        const { clientId, astrologerId, type } = req.body;
        if (!clientId || !astrologerId) return res.json({ ok: false, error: 'Missing IDs' });

        // Check if already in queue
        const existing = await Appointment.findOne({ clientId, astrologerId, status: 'waiting' });
        if (existing) return res.json({ ok: true, message: 'Already in queue', appointmentId: existing.appointmentId });

        // Get current queue length to determine position
        const queueCount = await Appointment.countDocuments({ astrologerId, status: 'waiting' });

        const appointment = await Appointment.create({
            appointmentId: 'APT-' + uuidv4().substring(0, 8).toUpperCase(),
            clientId,
            astrologerId,
            type: type || 'chat',
            status: 'waiting',
            queuePosition: queueCount + 1
        });

        res.json({ ok: true, appointmentId: appointment.appointmentId, position: appointment.queuePosition });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getMyQueueStatus = async (req, res) => {
    try {
        const { userId } = req.params;
        const activeQueue = await Appointment.find({ clientId: userId, status: 'waiting' })
            .populate({ path: 'astrologerId', select: 'name image', model: 'User', localField: 'astrologerId', foreignField: 'userId' });
        
        // Recalculate true positions
        const results = await Promise.all(activeQueue.map(async (apt) => {
            const countAhead = await Appointment.countDocuments({ 
                astrologerId: apt.astrologerId.userId, 
                status: 'waiting', 
                requestedAt: { $lt: apt.requestedAt } 
            });
            return {
                ...apt._doc,
                positionAhead: countAhead
            };
        }));

        res.json({ ok: true, queue: results });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const processNextInQueue = async (astrologerId, io) => {
    try {
        // Find the oldest waiting appointment
        const nextApt = await Appointment.findOne({ astrologerId, status: 'waiting' }).sort({ requestedAt: 1 });
        if (!nextApt) return;

        const client = await User.findOne({ userId: nextApt.clientId });
        if (client && client.fcmToken) {
            // Notify client
            await sendFcmV1Push(client.fcmToken, 
                { type: 'QUEUE_TURN', astrologerId, appointmentId: nextApt.appointmentId },
                { title: "It's your turn!", body: "Astrologer is now free. Connect now!" }
            );
            
            if (io) {
                io.to(client.userId).emit('queue-turn', { astrologerId, appointmentId: nextApt.appointmentId });
            }

            nextApt.status = 'notified';
            nextApt.notifiedAt = new Date();
            await nextApt.save();
        }
    } catch (err) {
        console.error('Error processing next in queue', err);
    }
};

module.exports = { joinQueue, getMyQueueStatus, processNextInQueue };
