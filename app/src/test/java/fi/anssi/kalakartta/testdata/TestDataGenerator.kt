package fi.anssi.kalakartta.testdata

import fi.anssi.kalakartta.data.FishingSession
import fi.anssi.kalakartta.data.TrackPoint
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Generaattori kalastusreittien stressitestausta varten.
 */
class TestDataGenerator(private val config: GeneratorConfig) {

    private val random = Random(config.randomSeed)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    data class GeneratorConfig(
        val randomSeed: Long,
        val trackingIntervalSeconds: Int,
        val outputFile: String,
        val prettyPrint: Boolean,
        val areas: List<AreaConfig>
    )

    data class AreaConfig(
        val name: String,
        val centerLatitude: Double,
        val centerLongitude: Double,
        val tripCount: Int,
        val dateFrom: String,
        val dateTo: String,
        val averageTripDurationMinutes: Int,
        val tripDurationVariationPercent: Int,
        val averageDirectionDegrees: Double,
        val directionVariationDegrees: Double,
        val averageDistanceMeters: Double,
        val distanceVariationPercent: Int,
        val averageSpeedMetersPerSecond: Double,
        val speedVariationPercent: Int,
        val averageAccuracyMeters: Double,
        val accuracyVariationPercent: Int,
        val ellipseAspectRatio: Double,
        val loopDirectionVariationDegrees: Double
    )

    fun generate() {
        val outputFile = File(config.outputFile)
        outputFile.parentFile?.mkdirs()

        println("Starting generation...")
        println("Output file: ${outputFile.absolutePath}")

        var totalSessions = 0
        var totalPoints = 0L
        var totalDistance = 0.0
        val startTime = System.currentTimeMillis()

        PrintWriter(outputFile).use { writer ->
            writer.println("{")
            writer.println("  \"sessions\": [")

            config.areas.forEachIndexed { areaIndex, area ->
                println("Generating area: ${area.name} (${area.tripCount} trips)")
                val dateFrom = parseDate(area.dateFrom)
                val dateTo = parseDate(area.dateTo)

                for (i in 0 until area.tripCount) {
                    val session = generateSession(area, dateFrom, dateTo)
                    
                    val sessionJson = sessionToJson(session.first, session.second)
                    writer.print(sessionJson)

                    totalSessions++
                    totalPoints += session.second.size
                    totalDistance += calculateTotalDistance(session.second)

                    val isLast = (areaIndex == config.areas.size - 1) && (i == area.tripCount - 1)
                    if (!isLast) {
                        writer.println(",")
                    } else {
                        writer.println()
                    }
                    
                    if (totalSessions % 100 == 0) {
                        println("Progress: $totalSessions sessions generated...")
                    }
                }
            }

            writer.println("  ]")
            writer.println("}")
        }

        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime

        println("\nGenerated:")
        println("- sessions: $totalSessions")
        println("- track points: $totalPoints")
        val areasDates = config.areas.flatMap { listOf(it.dateFrom, it.dateTo) }.sorted()
        println("- date range: ${areasDates.first()} ... ${areasDates.last()}")
        println("- tracking interval: ${config.trackingIntervalSeconds} s")
        println("- total route distance: ${String.format(Locale.US, "%.2f", totalDistance / 1000.0)} km")
        println("- output file: ${outputFile.name}")
        println("- output size: ${String.format(Locale.US, "%.2f", outputFile.length() / (1024.0 * 1024.0))} MB")
        println("- generation time: ${durationMs / 1000.0} s")
    }

    private fun generateSession(area: AreaConfig, dateFrom: Long, dateTo: Long): Pair<FishingSession, List<TrackPoint>> {
        // 1. Aloitusaika
        val startedAt = dateFrom + (random.nextDouble() * (dateTo - dateFrom)).toLong()
        
        // 2. Tavoitekesto
        val durationVariation = area.averageTripDurationMinutes * (area.tripDurationVariationPercent / 100.0)
        val targetDurationMinutes = area.averageTripDurationMinutes + (random.nextDouble() * 2 - 1) * durationVariation
        val targetDurationMs = (targetDurationMinutes * 60 * 1000).toLong()

        // 3. Tavoitekokonaismatka
        val distanceVariation = area.averageDistanceMeters * (area.distanceVariationPercent / 100.0)
        val targetDistanceMeters = area.averageDistanceMeters + (random.nextDouble() * 2 - 1) * distanceVariation

        // 4. Pääsuunta
        val directionVariation = area.directionVariationDegrees
        val mainDirectionDegrees = area.averageDirectionDegrees + (random.nextDouble() * 2 - 1) * directionVariation

        // 5-10. Reitin generointi
        val points = generateRoutePoints(area, startedAt, targetDurationMs, targetDistanceMeters, mainDirectionDegrees)

        val endedAt = points.last().timestamp
        val session = FishingSession(
            id = 0,
            startedAt = startedAt,
            endedAt = endedAt,
            notes = "Generated session in ${area.name}"
        )

        return Pair(session, points)
    }

