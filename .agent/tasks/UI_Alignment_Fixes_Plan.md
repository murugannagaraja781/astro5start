# UI Alignment & Text Fixes Plan

Based on the provided screenshots and your feedback, here are the identified issues and the plan to fix them.

## Identified Issues (Tamil List)

நீங்கள் கேட்டபடி, UI-ல் உள்ள பிழைகளின் பட்டியல் இதோ:

1.  **Floating CTA Text Wrap (எழுத்து உடைதல்):** கீழே உள்ள "ஜோதிடருடன் அரட்டை/பேசுங்கள்" பட்டன்களில், "ஜோதிடருடன்" என்ற வார்த்தை பாதியாக உடைந்து ("ஜோதிடருட-ன்") தெரிகிறது. இதை சரி செய்ய வேண்டும்.
2.  **Content Overlap (பட்டியல் மறைப்பு):** கீழே உள்ள அந்த பெரிய பட்டன்கள், ஜோதிடர் பட்டியலின் (Astrologer List) கடைசி பகுதியை மறைக்கின்றன. முழுமையாக Scroll செய்ய முடியவில்லை.
3.  **Banner Button Layout (பேனர் பட்டன்):** மேலே உள்ள Banner-இல் "இப்போது பேசுங்கள்" பட்டன் பாதியாக வெட்டப்பட்டது போல் அல்லது ஒரத்திற்கு தள்ளப்பட்டது போல் உள்ளது.
4.  **Header Alignment (தலைப்பு சீரமைப்பு):** "முன்னணி ஜோதிடர்கள்" தலைப்பும், "அனைத்தையும் காண்க" என்ற வார்த்தையும் ஒரே நேர்கோட்டில் (Alignment) சரியாக இல்லை.

## Implementation Steps

### 1. Fix Floating CTA Text & Layout
*   **Location:** `ClientDashboardActivity.kt` -> `BottomFloatingCTA`
*   **Action:**
    *   Adjust text size or allow generic 2-line structure without breaking words individually.
    *   Ensure the button content is centered properly.

### 2. Fix Content Padding (Overlap Issue)
*   **Location:** `ClientDashboardActivity.kt` -> `HomeScreenContent` -> `LazyColumn`
*   **Action:**
    *   Increase `contentPadding` bottom value to visible clear the Floating CTA height (e.g., increase from 80dp to 100dp or 120dp).

### 3. Fix Main Banner Button
*   **Location:** `ClientDashboardActivity.kt` -> `MainBanner`
*   **Action:**
    *   Check `Card` or `Box` constraints.
    *   Ensure the button inside the banner has enough space and isn't clipped by padding.

### 4. Fix Section Header Alignment
*   **Location:** `ClientDashboardActivity.kt` -> `TopAstrologersSection`
*   **Action:**
    *   Use `Row(verticalAlignment = Alignment.CenterVertically)` which is already there, but maybe add `Modifier.alignByBaseline()` or ensure adequate space division using `weight(1f)`.

## Verification
*   Rebuild APK.
*   Check if "ஜோதிடருடன்" displays fully.
*   Check if the last astrologer card is fully visible above the bottom buttons.
