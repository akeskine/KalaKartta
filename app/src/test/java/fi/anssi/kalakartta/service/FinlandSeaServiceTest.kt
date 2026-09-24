package fi.anssi.kalakartta.service



import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

class FinlandSeaServiceTest {
    @Test
    fun validatedMmlAssetCoversTheDocumentedSeaAreasAndExcludesInlandWater() {
        val service = generatedAssetService()

        assertTrue(service.isSea(60.0, 25.5))
        assertTrue(service.isSea(63.5, 21.5))
        assertTrue(service.isSea(60.0, 19.8))
        assertTrue("Särkisalo", service.isSea(60.132489, 22.898085))
        assertTrue("Hangon edusta ulkona", service.isSea(59.822092, 22.645795))
        assertTrue("Mossala", service.isSea(60.301151, 21.392096))
        assertTrue("Porin edusta", service.isSea(61.608949, 21.136415))
        assertTrue("Ahvenanmeri", service.isSea(60.238244, 19.274199))
        assertTrue("Kemijokisuu", service.isSea(65.755745, 24.373240))
        assertFalse(service.isSea(60.1699, 24.9384))
        assertFalse(service.isSea(62.2426, 25.7473))
        assertFalse(service.isSea(61.74, 26.12))
        assertFalse(service.isSea(61.72, 26.35))
        assertFalse("Björkboda Träsk, Kemiönsaari", service.isSea(60.077577, 22.569815))
        assertFalse("Kauniaisten Gallträsk", service.isSea(60.218908, 24.730277))
        assertFalse("Bodomjärvi Eteläranta", service.isSea(60.241064, 24.661880))
        assertFalse("Säkylän Pyhäjärvi", service.isSea(61.000792, 22.300922))
        assertFalse("Suontee", service.isSea(61.712211, 26.266856))
        assertFalse("Ruotsi, manner", service.isSea(63.092523, 12.704246))
        assertFalse("Norjanmeri", service.isSea(64.622158, 8.925556))
        assertFalse("Sodankylä", service.isSea(67.415421, 26.590946))
    }

    @Test
    fun validatedMmlAssetMarksACellWithOnlyASmallSeaIntersection() {
        val service = generatedAssetService()

        assertTrue(service.isSea(59.88385529222567, 24.838885306667358))
    }

    @Test
    fun transformsWgs84IntoTheMarkedProjectedCell() {
        val latitude = 60.1699
        val longitude = 24.9384
        val projected = wgs84ToEpsg3067(longitude, latitude)
        val originX = floor(projected.x / SeaGridBinaryReader.CELL_SIZE).toLong() *
            SeaGridBinaryReader.CELL_SIZE
        val originY = floor(projected.y / SeaGridBinaryReader.CELL_SIZE).toLong() *
            SeaGridBinaryReader.CELL_SIZE
        val service = FinlandSeaService.fromBinary(fixture(originX, originY))

        assertTrue(service.isSea(latitude, longitude))
        assertFalse(service.isSea(0.0, 0.0))
    }

    @Test
    fun invalidWgs84ValuesReturnFalse() {
        val service = FinlandSeaService.fromBinary(fixture(0, 0))

        assertFalse(service.isSea(Double.NaN, 24.0))
        assertFalse(service.isSea(60.0, Double.POSITIVE_INFINITY))
        assertFalse(service.isSea(90.000001, 24.0))
        assertFalse(service.isSea(-90.000001, 24.0))
        assertFalse(service.isSea(60.0, 180.000001))
        assertFalse(service.isSea(60.0, -180.000001))
    }

    private fun wgs84ToEpsg3067(longitude: Double, latitude: Double): ProjCoordinate {
        val crsFactory = CRSFactory()
        val transform = CoordinateTransformFactory().createTransform(
            crsFactory.createFromName("EPSG:4326"),
            crsFactory.createFromName("EPSG:3067")
        )
        return transform.transform(ProjCoordinate(longitude, latitude), ProjCoordinate())
    }

    private fun generatedAssetService(): FinlandSeaService {
        val asset = listOf(
            File("src/main/assets/finland_sea_grid.bin"),
            File("app/src/main/assets/finland_sea_grid.bin")
        ).firstOrNull { it.isFile }
            ?: error("Validated MML asset is missing")
        return FinlandSeaService.fromBinary(asset.readBytes())
    }

    private fun fixture(originX: Long, originY: Long): ByteArray {
        val bytes = ByteArray(41)
        bytes[0] = 'K'.code.toByte()
        bytes[1] = 'S'.code.toByte()
        bytes[2] = 'E'.code.toByte()
        bytes[3] = 'A'.code.toByte()
        putShort(bytes, 4, 2)
        putShort(bytes, 6, SeaGridBinaryReader.HEADER_SIZE)
        putInt(bytes, 8, SeaGridBinaryReader.CELL_SIZE)
        putLong(bytes, 12, originX)
        putLong(bytes, 20, originY)
        putInt(bytes, 28, 1)
        putInt(bytes, 32, 1)
        putInt(bytes, 36, 1)
        bytes[40] = 0x80.toByte()
        return bytes
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, value.toShort())
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }

    private fun putLong(bytes: ByteArray, offset: Int, value: Long) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(offset, value)
    }
}