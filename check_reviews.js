require('dotenv').config();
const mongoose = require('mongoose');
const Review = require('./models/Review');
const User = require('./models/User');

async function check() {
    const MONGO_URI = process.env.MONGODB_URI;
    console.log('Connecting to:', MONGO_URI.split('@').pop().split('?')[0]);

    await mongoose.connect(MONGO_URI);
    console.log('Connected to DB');

    const totalReviews = await Review.countDocuments();
    console.log(`Total Reviews in DB: ${totalReviews}`);

    const lastReviews = await Review.find().sort({ createdAt: -1 }).limit(5).populate('astrologerId', 'name userId');
    console.log(`Recent reviews:`);
    lastReviews.forEach(r => {
        console.log(`Review ID: ${r._id}`);
        console.log(`Astro: ${r.astrologerId ? r.astrologerId.name : 'NULL'} (${r.astrologerId ? r.astrologerId.userId : 'NULL'})`);
        console.log(`Client: ${r.clientName}`);
        console.log('---');
    });

    const sampleUsers = await User.find({ role: 'astrologer' }).limit(5);
    console.log(`Sample Astrologers in DB:`);
    sampleUsers.forEach(u => {
        console.log(`Name: ${u.name}, userId: ${u.userId}, _id: ${u._id}`);
    });

    const totalSessions = await require('./models/Session').countDocuments();
    console.log(`Total Sessions in DB: ${totalSessions}`);

    const sampleSessions = await require('./models/Session').find().sort({ _id: -1 }).limit(5);
    console.log(`Sample Sessions in DB:`);
    sampleSessions.forEach(s => {
        console.log(`ID: ${s.sessionId}, Client: ${s.clientId}, Astro: ${s.astrologerId}, Status: ${s.status}`);
    });

    // Verification Routine
    console.log('\n--- VERIFICATION ---');
    if (sampleUsers.length > 1 && sampleSessions.length > 0) {
        const astro = sampleUsers[1]; // Useganapathi or similar
        const client = sampleUsers[0]; // John
        const sess = sampleSessions[0];

        console.log(`Running test review for Astro: ${astro.userId}, Client: ${client.userId}, Session UUID: ${sess.sessionId}`);

        const reviewController = require('./controllers/reviewController');
        const req = {
            body: {
                astrologerId: astro.userId,
                clientId: client.userId,
                rating: 5,
                comment: 'Test successful review submission ' + new Date().toISOString(),
                sessionId: sess.sessionId
            }
        };
        const res = {
            json: function (data) {
                console.log('Backend Response:', data);
            }
        };

        await reviewController.createReview(req, res);

        const verificationCount = await Review.countDocuments({ comment: new RegExp('Test successful review submission') });
        console.log(`Verification: Found ${verificationCount} test reviews in DB.`);
    }

    process.exit();
}

check().catch(err => {
    console.error(err);
    process.exit(1);
});
