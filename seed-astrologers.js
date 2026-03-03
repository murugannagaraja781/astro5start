/**
 * Seed Script: Add 32 Dummy Astrologers to MongoDB
 * Run: node seed-astrologers.js
 */

require('dotenv').config();
const mongoose = require('mongoose');

const MONGO_URI = process.env.MONGODB_URI;

// User Schema (matches server.js)
const UserSchema = new mongoose.Schema({
    userId: { type: String, unique: true },
    phone: { type: String, unique: true },
    name: String,
    realName: String,
    gender: String,
    dob: String,
    tob: String,
    pob: String,
    cellNumber2: String,
    whatsAppNumber: String,
    address: String,
    aadharNumber: String,
    panNumber: String,
    astrologyExperience: String,
    profession: String,
    bankDetails: String,
    upiId: String,
    upiNumber: String,
    role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
    approvalStatus: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
    isOnline: { type: Boolean, default: false },
    isChatOnline: { type: Boolean, default: false },
    isAudioOnline: { type: Boolean, default: false },
    isVideoOnline: { type: Boolean, default: false },
    isBanned: { type: Boolean, default: false },
    skills: [String],
    price: { type: Number, default: 20 },
    walletBalance: { type: Number, default: 108 },
    superWalletBalance: { type: Number, default: 0 },
    totalEarnings: { type: Number, default: 0 },
    experience: { type: Number, default: 0 },
    isVerified: { type: Boolean, default: false },
    isDocumentVerified: { type: Boolean, default: false },
    documentStatus: { type: String, default: 'none' },
    image: { type: String, default: '' },
    birthDetails: {
        dob: String, tob: String, pob: String, lat: Number, lon: Number
    },
    intakeDetails: {
        gender: String, marital: String, occupation: String, topic: String,
        partner: { name: String, dob: String, tob: String, pob: String }
    },
    isAvailable: { type: Boolean, default: false },
    ratePerMinute: { type: Number, default: 10 },
    referralCode: { type: String, unique: true, sparse: true },
    fcmToken: { type: String, default: '' },
    lastSeen: { type: Date, default: Date.now },
    isBusy: { type: Boolean, default: false },
    availabilityExpiresAt: Date,
    referredBy: { type: String, default: null },
    referralCount: { type: Number, default: 0 },
    isNewUser: { type: Boolean, default: true }
});

const User = mongoose.model('User', UserSchema);

// ──────── DATA ────────
const firstNames = [
    'Acharya', 'Pandit', 'Guru', 'Jyotishi', 'Shastri',
    'Dr.', 'Swami', 'Sri', 'Maharaj', 'Acharya'
];

const lastNames = [
    'Sharma', 'Verma', 'Patel', 'Gupta', 'Iyer', 'Nair', 'Reddy', 'Singh',
    'Mishra', 'Joshi', 'Rao', 'Pillai', 'Menon', 'Deshmukh', 'Kulkarni',
    'Chatterjee', 'Banerjee', 'Mukherjee', 'Das', 'Bose', 'Pandey', 'Tiwari',
    'Agarwal', 'Kapoor', 'Malhotra', 'Saxena', 'Datta', 'Bhatt', 'Naidu',
    'Swamy', 'Hegde', 'Kamath'
];

const displayNames = [
    'Vedic Rishi', 'Cosmic Sage', 'Star Oracle', 'Nadi Master', 'Tarot Queen',
    'Astro Guru', 'Jyotish Acharya', 'Palm Reader', 'Vastu Expert', 'Numerologist',
    'KP Expert', 'Prashna Kundali', 'Remedial Expert', 'Gemstone Advisor', 'Vedic Scholar',
    'Horoscope Master', 'Lal Kitab Expert', 'Muhurat Specialist', 'Marriage Expert', 'Career Guide',
    'Health Astrologer', 'Finance Astrologer', 'Love Specialist', 'Child Astrologer', 'Transit Expert',
    'Dasha Analyst', 'Yoga Karaka', 'Manglik Expert', 'Sade Sati Guide', 'Graha Shanti',
    'Puja Expert', 'Spiritual Healer'
];

const skillSets = [
    ['Vedic Astrology', 'Horoscope Reading', 'Kundli Analysis'],
    ['Tarot Reading', 'Angel Cards', 'Numerology'],
    ['Palmistry', 'Face Reading', 'Aura Reading'],
    ['Vastu Shastra', 'Feng Shui', 'Space Healing'],
    ['KP Astrology', 'Prashna Kundali', 'Horary'],
    ['Nadi Astrology', 'Bhrigu Samhita', 'Past Life Reading'],
    ['Lal Kitab', 'Remedial Astrology', 'Gemstone Therapy'],
    ['Numerology', 'Name Correction', 'Lucky Numbers'],
    ['Marriage Matching', 'Compatibility', 'Love Astrology'],
    ['Career Guidance', 'Business Astrology', 'Financial Planning'],
    ['Health Astrology', 'Medical Astrology', 'Ayurveda'],
    ['Muhurat', 'Electional Astrology', 'Timing Events'],
    ['Transit Analysis', 'Dasha Prediction', 'Yearly Forecast'],
    ['Vedic Remedies', 'Puja Vidhi', 'Mantra Therapy'],
    ['Child Astrology', 'Education', 'Student Guidance'],
    ['Spirituality', 'Meditation', 'Chakra Healing']
];