    private fun generateRoutePoints(
        area: AreaConfig,
        startedAt: Long,
        targetDurationMs: Long,
        targetDistanceMeters: Double,
        mainDirectionDegrees: Double
    ): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val intervalMs = config.trackingIntervalSeconds * 1000L
        val numPoints = (targetDurationMs / intervalMs).toInt()
        
        // Lasketaan keskimääräinen vaadittu nopeus tavoitematkan saavuttamiseksi
        // targetDistance / duration = required speed
        val requiredAverageSpeed = targetDistanceMeters / (targetDurationMs / 1000.0)
        
        var currentLat = area.centerLatitude + (random.nextDouble() * 2 - 1) * 0.01
        var currentLon = area.centerLongitude + (random.nextDouble() * 2 - 1) * 0.01
        
        var currentSpeed = area.averageSpeedMetersPerSecond
        var currentAccuracy = area.averageAccuracyMeters
        
        // Ellipsin parametrit
        // Asetetaan ellipsin koko siten, että se vastaa suunnilleen tavoitematkaa
        // Yksi kierros ellipsillä: pituus n. 2 * pi * sqrt((a^2 + b^2) / 2)
        // a = pitkä akseli, b = lyhyt akseli = a * aspect
        // Matka = kierrokset * pituus
        
        val mainDirRad = Math.toRadians(90.0 - mainDirectionDegrees) // Muunnos: 0=N (90 deg y-akselilla)
        
        // Tehdään useita silmukoita
        val numLoops = 1 + random.nextInt(3)
        val pointsPerLoop = numPoints / numLoops
        
        var currentTime = startedAt
        
        for (loop in 0 until numLoops) {
            val loopDirOffset = (random.nextDouble() * 2 - 1) * area.loopDirectionVariationDegrees
            val loopDirRad = mainDirRad + Math.toRadians(loopDirOffset)
            
            // Skaalataan ellipsin kokoa tavoitematkan mukaan
            // Tämä on karkea arvio, jota korjataan myöhemmin jos tarpeen
            val loopDistance = targetDistanceMeters / numLoops
            val a = loopDistance / (2 * PI) // Karkea arvio säteestä jos olisi ympyrä
            val b = a * area.ellipseAspectRatio
            
            val loopStartLat = currentLat
            val loopStartLon = currentLon

            for (p in 0 until pointsPerLoop) {
                val t = (2 * PI * p) / pointsPerLoop
                
                // Ellipsin pisteet (ilman kiertoa)
                var dx = a * cos(t)
                var dy = b * sin(t)
                
                // Kierto
                val rotX = dx * cos(loopDirRad) - dy * sin(loopDirRad)
                val rotY = dx * sin(loopDirRad) + dy * cos(loopDirRad)
                
                // Metrit asteiksi (karkea approksimaatio)
                val latDeg = rotY / 111320.0
                val lonDeg = rotX / (111320.0 * cos(Math.toRadians(currentLat)))
                
                val targetLat = loopStartLat + latDeg
                val targetLon = loopStartLon + lonDeg
                
                // Lisätään kohinaa ja vähittäistä muutosta
                currentSpeed = moveTowards(currentSpeed, area.averageSpeedMetersPerSecond, area.speedVariationPercent, 0.1)
                currentAccuracy = moveTowards(currentAccuracy, area.averageAccuracyMeters, area.accuracyVariationPercent, 0.05)
                
                // GPS-kohinaa sijaintiin
                val noiseLat = (random.nextGaussian() * currentAccuracy / 10.0) / 111320.0
                val noiseLon = (random.nextGaussian() * currentAccuracy / 10.0) / (111320.0 * cos(Math.toRadians(targetLat)))
                
                points.add(TrackPoint(
                    fishingSessionId = 0,
                    timestamp = currentTime,
                    latitude = targetLat + noiseLat,
                    longitude = targetLon + noiseLon,
                    speed = currentSpeed.toFloat(),
                    accuracy = currentAccuracy.toFloat()
                ))
                
                currentTime += intervalMs
                currentLat = targetLat
                currentLon = targetLon
            }
        }
        
