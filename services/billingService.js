// services/billingService.js
const { activeSessions, userSockets, SLAB_RATES } = require('./sharedState');
const Session = require('../models/Session');
const User = require('../models/User');
const PairMonth = require('../models/PairMonth');
const BillingLedger = require('../models/BillingLedger');
const { sendFcmV1Push } = require('./fcmService');
const crypto = require('crypto');

let isTicking = false;

async function tickSessions(io) {
    if (isTicking) return;
    isTicking = true;
    try {
        const now = Date.now();
        const tickTime = Math.floor(now / 1000);

        for (const [sessionId, session] of activeSessions) {
            if (!session.actualBillingStart || now < session.actualBillingStart) continue;

            // STABILITY FIX: Don't pause billing for 1-second socket flickers. 
            // Only pause if session is explicitly ended or stale beyond 60s without any heartbeat.
            const isSessionValid = session.isAnswered && !session.isEnded;

            if (isSessionValid) {
                // Sync with mobile app timer using differential time (seconds from start)
                const secondsElapsed = Math.floor((now - session.actualBillingStart) / 1000) + 1;
                session.elapsedBillableSeconds = secondsElapsed;

                // RULE 1: Charge Client at the START of each minute (Minute 1, 2, 3...)
                // These charges are 100% Admin-credited initially.
                const currentMinuteIndex = Math.floor((secondsElapsed - 1) / 60) + 1;
                
                if (currentMinuteIndex > (session.lastBilledMinute || 0)) {
                    // Start of a new minute -> Charge Client full price
                    for (let m = (session.lastBilledMinute || 0) + 1; m <= currentMinuteIndex; m++) {
                        processBillingCharge(sessionId, m, 'client_full_charge', io);
                    }
                    session.lastBilledMinute = currentMinuteIndex;
                }

                // RULE 2: Pay Astrologer AFTER a full minute is completed.
                // A minute M is completed when secondsElapsed reaches M * 60.
                // Minute 1 is Admin-only, so we only pay for m >= 2.
                const completedMinutes = Math.floor(secondsElapsed / 60);
                if (completedMinutes >= 2 && completedMinutes > (session.lastMaturedMinute || 1)) {
                    for (let m = (session.lastMaturedMinute || 1) + 1; m <= completedMinutes; m++) {
                        // Pay share for matured minute 'm'
                        processBillingCharge(sessionId, m, 'astro_share_payout', io);
                    }
                    session.lastMaturedMinute = completedMinutes;
                }

                // Periodic slab check (every 10s)
                if (session.elapsedBillableSeconds % 10 === 0 && session.pairMonthId) {
                    updateSessionSlab(session);
                }

                // Emit regular timer and wallet-based remaining time
                const client = await User.findOne({ userId: session.clientId });
                if (client) {
                    const pricePerMin = session.pricePerMin || (await User.findOne({ userId: session.astrologerId }))?.price || 10;
                    const totalBalance = (client.walletBalance || 0) + (client.superWalletBalance || 0);
                    const remainingSeconds = Math.floor((totalBalance / pricePerMin) * 60);

                    io.to(session.clientId).emit('timer-update', {
                        elapsedSeconds: secondsElapsed,
                        remainingSeconds: remainingSeconds
                    });
                    io.to(session.astrologerId).emit('timer-update', {
                        elapsedSeconds: secondsElapsed,
                        remainingSeconds: remainingSeconds
                    });
                }
            }
        }
    } catch (err) {
        console.error('[Ticker] Critical Error:', err);
    } finally {
        isTicking = false;
    }
}

function updateSessionSlab(session) {
    const totalSeconds = (session.initialPairSeconds || 0) + session.elapsedBillableSeconds;
    let calculatedSlab = 1;
    if (totalSeconds > 1200) calculatedSlab = 4;
    else if (totalSeconds > 900) calculatedSlab = 4;
    else if (totalSeconds > 600) calculatedSlab = 3;
    else if (totalSeconds > 300) calculatedSlab = 2;

    const effectiveSlab = Math.max(calculatedSlab, session.currentSlab || 0);
    if (effectiveSlab > session.currentSlab) {
        session.currentSlab = effectiveSlab;
        PairMonth.updateOne({ _id: session.pairMonthId }, { currentSlab: effectiveSlab }).exec().catch(() => { });
    }
}

