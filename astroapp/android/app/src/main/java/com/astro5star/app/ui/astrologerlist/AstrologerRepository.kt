package com.astro5star.app.ui.astrologerlist

import com.astro5star.app.data.model.Astrologer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request

class AstrologerRepository {

    private val client = OkHttpClient()
    private val SERVER_URL = com.astro5star.app.utils.Constants.SERVER_URL

    /**
     * Optimized data fetching with pagination support.
     * In a real production app, this would use Retrofit or Paging 3.
     */
    suspend fun getAstrologers(page: Int, limit: Int): List<Astrologer> = withContext(Dispatchers.IO) {
        // Simulate network delay for shimmer demonstration
        if (page == 1) delay(1500) 

        val result = mutableListOf<Astrologer>()
        try {
            val url = "$SERVER_URL/api/astrology/astrologers?page=$page&limit=$limit"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        result.add(parseAstrologer(arr.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Performance: Sort on background thread before returning to UI
        result.sortedWith(
            compareByDescending<Astrologer> { it.isOnline || it.isChatOnline }.thenBy { it.displayOrder }
        )
    }

    private fun parseAstrologer(json: JSONObject): Astrologer {
        val skillsArr = json.optJSONArray("skills")
        val skills = mutableListOf<String>()
        if (skillsArr != null) {
            for (i in 0 until skillsArr.length()) {
                skills.add(skillsArr.getString(i))
            }
        }
        return Astrologer(
            userId = json.optString("userId", ""),
            name = json.optString("name", "Astrologer"),
            skills = skills,
            price = json.optInt("price", 15),
            isOnline = json.optBoolean("isOnline", false),
            isChatOnline = json.optBoolean("isChatOnline", false),
            isAudioOnline = json.optBoolean("isAudioOnline", false),
            isVideoOnline = json.optBoolean("isVideoOnline", false),
            image = json.optString("image", ""),
            experience = json.optInt("experience", 0),
            isBusy = json.optBoolean("isBusy", false),
            chatPrice = json.optInt("chatPrice", 10),
            audioPrice = json.optInt("audioPrice", 20),
            videoPrice = json.optInt("videoPrice", 30),
            displayOrder = json.optInt("displayOrder", 1000)
        )
    }
}
