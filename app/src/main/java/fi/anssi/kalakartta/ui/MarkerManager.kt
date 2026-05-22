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
import fi.anssi.kalakartta.utils.enlargeButtons
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import org.osmdroid.views.overlay.FolderOverlay
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class MarkerManager(
    private val context: Context,
    private val map: MapView,
    private val db: AppDatabase,
    private val onDeleteConfirmed: (Marker) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rebuildJob: Job? = null
    
    private val markersFolder = FolderOverlay()
    private val iconCache = mutableMapOf<Pair<Int, Int>, BitmapDrawable>()
    private val touchIconCache = mutableMapOf<Triple<Int, Int, Int>, BitmapDrawable>()
    private val clusterIconCache = mutableMapOf<Triple<Int, Int, Int>, BitmapDrawable>()
    private val allCatches = mutableListOf<FishCatch>()
    private var lastZoom = -1.0

    init {
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

    /**
     * Lisää tai päivittää yksittäisen markerin kartalle ilman koko aineiston uudelleenlatausta.
     * Käytetään kun lisätään yksi uusi kala.
     */
    fun addOrUpdateMarkerIncremental(fish: FishCatch, zoom: Double) {
        // Päivitetään sisäinen lista
        addMarker(fish)

        // Jos ollaan klusterointialueella, on turvallisempaa rakentaa kaikki uudelleen taustalla,
        // koska uusi piste voi muuttaa klusterien koostumusta.
        if (zoom < 14.5) {
            rebuildMarkers(zoom)
            return
        }

        // Jos ollaan yksittäisten pisteiden alueella, voidaan päivittää vain yksi
        // Etsitään vanha marker jos kyseessä on päivitys
        val existingMarker = markersFolder.items.find { (it as? Marker)?.relatedObject is FishCatch && ((it as? Marker)?.relatedObject as FishCatch).id == fish.id } as? Marker
        
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
        
        val species = db.fishSpeciesDao().getById(fish.species)
        marker.title = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        
        val iconName = species?.icon_default ?: ""
        val drawableId = getDrawableId(iconName)
        
        var iconSize = if (drawableId == R.drawable.default_point) 24 else 40
        var visibleSize = if (drawableId == R.drawable.default_point) 8 else iconSize
        
        if (fish.species == "SALMON") {
            iconSize = (iconSize * 1.3).toInt()
            visibleSize = (visibleSize * 1.3).toInt()
        } else if (fish.species == "PERCH") {
            iconSize = (iconSize * 0.8).toInt()
            visibleSize = (visibleSize * 0.8).toInt()
        }

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, visibleSize, 48)
            touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, visibleSize, 48) }
        } else {
            val key = Pair(drawableId, iconSize)
            iconCache.getOrPut(key) { getScaledMarkerIcon(drawableId, iconSize) }
        }
        marker.relatedObject = fish
    }

    fun rebuildMarkers(zoom: Double) {
        lastZoom = zoom
        
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            // Pieni viive jotta ei turhaan lasketa jos zoom/scroll on kesken
            delay(100)
            
            val catchesCopy = synchronized(allCatches) { allCatches.toList() }
            
            if (catchesCopy.isEmpty()) {
                withContext(Dispatchers.Main) {
                    markersFolder.items.clear()
                    map.invalidate()
                }
                return@launch
            }

            if (zoom < 14.5) {
                // Klusterointi voidaan laskea taustalla
                val clusters = withContext(Dispatchers.Default) {
                    calculateClusters(catchesCopy, zoom)
                }
                
                // Markerien luonti on tehtävä Main-säikeessä
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        markersFolder.items.clear()
                        clusters.forEach { (speciesId, clusterList) ->
                            if (clusterList.size == 1) {
                                addIndividualMarker(clusterList[0])
                            } else {
                                addClusterMarker(speciesId, clusterList)
                            }
                        }
                        map.invalidate()
                    }
                }
            } else {
                // Yksittäiset pisteet - käytetään näkyvyysrajoitusta (clipping)
                // jotta ei luoda tuhansia turhia markereita
                val visibleCatches = withContext(Dispatchers.Default) {
                    val bbox = map.boundingBox
                    // Marginaali 50% molempiin suuntiin sulavamman skrollauksen takia
                    val latMargin = bbox.latitudeSpan * 0.5
                    val lonMargin = bbox.longitudeSpan * 0.5
                    
                    catchesCopy.filter { fish ->
                        fish.latitude >= bbox.latSouth - latMargin && 
                        fish.latitude <= bbox.latNorth + latMargin &&
                        fish.longitude >= bbox.lonWest - lonMargin &&
                        fish.longitude <= bbox.lonEast + lonMargin
                    }
                }

                if (isActive) {
                    withContext(Dispatchers.Main) {
                        markersFolder.items.clear()
                        visibleCatches.forEach { addIndividualMarker(it) }
                        map.invalidate()
                    }
                }
            }
        }
    }

    private fun calculateClusters(catches: List<FishCatch>, zoom: Double): List<Pair<String, List<FishCatch>>> {
        val gridSize = 360.0 / (Math.pow(2.0, zoom) * 5.0)
        val groupedBySpecies = catches.groupBy { it.species }
        val result = mutableListOf<Pair<String, List<FishCatch>>>()

        for ((speciesId, speciesCatches) in groupedBySpecies) {
            val grid = mutableMapOf<Pair<Int, Int>, MutableList<FishCatch>>()
            for (fish in speciesCatches) {
                val gx = (fish.longitude / gridSize).toInt()
                val gy = (fish.latitude / gridSize).toInt()
                grid.getOrPut(gx to gy) { mutableListOf() }.add(fish)
            }
            for (clusterList in grid.values) {
                result.add(speciesId to clusterList)
            }
        }
        return result
    }

    private fun clusterMarkers(zoom: Double) {
        // Poistettu käytöstä, korvattu calculateClusters + rebuildMarkers logiikalla
    }

    private fun addIndividualMarker(fish: FishCatch) {
        val point = GeoPoint(fish.latitude, fish.longitude)
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        val species = db.fishSpeciesDao().getById(fish.species)
        marker.title = species?.name ?: if (fish.species == "UNKNOWN") "Tuntematon laji" else fish.species
        
        val iconName = species?.icon_default ?: ""
        val drawableId = getDrawableId(iconName)
        
        var iconSize = if (drawableId == R.drawable.default_point) 24 else 40
        var visibleSize = if (drawableId == R.drawable.default_point) 8 else iconSize
        
        if (fish.species == "SALMON") {
            iconSize = (iconSize * 1.3).toInt()
            visibleSize = (visibleSize * 1.3).toInt()
        } else if (fish.species == "PERCH") {
            iconSize = (iconSize * 0.8).toInt()
            visibleSize = (visibleSize * 0.8).toInt()
        }

        marker.icon = if (drawableId == R.drawable.default_point) {
            val key = Triple(drawableId, visibleSize, 48)
            touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, visibleSize, 48) }
        } else {
            val key = Pair(drawableId, iconSize)
            iconCache.getOrPut(key) { getScaledMarkerIcon(drawableId, iconSize) }
        }
        marker.relatedObject = fish

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            map.controller.animateTo(clickedMarker.position)
            showCatchDetailsDialog(clickedMarker)
            true
        }

        markersFolder.add(marker)
    }

    private fun addClusterMarker(speciesId: String, clusterList: List<FishCatch>) {
        val avgLat = clusterList.map { it.latitude }.average()
        val avgLon = clusterList.map { it.longitude }.average()
        val point = GeoPoint(avgLat, avgLon)
        
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        
        val species = db.fishSpeciesDao().getById(speciesId)
        val iconName = species?.icon_default ?: ""
        val drawableId = getDrawableId(iconName)
        val count = clusterList.size

        if (speciesId == "UNKNOWN") {
            val key = Triple(drawableId, 8, 48)
            marker.icon = touchIconCache.getOrPut(key) { getSmallIconWithLargeTouchArea(drawableId, 8, 48) }
            marker.title = "Tuntematon laji"
        } else {
            var iconSize = 40
            if (speciesId == "SALMON") {
                iconSize = (iconSize * 1.3).toInt()
            } else if (speciesId == "PERCH") {
                iconSize = (iconSize * 0.8).toInt()
            }
            val key = Triple(drawableId, iconSize, count)
            marker.icon = clusterIconCache.getOrPut(key) { 
                getClusteredMarkerIcon(drawableId, iconSize, count) 
            }
            marker.title = (species?.name ?: speciesId) + " ($count kpl)"
        }
        
        marker.relatedObject = clusterList

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            // Zoomataan klusteriin sisään
            val list = clickedMarker.relatedObject as? List<FishCatch>
            if (list != null && list.isNotEmpty()) {
                val minLat = list.minOf { it.latitude }
                val maxLat = list.maxOf { it.latitude }
                val minLon = list.minOf { it.longitude }
                val maxLon = list.maxOf { it.longitude }
                
                if (minLat == maxLat && minLon == maxLon) {
                    // Kaikki pisteet samassa kohdassa, nostetaan zoomia vain vähän (max 16.0)
                    // Käytetään +2.0 ja max 16.0, jotta ei zoomata liian lähelle "tyhjään"
                    val targetZoom = (map.zoomLevelDouble + 2.0).coerceAtMost(16.0)
                    map.controller.animateTo(clickedMarker.position, targetZoom, 500L)
                } else {
                    // Luodaan rajoittava laatikko ja lisätään 50% marginaali (1.5f)
                    val box = BoundingBox(maxLat, maxLon, minLat, minLon)
                    // Käytetään zoomToBoundingBoxia mutta rajoitetaan maksimizoomia tasolle 16.0
                    // 16.0 on riittävä taso nähdä pisteet erikseen, koska klusterointi loppuu jo 14.5 tasolla
                    map.zoomToBoundingBox(box.increaseByScale(1.5f), true, 0, 16.0, 500L)
                }
            } else {
                val targetZoom = (map.zoomLevelDouble + 1.0).coerceAtMost(16.0)
                map.controller.animateTo(clickedMarker.position, targetZoom, 500L)
            }
            true
        }

        markersFolder.add(marker)
    }

    fun setMarkersVisible(visible: Boolean, zoom: Double, forceRebuild: Boolean = false) {
        if (markersFolder.isEnabled != visible || Math.abs(lastZoom - zoom) > 0.1 || forceRebuild) {
            markersFolder.isEnabled = visible
            if (visible) {
                // Tarkistetaan pitääkö klusterointi päivittää
                // Jos zoom on muuttunut merkittävästi tai eka kerta tai pakotettu (skrollaus)
                if (forceRebuild || shouldRebuild(zoom)) {
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
        if (zoom < 14.5 || lastZoom < 14.5) {
            return Math.abs(lastZoom - zoom) >= 0.8
        }
        return false
    }

    fun clearMarkers() {
        synchronized(allCatches) {
            allCatches.clear()
        }
        markersFolder.items.clear()
        lastZoom = -1.0
    }

    fun removeMarker(marker: Marker) {
        val fish = marker.relatedObject as? FishCatch
        if (fish != null) {
            synchronized(allCatches) {
                allCatches.removeAll { it.id == fish.id }
            }
        }
        markersFolder.remove(marker)
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

            if (it.caughtAt > 0) {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy 'klo' HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.caughtAt))
                details.append("Saantiaika: $dateStr\n")
            }

            if (it.weight > 0) details.append("Paino: ${it.weight} g\n")
            if (it.length > 0) details.append("Pituus: ${it.length} cm\n")
            if (it.additionalInfo.isNotEmpty()) details.append("Lisätieto: ${it.additionalInfo}\n")
            if (it.originalRef.isNotEmpty()) details.append("Alkuperäinen viite: ${it.originalRef}\n")
        }

        val messageText = details.toString().trim()
        val finalMessage: CharSequence = if (fish != null && fish.tripNotes.isNotEmpty()) {
            val linkText = "Kalapäiväkirjan merkinnät"
            val spannable = SpannableString("$messageText\n\n$linkText")
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
            val start = spannable.length - linkText.length
            val end = spannable.length
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable
        } else {
            messageText
        }

        val titleView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_custom_title, null)
        titleView.findViewById<android.widget.TextView>(R.id.dialogTitle).text = if (hasSpecies) "Saaliin tiedot" else "Pisteen tiedot"

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
