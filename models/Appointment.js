const mongoose = require('mongoose');

const AppointmentSchema = new mongoose.Schema({
    appointmentId: { type: String, unique: true },
    clientId: { type: String, required: true, index: true },
    astrologerId: { type: String, required: true, index: true },
    type: { type: String, enum: ['chat', 'audio', 'video'], default: 'chat' },
    status: { 
        type: String, 
        enum: ['waiting', 'notified', 'booked', 'in-progress', 'completed', 'cancelled', 'expired'], 
        default: 'waiting' 
    },
    requestedAt: { type: Date, default: Date.now },
    scheduledFor: { type: Date }, // For future appointments
    queuePosition: { type: Number }, // Current position in queue
    notifiedAt: { type: Date }, // When client was notified that it's their turn
    sessionId: { type: String }, // Linked session once started
    meta: {
        reason: String,
        duration: Number // Expected duration
    }
}, { timestamps: true });

// Ensure a client can't join queue for the same astrologer multiple times (waiting)
AppointmentSchema.index({ clientId: 1, astrologerId: 1, status: 1 }, { unique: true, partialFilterExpression: { status: 'waiting' } });

module.exports = mongoose.model('Appointment', AppointmentSchema);
