package fi.anssi.kalakartta.ui

class MarkerVisibilityOverride {
    private var isHidden = false

    fun toggleHidden(): Boolean {
        isHidden = !isHidden
        return isHidden
    }

    fun shouldBeVisible(mapAllowsVisibility: Boolean): Boolean = mapAllowsVisibility && !isHidden
}