const mongoose = require('mongoose');
require('dotenv').config();
const User = require('./models/User');

async function checkAstros() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const astros = await User.find({ role: 'astrologer' }).limit(5);
        console.log(JSON.stringify(astros.map(a => ({ userId: a.userId, phone: a.phone, name: a.name })), null, 2));
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
}

checkAstros();
