package fi.anssi.kalakartta.map

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fi.anssi.kalakartta.ui.CopernicusCredentialStore
import fi.anssi.kalakartta.ui.SettingsStore
import fi.anssi.kalakartta.utils.CopernicusClientCredentials
import fi.anssi.kalakartta.utils.CopernicusProcessClient
import fi.anssi.kalakartta.utils.CopernicusTileCache
import fi.anssi.kalakartta.utils.CopernicusTileSource
import fi.anssi.kalakartta.utils.CopernicusTokenManager
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.IOException

internal fun usesCopernicusTileProvider(tileSource: ITileSource): Boolean =
    tileSource is CopernicusTileSource

internal fun copernicusProviderZoomRange(tileSource: ITileSource?): IntRange? =
    tileSource?.takeIf(::usesCopernicusTileProvider)?.let { it.minimumZoomLevel..it.maximumZoomLevel }

class CopernicusMapTileProvider(context: Context, settingsStore: SettingsStore) : MapTileProviderBasic(context) {
    init {
        val copernicusProvider = CopernicusMapTileModuleProvider(context, settingsStore)
        copernicusProvider.setTileSource(getTileSource() ?: TileSourceFactory.MAPNIK)
        val downloaderIndex = mTileProviderList.indexOfFirst { it is MapTileDownloader }
        val insertionIndex = if (downloaderIndex >= 0) downloaderIndex else mTileProviderList.size
        mTileProviderList.add(insertionIndex, copernicusProvider)
    }
}

private class CopernicusMapTileModuleProvider(
    context: Context,
    settingsStore: SettingsStore
) : MapTileModuleProviderBase(2, 40) {
    private val appContext = context.applicationContext
    private val credentialStore = CopernicusCredentialStore(appContext)
    private val tileCache = CopernicusTileCache(appContext.filesDir) {
        settingsStore.copernicusTileCacheLimitMb.toLong() * CopernicusTileCache.BYTES_PER_MEGABYTE
    }

    init {
        tileCache.enforceSizeLimitAsync()
    }

    private val tileSourceLock = Any()
    @Volatile
    private var tileSource: ITileSource? = null
    private var tileSourceGeneration = 0L

    private val tokenManager = CopernicusTokenManager({
        val clientId = settingsStore.copernicusClientId
        val clientSecret = credentialStore.getClientSecret()
        if (clientId.isBlank() || clientSecret.isNullOrBlank()) {
            throw IOException("Copernicus credentials are not configured")
        }
        CopernicusClientCredentials(clientId, clientSecret)
    })
    private val processClient = CopernicusProcessClient(tokenManager)

    private val tileLoader = object : TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val sourceSnapshot = sourceSnapshot() ?: return null
            val source = sourceSnapshot.source
            if (!source.sceneAvailable) return null
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return try {
                val jpeg = tileCache.getCached(source.imageDate, zoom, x, y)
                    ?: run {
                        if (!hasValidatedNetwork()) return null
                        tileCache.getOrLoadGroup(
                            source.imageDate,
                            zoom,
                            x,
                            y,
                            imageSizePixels = settingsStore.copernicusTileImageSizePixels,
                            loader = { group ->
                                processClient.getTile(
                                    group.zoom,
                                    group.firstX,
                                    group.firstY,
                                    source.imageDate,
                                    group.imageSizePixels
                                )
                            },
                            splitter = CopernicusTileImageSplitter::split
                        )
                    }
                if (!isCurrentSource(sourceSnapshot)) return null
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: run {
                    tileCache.remove(source.imageDate, zoom, x, y)
                    return null
                }
                if (!isCurrentSource(sourceSnapshot)) return null
                BitmapDrawable(appContext.resources, bitmap)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun hasValidatedNetwork(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun getTileLoader(): TileLoader = tileLoader

    override fun getUsesDataConnection(): Boolean = false

    override fun getMinimumZoomLevel(): Int = currentCopernicusZoomRange()?.first ?: Int.MAX_VALUE

    override fun getMaximumZoomLevel(): Int = currentCopernicusZoomRange()?.last ?: Int.MIN_VALUE

    override fun getName(): String = "Copernicus Process API"

    override fun getThreadGroupName(): String = "CopernicusTileProvider"

    override fun setTileSource(tileSource: ITileSource) {
        synchronized(tileSourceLock) {
            this.tileSource = tileSource
            tileSourceGeneration++
        }
    }

    private fun currentCopernicusZoomRange(): IntRange? = synchronized(tileSourceLock) {
        copernicusProviderZoomRange(tileSource)
    }

    private fun sourceSnapshot(): TileSourceSnapshot? = synchronized(tileSourceLock) {
        (tileSource as? CopernicusTileSource)?.let { TileSourceSnapshot(it, tileSourceGeneration) }
    }

    private fun isCurrentSource(snapshot: TileSourceSnapshot): Boolean = synchronized(tileSourceLock) {
        tileSourceGeneration == snapshot.generation && tileSource === snapshot.source
    }

    private data class TileSourceSnapshot(val source: CopernicusTileSource, val generation: Long)
}