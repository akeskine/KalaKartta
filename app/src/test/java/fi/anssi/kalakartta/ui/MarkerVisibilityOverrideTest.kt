package fi.anssi.kalakartta.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerVisibilityOverrideTest {
    private val visibilityOverride = MarkerVisibilityOverride()

    @Test
    fun `toggling hidden state hides and restores markers without changing map visibility`() {
        assertTrue(visibilityOverride.shouldBeVisible(mapAllowsVisibility = true))

        assertTrue(visibilityOverride.toggleHidden())
        assertFalse(visibilityOverride.shouldBeVisible(mapAllowsVisibility = true))

        assertFalse(visibilityOverride.toggleHidden())
        assertTrue(visibilityOverride.shouldBeVisible(mapAllowsVisibility = true))
    }

    @Test
    fun `restore tap only consumes hidden state once`() {
        assertFalse(visibilityOverride.restoreIfHidden())

        visibilityOverride.toggleHidden()

        assertTrue(visibilityOverride.restoreIfHidden())
        assertFalse(visibilityOverride.restoreIfHidden())
        assertTrue(visibilityOverride.shouldBeVisible(mapAllowsVisibility = true))
    }

    @Test
    fun `hidden override does not bypass map visibility`() {
        visibilityOverride.toggleHidden()

        assertFalse(visibilityOverride.shouldBeVisible(mapAllowsVisibility = false))

        visibilityOverride.toggleHidden()

        assertFalse(visibilityOverride.shouldBeVisible(mapAllowsVisibility = false))
    }
}