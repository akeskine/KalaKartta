package fi.anssi.kalakartta.utils

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

class MMLTileSource(
    name: String,
    private val layer: String,
    private val apiKey: String
) : XYTileSource(
    name,
    0, 19, 256, ".png",
    arrayOf("https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/")
) {
    override fun getTileURLString(pTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pTileIndex)
        val x = MapTileIndex.getX(pTileIndex)
        val y = MapTileIndex.getY(pTileIndex)
        return baseUrl + layer + "/default/WGS84_Pseudo-Mercator/$z/$y/$x.png?api-key=$apiKey"
    }
}
