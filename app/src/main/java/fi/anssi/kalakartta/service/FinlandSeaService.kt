package fi.anssi.kalakartta.service

import android.content.Context
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

/**
 * Answers whether a WGS84 coordinate falls in a sea-marked 500 m EPSG:3067 cell.
 *
 * The grid is loaded once when the service is created. Invalid WGS84 values and
 * coordinates outside the generated grid return false.
 */
class FinlandSeaService private constructor(
    private val grid: FinlandSeaGrid,
    private val coordinateTransform: CoordinateTransform
) {
    fun isSea(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false

        return try {
            val projected = coordinateTransform.transform(
                ProjCoordinate(longitude, latitude),
                ProjCoordinate()
            )
            grid.isSeaAtProjectedCoordinate(projected.x, projected.y)
        } catch (_: RuntimeException) {
            false
        }
    }

    companion object {
        private const val ASSET_NAME = "finland_sea_grid.bin"

        fun fromAssets(context: Context): FinlandSeaService {
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            return fromBinary(bytes)
        }

        internal fun fromBinary(bytes: ByteArray): FinlandSeaService {
            val grid = SeaGridBinaryReader.read(bytes)
            val crsFactory = CRSFactory()
            val source = crsFactory.createFromName("EPSG:4326")
            val target = crsFactory.createFromName("EPSG:3067")
            val transform = CoordinateTransformFactory().createTransform(source, target)
            return FinlandSeaService(grid, transform)
        }
    }
}