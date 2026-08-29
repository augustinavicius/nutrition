package io.github.augustinavicius.nutrition.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Format {
    /**
     * Built per call rather than cached: the user can change the system locale while the app
     * is alive, and a cached formatter would keep rendering the old one.
     */
    private fun dayFormatter(withYear: Boolean): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (withYear) "EEE, d MMM yyyy" else "EEE, d MMM", Locale.getDefault())

    /** Trims trailing zeros: 1.0 -> "1", 1.50 -> "1.5", 0.125 -> "0.13". */
    fun amount(value: Double): String {
        if (!value.isFinite()) return "0"
        val rounded = (value * 100).roundToLong() / 100.0
        return if (abs(rounded - rounded.roundToLong()) < 1e-9) {
            rounded.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    fun kcal(value: Double): String = value.roundToInt().toString()

    fun grams(value: Double?): String = value?.let { "${amount(it)} g" } ?: "–"

    fun milligrams(value: Double?): String = value?.let { "${amount(it)} mg" } ?: "–"

    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(dayFormatter(withYear = date.year != today.year))
    }

    fun bytes(count: Long): String {
        if (count < 1024) return "$count B"
        val kb = count / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}
