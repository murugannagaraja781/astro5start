package com.astro5star.app.utils

import java.util.Locale

data class CountryDialCode(
    val code: String,
    val countryName: String,
    val flag: String,
    val currency: String,
    val symbol: String,
    val minLength: Int = 7,
    val maxLength: Int = 15
)

object CurrencyHelper {

    val SUPPORTED_COUNTRIES = listOf(
        CountryDialCode("+91", "India", "🇮🇳", "INR", "₹", 10, 10),
        CountryDialCode("+1", "United States", "🇺🇸", "USD", "$", 10, 10),
        CountryDialCode("+44", "United Kingdom", "🇬🇧", "GBP", "£", 10, 11),
        CountryDialCode("+1", "Canada", "🇨🇦", "CAD", "C$", 10, 10),
        CountryDialCode("+61", "Australia", "🇦🇺", "AUD", "A$", 9, 9),
        CountryDialCode("+971", "UAE", "🇦🇪", "AED", "AED", 9, 9),
        CountryDialCode("+966", "Saudi Arabia", "🇸🇦", "SAR", "SAR", 9, 9),
        CountryDialCode("+65", "Singapore", "🇸🇬", "SGD", "S$", 8, 8),
        CountryDialCode("+60", "Malaysia", "🇲🇾", "MYR", "RM", 9, 10),
        CountryDialCode("+94", "Sri Lanka", "🇱🇰", "LKR", "Rs", 9, 9)
    )

    fun getCountryByCode(code: String): CountryDialCode {
        val normalized = if (code.startsWith("+")) code else "+$code"
        return SUPPORTED_COUNTRIES.find { it.code == normalized } ?: SUPPORTED_COUNTRIES[0]
    }

    fun getSymbol(currency: String?): String {
        return when (currency?.uppercase(Locale.ROOT)) {
            "USD" -> "$"
            "GBP" -> "£"
            "SGD" -> "S$"
            "MYR" -> "RM"
            "AED" -> "AED"
            "AUD" -> "A$"
            "LKR" -> "Rs"
            else -> "₹"
        }
    }

    val DEFAULT_EXCHANGE_RATES = mapOf(
        "INR" to 1.0,
        "USD" to 85.0,
        "SGD" to 65.0,
        "MYR" to 19.0,
        "LKR" to 0.28,
        "GBP" to 110.0,
        "AED" to 23.0,
        "AUD" to 55.0,
        "CAD" to 62.5
    )

    val DEFAULT_MULTIPLIERS = mapOf(
        "INR" to 1.0,
        "USD" to 2.0,
        "SGD" to 2.0,
        "MYR" to 2.0,
        "LKR" to 2.0
    )

    fun formatPrice(amount: Double, currency: String? = "INR"): String {
        val sym = getSymbol(currency)
        val cur = currency?.uppercase(Locale.ROOT) ?: "INR"
        return if (cur == "INR") {
            "$sym${if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)}"
        } else {
            "$sym${String.format(Locale.US, "%.2f", amount)}"
        }
    }

    /**
     * Converts INR rate to Target Currency using International Price Multiplier:
     * Effective INR = inrAmount * price_multiplier
     * Foreign Rate = round(Effective INR / inr_exchange_rate, 2)
     */
    fun convertInrToTarget(
        inrAmount: Double,
        currency: String? = "INR",
        exchangeRate: Double? = null,
        multiplier: Double? = null
    ): Double {
        if (currency == null || currency.uppercase(Locale.ROOT) == "INR" || inrAmount <= 0.0) return inrAmount
        val cur = currency.uppercase(Locale.ROOT)
        val rate = exchangeRate ?: (DEFAULT_EXCHANGE_RATES[cur] ?: 85.0)
        val mult = multiplier ?: (DEFAULT_MULTIPLIERS[cur] ?: 2.0)

        val effectiveInr = inrAmount * mult
        val converted = effectiveInr / rate
        return Math.round(converted * 100.0) / 100.0
    }
}
