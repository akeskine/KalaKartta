package fi.anssi.kalakartta.utils

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

class TraficomTileSource(layer: String = "Traficom:Merikarttasarjat public") : XYTileSource(
    "Traficom Meri- ja veneilykartat",
    5, 15, 256, ".png",
    arrayOf("https://julkinen.traficom.fi/rasteripalvelu/wmts?")
) {
    private val currentLayer = layer

    override fun getTileURLString(pTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pTileIndex)
        val x = MapTileIndex.getX(pTileIndex)
        val y = MapTileIndex.getY(pTileIndex)
        
        return (baseUrl + "service=WMTS&request=GetTile&version=1.0.0" +
                "&layer=$currentLayer" +
                "&style=default" +
                "&tilematrixset=WGS84_Pseudo-Mercator" +
                "&tilematrix=WGS84_Pseudo-Mercator:$z" +
                "&tilerow=$y" +
                "&tilecol=$x" +
                "&format=image/png")
    }
}
