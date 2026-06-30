# அப்ளிகேஷன் வேகத்தை அதிகரிக்க டிப்ஸ் (App Speed Optimization Guide)

Astro5Star மொபைல் செயலி (Android App) மற்றும் சர்வர் (Backend Server) ஆகியவற்றை அதிவேகமாகச் செயல்பட வைக்க செய்ய வேண்டிய முக்கிய மாற்றங்கள் கீழே வரிசைப்படுத்தப்பட்டுள்ளன:

---

## 1. டேட்டாபேஸ் மேம்படுத்தல் (Database Optimization)
அப்ளிகேஷன் மெதுவாக இருப்பதற்கு மிக முக்கிய காரணம் சர்வரில் டேட்டாபேஸ் குவரிகள் (Database queries) மெதுவாக நடப்பதே ஆகும்.

* **இண்டெக்சிங் (Indexing):** 
  அடிக்கடி தேடப்படும் ஃபீல்டுகளுக்கு இண்டெக்ஸ் அமைக்க வேண்டும். MongoDB-யில் பின்வரும் இண்டெக்ஸ்களை உருவாக்குங்கள்:
  ```javascript
  // Indexes for User Model
  db.users.createIndex({ userId: 1 });
  db.users.createIndex({ phone: 1 });
  db.users.createIndex({ isAvailable: 1, isBusy: 1 });

  // Indexes for Session Model
  db.sessions.createIndex({ sessionId: 1 });
  db.sessions.createIndex({ clientId: 1 });
  db.sessions.createIndex({ astrologerId: 1 });
  db.sessions.createIndex({ status: 1 });
  ```
* **தேவையான தகவல்களை மட்டும் எடுத்தல் (Projections):**
  அனைத்து டேட்டாக்களையும் எடுக்காமல் (`find()`), தேவையான ஃபீல்டுகளை மட்டும் தேர்ந்தெடுக்கவும்:
  ```javascript
  // மெதுவான முறை:
  const user = await User.findOne({ userId });
  
  // வேகமான முறை (Projections):
  const user = await User.findOne({ userId }).select('userId name profilePic walletBalance');
  ```
* **பேஜினேஷன் (Pagination):**
  ஹிஸ்டரி அல்லது லாக்ஸ் போன்ற பெரிய பட்டியல்களைக் காண்பிக்கும் போது ஒரே அடியில் 100+ பதிவுகளை எடுக்காமல், 10-10 பதிவுகளாகப் பிரிக்கவும் (`limit` & `skip` பயன்படுத்தி).

---

## 2. சாக்கெட் மற்றும் நெட்வொர்க் மேம்படுத்தல் (Socket & Network Optimization)
* **அதிர்வெண் ஒளிபரப்பைக் குறைத்தல் (Reduce Broadcasts):**
  தற்போது ஒவ்வொரு ஜோதிடரின் சிறிய மாற்றத்திற்கும் `Updated 29 astrologers list to all clients` என்ற முழுப் பட்டியலும் அனைத்து யூசர்களுக்கும் பிராட்காஸ்ட் செய்யப்படுகிறது. ஜோதிடர்களின் நிலை மாறும்போது மட்டுமே (Status change) இந்த பிராட்காஸ்ட்டை அனுப்பும்படி குறியீட்டை மாற்ற வேண்டும்.
* **கம்ப்ரெஷன் (Compression):**
  அனைத்து API பதில்களையும் கம்ப்ரெஸ் செய்ய சர்வரில் `compression` மிடில்வேர் பயன்படுத்துவதை உறுதி செய்க (இது ஏற்கனவே `server.js`-ல் உள்ளது).

---

## 3. ஆண்ட்ராய்டு ஆப் பக்க மேம்படுத்தல் (Android App Performance)
ஆப் பக்கத்தில் லேக் (lag) ஏற்படுவதைத் தவிர்க்க மொபைல் டெவலப்பரிடம் பின்வருவனவற்றைச் செய்யச் சொல்லுங்கள்:

* **பின்னணி திரெட்டுகள் (Background Threads):**
  நெட்வொர்க் அழைப்புகள் மற்றும் டேட்டாபேஸ் வேலைகளை மெயின் திரெட்டில் (UI Thread) செய்யாமல் கோரூட்டின் (`CoroutineScope(Dispatchers.IO).launch`) மூலம் செய்ய வேண்டும்.
* **படம் கையாளுதல் (Image Caching):**
  பயனர் மற்றும் ஜோதிடர் படங்களை லோடு செய்ய **Glide** அல்லது **Coil** போன்ற லைப்ரரியைப் பயன்படுத்தி படங்களை கேச்சிங் (Caching) செய்ய வேண்டும்.
* **மெமரி லீக் தவிர்த்தல் (Clear Socket Listeners):**
  ஒரு சாட் அல்லது கால் முடிந்து வெளியறும்போது சாக்கெட் லிசனர்களை முழுமையாக நீக்க வேண்டும் (`socket.off("event")`). இல்லையேல் பின்னணியில் பழைய லிசனர்கள் மெமரியை வீணடிக்கும்.
* **குறியீடு சுருக்கம் (Enable R8/Proguard):**
  ஆப்பை ரிலீஸ் செய்யும்போது `minifyEnabled true` மற்றும் `shrinkResources true` என்பதை `build.gradle`-ல் ஆன் செய்ய வேண்டும். இது ஆப்பின் அளவைக் குறைத்து வேகத்தைக் கூட்டும்.

---

## 4. சர்வர் வளங்கள் (Server Resources & Setup)
* **PM2 கிளஸ்டர் மோட் (Cluster Mode):**
  சர்வரில் 2 அல்லது அதற்கு மேற்பட்ட CPU கோர்கள் இருந்தால், PM2-ஐ கிளஸ்டர் மோடில் இயக்கவும்:
  ```bash
  pm2 start server.js -i max
  ```
* **Nginx Reverse Proxy:**
  ஸ்டாடிக் ஃபைல்களை நேரடியாக Node.js தராமல், Nginx மூலமாக கேச்சிங் மற்றும் கம்ப்ரெஷன் செய்து வழங்கினால் சர்வர் சுமை குறையும்.
