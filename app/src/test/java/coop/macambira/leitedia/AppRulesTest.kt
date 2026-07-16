package coop.macambira.leitedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AppRulesTest {
    private val now = Instant.parse("2026-07-16T12:00:00Z").toEpochMilli()

    @Test fun fullLicenseAllowsRecentOfflineSession() {
        assertTrue(AppRules.isOfflineSessionValid(now - 1_000, "full", "", true, now))
    }

    @Test fun offlineSessionExpiresAfterSevenDays() {
        assertFalse(AppRules.isOfflineSessionValid(now - 8L * 24 * 60 * 60 * 1000, "full", "", true, now))
    }

    @Test fun expiredTrialCannotBeUsedOffline() {
        assertFalse(AppRules.isOfflineSessionValid(now - 1_000, "trial", "2026-07-15T12:00:00Z", true, now))
    }

    @Test fun producerSearchMatchesNameAndCommunity() {
        val producers = listOf(
            Producer(1, "João Silva", "", "", "Sítio Novo", true),
            Producer(2, "Maria Souza", "", "", "Macambira", true)
        )
        assertEquals(listOf(1L), AppRules.filterProducers(producers, "joão").map { it.id })
        assertEquals(listOf(2L), AppRules.filterProducers(producers, "macambira").map { it.id })
    }

    @Test fun totalsSeparateMorningAndAfternoon() {
        val entries = listOf(
            MilkEntry(1, "2026-07-16", "A", "Manhã", 10.0, null, null, ""),
            MilkEntry(2, "2026-07-16", "B", "Tarde", 7.5, null, null, ""),
            MilkEntry(3, "2026-07-15", "A", "Manhã", 99.0, null, null, "")
        )
        assertEquals(MilkTotals(2, 17.5, 10.0, 7.5), AppRules.totals(entries, "2026-07-16"))
    }

    @Test fun reportPeriodMustBeOrderedAndLimitedToOneYear() {
        val start = LocalDate.parse("2026-01-01")
        assertTrue(AppRules.validReportPeriod(start, start.plusDays(365)))
        assertFalse(AppRules.validReportPeriod(start, start.minusDays(1)))
        assertFalse(AppRules.validReportPeriod(start, start.plusDays(366)))
    }
}
