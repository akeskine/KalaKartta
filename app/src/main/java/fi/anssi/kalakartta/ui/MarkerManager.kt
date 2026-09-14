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
import fi.anssi.kalakartta.utils.FishDiaryPageMatcher
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
    private val defaultPointsFolder get() = layerState.defaultPointsFolder
    private val catchesFolder get() = layerState.catchesFolder
    private val placesFolder get() = layerState.placesFolder
    private val markersFolder get() = layerState.markersFolder
    private val iconFactory = MarkerIconFactory(context)
    private val clusterCalculator = ClusterCalculator()
    private val clusterMarkerRenderer = ClusterMarkerRenderer(
        iconFactory = iconFactory,
        resolveIconParams = ::calculateIconParams,
        resolveSpeciesName = { speciesCache[it]?.name }
    )
    private val speciesCache = mutableMapOf<String, fi.anssi.kalakartta.data.FishSpecies>()
    private val placeTypeCache = mutableMapOf<String, PlaceOfInterestType>()
    
    private var fishIconScale = 1.0f
    private var otherIconScale = 1.0f

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
    
    private val allCatches = mutableListOf<FishCatch>()
    private val allPlaces = mutableListOf<PlaceOfInterest>()
    
    // Poistetut ID:t, joita ei näytetä vaikka ne olisivat allCatches/allPlaces-listoilla
    private val deletedFishIds = mutableSetOf<Long>()
    private val deletedPlaceIds = mutableSetOf<Long>()
    
    private val mediaLoader = MarkerMediaLoader(context)
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
        resolveIconParams = ::calculateIconParams,
        openMedia = ::openMedia,
        onEdit = { marker, fish ->
            val currentFish = fish ?: marker.relatedObject as? FishCatch
            val intent = Intent(context, EditCatchActivity::class.java)
            intent.putExtra("EXTRA_CATCH_ID", currentFish?.id)
            launchActivityForResult(intent, 1001)
        },
        onDelete = ::confirmDeleteMarker
    )
    
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
        synchronized(allCatches) {
            val existingIndex = allCatches.indexOfFirst { it.id == fish.id }
            if (existingIndex >= 0) {
                allCatches[existingIndex] = fish
            } else {
                allCatches.add(fish)
            }
        }
    }

    fun setAllCatches(catches: List<FishCatch>) {
        synchronized(allCatches) {
            allCatches.clear()
            allCatches.addAll(catches)
            // Älä tyhjennä deletedFishIds tässä, jotta asynkroninen rebuildMarkers 
            // tietää yhä mitkä on poistettu, jos reload tuli poiston jälkeen.
            // deletedFishIds tyhjennetään rebuildMarkersin alussa kun tiedetään
            // että uusi lista on saatu.
            android.util.Log.d("MarkerManager", "setAllCatches: list size = ${allCatches.size}")
        }
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun setAllPlaces(places: List<PlaceOfInterest>) {
        synchronized(allPlaces) {
            allPlaces.clear()
            allPlaces.addAll(places)
            // Älä tyhjennä deletedPlaceIds tässä.
        }
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun addOrUpdatePlaceIncremental(place: PlaceOfInterest, zoom: Double, filterManager: FilterManager? = null) {
        synchronized(allPlaces) {
            if (deletedPlaceIds.contains(place.id)) {
                android.util.Log.d("MarkerManager", "addOrUpdatePlaceIncremental: ignoring deleted place ${place.id}")
                return
            }
            val existingIndex = allPlaces.indexOfFirst { it.id == place.id }
            if (existingIndex >= 0) {
                allPlaces[existingIndex] = place
            } else {
                allPlaces.add(place)
            }
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

                // Poistetaan myös allPlaces-listasta jotta rebuildMarkers ei tuo sitä takaisin
                synchronized(allPlaces) {
                    allPlaces.removeAll { it.id == place.id }
                }
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
        synchronized(allCatches) {
            if (deletedFishIds.contains(fish.id)) {
                android.util.Log.d("MarkerManager", "addOrUpdateMarkerIncremental: ignoring deleted fish ${fish.id}")
                return
            }
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
                
                // Poistetaan myös allCatches-listasta jotta rebuildMarkers ei tuo sitä takaisin
                synchronized(allCatches) {
                    allCatches.removeAll { it.id == fish.id }
                }
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
        val point = GeoPoint(fish.latitude, fish.longitude)
        marker.position = point
        
        val species = speciesCache[fish.species]
        val speciesName = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        marker.title = if (fish.species == "OTHER" && !fish.otherSpecies.isNullOrEmpty()) {
            "$speciesName (${fish.otherSpecies})"
        } else {
            speciesName
        }
        
        val iconParams = calculateIconParams(fish)
        val drawableId = iconParams.first
        val iconPath = iconParams.second
        val finalIconSize = iconParams.third
        val finalVisibleSize = iconParams.fourth

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, finalVisibleSize, 48)
            iconFactory.getTouchIcon(drawableId, finalVisibleSize, 48)
        } else if (iconPath != null) {
            val key = Pair(iconPath, finalIconSize)
            iconFactory.getScaledIcon(iconPath, finalIconSize)
        } else {
            val key = Pair(drawableId, finalIconSize)
            iconFactory.getScaledIcon(drawableId, finalIconSize)
        }
        marker.relatedObject = fish
    }

    private fun calculateIconParams(fish: FishCatch): Quadruple<Int, String?, Int, Int> {
        val species = speciesCache[fish.species]
        var iconName = species?.icon_default ?: ""
        var iconPath: String? = null
        var scaleFactor = 1.0

        // Jos tapahtuma ei ole "Saatu kala" (tai tyhjä), käytetään tapahtumakohtaista kuvaketta
        val eventIcon = when (fish.eventType) {
            FishCatch.LOST_FISH -> "karkuutus"
            FishCatch.STRIKE_CERTAIN -> "tarppi_varma"
            FishCatch.STRIKE_UNCERTAIN -> "tarppi_epavarma"
            FishCatch.FISH_FOLLOW -> "seurio"
            else -> null
        }

        if (eventIcon != null) {
            iconName = eventIcon
            if (fish.eventType == FishCatch.FISH_FOLLOW) {
                scaleFactor *= 1.3
            }
        } else if (species != null) {
            val weight = fish.weight ?: 0L
            val length = fish.length ?: 0L
            
            val smallWeight = species.small_weight
            val smallLength = species.small_length
            val largeWeight = species.large_weight
            val largeLength = species.large_length
            val giantWeight = species.giant_weight
            val giantLength = species.giant_length

            // Tarkistetaan koot suurimmasta pienimpään
            if ((giantWeight > 0 && weight >= giantWeight) || (giantLength > 0 && length >= giantLength)) {
                if (species.icon_giant.isNotEmpty()) {
                    iconName = species.icon_giant
                } else {
                    scaleFactor = 1.6
                }
            } else if ((largeWeight > 0 && weight >= largeWeight) || (largeLength > 0 && length >= largeLength)) {
                if (species.icon_large.isNotEmpty()) {
                    iconName = species.icon_large
                } else {
                    scaleFactor = 1.3
                }
            } else if (smallWeight > 0 && smallLength > 0 && ((fish.weight != null && fish.weight > 0 && weight < smallWeight) || (fish.length != null && fish.length > 0 && length < smallLength))) {
                // Sääntö: joko paino tai pituus annettu (ei null tai 0) ja se on pienempi kuin raja
                if (species.icon_small.isNotEmpty()) {
                    iconName = species.icon_small
                } else {
                    scaleFactor = 0.7
                }
            }
        }

        var drawableId = getDrawableId(iconName)
        if (drawableId == 0 && iconName.isNotEmpty()) {
            // Tarkistetaan onko se polku tai kustomoitu ikoni
            if (iconName.startsWith("/") || iconName.startsWith("custom_icon_")) {
                iconPath = iconName
            }
        }
        
        if (drawableId == 0 && iconPath == null) {
            // Jos kyseessä on oletuslaji, mutta icon_default on tyhjä, kokeillaan palauttaa oletusikoni koodista
            val defaultSpecies = fi.anssi.kalakartta.data.FishSpecies.getDefaultList().find { it.id == fish.species }
            if (defaultSpecies != null && defaultSpecies.icon_default.isNotEmpty()) {
                drawableId = getDrawableId(defaultSpecies.icon_default)
            }
            
            if (drawableId == 0) {
                if (fish.species == "UNKNOWN") {
                    drawableId = R.drawable.default_point
                } else {
                    drawableId = R.drawable.muukala
                }
            }
        }
        
        // Varmistetaan, että iconPath on oikeasti olemassa oleva tiedosto
        if (iconPath != null) {
            val file = if (iconPath.startsWith("/")) File(iconPath) else File(context.filesDir, iconPath)
            if (!file.exists()) {
                iconPath = null
                if (fish.species == "UNKNOWN") {
                    drawableId = R.drawable.default_point
                } else {
                    drawableId = R.drawable.muukala
                }
            }
        }
        
        var baseIconSize = if (drawableId == R.drawable.default_point) 24 else 40
        var visibleSize = if (drawableId == R.drawable.default_point) 8 else 40

        // Sovelletaan yleistä skaalauskerrointa
        scaleFactor *= fishIconScale.toDouble()

        // Jos kalan painoa ja pituutta ei ole annettu, asetetaan koko pienen (0.7) ja keskikokoisen (1.0) väliin
        if (fish.weight == null && fish.length == null && drawableId != R.drawable.default_point) {
            scaleFactor *= 0.85
        }

        // Punaiset oletuspisteet (default_point) pidetään vakioina ja pieninä
        if (drawableId == R.drawable.default_point) {
            scaleFactor = 0.8 * fishIconScale.toDouble()
        }
        
        if (species != null && species.small_weight == 0L && species.small_length == 0L) {
             if (fish.species == "SALMON" || fish.species == "TROUT" || fish.species == "RAINBOW") {
                scaleFactor *= 1.3
            } else if (fish.species == "PERCH" || fish.species == "IDE") {
                scaleFactor *= 0.8
            } else if (fish.species == "BURBOT") {
                scaleFactor *= 1.2
            }
        }
        
        val finalIconSize = (baseIconSize * scaleFactor).toInt()
        val finalVisibleSize = (visibleSize * scaleFactor).toInt()
        
        // Varmistetaan että pienin koko on vähintään 16dp jos kyseessä ei ole default_point
        var adjustedFinalIconSize = finalIconSize
        if (drawableId != R.drawable.default_point && adjustedFinalIconSize < 16) {
            adjustedFinalIconSize = 16
        }

        return Quadruple(drawableId, iconPath, adjustedFinalIconSize, finalVisibleSize)
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
            val catchesCount = synchronized(allCatches) { allCatches.size }
            delay(if (catchesCount < 100) 10 else 40)
            
            val catchesCopy = synchronized(allCatches) { 
                allCatches.filter { 
                    !deletedFishIds.contains(it.id) && 
                    (it.caughtAt ?: 0L) >= minTimestamp &&
                    (it.caughtAt ?: 0L) <= maxTimestamp 
                }.toList() 
            }
            val placesCopy = synchronized(allPlaces) { 
                if (hidePlacesIfFiltering && (minTimestamp > 0 || maxTimestamp < Long.MAX_VALUE)) {
                    emptyList()
                } else {
                    allPlaces.filter { !deletedPlaceIds.contains(it.id) }.toList()
                }
            }
            
            // Tyhjennetään poistolistat vasta kun ollaan saatu kopiot uusista listoista
            // Tämä varmistaa että poisto pysyy voimassa jos reloadMarkersFromDb 
            // tapahtui juuri ennen tätä.
            // HUOM: Älä tyhjennä jos rebuild peruttiin välissä (mutta delay hoitaa sen)
            synchronized(allCatches) { deletedFishIds.clear() }
            synchronized(allPlaces) { deletedPlaceIds.clear() }

            android.util.Log.d("MarkerManager", "rebuildMarkers: visible catches = ${catchesCopy.size}")
            
            if (catchesCopy.isEmpty() && placesCopy.isEmpty()) {
                withContext(Dispatchers.Main) {
                    defaultPointsFolder.items.clear()
                    catchesFolder.items.clear()
                    placesFolder.items.clear()
                    markersFolder.items.clear()
                    activeIndividualMarkers.clear()
                    activePlaceMarkers.clear()
                    map.invalidate()
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

            val totalCount = catchesCopy.size + placesCopy.size
            val clusterLimit = if (totalCount < 15000) 13.0 else 15.0

            if (zoom < clusterLimit) {
                // Kierrätetään vanhat markerit ennen uutta laskentaa
                withContext(Dispatchers.Main) {
                    layerState.recycleVisibleMarkers()
                    iconFactory.clear()
                }

                // Klusterointi voidaan laskea taustalla
                val clusters =withContext(Dispatchers.Default) {
                    clusterCalculator.calculate(catchesCopy, zoom)
                }
                
                // Markerien luonti on tehtävä Main-säikeessä
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        val newDefaultMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                        val newCatchMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                        val newPlaceMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                        
                        // Muut paikat
                        placesCopy.forEach { place ->
                             createPlaceMarker(place, zoom)?.let { newPlaceMarkers.add(it) }
                        }

                        // Oletuspisteiden harvennus klusteroidussa näkymässä jos pisteitä on paljon
                        val bbox = map.boundingBox
                        val useThinning = totalCount > 15000 && bbox != null && bbox.latNorth != 0.0
                        val thinnedDefaultGrid = mutableSetOf<Pair<Int, Int>>()
                        
                        // Ruudukon koko riippuu zoomista: pienellä zoomilla (kaukana) harvempi ruudukko
                        val gridSizeDivider = when {
                            zoom < 8 -> 15.0
                            zoom < 10 -> 25.0
                            zoom < 12 -> 35.0
                            else -> 40.0
                        }

                        clusters.forEach { (groupKey, groupClusters) ->
                            groupClusters.forEach { clusterList ->
                                if (clusterList.size == 1) {
                                    val fish = clusterList[0]
                                    
                                    if (fish.species == "UNKNOWN" && useThinning) {
                                        val gridSizeLat = bbox!!.latitudeSpan / gridSizeDivider
                                        val gridSizeLon = (bbox.lonEast - bbox.lonWest) / gridSizeDivider
                                        val gridX = ((fish.latitude - bbox.latSouth) / gridSizeLat).toInt()
                                        val gridY = ((fish.longitude - bbox.lonWest) / gridSizeLon).toInt()
                                        val pos = Pair(gridX, gridY)
                                        
                                        // Näytetään oletuspiste vain jos ruudussa ei ole vielä pistettä
                                        // TAI jos tällä pisteellä on jotain tietoa (paino/pituus)
                                        val hasData = fish.weight != null || fish.length != null
                                        if (!thinnedDefaultGrid.contains(pos) || hasData) {
                                            createIndividualMarker(fish)?.let { marker ->
                                                newDefaultMarkers.add(marker)
                                                if (!hasData) thinnedDefaultGrid.add(pos)
                                            }
                                        }
                                    } else {
                                        createIndividualMarker(fish)?.let { marker ->
                                            if (fish.species == "UNKNOWN") {
                                                newDefaultMarkers.add(marker)
                                            } else {
                                                newCatchMarkers.add(marker)
                                            }
                                        }
                                    }
                                } else {
                                    createClusterMarker(groupKey, clusterList)?.let { newCatchMarkers.add(it) }
                                }
                            }
                        }
                        
                        if (isActive) {
                            defaultPointsFolder.items.clear()
                            catchesFolder.items.clear()
                            placesFolder.items.clear()
                            markersFolder.items.clear()
                            
                            defaultPointsFolder.items.addAll(newDefaultMarkers)
                            catchesFolder.items.addAll(newCatchMarkers)
                            placesFolder.items.addAll(newPlaceMarkers)
                            map.invalidate()
                        }
                    }
                }
            } else {
                // Kierrätetään vanhat markerit ennen uutta laskentaa
                withContext(Dispatchers.Main) {
                    // Kerätään ne, joita ei enää käytetä
                    // Rajoitetaan poolin kokoa jotta se ei syö liikaa muistia (esim. 5000 markeria)
                    layerState.recycleVisibleMarkers()
                    
                    defaultPointsFolder.items.clear()
                    catchesFolder.items.clear()
                    placesFolder.items.clear()
                    markersFolder.items.clear()
                }

            // Jos pisteitä on vähän, ei tarvita clippingiä ollenkaan.
            // Käytetään tässä 15 000 rajaa, jotta käyttäjän 4600 pisteen aineistolla
            // ei tapahdu clippingiä, mikä poistaisi pisteet reunojen läheltä.
            val (visibleCatches, visiblePlaces) = if (catchesCopy.size + placesCopy.size < 15000) {
                Pair(catchesCopy, placesCopy)
            } else {
                // Yksittäiset pisteet - käytetään näkyvyysrajoitusta (clipping)
                // jos pisteitä on todella paljon (> 15000) suorituskyvyn takia.
                var bbox = map.boundingBox
                
                // Jos bbox ei ole vielä valmis, käytetään fallbackina kaikkien näyttämistä.
                // Älä käytä lastBBoxia tässä, koska se voi olla kaukana nykyisestä sijainnista
                // ja aiheuttaa kaikkien pisteiden katoamisen (clipping väärälle alueelle).
                if (bbox != null && bbox.latNorth != 0.0 && bbox.latSouth != 0.0 && (bbox.latitudeSpan > 0.0 || bbox.lonEast - bbox.lonWest > 0.0)) {
                    lastBBox = bbox
                    withContext(Dispatchers.Default) {
                        val totalCount = catchesCopy.size + placesCopy.size
                        // Marginaali 200% molempiin suuntiin pienellä määrällä.
                        // Suurella määrällä (> 15000) marginaalia pienennetään entisestään (20%) suorituskyvyn takia.
                        val marginMultiplier = if (totalCount < 15000) 2.0 else 0.2
                            val latMargin = bbox.latitudeSpan * marginMultiplier
                            val lonMargin = (bbox.lonEast - bbox.lonWest) * marginMultiplier
                            
                            val filteredCatches = catchesCopy.filter { fish ->
                                fish.latitude >= bbox.latSouth - latMargin && 
                                fish.latitude <= bbox.latNorth + latMargin &&
                                fish.longitude >= bbox.lonWest - lonMargin &&
                                fish.longitude <= bbox.lonEast + lonMargin
                            }
                            val filteredPlaces = placesCopy.filter { place ->
                                place.latitude >= bbox.latSouth - latMargin && 
                                place.latitude <= bbox.latNorth + latMargin &&
                                place.longitude >= bbox.lonWest - lonMargin &&
                                place.longitude <= bbox.lonEast + lonMargin
                            }
                            
                            // Harvennus (Thinning) jos näkyvissä on silti liikaa pisteitä
                            val maxVisible = 2000
                            val finalCatches = if (filteredCatches.size > maxVisible && totalCount > 15000) {
                                // Erityinen harvennus oletuspisteille (UNKNOWN), jos niitä on paljon
                                val (unknowns, knowns) = filteredCatches.partition { it.species == "UNKNOWN" }
                                
                                if (unknowns.size > 200) {
                                    // Grid-pohjainen harvennus oletuspisteille
                                    // Ruudukon koko riippuu zoomista myös tässä
                                    val gridSizeDivider = when {
                                        zoom < 14 -> 20.0
                                        zoom < 16 -> 30.0
                                        else -> 40.0
                                    }
                                    val gridSizeLat = bbox.latitudeSpan / gridSizeDivider
                                    val gridSizeLon = (bbox.lonEast - bbox.lonWest) / gridSizeDivider
                                    val grid = mutableSetOf<Pair<Int, Int>>()
                                    val thinnedUnknowns = mutableListOf<FishCatch>()
                                    
                                    for (fish in unknowns) {
                                        val gridX = ((fish.latitude - bbox.latSouth) / gridSizeLat).toInt()
                                        val gridY = ((fish.longitude - bbox.lonWest) / gridSizeLon).toInt()
                                        val pos = Pair(gridX, gridY)
                                        
                                        val hasData = fish.weight != null || fish.length != null
                                        if (!grid.contains(pos) || hasData) {
                                            thinnedUnknowns.add(fish)
                                            if (!hasData) grid.add(pos)
                                        }
                                        if (thinnedUnknowns.size + knowns.size >= maxVisible) break
                                    }
                                    thinnedUnknowns + knowns
                                } else {
                                    filteredCatches.take(maxVisible)
                                }
                            } else {
                                filteredCatches
                            }
                            
                            Pair(finalCatches, filteredPlaces)
                        }
                    } else {
                        // Jos bboxia ei ole vielä, ja pisteitä on paljon, näytetään kaikki fallbackina tyhjän sijasta.
                        // Tämä estää pisteiden häviämisen käynnistyksessä tai animaatioiden aikana.
                        Pair(catchesCopy, placesCopy)
                    }
                }

                if (isActive) {
                    withContext(Dispatchers.Main) {
                        val newDefaultMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                        val newCatchMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                        val newPlaceMarkers = mutableListOf<org.osmdroid.views.overlay.Overlay>()

                        visiblePlaces.forEach { place ->
                            createPlaceMarker(place, zoom)?.let { newPlaceMarkers.add(it) }
                        }
                        visibleCatches.forEach { fish ->
                            createIndividualMarker(fish)?.let { marker ->
                                if (fish.species == "UNKNOWN") {
                                    newDefaultMarkers.add(marker)
                                } else {
                                    newCatchMarkers.add(marker)
                                }
                            }
                        }
                        
                        if (isActive) {
                            // Folderit tyhjennettiin jo ylhäällä kierrätyksen yhteydessä
                            defaultPointsFolder.items.addAll(newDefaultMarkers)
                            catchesFolder.items.addAll(newCatchMarkers)
                            placesFolder.items.addAll(newPlaceMarkers)
                            map.invalidate()
                        }
                    }
                }
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
        val point = GeoPoint(fish.latitude, fish.longitude)
        
        // Yritetään käyttää olemassa olevaa aktiivista markeria
        val marker = activeIndividualMarkers[fish.id] ?: layerState.obtainMarker(map)
        
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        val species = speciesCache[fish.species]
        val otherName = if (fish.species == "OTHER" && !fish.otherSpecies.isNullOrEmpty()) {
            val formatted = fish.otherSpecies.lowercase().replaceFirstChar { it.uppercase() }
            "${species?.name ?: fish.species} ($formatted)"
        } else {
            species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        }
        marker.title = otherName
        
        val iconParams = calculateIconParams(fish)
        val drawableId = iconParams.first
        val iconPath = iconParams.second
        val finalIconSize = iconParams.third
        val finalVisibleSize = iconParams.fourth

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, finalVisibleSize, 48)
            iconFactory.getTouchIcon(drawableId, finalVisibleSize, 48)
        } else if (iconPath != null) {
            val key = Pair(iconPath, finalIconSize)
            iconFactory.getScaledIcon(iconPath, finalIconSize)
        } else {
            val key = Pair(drawableId, finalIconSize)
            iconFactory.getScaledIcon(drawableId, finalIconSize)
        }
        marker.relatedObject = fish
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
        val point = GeoPoint(place.latitude, place.longitude)
        
        // Yritetään käyttää olemassa olevaa aktiivista markeria
        val marker = activePlaceMarkers[place.id] ?: layerState.obtainMarker(map)
        
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP)
        
        val type = placeTypeCache[place.typeId]
        val iconName = type?.icon ?: ""
        var drawableId = getDrawableId(iconName)
        
        val isDefault = drawableId == R.drawable.default_point
        if (isDefault) {
            drawableId = R.drawable.default_place_point
        }

        val visibleSize = 8
        val touchSize = 48
        
        marker.icon = if (drawableId == R.drawable.default_place_point) {
            val scaledVisibleSize = (visibleSize * otherIconScale).toInt()
            val key = Triple(drawableId, scaledVisibleSize, touchSize)
            iconFactory.getTouchIcon(drawableId, scaledVisibleSize, touchSize)
        } else {
            var iconSize = (40 * otherIconScale).toInt()
            when (place.typeId) {
                "SHALLOW", "DEEP" -> iconSize = (iconSize * 0.5).toInt()
                "ROCK", "VEGETATION" -> iconSize = (iconSize * 0.7).toInt()
                "ACCESS", "SHELTER", "PARKING", "RAMP", "LANDINGSPOT", "HARBOUR", "OTHER", "CAMPFIRE", "PROSPECT" -> iconSize = (iconSize * 0.8).toInt()
            }
            
            if (zoom >= 16.5 && place.name.isNotEmpty()) {
                val key = Triple(drawableId, iconSize, place.name)
                iconFactory.getLabelIcon(drawableId, iconSize, place.name)
            } else {
                val key = Pair(drawableId, iconSize)
                iconFactory.getScaledIcon(drawableId, iconSize)
            }
        }

        marker.infoWindow = placeInfoWindow
        marker.title = place.name
        
        // Suljetaan InfoWindow koska nimeä näytetään nyt suoraan ikonissa
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
                mediaLoader.getForPlace(place.latitude, place.longitude)
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
                        val catchesCount = synchronized(allCatches) { allCatches.size }
                        val placesCount = synchronized(allPlaces) { allPlaces.size }
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
        synchronized(allCatches) {
            allCatches.clear()
        }
        synchronized(allPlaces) {
            allPlaces.clear()
        }
        
        rebuildJob?.cancel()
        
        // Kierrätetään markerit
        layerState.recycleVisibleMarkers()
        iconFactory.clear()
        lastZoom = -1.0
        map.invalidate()
    }

    fun removeMarker(marker: Marker) {
        // Perutaan välittömästi käynnissä oleva rebuildMarkers, jotta se ei tuo merkkiä takaisin
        // vanhan allCatches/allPlaces-listan perusteella.
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
            synchronized(allCatches) {
                allCatches.removeAll { it.id == fish.id }
                deletedFishIds.add(fish.id)
            }
            activeIndividualMarkers.remove(fish.id)
        }
        
        if (place != null) {
            synchronized(allPlaces) {
                allPlaces.removeAll { it.id == place.id }
                deletedPlaceIds.add(place.id)
            }
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
            val (species, diaryPages, mediaList) = withContext(Dispatchers.IO) {
                val loadedSpecies = fish?.let { db.fishSpeciesDao().getById(it.species) }
                val loadedDiaryPages = fish?.let {
                    FishDiaryPageMatcher.pagesForCaughtAt(it.caughtAt, db.fishDiaryPageDao().getAll())
                }.orEmpty()
                val loadedMedia = fish?.let {
                    mediaLoader.getForCatch(it)
                }.orEmpty()
                Triple(loadedSpecies, loadedDiaryPages, loadedMedia)
            }
            withContext(Dispatchers.Main) {
                showCatchDetailsDialog(marker, fish, species, diaryPages, mediaList)
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
        val pressureGraphMarker = "\u0000PRESSURE_GRAPH\u0000"
        val details = StringBuilder()
        var hasSpecies = false
        
        fish?.let {
            val shouldShowPressureGraph = it.pressureSamples.isNotEmpty() && (it.caughtAt ?: 0L) > 0L
            var pressureGraphMarkerAdded = false
            if (species != null) {
                val speciesName = if (it.species == "OTHER" && !it.otherSpecies.isNullOrEmpty()) {
                    val otherSpeciesDisplay = it.otherSpecies.lowercase().replaceFirstChar { it.uppercase() }
                    "${species.name.lowercase().replaceFirstChar { it.uppercase() }} ($otherSpeciesDisplay)"
                } else if (it.species == "UNKNOWN") {
                    species.name
                } else {
                    species.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                details.append("Laji: $speciesName\n")
                hasSpecies = true
            } else {
                // Tuntematon laji (ei löydy tietokannasta)
                val otherSpeciesDisplay = if (!it.otherSpecies.isNullOrEmpty()) {
                    it.otherSpecies.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    it.species.lowercase().replaceFirstChar { it.uppercase() }
                }
                val otherSpeciesBase = "Muu kalalaji"
                details.append("Laji: $otherSpeciesBase ($otherSpeciesDisplay)\n")
                hasSpecies = true
            }

            it.caughtAt?.let { caughtAt ->
                if (caughtAt > 0) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy 'klo' HH:mm", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(caughtAt))
                    val timeLabel = if (it.eventType != null && it.eventType != FishCatch.CAUGHT_FISH) "Aika" else "Saantiaika"
                    details.append("$timeLabel: $dateStr\n")
                }
            }
            if (it.fisherman.isNotEmpty()) {
                fun formatName(name: String): String {
                    return name.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { part ->
                        part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
                val fishermanDisplay = formatName(it.fisherman)
                details.append("Kalastaja: $fishermanDisplay\n")
            }

            if (it.method.isNotEmpty()) details.append("Kalastustapa: ${it.method}\n")
            if (!it.lure.isNullOrEmpty() || !it.lureColor.isNullOrEmpty()) {
                val lureParts = mutableListOf<String>()
                if (!it.lure.isNullOrEmpty()) lureParts.add(it.lure!!)
                if (!it.lureColor.isNullOrEmpty()) lureParts.add(it.lureColor!!)
                details.append("Viehe: ${lureParts.joinToString(", ")}\n")
            }

            if (it.weight != null && it.weight!! > 0) details.append("Paino: ${it.weight} g\n")
            if (it.length != null && it.length!! > 0) details.append("Pituus: ${it.length} cm\n")
            
            // Säätiedot
            if (it.weatherSource.isNotEmpty()) {
                details.append("\nSää (${it.weatherSource}):\n")
                if (it.airTemp != null) details.append("  Ilma: ${it.airTemp} °C\n")
                if (it.waterTemp != null) details.append("  Vesi: ${it.waterTemp} °C\n")
                if (it.windSpeed != null) {
                    val dir = if (it.windDirection != null) " (${it.windDirection}°)" else ""
                    details.append("  Tuuli: ${it.windSpeed} m/s$dir")
                    if (it.windDirection != null) {
                        details.append(" ")
                    }
                    details.append("\n")
                }
                val rainLevels = context.resources.getStringArray(R.array.rain_levels)
                val rainDesc = if (it.rain != null && (it.rain!!.toInt() + 1) < rainLevels.size) rainLevels[it.rain!!.toInt() + 1] else ""
                
                if (it.cloudiness != null || rainDesc.isNotEmpty() || it.rainHourMm != null) {
                    val parts = mutableListOf<String>()
                    if (it.cloudiness != null) parts.add("Pilvisyys: ${it.cloudiness}/8")
                    if (rainDesc.isNotEmpty()) parts.add("Sade: $rainDesc")
                    if (it.rainHourMm != null) parts.add("Sade: ${it.rainHourMm} mm/h")
                    details.append("  ${parts.joinToString(", ")}\n")
                }

                if (it.pressure != null) {
                    details.append("  Paine: ${it.pressure} hPa\n")
                    if (shouldShowPressureGraph) {
                        details.append(pressureGraphMarker)
                        pressureGraphMarkerAdded = true
                    }
                } else if (shouldShowPressureGraph) {
                    details.append(pressureGraphMarker)
                    pressureGraphMarkerAdded = true
                }

                if (it.weatherStation.isNotEmpty()) {
                    val stationName = it.weatherStation.substringAfter(":")
                    details.append("  Asema: $stationName\n")
                }
            }

            if (shouldShowPressureGraph && !pressureGraphMarkerAdded) {
                details.append(pressureGraphMarker)
            }

            if (it.additionalInfo.isNotEmpty()) details.append("\nLisätieto: ${it.additionalInfo}\n")
            if (it.originalRef.isNotEmpty()) details.append("Alkuperäinen viite: ${it.originalRef}\n")
        }

        catchDetailsDialog.show(
            marker = marker,
            fish = fish,
            diaryPages = diaryPages,
            mediaList = mediaList,
            detailsText = details.toString().trim(),
            hasSpecies = hasSpecies
        )
        return

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
