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
            if (!session.actualBillingStart) {
                // If it's a valid session but missing start time, log it once
                if (session.isAnswered && !session._skipLogSent) {
                    console.log(`[Ticker] Skipping session ${sessionId}: missing actualBillingStart. isAnswered=${session.isAnswered}`);
                    session._skipLogSent = true;
                }
                continue;
            }
            if (now < session.actualBillingStart) continue;

            // STABILITY FIX: Don't pause billing for 1-second socket flickers. 
            // Only pause if session is explicitly ended or stale beyond 60s without any heartbeat.
            const isSessionValid = session.isAnswered && !session.isEnded;

            if (isSessionValid) {
                // SYNC: Differential time (seconds from start)
                const secondsElapsed = Math.floor((now - session.actualBillingStart) / 1000);
                session.elapsedBillableSeconds = secondsElapsed;

                let needsDbSync = false;

                // RULE: Global Free Call Duration (e.g., first 3 minutes free)
                const { REFERRAL_CONFIG } = require('./sharedState');
                const freeSeconds = (REFERRAL_CONFIG.FREE_CALL_DURATION || 0) * 60;

                if (secondsElapsed > freeSeconds) {
                    const billableSecondsSinceFree = secondsElapsed - freeSeconds;
                    
                    // RULE: Charge Client at the END of each minute (at 60s, 120s...)
                    // This satisfies the requirement: "No charge if session ends within the first minute"
                    const currentCompletedMinute = Math.floor(billableSecondsSinceFree / 60);
                    const MAX_CATCHUP = 5;

                    if (currentCompletedMinute > (session.lastBilledMinute || 0)) {
                        const startMin = (session.lastBilledMinute || 0) + 1;
                        const endMin = Math.min(currentCompletedMinute, startMin + MAX_CATCHUP - 1);
                        
                        for (let m = startMin; m <= endMin; m++) {
                            // Verify session still exists before charging
                            if (!activeSessions.has(sessionId)) break;
                            await processBillingCharge(sessionId, m, 'client_full_charge', io);
                        }
                        
                        // Final check before updating state
                        if (activeSessions.has(sessionId)) {
                            session.lastBilledMinute = endMin;
                            session.lastMaturedMinute = endMin;
                            await processBillingCharge(sessionId, endMin, 'astro_share_payout', io);
                            needsDbSync = true;
                        }
                    }
                }

                // Periodic slab check (every 10s)
                if (session.elapsedBillableSeconds % 10 === 0 && session.pairMonthId) {
                    const oldSlab = session.currentSlab;
                    updateSessionSlab(session);
                    if (session.currentSlab !== oldSlab) needsDbSync = true;
                }

                // PERFORMANCE FIX: Only persist state to DB when billing markers change.
                // This removes the heavy 1-second write loop.
                if (needsDbSync) {
                    Session.updateOne({ sessionId }, {
                        lastBilledMinute: session.lastBilledMinute,
                        lastMaturedMinute: session.lastMaturedMinute,
                        currentSlab: session.currentSlab,
                        totalEarned: session.totalEarned,
                        totalCharged: session.totalDeducted
                    }).catch(e => console.error('[Ticker] DB Sync Error', e));
                }

                // PERFORMANCE FIX: Emit timer-update EVERY second for better UX
                const client = await User.findOne({ userId: session.clientId }).select('walletBalance superWalletBalance').lean();
                if (client) {
                    let remainingSeconds = 0;
                    if (session.type === 'unlimited') {
                        // 40 Minute Cap
                        remainingSeconds = 2400 - secondsElapsed;
                        if (remainingSeconds < 1) {
                            console.log(`[Ticker] Force-cutting session ${sessionId}: Unlimited time limit reached.`);
                            return forceEndSession(sessionId, 'unlimited_limit_reached', io);
                        }
                    } else {
                        const price = session.pricePerMin || 10;
                        const totalBalance = (client.walletBalance || 0) + (client.superWalletBalance || 0);
                        remainingSeconds = Math.floor((totalBalance / price) * 60);

                        // FINANCIAL SAFETY: Force-cut call IMMEDIATELY if balance hits zero
                        if (remainingSeconds < 1) {
                            console.log(`[Ticker] Force-cutting session ${sessionId}: Balance exhausted.`);
                            return forceEndSession(sessionId, 'insufficient_funds', io);
                        }
                    }

                    const timerPayload = {
                        elapsedSeconds: secondsElapsed,
                        remainingSeconds: Math.max(0, remainingSeconds)
                    };

                    io.to(session.clientId).emit('timer-update', timerPayload);
                    io.to(session.astrologerId).emit('timer-update', timerPayload);

                    // 2-Minute Warning for Unlimited
                    if (session.type === 'unlimited' && remainingSeconds === 120) {
                        const warningMsg = "Your 40-minute session will end in 2 minutes.";
                        io.to(session.clientId).emit('session-warning', { message: warningMsg });
                        io.to(session.astrologerId).emit('session-warning', { message: warningMsg });
                    }
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
        if (!session || session.status === 'ended' || session.status === 'rejected') return;

        // Financial Safety Check: Don't process if this exact minute + type is already in the ledger
        const exists = await BillingLedger.exists({ 
            sessionId, 
            minuteIndex, 
            reason: { $regex: type === 'client_full_charge' ? '^first_minute|^minute_start|^unlimited' : '^slab_|^unlimited' } 
        });
        if (exists) {
            console.log(`[Billing] Skipping duplicate charge: sid=${sessionId}, minute=${minuteIndex}, type=${type}`);
            return;
        }

        const astro = await User.findOne({ userId: session.astrologerId });
        if (!astro) return;

        const client = await User.findOne({ userId: session.clientId });
        if (!client) return;

        let pricePerMin = 10;
        let isUnlimited = session.type === 'unlimited';

        if (isUnlimited) pricePerMin = astro.unlimitedPrice || 299;
        else if (session.type === 'chat') pricePerMin = astro.chatPrice || 10;
        else if (session.type === 'audio') pricePerMin = astro.audioPrice || 20;
        else if (session.type === 'video') pricePerMin = astro.videoPrice || 30;
        else if (astro.price && astro.price > 0) pricePerMin = parseInt(astro.price);

        let totalToClientDeduct = 0;
        let adminAmount = 0;
        let astroAmount = 0;
        let reason = '';
        let isFinalBalanceUpdate = false;

        // UNLIMITED LOGIC: Charge full amount at Minute 1, then 0.
        if (isUnlimited) {
            if (type === 'client_full_charge') {
                if (minuteIndex === 1) {
                    totalToClientDeduct = pricePerMin;
                    adminAmount = pricePerMin;
                    reason = 'unlimited_full_upfront';
                    isFinalBalanceUpdate = true;
                } else {
                    return; // No further charges to client
                }
            } else if (type === 'astro_share_payout') {
                // Pay astro 1/40th of their share per minute? 
                // Or pay based on a virtual per-minute rate (pricePerMin / 40)
                const virtualPrice = pricePerMin / 40;
                const activeSess = activeSessions.get(sessionId);
                const currentSlab = activeSess?.currentSlab || 1;
                let rate = SLAB_RATES[currentSlab] || 0.30;
                if (rate > 1) rate = rate / 100;
                
                astroAmount = virtualPrice * rate;
                adminAmount = -astroAmount;
                reason = `unlimited_slab_${currentSlab}_payout`;
                isFinalBalanceUpdate = true;
            }
        } 
        // STANDARD LOGIC
        else if (type === 'client_full_charge') {
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
            let mainDeduct = totalToClientDeduct;
            let superDeduct = 0;

            // Logic remains for calculation, but update must be atomic
            if (client.superWalletBalance > 0) {
                const potentialSuperDeduct = totalToClientDeduct * 0.3;
                superDeduct = Math.min(client.superWalletBalance, potentialSuperDeduct);
                mainDeduct = totalToClientDeduct - superDeduct;
            }

            // ATOMIC UPDATE: No more race conditions
            const updatedClient = await User.findOneAndUpdate(
                { userId: client.userId, walletBalance: { $gte: mainDeduct } },
                { $inc: { walletBalance: -mainDeduct, superWalletBalance: -superDeduct } },
                { returnDocument: 'after' }
            );

            if (!updatedClient) {
                console.warn(`[Billing] Potential double-charge or insufficient funds during atomic update sid=${sessionId}`);
                return forceEndSession(sessionId, 'insufficient_funds', io);
            }
            
            // Use updated balance for next steps
            client.walletBalance = updatedClient.walletBalance;
            client.superWalletBalance = updatedClient.superWalletBalance;

            if (minuteIndex === 1 && !client.isFirstCallDone) {
                await User.updateOne({ userId: client.userId }, { $set: { isFirstCallDone: true } });
            }
        }

        if (astroAmount > 0) {
            // ATOMIC UPDATE: No more race conditions
            const updatedAstro = await User.findOneAndUpdate(
                { userId: astro.userId },
                { $inc: { walletBalance: astroAmount, totalEarnings: astroAmount } },
                { returnDocument: 'after' }
            );
            if (updatedAstro) {
                astro.walletBalance = updatedAstro.walletBalance;
                astro.totalEarnings = updatedAstro.totalEarnings;
            }
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

            // Immediate DB Sync for financial fields
            await Session.updateOne({ sessionId }, {
                totalCharged: activeSess.totalDeducted,
                totalEarned: activeSess.totalEarned,
                lastBilledMinute: activeSess.lastBilledMinute,
                lastMaturedMinute: activeSess.lastMaturedMinute
            }).catch(e => console.error('[Billing] Financial Sync Error', e));
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

        // USER REQUEST: Silent notifications for wallet updates. 
        // Using types that don't trigger SoundManager's custom pips in Android.
        if (totalToClientDeduct > 0 && client.fcmToken) {
            sendFcmV1Push(client.fcmToken, { type: 'WALLET_DEBIT_SILENT', amount: totalToClientDeduct }, { title: 'Wallet Updated', body: `₹${totalToClientDeduct.toFixed(2)} deducted for session.` }).catch(() => {});
        }
        if (astroAmount > 0 && astro.fcmToken) {
            sendFcmV1Push(astro.fcmToken, { type: 'WALLET_CREDIT_SILENT', amount: astroAmount }, { title: 'Earnings Updated', body: `₹${astroAmount.toFixed(2)} credited to your wallet.` }).catch(() => {});
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

    session.isEnded = true; // Mark locally so ticker skips it immediately
    
    // Cleanup will happen in disconnect or end-session handlers
}

module.exports = { tickSessions, processBillingCharge, forceEndSession };
