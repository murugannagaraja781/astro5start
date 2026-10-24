# 🌟 Astro 5 Star - முழுமையான ஆப் ஈவென்ட்கள் பட்டியல் (Complete App Events List)
> **பயன்பாடு:** Google Play Console, Meta (Facebook) Events Manager & Ads Campaign Tracking  
> **ஆப் வெர்ஷன்:** v7.6 (Version Code: 53)  
> **Package Name:** `com.astro5star.app`

---

## 📌 சுருக்கமான கண்ணோட்டம் (Overview)
இந்த ஆவணத்தில் Astro 5 Star செயலியில் உள்ள அனைத்து பயனர் செயல்பாடுகள் (User Interactions), பட்டன் கிளிக்குகள் (Button Clicks), மற்றும் ஆக்ஷன்கள் (Actions) ஒழுங்குபடுத்தப்பட்டு, **Meta Ads & Analytics Standard Events** உடன் இணைக்கப்பட்டுள்ளன.

---

## 1. பயனர் பதிவு & உள்நுழைவு (User Onboarding & Authentication)

| ஈவென்ட் பெயர் (Event Name) | Meta / Analytics Event | பயனர் செய்யும் செயல் (User Action / Trigger) | திரை (Screen / Activity) | அனுப்பப்படும் விவரங்கள் (Parameters) |
|---|---|---|---|---|
| **`app_open`** | `fb_mobile_activate_app` | பயனர் ஆப் திறக்கும்போது (தானாக நடப்பது) | `SplashActivity` | `timestamp`, `device_model`, `app_version` |
| **`login_screen_viewed`** | `ViewContent` | லாகின் திரை திறக்கும்போது | `LoginActivity` | `content_type: 'auth_page'` |
| **`country_code_selected`** | `CustomEvent` | சர்வதேச நாட்டுக் குறியீடு (+91, +1, போன்றவை) மாற்றுவது | `LoginActivity` | `country_code`, `dial_code` |
| **`phone_number_submitted`** | `CustomEvent` | மொபைல் எண் உள்ளிட்டு "Get OTP" பட்டன் கிளிக் செய்தல் | `LoginActivity` | `phone_length`, `country_code` |
| **`otp_requested`** | `CustomEvent` | OTP SMS அனுப்பப்படுவது | `LoginActivity` | `status: 'sent'`, `retry_count` |
| **`otp_resend_clicked`** | `CustomEvent` | "Resend OTP" பட்டன் கிளிக் செய்வது | `LoginActivity` | `cooldown_remaining` |
| **`login_completed`** | `fb_mobile_complete_registration` | OTP சரிபார்க்கப்பட்டு வெற்றிகரமாக லாகின் செய்தல் | `LoginActivity` | `user_id`, `method: 'phone_otp'` |
| **`guest_mode_clicked`** | `CustomEvent` | லாகின் செய்யாமல் "Explore as Guest" பட்டன் கிளிக் செய்வது | `LoginActivity` | `role: 'guest'` |
| **`profile_setup_completed`** | `fb_mobile_complete_registration` | பிறந்த தேதி, நேரம், இடம் கொடுத்து சுயவிவரம் முடிப்பது | `ProfileActivity` | `gender`, `birth_place`, `has_tob: true` |

---

## 2. ஜோதிடர்கள் & ஆலோசனைகள் (Astrologer Consultation Events)

