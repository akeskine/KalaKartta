package fi.anssi.kalakartta.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.PopupMenu
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.createBitmap
import fi.anssi.kalakartta.R
import fi.anssi.kalakartta.data.AppDatabase
import fi.anssi.kalakartta.data.FishCatch
import fi.anssi.kalakartta.data.PlaceOfInterest
import fi.anssi.kalakartta.data.PlaceOfInterestType
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import org.osmdroid.views.overlay.FolderOverlay
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
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rebuildJob: Job? = null
    
    private val defaultPointsFolder = FolderOverlay()
    private val catchesFolder = FolderOverlay()
    private val placesFolder = FolderOverlay()
    private val markersFolder = FolderOverlay()
    private val iconCache = mutableMapOf<Pair<Int, Int>, BitmapDrawable>()
    private val touchIconCache = mutableMapOf<Triple<Int, Int, Int>, BitmapDrawable>()
    private val clusterIconCache = mutableMapOf<Any, BitmapDrawable>()
    private val speciesCache = mutableMapOf<String, fi.anssi.kalakartta.data.FishSpecies>()
    private val placeTypeCache = mutableMapOf<String, PlaceOfInterestType>()
    
    // Marker-olioiden kierrätys
    private val markerPool = mutableListOf<Marker>()
    private val activeIndividualMarkers = mutableMapOf<Long, Marker>()
    private val activePlaceMarkers = mutableMapOf<Long, Marker>()
    
    private val allCatches = mutableListOf<FishCatch>()
    private val allPlaces = mutableListOf<PlaceOfInterest>()
    private var lastZoom = -1.0
    private var lastBBox: BoundingBox? = null
    
    private val placeInfoWindow by lazy {
        object : MarkerInfoWindow(R.layout.place_info_window, map) {
            override fun onOpen(item: Any?) {
                val marker = item as? Marker
                val title = mView.findViewById<TextView>(R.id.bubble_title)
                title.text = marker?.title
                
                // Sulje infowindow klikattaessa tekstiä, jotta se ei estä merkin klikkausta
                mView.setOnClickListener {
                    close()
                    marker?.let { showPlaceDetailsDialog(it) }
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
            android.util.Log.d("MarkerManager", "setAllCatches: list size = ${allCatches.size}")
        }
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun setAllPlaces(places: List<PlaceOfInterest>) {
        synchronized(allPlaces) {
            allPlaces.clear()
            allPlaces.addAll(places)
        }
        rebuildMarkers(if (lastZoom < 1.0) 15.0 else lastZoom)
    }

    fun addOrUpdatePlaceIncremental(place: PlaceOfInterest, zoom: Double, filterManager: FilterManager? = null) {
        synchronized(allPlaces) {
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
        marker.title = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        
        val iconParams = calculateIconParams(fish)
        val drawableId = iconParams.first
        val finalIconSize = iconParams.second
        val finalVisibleSize = iconParams.third

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, finalVisibleSize, 48)
            touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, finalVisibleSize, 48) }
        } else {
            val key = Pair(drawableId, finalIconSize)
            iconCache.getOrPut(key) { getScaledMarkerIcon(drawableId, finalIconSize) }
        }
        marker.relatedObject = fish
    }

    private fun calculateIconParams(fish: FishCatch): Triple<Int, Int, Int> {
        val species = speciesCache[fish.species]
        var iconName = species?.icon_default ?: ""
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

        val drawableId = getDrawableId(iconName)
        
        var baseIconSize = if (drawableId == R.drawable.default_point) 24 else 40
        var visibleSize = if (drawableId == R.drawable.default_point) 8 else baseIconSize

        // Punaiset oletuspisteet (default_point) pidetään aina vakioina ja pieninä
        if (drawableId == R.drawable.default_point) {
            scaleFactor = 0.8
        }
        
        if (species != null && species.small_weight == 0L && species.small_length == 0L) {
             if (fish.species == "SALMON") {
                scaleFactor *= 1.3
            } else if (fish.species == "PERCH") {
                scaleFactor *= 0.8
            }
        }
        
        // Varmistetaan, ettei skaalaus ole pienempi kuin 1.0, jos painoa/pituutta ei ole annettu
        if (fish.weight == null && fish.length == null && scaleFactor < 1.0 && drawableId != R.drawable.default_point) {
            scaleFactor = 1.0
        }
        
        val finalIconSize = (baseIconSize * scaleFactor).toInt()
        val finalVisibleSize = (visibleSize * scaleFactor).toInt()
        
        return Triple(drawableId, finalIconSize, finalVisibleSize)
    }

    fun rebuildMarkers(zoom: Double) {
        lastZoom = zoom
        
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            // Pieni viive jotta ei turhaan lasketa jos zoom/scroll on kesken
            // Mutta jos lista on pieni, voidaan päivittää nopeammin
            val catchesCount = synchronized(allCatches) { allCatches.size }
            delay(if (catchesCount < 100) 20 else 60)
            
            val catchesCopy = synchronized(allCatches) { allCatches.toList() }
            val placesCopy = synchronized(allPlaces) { allPlaces.toList() }
            android.util.Log.d("MarkerManager", "rebuildMarkers: allCatches size = ${catchesCopy.size}")
            
            if (catchesCopy.isEmpty() && placesCopy.isEmpty()) {
                withContext(Dispatchers.Main) {
                    defaultPointsFolder.items.clear()
                    catchesFolder.items.clear()
                    placesFolder.items.clear()
                    markersFolder.items.clear()
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
                // Klusterointi voidaan laskea taustalla
                val clusters =withContext(Dispatchers.Default) {
                    calculateClusters(catchesCopy, zoom)
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
                                        val gridSizeLon = bbox.longitudeSpan / gridSizeDivider
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
                    val allActive = (defaultPointsFolder.items + catchesFolder.items + placesFolder.items + markersFolder.items)
                        .filterIsInstance<Marker>()
                    
                    // Kerätään ne, joita ei enää käytetä
                    // Rajoitetaan poolin kokoa jotta se ei syö liikaa muistia (esim. 5000 markeria)
                    if (markerPool.size < 5000) {
                        markerPool.addAll(allActive)
                    }
                    activeIndividualMarkers.clear()
                    activePlaceMarkers.clear()
                    
                    defaultPointsFolder.items.clear()
                    catchesFolder.items.clear()
                    placesFolder.items.clear()
                    markersFolder.items.clear()
                }

                // Jos pisteitä on vähän, ei tarvita clippingiä ollenkaan.
                // Tämä estää pisteiden katoamisen ja välkkymisen heikolla sijainnilla.
                val (visibleCatches, visiblePlaces) = if (catchesCopy.size + placesCopy.size < 15000) {
                    Pair(catchesCopy, placesCopy)
                } else {
                    // Yksittäiset pisteet - käytetään näkyvyysrajoitusta (clipping)
                    // jos pisteitä on todella paljon (> 15000) suorituskyvyn takia.
                    var bbox = map.boundingBox
                    
                    // Jos bbox ei ole vielä valmis, käytetään fallbackina kaikkien näyttämistä.
                    // Älä käytä lastBBoxia tässä, koska se voi olla kaukana nykyisestä sijainnista
                    // ja aiheuttaa kaikkien pisteiden katoamisen (clipping väärälle alueelle).
                    if (bbox != null && bbox.latNorth != 0.0 && bbox.latSouth != 0.0 && (bbox.latitudeSpan > 0.0 || bbox.longitudeSpan > 0.0)) {
                        lastBBox = bbox
                        withContext(Dispatchers.Default) {
                            val totalCount = catchesCopy.size + placesCopy.size
                            // Marginaali 200% molempiin suuntiin pienellä määrällä.
                            // Suurella määrällä (> 15000) marginaalia pienennetään entisestään (20%) suorituskyvyn takia.
                            val marginMultiplier = if (totalCount < 15000) 2.0 else 0.2
                            val latMargin = bbox.latitudeSpan * marginMultiplier
                            val lonMargin = bbox.longitudeSpan * marginMultiplier
                            
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
                            val finalCatches = if (filteredCatches.size > maxVisible) {
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
                                    val gridSizeLon = bbox.longitudeSpan / gridSizeDivider
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
        val marker = activeIndividualMarkers[fish.id] ?: if (markerPool.isNotEmpty()) {
            markerPool.removeAt(markerPool.size - 1)
        } else {
            Marker(map)
        }
        
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        val species = speciesCache[fish.species]
        marker.title = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        
        val iconParams = calculateIconParams(fish)
        val drawableId = iconParams.first
        val finalIconSize = iconParams.second
        val finalVisibleSize = iconParams.third

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, finalVisibleSize, 48)
            touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, finalVisibleSize, 48) }
        } else {
            val key = Pair(drawableId, finalIconSize)
            iconCache.getOrPut(key) { getScaledMarkerIcon(drawableId, finalIconSize) }
        }
        marker.relatedObject = fish

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
        val marker = activePlaceMarkers[place.id] ?: if (markerPool.isNotEmpty()) {
            markerPool.removeAt(markerPool.size - 1)
        } else {
            Marker(map)
        }
        
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
            val key = Triple(drawableId, visibleSize, touchSize)
            touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, visibleSize, touchSize) }
        } else {
            val iconSize = 40
            val key = Pair(drawableId, iconSize)
            iconCache.getOrPut(key) { getScaledMarkerIcon(drawableId, iconSize) }
        }

        marker.infoWindow = placeInfoWindow
        marker.title = place.name
        
        if (zoom >= 16.5 && place.name.isNotEmpty()) {
            marker.showInfoWindow()
        } else {
            marker.closeInfoWindow()
        }

        marker.relatedObject = place
        marker.setOnMarkerClickListener { clickedMarker, _ ->
            map.controller.animateTo(clickedMarker.position)
            showPlaceDetailsDialog(clickedMarker)
            true
        }
        
        activePlaceMarkers[place.id] = marker
        return marker
    }

    private fun showPlaceDetailsDialog(marker: Marker) {
        val place = marker.relatedObject as? PlaceOfInterest ?: return
        val type = placeTypeCache[place.typeId]
        
        val titleView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_custom_title, null)
        val titleText = if (place.name.isEmpty()) type?.name ?: place.typeId else place.name
        titleView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = titleText

        val message = if (place.name.isEmpty()) place.additionalInfo else "${type?.name ?: place.typeId}\n\n${place.additionalInfo}"

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setMessage(message.trim())
            .setPositiveButton(R.string.ok, null)
            .create()

        val editMenuButton = titleView.findViewById<android.view.View>(R.id.editMenuButton)
        editMenuButton.setOnClickListener {
            val popup = PopupMenu(context, editMenuButton)
            popup.menu.add(0, 0, 0, context.getString(R.string.edit))
            popup.menu.add(0, 1, 1, context.getString(R.string.delete))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        showEditPlaceDialog(marker, place)
                        dialog.dismiss()
                        true
                    }
                    1 -> {
                        dialog.dismiss()
                        confirmDeletePlace(marker)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        dialog.show()
        dialog.enlargeButtons()
    }

    private fun showEditPlaceDialog(marker: Marker, place: PlaceOfInterest) {
        val intent = Intent(context, EditCatchActivity::class.java)
        intent.putExtra("EXTRA_IS_PLACE", true)
        intent.putExtra("EXTRA_PLACE_ID", place.id)
        (context as androidx.appcompat.app.AppCompatActivity).startActivityForResult(intent, 1002)
    }

    private fun confirmDeletePlace(marker: Marker) {
        val place = marker.relatedObject as? PlaceOfInterest ?: return
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.delete)
            .setMessage("Haluatko varmasti poistaa paikan ${place.name}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                Thread {
                    db.placeOfInterestDao().deleteById(place.id)
                    (context as? android.app.Activity)?.runOnUiThread {
                        removeMarker(marker)
                        synchronized(allPlaces) {
                            allPlaces.removeAll { it.id == place.id }
                        }
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.enlargeButtons()
    }

    private fun createClusterMarker(groupKey: Any, clusterList: List<FishCatch>): Marker? {
        val avgLat = clusterList.map { it.latitude }.average()
        val avgLon = clusterList.map { it.longitude }.average()
        val point = GeoPoint(avgLat, avgLon)
        
        val marker = if (markerPool.isNotEmpty()) {
            markerPool.removeAt(markerPool.size - 1)
        } else {
            Marker(map)
        }
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        val speciesId = if (groupKey is String) groupKey else (groupKey as Pair<*, *>).first as String
        val eventType = if (groupKey is Pair<*, *>) groupKey.second as String else null

        val species = speciesCache[speciesId]
        
        // Käytetään calculateIconParams -metodia ikoniparametrien hakemiseen (skaalaus mukaan lukien)
        // Käytetään klusterin ensimmäistä kalaa edustamaan koko ryhmää ikonivalinnassa
        val iconParams = calculateIconParams(clusterList[0])
        val drawableId = iconParams.first
        var iconSize = iconParams.second
        
        val count = clusterList.size

        if (speciesId == "UNKNOWN" && (eventType == null || eventType == FishCatch.CAUGHT_FISH)) {
            val key = Triple(drawableId, iconSize, 48)
            marker.icon = touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, iconSize, 48) }
            marker.title = "Tuntematon laji"
        } else {
            // Ryhmämerkissä käytetään base kokoa 40dp jos se on skaalattu oletuksesta
            if (drawableId != R.drawable.default_point && iconSize == 24) {
                iconSize = 40
            }

            val key = if (groupKey is String) {
                Triple(drawableId, iconSize, count)
            } else {
                // Lisätään eventType avaimeen jotta eri tapahtumatyypit eivät käytä samaa välimuistipaikkaa vahingossa
                Quadruple(drawableId, iconSize, count, eventType)
            }

            marker.icon = clusterIconCache.getOrPut(key) { 
                getClusteredMarkerIcon(drawableId, iconSize, count) 
            }
            
            val speciesName = species?.name ?: speciesId
            marker.title = if (eventType != null && eventType != FishCatch.CAUGHT_FISH) {
                "${FishCatch.getEventTypeName(eventType)}: $speciesName ($count kpl)"
            } else {
                "$speciesName ($count kpl)"
            }
        }
        
        marker.relatedObject = clusterList

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            val list = clickedMarker.relatedObject as? List<FishCatch>
            if (list != null && list.isNotEmpty()) {
                val minLat = list.minOf { it.latitude }
                val maxLat = list.maxOf { it.latitude }
                val minLon = list.minOf { it.longitude }
                val maxLon = list.maxOf { it.longitude }
                
                if (minLat == maxLat && minLon == maxLon) {
                    val targetZoom = (map.zoomLevelDouble + 2.0).coerceAtMost(16.0)
                    map.controller.animateTo(clickedMarker.position, targetZoom, 500L)
                } else {
                    val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                    map.zoomToBoundingBox(box.increaseByScale(1.5f), true, 0, 16.0, 500L)
                }
            } else {
                val targetZoom = (map.zoomLevelDouble + 1.0).coerceAtMost(16.0)
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
                        if (catchesCount + placesCount < 15000 && !shouldRebuild(zoom)) {
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
                            val threshold = if (catchesCount + placesCount < 15000) 0.8 else 0.15
                            if (latDiff < bbox.latitudeSpan * threshold && lonDiff < bbox.longitudeSpan * threshold) {
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
        val allActive = (defaultPointsFolder.items + catchesFolder.items + placesFolder.items + markersFolder.items)
            .filterIsInstance<Marker>()
        markerPool.addAll(allActive)
        activeIndividualMarkers.clear()
        activePlaceMarkers.clear()

        defaultPointsFolder.items.clear()
        catchesFolder.items.clear()
        placesFolder.items.clear()
        markersFolder.items.clear()
        lastZoom = -1.0
        map.invalidate()
    }

    fun removeMarker(marker: Marker) {
        val fish = marker.relatedObject as? FishCatch
        if (fish != null) {
            synchronized(allCatches) {
                allCatches.removeAll { it.id == fish.id }
            }
            activeIndividualMarkers.remove(fish.id)
            if (fish.species == "UNKNOWN") {
                defaultPointsFolder.remove(marker)
            } else {
                catchesFolder.remove(marker)
            }
        }
        
        val place = marker.relatedObject as? PlaceOfInterest
        if (place != null) {
            synchronized(allPlaces) {
                allPlaces.removeAll { it.id == place.id }
            }
            activePlaceMarkers.remove(place.id)
            placesFolder.remove(marker)
        }
        
        markerPool.add(marker)
        map.invalidate()
    }

    private fun showCatchDetailsDialog(marker: Marker) {
        val fish = marker.relatedObject as? FishCatch
        val details = StringBuilder()
        var hasSpecies = false
        
        fish?.let {
            val species = db.fishSpeciesDao().getById(it.species)
            if (species != null) {
                details.append("Laji: ${species.name}\n")
                hasSpecies = true
            }

            it.caughtAt?.let { caughtAt ->
                if (caughtAt > 0) {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy 'klo' HH:mm", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(caughtAt))
                    val timeLabel = if (it.eventType != null && it.eventType != FishCatch.CAUGHT_FISH) "Aika" else "Saantiaika"
                    details.append("$timeLabel: $dateStr\n")
                    
                    if (it.fisherman.isNotEmpty()) {
                        details.append("Kalastaja: ${it.fisherman}\n")
                    }
                }
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
                if (it.pressure != null) details.append("  Paine: ${it.pressure} hPa\n")
                
                val rainLevels = context.resources.getStringArray(R.array.rain_levels)
                val rainDesc = if (it.rain != null && (it.rain!!.toInt() + 1) < rainLevels.size) rainLevels[it.rain!!.toInt() + 1] else ""
                
                if (it.cloudiness != null || rainDesc.isNotEmpty() || it.rainHourMm != null) {
                    val parts = mutableListOf<String>()
                    if (it.cloudiness != null) parts.add("Pilvisyys: ${it.cloudiness}/8")
                    if (rainDesc.isNotEmpty()) parts.add("Sade: $rainDesc")
                    if (it.rainHourMm != null) parts.add("Sade: ${it.rainHourMm} mm/h")
                    details.append("  ${parts.joinToString(", ")}\n")
                }
                if (it.weatherStation.isNotEmpty()) {
                    val stationName = it.weatherStation.substringAfter(":")
                    details.append("  Asema: $stationName\n")
                }
            }

            if (it.additionalInfo.isNotEmpty()) details.append("\nLisätieto: ${it.additionalInfo}\n")
            if (it.originalRef.isNotEmpty()) details.append("Alkuperäinen viite: ${it.originalRef}\n")
        }

        val titleView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_custom_title, null)
        val dialogTitle = if (fish?.eventType != null && fish.eventType != FishCatch.CAUGHT_FISH) {
            FishCatch.getEventTypeName(fish.eventType)
        } else if (hasSpecies) {
            "Saaliin tiedot"
        } else {
            "Pisteen tiedot"
        }
        titleView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = dialogTitle

        val messageText = details.toString().trim()
        val spannableMessage = SpannableString(messageText)
        
        fish?.windDirection?.let { direction ->
            val windMarker = "Tuuli: "
            val windIndex = messageText.indexOf(windMarker)
            if (windIndex != -1) {
                val endOfLine = messageText.indexOf("\n", windIndex)
                val insertPos = if (endOfLine != -1) endOfLine else messageText.length
                
                // Käytetään ImageSpania nuolen lisäämiseen
                val arrowDrawable = ContextCompat.getDrawable(context, R.drawable.ic_wind_arrow)?.mutate()
                arrowDrawable?.let { drawable ->
                    drawable.setBounds(0, 0, (16 * context.resources.displayMetrics.density).toInt(), (16 * context.resources.displayMetrics.density).toInt())
                    val rotatedDrawable = run {
                        // Pyöritys on tehtävä dynaamisesti
                        val bitmap = android.graphics.Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.rotate((direction.toFloat() + 180) % 360, bitmap.width / 2f, bitmap.height / 2f)
                        drawable.draw(canvas)
                        android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                    }
                    rotatedDrawable.setBounds(0, 0, (16 * context.resources.displayMetrics.density).toInt(), (16 * context.resources.displayMetrics.density).toInt())
                    
                    val imageSpan = android.text.style.ImageSpan(rotatedDrawable, android.text.style.ImageSpan.ALIGN_BOTTOM)
                    // Koska lisäsimme välilyönnin jo aiemmin, korvataan se nuolella
                    val spacePos = insertPos - 1
                    if (spacePos >= 0 && messageText[spacePos] == ' ') {
                        spannableMessage.setSpan(imageSpan, spacePos, insertPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        val finalMessage: CharSequence = if (fish != null && fish.tripNotes.isNotEmpty()) {
            val linkText = "Kalapäiväkirjan merkinnät"
            val combinedSpannable = android.text.SpannableStringBuilder(spannableMessage)
            combinedSpannable.append("\n\n")
            val linkStart = combinedSpannable.length
            combinedSpannable.append(linkText)
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(context, TripNotesActivity::class.java)
                    intent.putExtra("EXTRA_NOTES", fish.tripNotes)
                    if (context !is android.app.Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            combinedSpannable.setSpan(clickableSpan, linkStart, combinedSpannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            combinedSpannable
        } else {
            spannableMessage
        }

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setMessage(finalMessage)
            .setPositiveButton("OK", null)
            .create()

        val editMenuButton = titleView.findViewById<android.view.View>(R.id.editMenuButton)
        editMenuButton.setOnClickListener {
            val popup = PopupMenu(context, editMenuButton)
            popup.menu.add(0, 0, 0, context.getString(R.string.edit))
            popup.menu.add(0, 1, 1, context.getString(R.string.delete))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        val intent = Intent(context, EditCatchActivity::class.java)
                        intent.putExtra("EXTRA_CATCH_ID", fish?.id)
                        if (context is android.app.Activity) {
                            context.startActivityForResult(intent, 1001)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                        dialog.dismiss()
                        true
                    }
                    1 -> {
                        dialog.dismiss()
                        confirmDeleteMarker(marker)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        if (fish != null && fish.tripNotes.isNotEmpty()) {
            dialog.setOnShowListener {
                dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
            }
        }
        
        dialog.show()
        dialog.enlargeButtons()
    }

    private fun confirmDeleteMarker(marker: Marker) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Poista merkki?")
            .setMessage("Haluatko varmasti poistaa tämän kalamerkin?")
            .setPositiveButton("Poista") { _, _ ->
                onDeleteConfirmed(marker)
            }
            .setNegativeButton("Peruuta", null)
            .show()

        dialog.enlargeButtons()
    }

    @Suppress("DiscouragedApi")
    private fun getDrawableId(iconName: String): Int {
        if (iconName.isEmpty()) return R.drawable.default_point
        
        val id = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        return if (id != 0) id else R.drawable.default_point
    }

    private fun calculateClusters(catches: List<FishCatch>, zoom: Double): Map<Any, List<List<FishCatch>>> {
        val result = mutableMapOf<Any, MutableList<MutableList<FishCatch>>>()
        
        // Ryhmitellään avaimen mukaan: laji (+ tapahtumatyyppi, jos ei saatu kala)
        val grouped = catches.groupBy { fish ->
            if (fish.species == "UNKNOWN" && (fish.eventType == null || fish.eventType == FishCatch.CAUGHT_FISH)) {
                // Palautetaan uniikki avain jokaiselle oletuspisteelle, jotta niitä ei klusteroida
                "UNKNOWN_INDIVIDUAL_${fish.id}"
            } else if (fish.eventType == null || fish.eventType == FishCatch.CAUGHT_FISH) {
                fish.species
            } else {
                fish.species to fish.eventType
            }
        }
        
        // Etäisyyskynnys pikseleinä (muunnetaan asteiksi)
        val threshold = when {
            zoom < 10 -> 0.5
            zoom < 12 -> 0.1
            zoom < 13 -> 0.02
            zoom < 14 -> 0.01
            zoom < 15 -> 0.005
            else -> 0.002
        }
        
        // Jos pisteitä on todella paljon, käytetään grid-pohjaista klusterointia nopeuden takia
        val useGrid = catches.size > 15000

        grouped.forEach { (groupKey, groupCatches) ->
            val clusters = mutableListOf<MutableList<FishCatch>>()
            
            if (useGrid) {
                // Grid-pohjainen klusterointi: jaetaan alue ruutuihin
                val grid = mutableMapOf<Pair<Int, Int>, MutableList<FishCatch>>()
                groupCatches.forEach { fish ->
                    val gridX = (fish.longitude / threshold).toInt()
                    val gridY = (fish.latitude / threshold).toInt()
                    val key = gridX to gridY
                    grid.getOrPut(key) { mutableListOf() }.add(fish)
                }
                grid.values.forEach { clusters.add(it) }
            } else {
                // Alkuperäinen etäisyyteen perustuva klusterointi pienemmille määrille
                groupCatches.forEach { fish ->
                    var found = false
                    for (cluster in clusters) {
                        val first = cluster[0]
                        val dist = Math.sqrt(Math.pow(fish.latitude - first.latitude, 2.0) + Math.pow(fish.longitude - first.longitude, 2.0))
                        if (dist < threshold) {
                            cluster.add(fish)
                            found = true
                            break
                        }
                    }
                    
                    if (!found) {
                        clusters.add(mutableListOf(fish))
                    }
                }
            }
            result[groupKey] = clusters
        }
        
        return result
    }

    private fun getClusteredMarkerIcon(drawableId: Int, sizeDp: Int, count: Int): BitmapDrawable {
        val baseIcon = getScaledMarkerIcon(drawableId, sizeDp).bitmap
        val density = context.resources.displayMetrics.density
        
        // Luodaan kopio jota muokataan
        val bitmap = baseIcon.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 12 * density
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val text = count.toString()
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        
        val radius = (bounds.width().coerceAtLeast(bounds.height()) / 2f) + (4 * density)
        val centerX = bitmap.width - radius
        val centerY = radius
        
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawText(text, centerX, centerY + (bounds.height() / 2f), textPaint)
        
        return bitmap.toDrawable(context.resources)
    }

    private fun getScaledMarkerIcon(drawableId: Int, sizeDp: Int): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = drawable.toBitmap(sizePx, sizePx)
        return bitmap.toDrawable(context.resources)
    }

    private fun getSmallIconWithLargeTouchArea(drawableId: Int, visibleSizeDp: Int, touchSizeDp: Int): BitmapDrawable {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: ContextCompat.getDrawable(context, R.drawable.default_point)!!
        
        val density = context.resources.displayMetrics.density
        val visibleSizePx = (visibleSizeDp * density).toInt()
        val touchSizePx = (touchSizeDp * density).toInt()
        
        val bitmap = createBitmap(touchSizePx, touchSizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val left = (touchSizePx - visibleSizePx) / 2
        val top = (touchSizePx - visibleSizePx) / 2
        
        drawable.setBounds(left, top, left + visibleSizePx, top + visibleSizePx)
        drawable.draw(canvas)
        
        return bitmap.toDrawable(context.resources)
    }
}
