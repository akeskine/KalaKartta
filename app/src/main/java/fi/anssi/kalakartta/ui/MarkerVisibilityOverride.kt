package fi.anssi.kalakartta.ui

class MarkerVisibilityOverride {
    private var isHidden = false

    fun toggleHidden(): Boolean {
        isHidden = !isHidden
        return isHidden
    }

    fun restoreIfHidden(): Boolean {
        if (!isHidden) return false
        isHidden = false
        return true
    }

    fun shouldBeVisible(mapAllowsVisibility: Boolean): Boolean = mapAllowsVisibility && !isHidden
}