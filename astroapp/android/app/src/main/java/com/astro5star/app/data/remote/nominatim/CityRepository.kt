package com.astro5star.app.data.remote.nominatim

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap

// -------------------- API --------------------

interface NominatimApi {

    @GET("search")
    suspend fun searchCity(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 10
    ): List<NominatimResult>
}

// -------------------- REPOSITORY --------------------

object CityRepository {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/"
    private const val CACHE_TTL = 5 * 60 * 1000L // 5 minutes
    private const val MAX_CACHE_SIZE = 50

    // ---- OkHttp (policy-compliant) ----
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header(
                            "User-Agent",
                            "Astro5StarApp/1.0 (contact@astro5star.com)"
                        )
                        .build()
                )
            }
            .build()
    }

    // ---- Retrofit ----
    private val api: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }

    // ---- Cache ----
    private data class CacheEntry(
        val data: List<NominatimResult>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // ---- Public API ----
    suspend fun searchCities(query: String): Result<List<NominatimResult>> {
        val key = query.trim().lowercase()
        if (key.length < 2) return Result.success(emptyList())

        val now = System.currentTimeMillis()

        cache[key]?.let {
            if (now - it.timestamp < CACHE_TTL) {
                return Result.success(it.data)
            }
        }

        return try {
            val result = api.searchCity(key)

            evictIfNeeded()

            cache[key] = CacheEntry(
                data = result,
                timestamp = now
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Cache eviction ----
    private fun evictIfNeeded() {
        if (cache.size < MAX_CACHE_SIZE) return

        val oldestKey = cache.minByOrNull {
            it.value.timestamp
        }?.key ?: return

        cache.remove(oldestKey)
    }
}
