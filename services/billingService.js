// services/billingService.js
const { activeSessions, userSockets, SLAB_RATES } = require('./sharedState');
const Session = require('../models/Session');
const User = require('../models/User');
const PairMonth = require('../models/PairMonth');
const BillingLedger = require('../models/BillingLedger');
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

            const isClientConnected = !!userSockets.get(session.clientId);
            const isAstroConnected = !!userSockets.get(session.astrologerId);

            if (isClientConnected && isAstroConnected) {
                // Sync with mobile app timer using differential time
                const secondsElapsed = Math.floor((now - session.actualBillingStart) / 1000) + 1;
                session.elapsedBillableSeconds = secondsElapsed;

                // Charge logic: Trigger at 1s, 61s, 121s, etc. (Start of each minute)
                const currentMinuteIndex = Math.floor((secondsElapsed - 1) / 60) + 1;
                
                if (currentMinuteIndex > (session.lastBilledMinute || 0)) {
                    // CATCH-UP LOOP: Even if they reconnect after 2-3 mins, bill all skipped minutes
                    for (let m = (session.lastBilledMinute || 0) + 1; m <= currentMinuteIndex; m++) {
                        const billingType = (m === 1) ? 'first_60_full' : 'slab';
                        processBillingCharge(sessionId, 60, m, billingType, io);
                    }
                    session.lastBilledMinute = currentMinuteIndex;
                }

                if (session.elapsedBillableSeconds % 10 === 0 && session.pairMonthId) {
                    updateSessionSlab(session);
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

async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type, io) {
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

        let amountToCharge = 0;
        let adminShare = 0;
        let astroShare = 0;
        let reason = '';

        if (type === 'first_60_full') {
            amountToCharge = pricePerMin;
            adminShare = amountToCharge;
            astroShare = 0;
            reason = 'first_60';
        } else if (type === 'early_exit') {
            amountToCharge = pricePerMin;
            const rate = SLAB_RATES[1] || 0.30;
            astroShare = amountToCharge * rate;
            adminShare = amountToCharge - astroShare;
            reason = 'first_60_min_charge';
        } else if (type === 'slab') {
            const activeSess = activeSessions.get(sessionId);
            const currentSlab = activeSess?.currentSlab || 1;
            const rate = SLAB_RATES[currentSlab] || 0.30;

            amountToCharge = pricePerMin;
            astroShare = amountToCharge * rate;
            adminShare = amountToCharge - astroShare;
            reason = `slab_${currentSlab}`;
        } else if (type === 'fraction') {
            amountToCharge = pricePerMin;
            adminShare = amountToCharge;
            astroShare = 0;
            reason = 'fraction_roundup';
        } else {
            return;
        }

        const totalToDeduct = amountToCharge;
        if (client.walletBalance >= totalToDeduct) {
            let mainDeduct = totalToDeduct;
            let superDeduct = 0;

            // Apply Super Wallet Logic (30% logic mentioned in original code)
            // If super wallet has balance, use it for 30% of the charge
            let potentialSuperDeduct = totalToDeduct * 0.3;
            if (client.superWalletBalance > 0) {
                if (client.superWalletBalance >= potentialSuperDeduct) {
                    superDeduct = potentialSuperDeduct;
                    mainDeduct = totalToDeduct - superDeduct;
                } else {
                    superDeduct = client.superWalletBalance;
                    mainDeduct = totalToDeduct - superDeduct;
                }
                client.superWalletBalance -= superDeduct;
            }

            client.walletBalance -= mainDeduct;
            await client.save();

            if (astroShare > 0) {
                astro.walletBalance += astroShare;
                astro.totalEarnings = (astro.totalEarnings || 0) + astroShare;
                await astro.save();
            }

            await BillingLedger.create({
                billingId: crypto.randomUUID(),
                sessionId,
                minuteIndex,
                chargedToClient: amountToCharge,
                creditedToAstrologer: astroShare,
                adminAmount: adminShare,
                reason
            });

            const activeSess = activeSessions.get(sessionId);
            if (activeSess) {
                activeSess.totalDeducted = (activeSess.totalDeducted || 0) + amountToCharge;
                activeSess.totalEarned = (activeSess.totalEarned || 0) + astroShare;
            }

            // Notify via Rooms
            io.to(client.userId).emit('wallet-update', {
                balance: client.walletBalance,
                superBalance: client.superWalletBalance || 0
            });
            io.to(astro.userId).emit('wallet-update', {
                balance: astro.walletBalance,
                totalEarnings: astro.totalEarnings || 0,
                superBalance: astro.superWalletBalance || 0
            });

        } else {
            forceEndSession(sessionId, 'insufficient_funds', io);
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
