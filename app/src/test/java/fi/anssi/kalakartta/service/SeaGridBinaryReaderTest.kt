package fi.anssi.kalakartta.service

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class SeaGridBinaryReaderTest {
    @Test
    fun readsMsbFirstRowMajorBitsAndCellBoundaries() {
        val grid = SeaGridBinaryReader.read(fixture())

        assertTrue(grid.isSeaCell(0, 0))
        assertFalse(grid.isSeaCell(1, 0))
        assertTrue(grid.isSeaCell(1, 1))
        assertTrue(grid.isSeaCell(2, 2))
        assertFalse(grid.isSeaCell(2, 1))
        assertFalse(grid.isSeaCell(-1, 0))
        assertFalse(grid.isSeaCell(3, 0))
        assertFalse(grid.isSeaCell(0, 3))
    }

    @Test
    fun projectedCoordinatesUseTheExpectedHalfOpenGridBounds() {
        val grid = SeaGridBinaryReader.read(fixture())

        assertTrue(grid.isSeaAtProjectedCoordinate(1_000.0, 2_000.0))
        assertTrue(grid.isSeaAtProjectedCoordinate(1_500.0, 2_500.0))
        assertFalse(grid.isSeaAtProjectedCoordinate(2_000.0, 2_500.0))
        assertFalse(grid.isSeaAtProjectedCoordinate(2_500.0, 3_500.0))
        assertFalse(grid.isSeaAtProjectedCoordinate(2_500.0, 2_000.0))
        assertFalse(grid.isSeaAtProjectedCoordinate(Double.NaN, 2_000.0))
    }

    @Test
    fun rejectsInvalidMagic() {
        assertInvalid(fixture().also { it[0] = 'X'.code.toByte() })
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertInvalid(fixture().also { putShort(it, 4, 2) })
    }

    @Test
    fun rejectsInvalidMetadataAndPayloadLength() {
        assertInvalid(fixture().also { putShort(it, 6, 39) })
        assertInvalid(fixture().also { putInt(it, 8, 250) })
        assertInvalid(fixture().also { putLong(it, 12, -500) })
        assertInvalid(fixture().also { putInt(it, 28, 0) })
        assertInvalid(fixture().also { putInt(it, 36, 1) })
        assertInvalid(fixture().copyOf(fixture().size - 1))
        assertInvalid(fixture() + byteArrayOf(0))
    }

    @Test
    fun rejectsNonZeroUnusedPayloadBits() {
        assertInvalid(fixture().also { it[it.lastIndex] = 0x81.toByte() })
    }

    private fun fixture(): ByteArray {
        val bytes = ByteArray(42)
        bytes[0] = 'K'.code.toByte()
        bytes[1] = 'S'.code.toByte()
        bytes[2] = 'E'.code.toByte()
        bytes[3] = 'A'.code.toByte()
        putShort(bytes, 4, 1)
        putShort(bytes, 6, SeaGridBinaryReader.HEADER_SIZE)
        putInt(bytes, 8, SeaGridBinaryReader.CELL_SIZE)
        putLong(bytes, 12, 1_000)
        putLong(bytes, 20, 2_000)
        putInt(bytes, 28, 3)
        putInt(bytes, 32, 3)
        putInt(bytes, 36, 2)
        bytes[40] = 0x88.toByte()
        bytes[41] = 0x80.toByte()
        return bytes
    }

    private fun assertInvalid(bytes: ByteArray) {
        try {
            SeaGridBinaryReader.read(bytes)
            fail("Expected malformed sea-grid binary to be rejected")
        } catch (_: SeaGridFormatException) {
            // Expected.
        }
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