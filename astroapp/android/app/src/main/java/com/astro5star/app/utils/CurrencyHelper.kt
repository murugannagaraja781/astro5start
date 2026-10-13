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

    fun formatPrice(amount: Double, currency: String? = "INR"): String {
        val sym = getSymbol(currency)
        return if (currency == "USD") {
            "$sym${String.format(Locale.US, "%.2f", amount)}"
        } else {
            "$sym${if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)}"
        }
    }

    fun convertInrToTarget(inrAmount: Double, currency: String? = "INR", exchangeRate: Double = 85.0): Double {
        if (currency == null || currency == "INR") return inrAmount
        val rate = if (exchangeRate > 0) exchangeRate else 85.0
        val converted = inrAmount / rate
        return Math.round(converted * 100.0) / 100.0
    }
}
