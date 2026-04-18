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
            if (!session.actualBillingStart) continue;
            if (now < session.actualBillingStart) continue;

            const isSessionValid = session.isAnswered && !session.isEnded;

            if (isSessionValid) {
                const secondsElapsed = Math.floor((now - session.actualBillingStart) / 1000);
                session.elapsedBillableSeconds = secondsElapsed;

                const client = await User.findOne({ userId: session.clientId }).select('isFirstCallDone walletBalance superWalletBalance').lean();
                if (!client) continue;

                const { REFERRAL_CONFIG } = require('./sharedState');
                
                // CLIENT BILLING CALCULATION
                // Rule: 50s free (global), 3m free (first call)
                const globalFreeSec = REFERRAL_CONFIG.CLIENT_FREE_SECONDS || 50;
                const firstCallFreeSec = client.isFirstCallDone ? 0 : (REFERRAL_CONFIG.FREE_CALL_DURATION || 3) * 60;
                
                // Client rounding: Math.ceil if > 50s. 
                // Transition points: 51s -> 1m, 61s -> 2m, 121s -> 3m...
                let clientBillableMin = 0;
                if (secondsElapsed > globalFreeSec) {
                    // Total minutes based on ceil rounding
                    const totalMins = Math.ceil(secondsElapsed / 60);
                    // Subtract free minutes for first call
                    clientBillableMin = Math.max(0, totalMins - (firstCallFreeSec / 60));
                }

                if (clientBillableMin > (session.lastBilledMinute || 0)) {
                    if (clientBillableMin >= 1 && !client.isFirstCallDone) {
                        await User.updateOne({ userId: session.clientId }, { $set: { isFirstCallDone: true } });
                    }
                    const startMin = (session.lastBilledMinute || 0) + 1;
                    for (let m = startMin; m <= clientBillableMin; m++) {
                        if (!activeSessions.has(sessionId)) break;
                        await processBillingCharge(sessionId, m, 'client_full_charge', io);
                    }
                    session.lastBilledMinute = clientBillableMin;
                }

                // ASTROLOGER PAYOUT CALCULATION
                // Rule: 1 min free. Rounding > 50s. 
                // 7m 48s -> 7m. 7m 51s -> 8m.
                const astroFreeMin = REFERRAL_CONFIG.ASTRO_FREE_MINUTES || 1;
                const astroThreshold = REFERRAL_CONFIG.ASTRO_ROUNDING_THRESHOLD || 50;

                let astroTotalMinutes = Math.floor(secondsElapsed / 60);
                if ((secondsElapsed % 60) > astroThreshold) {
                    astroTotalMinutes += 1;
                }
                const astroBillableMinutes = Math.max(0, astroTotalMinutes - astroFreeMin);

                if (astroBillableMinutes > (session.lastMaturedMinute || 0)) {
                    const startMin = (session.lastMaturedMinute || 0) + 1;
                    for (let m = startMin; m <= astroBillableMinutes; m++) {
                        if (!activeSessions.has(sessionId)) break;
                        await processBillingCharge(sessionId, m, 'astro_share_payout', io);
                    }
                    session.lastMaturedMinute = astroBillableMinutes;
                }

                // Periodic slab check (every 10s)
                if (secondsElapsed % 10 === 0 && session.pairMonthId) {
                    updateSessionSlab(session);
                }

                // Timer Update
                let remainingSeconds = 0;
                if (session.type === 'unlimited') {
                    remainingSeconds = 2400 - secondsElapsed;
                } else {
                    const price = session.pricePerMin || 10;
                    const totalBalance = (client.walletBalance || 0) + (client.superWalletBalance || 0);
                    remainingSeconds = Math.floor((totalBalance / price) * 60);
                }

                const timerPayload = {
                    elapsedSeconds: secondsElapsed,
                    remainingSeconds: Math.max(0, remainingSeconds)
                };
                io.to(session.clientId).emit('timer-update', timerPayload);
                io.to(session.astrologerId).emit('timer-update', timerPayload);

                if (remainingSeconds < 1) {
                    return forceEndSession(sessionId, 'insufficient_funds', io);
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
    
    // RULE: Level 1: 0-5m, Level 2: 5-10m, Level 3: 10-15m, Level 4: 15-20m, Level 5: 20m+
    if (totalSeconds > 1200) calculatedSlab = 5;
    else if (totalSeconds > 900) calculatedSlab = 4;
    else if (totalSeconds > 600) calculatedSlab = 3;
    else if (totalSeconds > 300) calculatedSlab = 2;

    // RULE: Levels 1-3 are PERMANENT. Levels 4-5 reset to 3 every month.
    // Logic: If they were 4 or 5 in a PREVIOUS month, we start them at 3 inside tryStartBilling.
    // During the session, we only progress UPwards.
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

        // Financial Safety Check
        const exists = await BillingLedger.exists({ 
            sessionId, 
            minuteIndex, 
            reason: { $regex: type === 'client_full_charge' ? '^first_minute|^minute_start|^unlimited' : '^slab_|^unlimited' } 
        });
        if (exists) return;

        const astro = await User.findOne({ userId: session.astrologerId });
        if (!astro) return;

        const client = await User.findOne({ userId: session.clientId });
        if (!client) return;

        const { UNLIMITED_PAYOUT_CONFIG, SLAB_RATES } = require('./sharedState');
        let pricePerMin = 10;
        let isUnlimited = session.type === 'unlimited';

        if (isUnlimited) {
            // Price is fixed upfront for the selected tier (Normal/Silver/Gold/Diamond)
            if (session.offerType === 'silver') pricePerMin = 350;
            else if (session.offerType === 'gold') pricePerMin = 500;
            else if (session.offerType === 'diamond') pricePerMin = 700;
            else pricePerMin = 200; // normal
        } else {
            if (session.type === 'chat') pricePerMin = astro.chatPrice || 10;
            else if (session.type === 'audio') pricePerMin = astro.audioPrice || 20;
            else if (session.type === 'video') pricePerMin = astro.videoPrice || 30;
            else pricePerMin = parseInt(astro.price) || 10;
        }

        let totalToClientDeduct = 0;
        let adminAmount = 0;
        let astroAmount = 0;
        let reason = '';

        if (isUnlimited) {
            if (type === 'client_full_charge' && minuteIndex === 1) {
                totalToClientDeduct = pricePerMin;
                adminAmount = pricePerMin;
                reason = `unlimited_${session.offerType || 'normal'}_full_upfront`;
            } else if (type === 'astro_share_payout') {
                // RULE: 30% to Astrologer for all call/chat in UNLIMITED.
                const totalPackagePrice = pricePerMin;
                const totalDuration = session.unlimitedDuration || 15;
                const virtualPricePerMin = totalPackagePrice / totalDuration;
                
                const astroPercentage = UNLIMITED_PAYOUT_CONFIG.ASTRO_PERCENTAGE || 30;
                astroAmount = virtualPricePerMin * (astroPercentage / 100);
                adminAmount = -astroAmount;
                reason = `unlimited_${session.offerType || 'normal'}_payout_min_${minuteIndex}`;
            } else {
                return;
            }
        } else {
            // STANDARD LOGIC
            if (type === 'client_full_charge') {
                totalToClientDeduct = pricePerMin;
                adminAmount = pricePerMin;
                reason = (minuteIndex === 1) ? 'first_minute_admin' : 'minute_start_admin';
            } else if (type === 'astro_share_payout') {
                const activeSess = activeSessions.get(sessionId);
                const currentSlab = activeSess?.currentSlab || 1;
                let rate = SLAB_RATES[currentSlab] || 0.30;
                if (rate > 1) rate = rate / 100;

                astroAmount = pricePerMin * rate;
                adminAmount = -astroAmount;
                reason = `slab_${currentSlab}_payout`;
            } else {
                return;
            }
        }

        if (totalToClientDeduct > 0) {
            let mainDeduct = totalToClientDeduct;
            let superDeduct = 0;

            // Simple split: Bonus first, then Wallet
            if (client.superWalletBalance > 0) {
                superDeduct = Math.min(client.superWalletBalance, totalToClientDeduct);
                mainDeduct = totalToClientDeduct - superDeduct;
            }

            const updatedClient = await User.findOneAndUpdate(
                { userId: client.userId, walletBalance: { $gte: mainDeduct } },
                { $inc: { walletBalance: -mainDeduct, superWalletBalance: -superDeduct } },
                { returnDocument: 'after' }
            );

            if (!updatedClient) return forceEndSession(sessionId, 'insufficient_funds', io);
            client.walletBalance = updatedClient.walletBalance;
            client.superWalletBalance = updatedClient.superWalletBalance;
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

/**
 * NEW: Automated Referrer Payout Logic
 * Triggered when a referee completes 1 minute of their first paid session.
 * Standard Payout: ₹81 credited to Referrer's Wallet.
 */
async function handleReferralPayout(refereeId, io) {
    try {
        const referee = await User.findOne({ userId: refereeId });
        if (!referee || !referee.referredBy || referee.isReferralRewardClaimed) return;

        const referrerId = referee.referredBy;
        const referrer = await User.findOne({ userId: referrerId });
        if (!referrer) return;

        const { REFERRAL_CONFIG } = require('./sharedState');
        const rewardAmount = REFERRAL_CONFIG.REFERRER_REWARD || 81;

        // ATOMIC UPDATE: Credit Referrer
        const updatedReferrer = await User.findOneAndUpdate(
            { userId: referrerId },
            { 
                $inc: { walletBalance: rewardAmount, referralCount: 1 },
            },
            { returnDocument: 'after' }
        );

        if (updatedReferrer) {
            // Mark Referee's reward as claimed to prevent multi-payouts
            await User.updateOne({ userId: refereeId }, { $set: { isReferralRewardClaimed: true } });

            // RECORD IN LEDGER: For admin visibility and user transparency
            await BillingLedger.create({
                billingId: `REF_PAY_${crypto.randomUUID().substring(0, 8)}`,
                sessionId: `REF_${refereeId}`, // Identifier for referral-originated credit
                minuteIndex: 0,
                chargedToClient: 0,
                creditedToAstrologer: 0,
                adminAmount: -rewardAmount, // Admin pays out the reward
                reason: `referral_reward_for_${refereeId}`,
                appliedRate: 0,
                createdAt: new Date()
            });

            // REAL-TIME UPDATES
            if (io) {
                io.to(referrerId).emit('wallet-update', {
                    balance: updatedReferrer.walletBalance,
                    superBalance: updatedReferrer.superWalletBalance || 0,
                    referralCount: updatedReferrer.referralCount
                });
            }

            // NOTIFY REFERRER
            if (updatedReferrer.fcmToken) {
                sendFcmV1Push(updatedReferrer.fcmToken, { 
                    type: 'REFERRAL_REWARD', 
                    amount: rewardAmount 
                }, { 
                    title: '🎁 Referral Reward!', 
                    body: `You earned ₹${rewardAmount} because your friend completed their first session!` 
                }).catch(() => {});
            }

            console.log(`[Referral] Successfully paid ₹${rewardAmount} to ${referrerId} for referee ${refereeId}`);
        }

    } catch (err) {
        console.error('[Referral] Payout Logic Error:', err);
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

module.exports = { 
    tickSessions, 
    processBillingCharge, 
    handleReferralPayout, 
    forceEndSession 
};