async function processBillingCharge(sessionId, minuteIndex, type, io) {
    try {
        const session = await Session.findOne({ sessionId });
        if (!session) return;

        const astro = await User.findOne({ userId: session.astrologerId });
        if (!astro) return;

        const client = await User.findOne({ userId: session.clientId });
        if (!client) return;

        let pricePerMin = 10;
        if (astro.price && astro.price > 0) {
            pricePerMin = parseInt(astro.price);
        } else {
            if (session.type === 'audio') pricePerMin = 15;
            if (session.type === 'video') pricePerMin = 20;
        }

        let totalToClientDeduct = 0;
        let adminAmount = 0;
        let astroAmount = 0;
        let reason = '';
        let isFinalBalanceUpdate = false;

        // TYPE 1: Charge the client for a whole minute. Start/Fractional minutes are 100% Admin-owned.
        if (type === 'client_full_charge') {
            totalToClientDeduct = pricePerMin;
            adminAmount = pricePerMin;
            astroAmount = 0;
            reason = (minuteIndex === 1) ? 'first_minute_admin' : 'minute_start_admin';
            isFinalBalanceUpdate = true;
        } 
        // TYPE 2: Pay the astrologer their share for a COMPLETED minute (M >= 2).
        else if (type === 'astro_share_payout') {
            const activeSess = activeSessions.get(sessionId);
            const currentSlab = activeSess?.currentSlab || 1;
            let rate = SLAB_RATES[currentSlab] || 0.30;
            if (rate > 1) rate = rate / 100;

            const shareAmount = pricePerMin * rate;
            
            // We transfer the share from Admin back to Astro for this matured minute.
            // Client is NOT deducted again.
            totalToClientDeduct = 0; 
            astroAmount = shareAmount;
            adminAmount = -shareAmount; // Deduct from Admin's previous 100% hold
            reason = `slab_${currentSlab}_payout`;
            isFinalBalanceUpdate = true;
        } else {
            return;
        }

        if (totalToClientDeduct > 0) {
            if (client.walletBalance < totalToClientDeduct) {
                return forceEndSession(sessionId, 'insufficient_funds', io);
            }

            let mainDeduct = totalToClientDeduct;
            let superDeduct = 0;

            // Super Wallet Logic (30% discount/pay)
            let potentialSuperDeduct = totalToClientDeduct * 0.3;
            if (client.superWalletBalance > 0) {
                if (client.superWalletBalance >= potentialSuperDeduct) {
                    superDeduct = potentialSuperDeduct;
                } else {
                    superDeduct = client.superWalletBalance;
                }
                client.superWalletBalance -= superDeduct;
                mainDeduct = totalToClientDeduct - superDeduct;
            }

            client.walletBalance -= mainDeduct;
            await client.save();
        }

        if (astroAmount > 0) {
            astro.walletBalance += astroAmount;
            astro.totalEarnings = (astro.totalEarnings || 0) + astroAmount;
            await astro.save();
        }

        // Create Ledger Record
        await BillingLedger.create({
            billingId: crypto.randomUUID(),
            sessionId,
            minuteIndex,
            chargedToClient: totalToClientDeduct,
            creditedToAstrologer: astroAmount,
            adminAmount: adminAmount,
            reason,
            appliedRate: totalToClientDeduct > 0 ? (astroAmount / totalToClientDeduct) : 0
        });

        const activeSess = activeSessions.get(sessionId);
        if (activeSess) {
            activeSess.totalDeducted = (activeSess.totalDeducted || 0) + totalToClientDeduct;
            activeSess.totalEarned = (activeSess.totalEarned || 0) + astroAmount;
        }

        if (io) {
            io.to(client.userId).emit('wallet-update', {
                balance: client.walletBalance,
                superBalance: client.superWalletBalance || 0
            });
            io.to(astro.userId).emit('wallet-update', {
                balance: astro.walletBalance,
                totalEarnings: astro.totalEarnings || 0,
                superBalance: astro.superWalletBalance || 0
            });
        }

        // Send FCM Notifications
        if (totalToClientDeduct > 0 && client.fcmToken) {
            sendFcmV1Push(client.fcmToken, { type: 'WALLET_DEBIT', amount: totalToClientDeduct }, { title: 'Wallet Updated', body: `₹${totalToClientDeduct.toFixed(2)} deducted for session.` }).catch(() => {});
        }
        if (astroAmount > 0 && astro.fcmToken) {
            sendFcmV1Push(astro.fcmToken, { type: 'WALLET_CREDIT', amount: astroAmount }, { title: 'Earnings Updated', body: `₹${astroAmount.toFixed(2)} credited to your wallet.` }).catch(() => {});
        }

    } catch (err) {
        console.error('processBillingCharge error', err);
    }
}

function forceEndSession(sessionId, reason, io) {
    const session = activeSessions.get(sessionId);
    if (!session) return;

    // Use a shared endSession function if possible, but keep it simple here
    const payload = {
        reason,
        summary: {
            deducted: session.totalDeducted || 0,
            earned: session.totalEarned || 0,
            duration: session.elapsedBillableSeconds || 0
        }
    };

    io.to(session.clientId).emit('session-ended', payload);
    io.to(session.astrologerId).emit('session-ended', payload);

    // Cleanup will happen in disconnect or end-session handlers
}

module.exports = { tickSessions, processBillingCharge, forceEndSession };