        // Skaalataan lopuksi pisteet vastaamaan tavoitematkaa paremmin jos heittoa on paljon
        return scalePointsToDistance(points, targetDistanceMeters)
    }

    private fun moveTowards(current: Double, target: Double, variationPercent: Int, maxStep: Double): Double {
        val variation = target * (variationPercent / 100.0)
        val min = target - variation
        val max = target + variation
        
        val step = (random.nextDouble() * 2 - 1) * maxStep
        var new = current + step
        
        if (new < min) new = min
        if (new > max) new = max
        
        return new
    }

    private fun scalePointsToDistance(points: List<TrackPoint>, targetDistance: Double): List<TrackPoint> {
        val actualDistance = calculateTotalDistance(points)
        if (actualDistance < 1.0) return points
        
        val scale = targetDistance / actualDistance
        
        // Emme voi vain skaalata koordinaatteja suoraan origosta, koska ne ovat maantieteellisiä.
        // Skaalataan etäisyydet alkupisteestä.
        val firstPoint = points.first()
        return points.map { pt ->
            val dLat = pt.latitude - firstPoint.latitude
            val dLon = pt.longitude - firstPoint.longitude
            pt.copy(
                id = 0,
                fishingSessionId = 0,
                latitude = firstPoint.latitude + dLat * sqrt(scale), // sqrt koska pinta-ala vs pituus
                longitude = firstPoint.longitude + dLon * sqrt(scale)
            )
        }
    }

    private fun calculateTotalDistance(points: List<TrackPoint>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            distance += haversine(points[i].latitude, points[i].longitude, points[i+1].latitude, points[i+1].longitude)
        }
        return distance
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    private fun parseDate(dateStr: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.parse(dateStr)?.time ?: 0L
    }

    private fun sessionToJson(session: FishingSession, points: List<TrackPoint>): String {
        val obj = JSONObject()
        obj.put("startedAt", isoFormat.format(Date(session.startedAt)))
        session.endedAt?.let { obj.put("endedAt", isoFormat.format(Date(it))) }
        obj.put("notes", session.notes)
        
        val pointsArray = JSONArray()
        points.forEach { pt ->
            val pObj = JSONObject()
            pObj.put("timestamp", isoFormat.format(Date(pt.timestamp)))
            pObj.put("latitude", String.format(Locale.US, "%.6f", pt.latitude).toDouble())
            pObj.put("longitude", String.format(Locale.US, "%.6f", pt.longitude).toDouble())
            pObj.put("speed", String.format(Locale.US, "%.1f", pt.speed).toDouble())
            pObj.put("accuracy", String.format(Locale.US, "%.1f", pt.accuracy).toDouble())
            pointsArray.put(pObj)
        }
        obj.put("points", pointsArray)
        
        return if (config.prettyPrint) obj.toString(2) else obj.toString()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val configPath = if (args.isNotEmpty()) args[0] else "app/src/test/resources/test-data-config.json"
            val configFile = File(configPath)
            
            if (!configFile.exists()) {
                println("Configuration file not found: ${configFile.absolutePath}")
                return
            }
            
            val configJson = JSONObject(configFile.readText())
            val areasArray = configJson.getJSONArray("areas")
            val areas = mutableListOf<AreaConfig>()
            
            for (i in 0 until areasArray.length()) {
                val a = areasArray.getJSONObject(i)
                areas.add(AreaConfig(
                    name = a.getString("name"),
                    centerLatitude = a.getDouble("centerLatitude"),
                    centerLongitude = a.getDouble("centerLongitude"),
                    tripCount = a.getInt("tripCount"),
                    dateFrom = a.getString("dateFrom"),
                    dateTo = a.getString("dateTo"),
                    averageTripDurationMinutes = a.getInt("averageTripDurationMinutes"),
                    tripDurationVariationPercent = a.getInt("tripDurationVariationPercent"),
                    averageDirectionDegrees = a.getDouble("averageDirectionDegrees"),
                    directionVariationDegrees = a.getDouble("directionVariationDegrees"),
                    averageDistanceMeters = a.getDouble("averageDistanceMeters"),
                    distanceVariationPercent = a.getInt("distanceVariationPercent"),
                    averageSpeedMetersPerSecond = a.getDouble("averageSpeedMetersPerSecond"),
                    speedVariationPercent = a.getInt("speedVariationPercent"),
                    averageAccuracyMeters = a.getDouble("averageAccuracyMeters"),
                    accuracyVariationPercent = a.getInt("accuracyVariationPercent"),
                    ellipseAspectRatio = a.getDouble("ellipseAspectRatio"),
                    loopDirectionVariationDegrees = a.getDouble("loopDirectionVariationDegrees")
                ))
            }
            
            val config = GeneratorConfig(
                randomSeed = configJson.getLong("randomSeed"),
                trackingIntervalSeconds = configJson.getInt("trackingIntervalSeconds"),
                outputFile = configJson.getString("outputFile"),
                prettyPrint = configJson.optBoolean("prettyPrint", false),
                areas = areas
            )
            
            TestDataGenerator(config).generate()
        }
    }
}
