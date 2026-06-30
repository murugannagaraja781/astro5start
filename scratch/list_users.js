const mongoose = require('mongoose');
const User = require('../models/User');
require('dotenv').config({ path: '../.env' });

const uri = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';

async function run() {
    try {
        console.log('Connecting to database...');
        await mongoose.connect(uri);
        console.log('Successfully connected.');

        const users = await User.find({});
        console.log('--- ALL USERS ---');
        console.log(JSON.stringify(users.map(u => ({
            id: u.userId,
            phone: u.phone,
            name: u.name,
            role: u.role,
            walletBalance: u.walletBalance,
            superWalletBalance: u.superWalletBalance
        })), null, 2));

        // Perform the migration if any client has superWalletBalance > 0
        const clientsWithBonus = users.filter(u => u.role === 'client' && u.superWalletBalance > 0);
        if (clientsWithBonus.length > 0) {
            console.log(`\nFound ${clientsWithBonus.length} clients with superWalletBalance > 0. Migrating them...`);
            for (const u of clientsWithBonus) {
                const oldWallet = u.walletBalance || 0;
                const oldSuper = u.superWalletBalance || 0;
                u.walletBalance = oldWallet + oldSuper;
                u.superWalletBalance = 0;
                await u.save();
                console.log(`Migrated user ${u.phone} (${u.name}): Main Wallet was ₹${oldWallet}, Super Wallet was ₹${oldSuper}. New Main Wallet is ₹${u.walletBalance}`);
            }
            console.log('Migration completed successfully.');
        } else {
            console.log('\nNo clients with superWalletBalance > 0 were found.');
        }

    } catch (e) {
        console.error('Error during migration:', e);
    } finally {
        await mongoose.disconnect();
    }
}

run();
