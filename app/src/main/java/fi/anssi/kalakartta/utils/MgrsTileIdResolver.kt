package fi.anssi.kalakartta.utils

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class MgrsTileIdResolver {
    private val crsFactory = CRSFactory()
    private val sourceCrs = crsFactory.createFromName("EPSG:4326")
    private val transforms = ConcurrentHashMap<Int, CoordinateTransform>()

    fun resolve(latitude: Double, longitude: Double): String {
        require(latitude.isFinite() && longitude.isFinite()) { "Coordinates must be finite" }
        require(latitude in -80.0..84.0 && longitude in -180.0..180.0) {
            "Coordinate is outside the MGRS coverage area"
        }

        val zone = utmZone(latitude, longitude)
        val latitudeBand = latitudeBand(latitude)
        val epsgCode = (if (latitude < 0.0) 32700 else 32600) + zone
        val transform = transforms.computeIfAbsent(epsgCode) {
            val targetCrs = crsFactory.createFromName("EPSG:$epsgCode")
            CoordinateTransformFactory().createTransform(sourceCrs, targetCrs)
        }
        val projected = synchronized(transform) {
            transform.transform(ProjCoordinate(longitude, latitude), ProjCoordinate())
        }
        require(projected.x.isFinite() && projected.y.isFinite()) { "Unable to project coordinate to UTM" }

        val eastingColumn = floor(projected.x / 100_000.0).toInt() - 1
        val columnSet = (zone - 1) % 6
        val columnLetter = COLUMN_SETS[columnSet].getOrNull(eastingColumn)
            ?: throw IllegalArgumentException("Projected easting is outside the MGRS grid")
        val rowIndex = floor(projected.y / 100_000.0).toInt() % ROW_LETTERS.length
        val rowOffset = if (zone % 2 == 0) 5 else 0
        val rowLetter = ROW_LETTERS[(rowIndex + rowOffset) % ROW_LETTERS.length]

        return "$zone$latitudeBand$columnLetter$rowLetter"
    }

    private fun utmZone(latitude: Double, longitude: Double): Int {
        var zone = if (longitude == 180.0) 60 else floor((longitude + 180.0) / 6.0).toInt() + 1

        if (latitude >= 56.0 && latitude < 64.0 && longitude >= 3.0 && longitude < 12.0) {
            zone = 32
        } else if (latitude >= 72.0 && latitude < 84.0) {
            zone = when {
                longitude >= 0.0 && longitude < 9.0 -> 31
                longitude >= 9.0 && longitude < 21.0 -> 33
                longitude >= 21.0 && longitude < 33.0 -> 35
                longitude >= 33.0 && longitude < 42.0 -> 37
                else -> zone
            }
        }
        return zone.coerceIn(1, 60)
    }

    private fun latitudeBand(latitude: Double): Char {
        val index = floor((latitude + 80.0) / 8.0).toInt().coerceIn(0, LATITUDE_BANDS.lastIndex)
        return LATITUDE_BANDS[index]
    }

    private companion object {
        const val LATITUDE_BANDS = "CDEFGHJKLMNPQRSTUVWX"
        const val COLUMN_SET_TEXT = "ABCDEFGH,JKLMNPQR,STUVWXYZ,ABCDEFGH,JKLMNPQR,STUVWXYZ"
        const val ROW_LETTERS = "ABCDEFGHJKLMNPQRSTUV"
        val COLUMN_SETS = COLUMN_SET_TEXT.split(',')
    }
}