package fi.anssi.kalakartta.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSettingsDialogTest {
    @Test
    fun cacheUsageHintShowsDefaultUsedSpaceAndConfiguredLimit() {
        val hint = copernicusCacheUsageHint(1_572_864L, 512)

        assertTrue(hint.matches(Regex("Oletus 512 MB\\. Käytetty 1[.,]5 / 512 MB\\.")))
    }
}