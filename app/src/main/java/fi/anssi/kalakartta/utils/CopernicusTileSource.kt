package fi.anssi.kalakartta.utils

import org.osmdroid.tileprovider.tilesource.XYTileSource

class CopernicusTileSource(
    val imageDate: String = INITIAL_IMAGE_DATE,
    val sceneAvailable: Boolean = true,
    val maxCloudCoveragePercent: Int = CopernicusCloudCoverage.DEFAULT_PERCENT,
    val onBlackTile: ((String) -> Unit)? = null
) : XYTileSource(
    "Copernicus Sentinel-2",
    0,
    18,
    256,
    ".jpg",
    arrayOf(PROCESS_API_URL)
) {
    init {
        CopernicusCloudCoverage.requireValid(maxCloudCoveragePercent)
    }

    override fun getTileURLString(pTileIndex: Long): String = ""

    companion object {
        const val INITIAL_IMAGE_DATE = "2026-09-27"
        const val PROCESS_API_URL = "https://sh.dataspace.copernicus.eu/process/v1"
    }
}