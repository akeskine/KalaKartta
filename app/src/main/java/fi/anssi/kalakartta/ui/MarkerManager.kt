package fi.anssi.kalakartta.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.content.Intent
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.MainActivity
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.FishDiaryPage
import fi.anssi.kalakartta.data.Media
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import kotlinx.coroutines.*

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

class MarkerManager(
    private val context: Context,
    private val map: MapView,
    private val db: AppDatabase,
    private val onDeleteConfirmed: (Marker) -> Unit
) {
    private fun launchActivityForResult(intent: Intent, requestCode: Int) {
        if (context is MainActivity) {
            context.launchActivityForResult(intent, requestCode)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settingsStore = SettingsStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    private var rebuildJob: Job? = null
    
    private val layerState = MarkerLayerState()
    private val overlayController = MarkerOverlayController(map, layerState)
    private val defaultPointsFolder get() = layerState.defaultPointsFolder
    private val catchesFolder get() = layerState.catchesFolder
    private val placesFolder get() = layerState.placesFolder
    private val markersFolder get() = layerState.markersFolder
    private val iconFactory = MarkerIconFactory(context)
    private val clusterCalculator = ClusterCalculator()
    private val visibilityCalculator = MarkerVisibilityCalculator()
    private val speciesCache = mutableMapOf<String, fi.anssi.kalakartta.data.FishSpecies>()
    private val placeTypeCache = mutableMapOf<String, PlaceOfInterestType>()

    private var fishIconScale = 1.0f
    private var otherIconScale = 1.0f

    private val fishIconResolver = FishIconResolver(
        filesDir = context.filesDir,
        resolveSpecies = { speciesCache[it] },
        resolveDrawableId = ::getDrawableId,
        getIconScale = { fishIconScale }
    )
    private val clusterMarkerRenderer = ClusterMarkerRenderer(
        iconFactory = iconFactory,
        resolveIconParams = fishIconResolver::resolve,
        resolveSpeciesName = { speciesCache[it]?.name }
    )
    private val individualMarkerRenderer = IndividualMarkerRenderer(
        iconFactory = iconFactory,
        resolveIconParams = fishIconResolver::resolve,
        resolveSpeciesName = { speciesCache[it]?.name }
    )
    private val placeMarkerRenderer = PlaceMarkerRenderer(
        iconFactory = iconFactory,
        resolveDrawableId = ::getDrawableId,
        getOtherIconScale = { otherIconScale }
    )
    private val rebuildRenderer = MarkerRebuildRenderer(
        map = map,
        overlayController = overlayController,
        iconFactory = iconFactory,
        clusterCalculator = clusterCalculator,
        visibilityCalculator = visibilityCalculator,
        createPlaceMarker = ::createPlaceMarker,
        createIndividualMarker = ::createIndividualMarker,
        createClusterMarker = ::createClusterMarker
    )

    init {
        loadSettings()
    }

    private fun loadSettings() {
        fishIconScale = settingsStore.fishIconScale
        otherIconScale = settingsStore.otherIconScale
    }
    
    // Marker-olioiden kierrätys
    private val activeIndividualMarkers get() = layerState.activeIndividualMarkers
    private val activePlaceMarkers get() = layerState.activePlaceMarkers

    private val dataStore = MarkerDataStore()
    private val mediaLoader = MarkerMediaLoader(context)
    private val detailsLoader = MarkerDetailsLoader(db, mediaLoader)
    private val deletionHandler = MarkerDeletionHandler(context, map, onDeleteConfirmed)
    private val placeDetailsDialog = PlaceDetailsDialog(
        context = context,
        mediaLoader = mediaLoader,
        openMedia = ::openMedia,
        onEdit = ::showEditPlaceDialog,
        onDelete = ::confirmDeletePlace
    )
    private val catchDetailsDialog = CatchDetailsDialog(
        context = context,
        mediaLoader = mediaLoader,
        resolveIconParams = fishIconResolver::resolve,
        openMedia = ::openMedia,
        onEdit = { marker, fish ->
            val currentFish = fish ?: marker.relatedObject as? FishCatch
            val intent = Intent(context, EditCatchActivity::class.java)
            intent.putExtra("EXTRA_CATCH_ID", currentFish?.id)
            launchActivityForResult(intent, 1001)
        },
        onDelete = ::confirmDeleteMarker
    )
    private val catchDetailsTextBuilder = CatchDetailsTextBuilder(context)
    
    private var lastZoom = -1.0
    private var lastBBox: BoundingBox? = null
    
    private val placeInfoWindow by lazy {
        object : MarkerInfoWindow(R.layout.place_info_window, map) {
            override fun onOpen(item: Any?) {
                val marker = item as? Marker
                val title = mView.findViewById<TextView>(R.id.bubble_title)
                val image = mView.findViewById<ImageView>(R.id.bubble_image)
                title.text = marker?.title

                val related = marker?.relatedObject
                image.visibility = View.GONE
                scope.launch {
                    val mediaList = withContext(Dispatchers.IO) {
                        when (related) {
                            is FishCatch -> mediaLoader.getForCatch(related)
                            is PlaceOfInterest -> mediaLoader.getForPlace(related.latitude, related.longitude)
                            else -> emptyList()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        val firstImage = mediaList.firstOrNull { it.mimeType.startsWith("image/") }
                        if (firstImage != null) {
                            val bitmap = mediaLoader.decodeBitmap(firstImage)
                            if (bitmap != null) {
                                image.setImageBitmap(bitmap)
                                image.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                
                // Sulje infowindow klikattaessa tekstiä, jotta se ei estä merkin klikkausta
                mView.setOnClickListener {
                    close()
                    marker?.let { 
                        if (related is FishCatch) showCatchDetailsDialog(it)
                        else showPlaceDetailsDialog(it)
                    }
                }
            }
        }
    }

    init {
        map.overlays.add(defaultPointsFolder)
        map.overlays.add(placesFolder)
        map.overlays.add(catchesFolder)
        map.overlays.add(markersFolder)
    }

    fun addMarker(fish: FishCatch) {
        dataStore.upsertCatch(fish)
    }

    fun setAllCatches(catches: List<FishCatch>) {
        dataStore.setCatches(catches)
        android.util.Log.d("MarkerManager", "setAllCatches: list size = ${catches.size}")
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun setAllPlaces(places: List<PlaceOfInterest>) {
        dataStore.setPlaces(places)
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun addOrUpdatePlaceIncremental(place: PlaceOfInterest, zoom: Double, filterManager: FilterManager? = null) {
        if (!dataStore.upsertPlace(place)) {
            android.util.Log.d("MarkerManager", "addOrUpdatePlaceIncremental: ignoring deleted place ${place.id}")
            return
        }

        // Tarkistetaan suodatus jos filterManager on annettu
        if (filterManager != null) {
            val filtered = filterManager.applyPlaceFilter(listOf(place))
            if (filtered.isEmpty()) {
                // Jos paikka ei läpäise suodatinta, poistetaan se kartalta (jos oli siellä)
                val existingMarker = placesFolder.items.find { (it as? Marker)?.relatedObject is PlaceOfInterest && ((it as? Marker)?.relatedObject as PlaceOfInterest).id == place.id } as? Marker
                if (existingMarker != null) {
                    placesFolder.remove(existingMarker)
                    map.invalidate()
                }

                dataStore.removePlace(place.id)
                return
            }
        }
        
        if (placeTypeCache.isEmpty()) {
            scope.launch {
                val types = withContext(Dispatchers.IO) { db.placeOfInterestTypeDao().getAll() }
                types.forEach { placeTypeCache[it.id] = it }
                rebuildMarkers(zoom)
            }
            return
        }
        rebuildMarkers(zoom)
    }

    /**
     * Lisää tai päivittää yksittäisen markerin kartalle ilman koko aineiston uudelleenlatausta.
     * Käytetään kun lisätään yksi uusi kala.
     */
    fun addOrUpdateMarkerIncremental(fish: FishCatch, zoom: Double, filterManager: FilterManager? = null) {
        if (dataStore.isCatchDeleted(fish.id)) {
            android.util.Log.d("MarkerManager", "addOrUpdateMarkerIncremental: ignoring deleted fish ${fish.id}")
            return
        }
        // Päivitetään sisäinen lista
        addMarker(fish)

        // Ladataan lajit välimuistiin jos puuttuu
        if (speciesCache.isEmpty()) {
            scope.launch {
                val species = withContext(Dispatchers.IO) { db.fishSpeciesDao().getAll() }
                species.forEach {
                    speciesCache[it.id] = it
                }
                // Jatka päivitystä kun lajit on ladattu
                addOrUpdateMarkerIncrementalInternal(fish, zoom, filterManager)
            }
            return
        }

        addOrUpdateMarkerIncrementalInternal(fish, zoom, filterManager)
    }

    private fun addOrUpdateMarkerIncrementalInternal(fish: FishCatch, zoom: Double, filterManager: FilterManager? = null) {
        // Tarkistetaan suodatus jos filterManager on annettu
        if (filterManager != null) {
            val filtered = filterManager.applyFilter(listOf(fish))
            if (filtered.isEmpty()) {
                // Jos kala ei läpäise suodatinta, poistetaan se kartalta (jos oli siellä) ja poistutaan
                val folder = if (fish.species == "UNKNOWN") defaultPointsFolder else catchesFolder
                val existingMarker = folder.items.find { (it as? Marker)?.relatedObject is FishCatch && ((it as? Marker)?.relatedObject as FishCatch).id == fish.id } as? Marker
                if (existingMarker != null) {
                    folder.remove(existingMarker)
                    map.invalidate()
                }
                
                dataStore.removeCatch(fish.id)
                return
            }
        }

        // Jos ollaan klusterointialueella, on turvallisempaa rakentaa kaikki uudelleen taustalla,
        // koska uusi piste voi muuttaa klusterien koostumusta.
        if (zoom < 13.0) {
            rebuildMarkers(zoom)
            return
        }

        // Jos ollaan yksittäisten pisteiden alueella, voidaan päivittää vain yksi
        // Etsitään vanha marker jos kyseessä on päivitys
        val folder = if (fish.species == "UNKNOWN") defaultPointsFolder else catchesFolder
        val existingMarker = folder.items.find { (it as? Marker)?.relatedObject is FishCatch && ((it as? Marker)?.relatedObject as FishCatch).id == fish.id } as? Marker
        
        if (existingMarker != null) {
            updateMarkerData(existingMarker, fish)
        } else {
            addIndividualMarker(fish)
        }
        map.invalidate()
    }

    private fun updateMarkerData(marker: Marker, fish: FishCatch) {
        individualMarkerRenderer.render(marker, fish)
    }

    private var maxTimestamp: Long = Long.MAX_VALUE
    private var minTimestamp: Long = 0L
    private var hidePlacesIfFiltering: Boolean = false

    fun setMaxTimestamp(timestamp: Long) {
        maxTimestamp = timestamp
        rebuildMarkers(lastZoom)
    }

    fun setTimeRange(min: Long, max: Long, hidePlaces: Boolean = false) {
        minTimestamp = min
        maxTimestamp = max
        hidePlacesIfFiltering = hidePlaces
        rebuildMarkers(lastZoom)
    }

    fun resetTimeRange() {
        minTimestamp = 0L
        maxTimestamp = Long.MAX_VALUE
        hidePlacesIfFiltering = false
        rebuildMarkers(lastZoom)
    }

    fun rebuildMarkers(zoom: Double, forceRefreshSpecies: Boolean = false) {
        loadSettings()
        if (forceRefreshSpecies) {
            speciesCache.clear()
            iconFactory.clear()
            
            // Ladataan lajit uudelleen välimuistiin
            scope.launch(Dispatchers.IO) {
                val speciesList = db.fishSpeciesDao().getAll()
                synchronized(speciesCache) {
                    speciesCache.clear()
                    speciesList.forEach { speciesCache[it.id] = it }
                }
            }
        }
        
        lastZoom = zoom
        
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            // Pieni viive jotta ei turhaan lasketa jos zoom/scroll on kesken
            // Mutta jos lista on pieni, voidaan päivittää nopeammin
            val catchesCount = dataStore.catchCount()
            delay(if (catchesCount < 100) 10 else 40)

            val snapshot = dataStore.snapshot(minTimestamp, maxTimestamp, hidePlacesIfFiltering)
            val catchesCopy = snapshot.catches
            val placesCopy = snapshot.places

            android.util.Log.d("MarkerManager", "rebuildMarkers: visible catches = ${catchesCopy.size}")
            
            if (catchesCopy.isEmpty() && placesCopy.isEmpty()) {
                withContext(Dispatchers.Main) {
                    overlayController.clear()
                }
                return@launch
            }

            // Ladataan lajit välimuistiin jos puuttuu
            if (speciesCache.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val species = db.fishSpeciesDao().getAll()
                    withContext(Dispatchers.Main) {
                        species.forEach {
                            speciesCache[it.id] = it
                        }
                    }
                }
            }
            
            // Ladataan paikkatyypit välimuistiin jos puuttuu
            if (placeTypeCache.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val types = db.placeOfInterestTypeDao().getAll()
                    withContext(Dispatchers.Main) {
                        types.forEach {
                            placeTypeCache[it.id] = it
                        }
                    }
                }
            }

            val visibleBoundingBox = rebuildRenderer.render(catchesCopy, placesCopy, zoom)
            if (visibleBoundingBox != null) {
                lastBBox = visibleBoundingBox
            }
}
    }

    private fun addIndividualMarker(fish: FishCatch) {
        createIndividualMarker(fish)?.let { marker ->
            if (fish.species == "UNKNOWN") {
                defaultPointsFolder.add(marker)
            } else {
                catchesFolder.add(marker)
            }
        }
    }

    private fun createIndividualMarker(fish: FishCatch): Marker? {
        val marker = activeIndividualMarkers[fish.id] ?: layerState.obtainMarker(map)
        individualMarkerRenderer.render(marker, fish)
        marker.infoWindow = placeInfoWindow

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            map.controller.animateTo(clickedMarker.position)
            showCatchDetailsDialog(clickedMarker)
            true
        }
        
        activeIndividualMarkers[fish.id] = marker
        return marker
    }

    private fun createPlaceMarker(place: PlaceOfInterest, zoom: Double): Marker? {
        val marker = activePlaceMarkers[place.id] ?: layerState.obtainMarker(map)
        val type = placeTypeCache[place.typeId]
        placeMarkerRenderer.render(marker, place, type, zoom)
        marker.infoWindow = placeInfoWindow
        marker.closeInfoWindow()

        marker.relatedObject = place
        marker.setOnMarkerClickListener { clickedMarker, _ ->
            map.controller.animateTo(clickedMarker.position)
            showPlaceDetailsDialog(clickedMarker)
            true
        }
        
        activePlaceMarkers[place.id] = marker
        return marker
    }

    private fun openMedia(media: Media) {
        val file = mediaLoader.fileFor(media)
        if (!file.exists()) return
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context !is android.app.Activity) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun showPlaceDetailsDialog(marker: Marker) {
        val place = marker.relatedObject as? PlaceOfInterest ?: return
        val type = placeTypeCache[place.typeId]
        scope.launch {
            val mediaList = withContext(Dispatchers.IO) {
                detailsLoader.loadPlaceMedia(place.latitude, place.longitude)
            }
            withContext(Dispatchers.Main) {
                showPlaceDetailsDialogWithMedia(marker, place, type, mediaList)
            }
        }
    }

    private fun showPlaceDetailsDialogWithMedia(
        marker: Marker,
        place: PlaceOfInterest,
        type: PlaceOfInterestType?,
        mediaList: List<Media>
    ) {
        placeDetailsDialog.show(marker, place, type, mediaList)
    }

    private fun showEditPlaceDialog(marker: Marker, place: PlaceOfInterest) {
        val intent = Intent(context, EditCatchActivity::class.java)
        intent.putExtra("EXTRA_IS_PLACE", true)
        intent.putExtra("EXTRA_PLACE_ID", place.id)
        launchActivityForResult(intent, 1002)
    }

    private fun confirmDeletePlace(marker: Marker, place: PlaceOfInterest) {
        deletionHandler.confirmPlace(marker, place)
    }

    private fun createClusterMarker(groupKey: Any, clusterList: List<FishCatch>): Marker? {
        val marker = clusterMarkerRenderer.render(layerState.obtainMarker(map), groupKey, clusterList)
            ?: return null

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            val list = (clickedMarker.relatedObject as? List<*>)?.filterIsInstance<FishCatch>()
            if (list != null && list.isNotEmpty()) {
                val minLat = list.minOf { it.latitude }
                val maxLat = list.maxOf { it.latitude }
                val minLon = list.minOf { it.longitude }
                val maxLon = list.maxOf { it.longitude }
                
                if (minLat == maxLat && minLon == maxLon) {
                    val targetZoom = (map.zoomLevelDouble + 2.0).coerceAtMost(19.0)
                    map.controller.animateTo(clickedMarker.position, targetZoom, 500L)
                } else {
                    val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                    map.zoomToBoundingBox(box.increaseByScale(1.5f), true, 0, 19.0, 500L)
                }
            } else {
                val targetZoom = (map.zoomLevelDouble + 1.0).coerceAtMost(19.0)
                map.controller.animateTo(clickedMarker.position, targetZoom, 500L)
            }
            true
        }
        return marker
    }

    fun setMarkersVisible(visible: Boolean, zoom: Double, forceRebuild: Boolean = false) {
        if (markersFolder.isEnabled != visible || Math.abs(lastZoom - zoom) > 0.1 || forceRebuild) {
            defaultPointsFolder.isEnabled = visible
            catchesFolder.isEnabled = visible
            placesFolder.isEnabled = visible
            markersFolder.isEnabled = visible
            if (visible) {
                // Tarkistetaan pitääkö klusterointi päivittää
                // Jos zoom on muuttunut merkittävästi tai eka kerta tai pakotettu (skrollaus)
                if (forceRebuild || shouldRebuild(zoom)) {
                    // Jos kyseessä on vain skrollaus (forceRebuild), tarkistetaan onko näkymäalue muuttunut tarpeeksi
                    if (forceRebuild && lastZoom >= 13.0 && zoom >= 13.0) {
                        // Jos pisteitä on vähän, ei tarvita clippingiä (näkymän perusteella suodatusta) JA zoom-kynnys ei ylittynyt.
                        val catchesCount = dataStore.catchCount()
                        val placesCount = dataStore.placeCount()
                        if (catchesCount + placesCount < 5000 && !shouldRebuild(zoom)) {
                            // Varmistetaan että markerit on ladattu joskus, mutta ei ladata niitä joka skrollauksella
                            if (markersFolder.items.isNotEmpty()) {
                                return
                            }
                        }
 
                        val bbox = map.boundingBox
                        if (bbox != null && lastBBox != null) {
                            val latDiff = Math.abs(bbox.centerLatitude - lastBBox!!.centerLatitude)
                            val lonDiff = Math.abs(bbox.centerLongitude - lastBBox!!.centerLongitude)
                            // Päivitetään vain jos näkymä on siirtynyt yli 80% leveydestä/korkeudesta
                            // koska clipping-marginaali on 100% (20% suurilla määrillä).
                            val threshold = if (catchesCount + placesCount < 5000) 0.8 else 0.15
                            if (latDiff < bbox.latitudeSpan * threshold && lonDiff < (bbox.lonEast - bbox.lonWest) * threshold) {
                                // Varmistetaan että markerit on ladattu, mutta ei ladata niitä joka skrollauksella
                                if (defaultPointsFolder.items.isNotEmpty() || catchesFolder.items.isNotEmpty() || placesFolder.items.isNotEmpty()) {
                                    return
                                }
                            }
                        }
                    }
                    rebuildMarkers(zoom)
                }
            } else {
                map.invalidate()
            }
        }
    }

    private fun shouldRebuild(zoom: Double): Boolean {
        if (lastZoom < 0) return true
        
        // Jos ollaan klusterointialueella tai siirtymässä sinne, päivitys 0.8 askeleen välein
        if (zoom < 13.0 || lastZoom < 13.0) {
            return Math.abs(lastZoom - zoom) >= 0.8
        }
        
        // Tarkistetaan nimen näyttämisen kynnys muiden paikkojen osalta (zoom 16.5)
        val threshold = 16.5
        if ((lastZoom < threshold && zoom >= threshold) || (lastZoom >= threshold && zoom < threshold)) {
            return true
        }
        
        return false
    }

    fun clearMarkers() {
        dataStore.clear()
        
        rebuildJob?.cancel()
        
        // Kierrätetään markerit
        overlayController.recycleAndClear()
        iconFactory.clear()
        lastZoom = -1.0
        map.invalidate()
    }

    fun removeMarker(marker: Marker) {
        // Perutaan välittömästi käynnissä oleva rebuildMarkers, jotta se ei tuo merkkiä takaisin.
        rebuildJob?.cancel()

        val related = marker.relatedObject
        val fish = related as? FishCatch
        val place = related as? PlaceOfInterest

        if (fish == null && place == null) {
            android.util.Log.w("MarkerManager", "removeMarker: marker has no related object, nothing to remove")
            return
        }

        android.util.Log.d("MarkerManager", "removeMarker: fish=${fish?.id}, place=${place?.id}")

        if (fish != null) {
            dataStore.removeCatch(fish.id)
            activeIndividualMarkers.remove(fish.id)
        }
        
        if (place != null) {
            dataStore.removePlace(place.id)
            activePlaceMarkers.remove(place.id)
        }

        // Tyhjennetään markerin tila VÄLITTÖMÄSTI ennen kuin se laitetaan pooliin
        marker.relatedObject = null
        marker.title = null
        marker.snippet = null

        // Poistetaan marker kaikista mahdollisista kansioista
        defaultPointsFolder.remove(marker)
        catchesFolder.remove(marker)
        placesFolder.remove(marker)
        markersFolder.remove(marker)
        
        // Suljetaan infowindow jos se on auki tälle markerille
        if (marker.isInfoWindowShown) {
            marker.closeInfoWindow()
        }
        
        layerState.recycleMarker(marker)
        
        map.invalidate()

        // Käynnistetään rebuildMarkers jotta näkymä päivittyy (esim. klusterit)
        val zoomToUse = if (lastZoom < 1.0) map.zoomLevelDouble else lastZoom
        rebuildMarkers(zoomToUse)
    }

    private fun showCatchDetailsDialog(marker: Marker) {
        val fish = marker.relatedObject as? FishCatch
        scope.launch {
            val details = withContext(Dispatchers.IO) { detailsLoader.loadCatch(fish) }
            withContext(Dispatchers.Main) {
                showCatchDetailsDialog(marker, fish, details.species, details.diaryPages, details.media)
            }
        }
    }

    private fun showCatchDetailsDialog(
        marker: Marker,
        fish: FishCatch?,
        species: fi.anssi.kalakartta.data.FishSpecies?,
        diaryPages: List<FishDiaryPage>,
        mediaList: List<Media>
    ) {
        val content = catchDetailsTextBuilder.build(fish, species)
        catchDetailsDialog.show(
            marker = marker,
            fish = fish,
            diaryPages = diaryPages,
            mediaList = mediaList,
            detailsText = content.text,
            hasSpecies = content.hasSpecies
        )
    }
    private fun confirmDeleteMarker(marker: Marker, fishFromDialog: FishCatch?) {
        deletionHandler.confirmMarker(marker, fishFromDialog)
        return
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return 0
        
        val id = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        return id
    }

}