const professions = [
    'Vedic Astrologer', 'Tarot Reader', 'KP Astrologer', 'Nadi Astrologer',
    'Palmist', 'Numerologist', 'Vastu Consultant', 'Lal Kitab Expert',
    'Prashna Kundali Expert', 'Horoscope Analyst', 'Spiritual Healer',
    'Gemstone Therapist', 'Marriage Counselor', 'Career Astrologer',
    'Muhurat Specialist', 'Remedial Astrologer'
];

const cities = [
    'Mumbai', 'Delhi', 'Bangalore', 'Chennai', 'Kolkata', 'Hyderabad',
    'Pune', 'Ahmedabad', 'Jaipur', 'Lucknow', 'Varanasi', 'Ujjain',
    'Madurai', 'Coimbatore', 'Kochi', 'Mysore', 'Indore', 'Bhopal',
    'Nagpur', 'Patna', 'Ranchi', 'Rishikesh', 'Haridwar', 'Nashik',
    'Surat', 'Kanpur', 'Allahabad', 'Thiruvananthapuram', 'Mangalore',
    'Visakhapatnam', 'Tiruchirappalli', 'Salem'
];

const genders = ['Male', 'Male', 'Male', 'Male', 'Female', 'Female', 'Male', 'Female'];

// Profile image URLs (placeholder astrologer avatars)
const maleImages = [
    'https://randomuser.me/api/portraits/men/1.jpg',
    'https://randomuser.me/api/portraits/men/2.jpg',
    'https://randomuser.me/api/portraits/men/3.jpg',
    'https://randomuser.me/api/portraits/men/4.jpg',
    'https://randomuser.me/api/portraits/men/5.jpg',
    'https://randomuser.me/api/portraits/men/6.jpg',
    'https://randomuser.me/api/portraits/men/7.jpg',
    'https://randomuser.me/api/portraits/men/8.jpg',
    'https://randomuser.me/api/portraits/men/9.jpg',
    'https://randomuser.me/api/portraits/men/10.jpg',
    'https://randomuser.me/api/portraits/men/11.jpg',
    'https://randomuser.me/api/portraits/men/12.jpg',
    'https://randomuser.me/api/portraits/men/13.jpg',
    'https://randomuser.me/api/portraits/men/14.jpg',
    'https://randomuser.me/api/portraits/men/15.jpg',
    'https://randomuser.me/api/portraits/men/16.jpg',
    'https://randomuser.me/api/portraits/men/17.jpg',
    'https://randomuser.me/api/portraits/men/18.jpg',
    'https://randomuser.me/api/portraits/men/19.jpg',
    'https://randomuser.me/api/portraits/men/20.jpg',
    'https://randomuser.me/api/portraits/men/21.jpg',
    'https://randomuser.me/api/portraits/men/22.jpg',
    'https://randomuser.me/api/portraits/men/23.jpg',
    'https://randomuser.me/api/portraits/men/24.jpg',
];

const femaleImages = [
    'https://randomuser.me/api/portraits/women/1.jpg',
    'https://randomuser.me/api/portraits/women/2.jpg',
    'https://randomuser.me/api/portraits/women/3.jpg',
    'https://randomuser.me/api/portraits/women/4.jpg',
    'https://randomuser.me/api/portraits/women/5.jpg',
    'https://randomuser.me/api/portraits/women/6.jpg',
    'https://randomuser.me/api/portraits/women/7.jpg',
    'https://randomuser.me/api/portraits/women/8.jpg',
];