| ஈவென்ட் பெயர் (Event Name) | Meta / Analytics Event | பயனர் செய்யும் செயல் (User Action / Trigger) | திரை (Screen / Activity) | அனுப்பப்படும் விவரங்கள் (Parameters) |
|---|---|---|---|---|
| **`home_screen_viewed`** | `ViewContent` | முகப்புப் பக்க ஜோதிடர் பட்டியலைப் பார்ப்பது | `HomeScreen` | `active_tab: 'astrologers'` |
| **`category_filter_clicked`** | `Search` | Vedic, Tarot, Vastu, Numerology போன்ற வகைகளை வடிகட்டுவது | `HomeScreen` | `category_name`, `language_filter` |
| **`astrologer_card_clicked`** | `ViewContent` | ஒரு குறிப்பிட்ட ஜோதிடரின் அட்டையை (Card) கிளிக் செய்தல் | `HomeScreen` | `astrologer_id`, `astrologer_name`, `rate_per_min` |
| **`astrologer_profile_viewed`** | `ViewContent` | ஜோதிடரின் முழு சுயவிவரப் பக்கத்தைப் பார்ப்பது | `AstrologerProfileActivity` | `astrologer_id`, `rating`, `experience` |
| **`call_button_clicked`** | `CustomEvent` | "Call Now" (ஆடியோ/வீடியோ) பட்டன் கிளிக் செய்வது | Profile / Home | `astrologer_id`, `call_type: 'audio/video'`, `rate` |
| **`intake_form_submitted`** | `Lead` | அழைப்பிற்கு முன் பயனர் பெயர், ராசி, நட்சத்திரம் உள்ளிட்டு அனுப்புவது | `IntakeFormActivity` | `name`, `rashi`, `problem_category` |
| **`call_connected`** | `CustomEvent` | ஜோதிடருடன் WebRTC அழைப்பு இணைக்கப்படுவது | `CallActivity` | `session_id`, `astrologer_id` |
| **`call_ended`** | `CustomEvent` | அழைப்பு முடிவடைவது | `CallActivity` | `duration_seconds`, `total_charge`, `end_reason` |
| **`chat_button_clicked`** | `CustomEvent` | "Chat Now" பட்டன் கிளிக் செய்வது | Profile / Home | `astrologer_id`, `rate_per_min` |
| **`chat_session_started`** | `CustomEvent` | ஜோதிடருடன் நேரலை சாட் தொடங்குவது | `ChatActivity` | `session_id`, `astrologer_id` |
| **`chat_message_sent`** | `CustomEvent` | சாட்டில் உரை (Text) அல்லது ஆடியோ செய்தி அனுப்புவது | `ChatActivity` | `message_type: 'text/audio'` |
| **`chat_session_ended`** | `CustomEvent` | சாட் உரையாடலை முடிப்பது | `ChatActivity` | `duration_minutes`, `total_charge` |
| **`review_submitted`** | `Rate` | ஆலோசனை முடிந்ததும் ஸ்டார் ரேட்டிங் மற்றும் கருத்து வழங்குவது | `RatingDialog` | `astrologer_id`, `rating_stars`, `has_comment` |

---

## 3. பணப்பை & கட்டணங்கள் (Wallet, Recharge & Monetization Events)
*(Meta Ads-ல் ROAS & Purchase Tracking-ற்கு இவை மிக முக்கியமானவை)*

| ஈவென்ட் பெயர் (Event Name) | Meta / Analytics Event | பயனர் செய்யும் செயல் (User Action / Trigger) | திரை (Screen / Activity) | அனுப்பப்படும் விவரங்கள் (Parameters) |
|---|---|---|---|---|
| **`wallet_screen_viewed`** | `ViewContent` | Wallet பக்கத்தை திறந்து இருப்புத் தொகையைப் பார்ப்பது | `WalletActivity` | `current_balance`, `currency` |
| **`recharge_pack_selected`** | `CustomEvent` | ₹100, ₹200, ₹500, ₹1000 பேக்குகளில் ஒன்றைத் தேர்ந்தெடுப்பது | `WalletActivity` | `amount`, `bonus_offer_percent` |
| **`coupon_applied`** | `CustomEvent` | ஆஃபர் கூப்பன் கோடு உள்ளிட்டு "Apply" கிளிக் செய்வது | `WalletActivity` | `coupon_code`, `discount_amount` |
| **`checkout_initiated`** | `fb_mobile_initiated_checkout` | "Pay Now" பட்டன் கிளிக் செய்து பேமெண்ட் துவங்குவது | `WalletActivity` | `amount`, `currency: 'INR'`, `pack_id` |
| **`payment_gateway_opened`** | `CustomEvent` | PhonePe / UPI கேட்வே திரை திறப்பது | `PaymentActivity` | `gateway: 'PhonePe'`, `transaction_id` |
| **`payment_completed`** | `fb_mobile_purchase` | கட்டணம் வெற்றிகரமாக செலுத்தப்பட்டு Wallet-ல் சேருவது | `PaymentActivity` | `value: amount`, `currency: 'INR'`, `status: 'SUCCESS'` |
| **`payment_failed`** | `CustomEvent` | பேமெண்ட் தோல்வியடைவது அல்லது பயனர் கேன்சல் செய்வது | `PaymentActivity` | `reason`, `amount`, `error_code` |

---

## 4. இலவச ஜோதிடச் சேவைகள் (Free Astrological Services Events)

