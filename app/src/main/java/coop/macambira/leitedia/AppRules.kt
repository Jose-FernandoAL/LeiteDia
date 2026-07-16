package coop.macambira.leitedia

import java.time.Instant
import java.time.LocalDate

data class MilkTotals(val entries: Int, val liters: Double, val morning: Double, val afternoon: Double)

object AppRules {
    private const val OFFLINE_SESSION_MAX_AGE = 7L * 24 * 60 * 60 * 1000

    fun isOfflineSessionValid(cachedAt: Long, licenseStatus: String, trialEndsAt: String, licenseActive: Boolean, now: Long): Boolean {
        if (!licenseActive || cachedAt <= 0 || now - cachedAt > OFFLINE_SESSION_MAX_AGE) return false
        if (licenseStatus == "full") return true
        return runCatching { Instant.parse(trialEndsAt).toEpochMilli() > now }.getOrDefault(false)
    }

    fun filterProducers(producers: List<Producer>, search: String): List<Producer> {
        val term = search.trim()
        return if (term.isBlank()) producers else producers.filter {
            it.name.contains(term, ignoreCase = true) || it.community.contains(term, ignoreCase = true)
        }
    }

    fun totals(entries: List<MilkEntry>, date: String): MilkTotals {
        val selected = entries.filter { it.entryDate == date }
        return MilkTotals(
            entries = selected.size,
            liters = selected.sumOf { it.liters },
            morning = selected.filter { it.shift.startsWith("Manh", ignoreCase = true) }.sumOf { it.liters },
            afternoon = selected.filter { it.shift.startsWith("Tarde", ignoreCase = true) }.sumOf { it.liters }
        )
    }

    fun validReportPeriod(start: LocalDate?, end: LocalDate?): Boolean =
        start != null && end != null && !end.isBefore(start) && !end.isAfter(start.plusDays(365))
}