function rand(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function randInt(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }

function generateAstrologer(index) {
    const gender = genders[index % genders.length];
    const realName = `${rand(firstNames)} ${lastNames[index % lastNames.length]}`;
    const displayName = `${displayNames[index]} ${lastNames[index % lastNames.length]}`;
    const city = cities[index % cities.length];
    const exp = randInt(3, 25);
    const price = [10, 12, 15, 18, 20, 25, 30, 35, 40, 50][randInt(0, 9)];
    const skillSet = skillSets[index % skillSets.length];
    const isOnline = Math.random() > 0.4; // 60% online
    const isChatOnline = isOnline && Math.random() > 0.3;
    const isAudioOnline = isOnline && Math.random() > 0.3;

    const maleIdx = index % maleImages.length;
    const femaleIdx = index % femaleImages.length;
    const image = gender === 'Female' ? femaleImages[femaleIdx] : maleImages[maleIdx];

    const phoneNum = `70000${String(10001 + index).padStart(5, '0')}`;

    return {
        userId: `astro_dummy_${String(index + 1).padStart(3, '0')}`,
        phone: phoneNum,
        name: displayName,
        realName: realName,
        gender: gender,
        dob: `${randInt(1965, 1995)}-${String(randInt(1, 12)).padStart(2, '0')}-${String(randInt(1, 28)).padStart(2, '0')}`,
        tob: `${String(randInt(4, 22)).padStart(2, '0')}:${String(randInt(0, 59)).padStart(2, '0')}`,
        pob: city,
        cellNumber2: `98${randInt(10000000, 99999999)}`,
        whatsAppNumber: phoneNum,
        address: `${randInt(1, 500)}, ${rand(['MG Road', 'Temple Street', 'Gandhi Nagar', 'Shanti Colony', 'Ashram Road', 'Lake View', 'Hill Top', 'River Side'])}, ${city}`,
        aadharNumber: `${randInt(1000, 9999)} ${randInt(1000, 9999)} ${randInt(1000, 9999)}`,
        panNumber: `${String.fromCharCode(65 + randInt(0, 25))}${String.fromCharCode(65 + randInt(0, 25))}${String.fromCharCode(65 + randInt(0, 25))}P${String.fromCharCode(65 + randInt(0, 25))}${randInt(1000, 9999)}${String.fromCharCode(65 + randInt(0, 25))}`,
        astrologyExperience: `${exp} years`,
        profession: professions[index % professions.length],
        bankDetails: `SBI A/C: ${randInt(10000000000, 99999999999)}, IFSC: SBIN00${randInt(10000, 99999)}`,
        upiId: `${realName.split(' ').pop().toLowerCase()}${randInt(100, 999)}@upi`,
        upiNumber: phoneNum,
        role: 'astrologer',
        approvalStatus: 'approved',
        isOnline: isOnline,
        isChatOnline: isChatOnline,
        isAudioOnline: isAudioOnline,
        isVideoOnline: false,
        isBanned: false,
        skills: skillSet,
        price: price,
        walletBalance: randInt(0, 5000),
        superWalletBalance: 0,
        totalEarnings: randInt(5000, 200000),
        experience: exp,
        isVerified: true,
        isDocumentVerified: true,
        documentStatus: 'verified',
        image: image,
        birthDetails: {
            dob: `${randInt(1965, 1995)}-${String(randInt(1, 12)).padStart(2, '0')}-${String(randInt(1, 28)).padStart(2, '0')}`,
            tob: `${String(randInt(4, 22)).padStart(2, '0')}:${String(randInt(0, 59)).padStart(2, '0')}`,
            pob: city,
            lat: parseFloat((randInt(8, 33) + Math.random()).toFixed(4)),
            lon: parseFloat((randInt(72, 88) + Math.random()).toFixed(4))
        },
        isAvailable: isOnline,
        ratePerMinute: price,
        referralCode: `ASTRO${String(index + 1).padStart(2, '0')}${randInt(100, 999)}`,
        fcmToken: '',
        lastSeen: new Date(),
        isBusy: false,
        referredBy: null,
        referralCount: randInt(0, 50),
        isNewUser: false
    };
}

// ──────── MAIN ────────
async function main() {
    try {
        console.log('🔌 Connecting to MongoDB...');
        await mongoose.connect(MONGO_URI);
        console.log('✅ Connected to MongoDB');

        // Generate 32 astrologers
        const astrologers = [];
        for (let i = 0; i < 32; i++) {
            astrologers.push(generateAstrologer(i));
        }

        console.log(`\n🚀 Inserting ${astrologers.length} dummy astrologers...\n`);

        let inserted = 0;
        let skipped = 0;

        for (const astro of astrologers) {
            try {
                // Check if already exists
                const exists = await User.findOne({
                    $or: [{ userId: astro.userId }, { phone: astro.phone }]
                });

                if (exists) {
                    console.log(`  ⏭️  Skipped: ${astro.name} (already exists)`);
                    skipped++;
                    continue;
                }

                await User.create(astro);
                console.log(`  ✅ Added: ${astro.name} | ${astro.profession} | ${astro.experience}yr | ₹${astro.price}/min | ${astro.skills.join(', ')}`);
                inserted++;
            } catch (err) {
                if (err.code === 11000) {
                    console.log(`  ⏭️  Skipped: ${astro.name} (duplicate key)`);
                    skipped++;
                } else {
                    console.error(`  ❌ Error for ${astro.name}:`, err.message);
                }
            }
        }

        console.log(`\n${'═'.repeat(50)}`);
        console.log(`📊 SEED RESULTS`);
        console.log(`${'═'.repeat(50)}`);
        console.log(`   ✅ Inserted: ${inserted}`);
        console.log(`   ⏭️  Skipped:  ${skipped}`);
        console.log(`   📝 Total:    ${inserted + skipped}`);
        console.log(`${'═'.repeat(50)}\n`);

        // Show summary of all astrologers in DB
        const totalAstrologers = await User.countDocuments({ role: 'astrologer' });
        const approvedCount = await User.countDocuments({ role: 'astrologer', approvalStatus: 'approved' });
        const onlineCount = await User.countDocuments({ role: 'astrologer', isOnline: true });
        console.log(`📈 DB Status: ${totalAstrologers} total astrologers | ${approvedCount} approved | ${onlineCount} online\n`);

    } catch (err) {
        console.error('❌ Fatal error:', err);
    } finally {
        await mongoose.disconnect();
        console.log('🔌 Disconnected from MongoDB');
        process.exit(0);
    }
}

main();