| ஈவென்ட் பெயர் (Event Name) | Meta / Analytics Event | பயனர் செய்யும் செயல் (User Action / Trigger) | திரை (Screen / Activity) | அனுப்பப்படும் விவரங்கள் (Parameters) |
|---|---|---|---|---|
| **`free_horoscope_opened`** | `ViewContent` | "Free Horoscope" (இலவச ஜாதகம்) பட்டன் கிளிக் செய்வது | `FreeHoroscopeActivity` | `service: 'kundali'` |
| **`birth_details_submitted`** | `Lead` | ஜாதகம் கணிக்க பிறந்த தேதி, நேரம், ஊர் உள்ளிட்டு சமர்ப்பித்தல் | `FreeHoroscopeActivity` | `latitude`, `longitude`, `chart_style: 'south/north'` |
| **`horoscope_chart_viewed`** | `CustomEvent` | ராசி, நவாம்சம் கட்டங்கள் மற்றும் கிரக நிலைகளைப் பார்ப்பது | `ChartActivity` | `lagna`, `rashi`, `nakshatra` |
| **`rasipalan_opened`** | `ViewContent` | "தினசரி ராசிபலன்" (Daily Rasipalan) பட்டன் கிளிக் செய்வது | `RasipalanActivity` | `view_type: 'daily/weekly/monthly'` |
| **`rasi_selected`** | `CustomEvent` | 12 ராசிகளில் தனது ராசியை கிளிக் செய்து பலன் படிப்பது | `RasipalanActivity` | `rasi_name` (எ.கா: 'மேஷம்', 'ரிஷபம்') |
| **`match_making_clicked`** | `ViewContent` | "திருமணப் பொருத்தம்" (Horoscope Matching) பார்ப்பது | `MatchMakingActivity` | `boy_star`, `girl_star` |
| **`astro_academy_opened`** | `ViewContent` | "Astro Academy" வீடியோ/கட்டுரைப் பகுதிக்குள் நுழைவது | `AcademyActivity` | `article_id`, `category` |

---

## 5. பயனர் ஈடுபாடு & அமைப்புகள் (Engagement, Referral & Settings)

| ஈவென்ட் பெயர் (Event Name) | Meta / Analytics Event | பயனர் செய்யும் செயல் (User Action / Trigger) | திரை (Screen / Activity) | அனுப்பப்படும் விவரங்கள் (Parameters) |
|---|---|---|---|---|
| **`referral_screen_viewed`** | `ViewContent` | "Invite Friends & Earn" திரையைப் பார்ப்பது | `ReferralActivity` | `referral_code` |
| **`referral_share_clicked`** | `CustomEvent` | WhatsApp / SMS வழியாக ரெஃப்ரல் லிங்க் பகிர்வது | `ReferralActivity` | `channel: 'whatsapp/share_sheet'` |
| **`call_history_viewed`** | `ViewContent` | முந்தைய அழைப்புப் பதிவுகள் (Call History) பார்ப்பது | `HistoryActivity` | `history_type: 'calls'` |
| **`chat_history_viewed`** | `ViewContent` | பழைய சாட் பதிவுகளைப் பார்ப்பது | `HistoryActivity` | `history_type: 'chats'` |
| **`transaction_history_viewed`** | `ViewContent` | ரீசார்ஜ் பணப் பரிவர்த்தனைகளைப் பார்ப்பது | `WalletActivity` | `filter: 'all_transactions'` |
| **`app_update_accepted`** | `CustomEvent` | புதிய வெர்ஷன் அப்டேட் பாப்அப்பில் "Update" கிளிக் செய்வது | `MainActivity` | `current_version`, `target_version` |
| **`delete_account_clicked`** | `CustomEvent` | கணக்கு நீக்கம் (Account Delete) கோருவது | `SettingsActivity` | `reason`, `phone` |
| **`logout_clicked`** | `CustomEvent` | கணக்கிலிருந்து வெளியேறுவது (Log out) | `SettingsActivity` | `user_id` |

---

## 🎯 Google Play & Meta Ads-க்கான முக்கிய குறிப்புகள்:
1. **App Installs & Open Tracking:** Meta SDK தானாகவே `fb_mobile_activate_app` மூலம் கணக்கிடுகிறது.
2. **Standard Purchase Event:** ரீசார்ஜ் செய்யும் போது வரும் `fb_mobile_purchase` ஈவென்ட், விளம்பரத்தின் வருவாயை (ROAS) Meta Ads Manager-ல் துல்லியமாக காட்டும்.
3. **Lead & Registration:** பயனர் மொபைல் லாகின் முடிக்கும்போது `fb_mobile_complete_registration` ஈவென்ட் தூண்டப்படுகிறது.
