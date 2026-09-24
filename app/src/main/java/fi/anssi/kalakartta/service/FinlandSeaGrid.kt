package fi.anssi.kalakartta.service

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * A validated 100 m sea-cell grid in EPSG:3067 coordinates.
 */
class FinlandSeaGrid internal constructor(
    val originX: Long,
    val originY: Long,
    val columns: Int,
    val rows: Int,
    private val payload: ByteArray
) {
    val cellSize: Int = SeaGridBinaryReader.CELL_SIZE

    init {
        require(payload.size == ((columns.toLong() * rows.toLong() + 7L) / 8L).toInt())
    }

    fun isSeaCell(column: Int, row: Int): Boolean {
        if (column !in 0 until columns || row !in 0 until rows) return false

        val cellIndex = row.toLong() * columns.toLong() + column.toLong()
        val byteIndex = (cellIndex / 8L).toInt()
        val bitMask = 1 shl (7 - (cellIndex % 8L).toInt())
        return payload[byteIndex].toInt() and bitMask != 0
    }

    fun isSeaAtProjectedCoordinate(x: Double, y: Double): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false

        val eastExclusive = originX.toDouble() + columns.toDouble() * cellSize.toDouble()
        val northExclusive = originY.toDouble() + rows.toDouble() * cellSize.toDouble()
        if (x < originX.toDouble() || x >= eastExclusive ||
            y < originY.toDouble() || y >= northExclusive
        ) {
            return false
        }

        val column = floor((x - originX.toDouble()) / cellSize.toDouble()).toInt()
        val row = floor((y - originY.toDouble()) / cellSize.toDouble()).toInt()
        return isSeaCell(column, row)
    }
}

/**
 * Exception thrown when a sea-grid binary does not conform to format v2.
 */
class SeaGridFormatException(message: String) : IllegalArgumentException(message)

object SeaGridBinaryReader {
    const val HEADER_SIZE = 40
    const val CELL_SIZE = 100
    private const val VERSION = 2
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'E'.code.toByte(), 'A'.code.toByte())
    private const val MAX_EPSG3067_EASTING = 1_000_000L
    private const val MAX_EPSG3067_NORTHING = 10_000_000L

    fun read(bytes: ByteArray): FinlandSeaGrid {
        if (bytes.size < HEADER_SIZE) {
            invalid("File is shorter than the $HEADER_SIZE-byte header")
        }

        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            invalid("Invalid sea-grid magic")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.getShort(4).toInt() and 0xffff
        if (version != VERSION) invalid("Unsupported sea-grid version: $version")

        val headerSize = buffer.getShort(6).toInt() and 0xffff
        if (headerSize != HEADER_SIZE) invalid("Invalid sea-grid header size: $headerSize")

        val cellSize = buffer.getInt(8)
        if (cellSize != CELL_SIZE) invalid("Invalid sea-grid cell size: $cellSize")

        val originX = buffer.getLong(12)
        val originY = buffer.getLong(20)
        validateOrigin(originX, originY)

        val columnsLong = buffer.getInt(28).toLong() and 0xffffffffL
        val rowsLong = buffer.getInt(32).toLong() and 0xffffffffL
        if (columnsLong !in 1..Int.MAX_VALUE.toLong()) {
            invalid("Invalid sea-grid column count: $columnsLong")
        }
        if (rowsLong !in 1..Int.MAX_VALUE.toLong()) {
            invalid("Invalid sea-grid row count: $rowsLong")
        }

        val columns = columnsLong.toInt()
        val rows = rowsLong.toInt()
        val cellCount = columnsLong * rowsLong
        val expectedPayloadBytes = (cellCount + 7L) / 8L
        val payloadBytes = buffer.getInt(36).toLong() and 0xffffffffL
        if (payloadBytes != expectedPayloadBytes) {
            invalid("Invalid sea-grid payload size: $payloadBytes, expected $expectedPayloadBytes")
        }
        if (originX > MAX_EPSG3067_EASTING - columnsLong * CELL_SIZE.toLong()) {
            invalid("Sea-grid easting extent is outside EPSG:3067")
        }
        if (originY > MAX_EPSG3067_NORTHING - rowsLong * CELL_SIZE.toLong()) {
            invalid("Sea-grid northing extent is outside EPSG:3067")
        }

        val expectedFileSize = HEADER_SIZE.toLong() + expectedPayloadBytes
        if (expectedPayloadBytes > Int.MAX_VALUE || expectedFileSize != bytes.size.toLong()) {
            invalid("Sea-grid file length does not match its header")
        }

        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        validateUnusedBits(payload, cellCount)
        return FinlandSeaGrid(originX, originY, columns, rows, payload)
    }

    private fun validateOrigin(originX: Long, originY: Long) {
        if (originX < 0L || originX > MAX_EPSG3067_EASTING || originX % CELL_SIZE != 0L) {
            invalid("Invalid EPSG:3067 originX: $originX")
        }
        if (originY < 0L || originY > MAX_EPSG3067_NORTHING || originY % CELL_SIZE != 0L) {
            invalid("Invalid EPSG:3067 originY: $originY")
        }
    }

    private fun validateUnusedBits(payload: ByteArray, cellCount: Long) {
        val usedBitsInLastByte = (cellCount % 8L).toInt()
        if (usedBitsInLastByte == 0) return

        val unusedBitsMask = (1 shl (8 - usedBitsInLastByte)) - 1
        if (payload.last().toInt() and unusedBitsMask != 0) {
            invalid("Unused bits in the sea-grid payload must be zero")
        }
    }

    private fun invalid(message: String): Nothing = throw SeaGridFormatException(message)
}