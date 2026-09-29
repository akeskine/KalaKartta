package fi.anssi.kalakartta.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

data class CopernicusSceneDateCacheEntry(val sceneDate: String?, val cachedAtMillis: Long)

interface CopernicusSceneDateCacheStore {
    fun get(key: String): CopernicusSceneDateCacheEntry?
    fun put(key: String, entry: CopernicusSceneDateCacheEntry)
}

class SharedPreferencesCopernicusSceneDateCacheStore(context: Context) : CopernicusSceneDateCacheStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): CopernicusSceneDateCacheEntry? {
        val timeKey = "$key.cached_at"
        if (!preferences.contains(timeKey)) return null
        val storedSceneDate = preferences.getString("$key.scene_date", NO_SCENE_FOUND) ?: NO_SCENE_FOUND
        return CopernicusSceneDateCacheEntry(
            sceneDate = storedSceneDate.takeUnless { it == NO_SCENE_FOUND },
            cachedAtMillis = preferences.getLong(timeKey, 0)
        )
    }

    override fun put(key: String, entry: CopernicusSceneDateCacheEntry) {
        preferences.edit()
            .putString("$key.scene_date", entry.sceneDate ?: NO_SCENE_FOUND)
            .putLong("$key.cached_at", entry.cachedAtMillis)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "copernicus_scene_dates"
        const val NO_SCENE_FOUND = "__no_scene_found__"
    }
}

class CopernicusSceneDateResolver(
    private val catalogClient: CopernicusCatalogClient,
    private val cacheStore: CopernicusSceneDateCacheStore,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val inFlightLookups = ConcurrentHashMap<String, CompletableFuture<CopernicusSceneDateCacheEntry>>()

    fun resolveSceneDate(tileId: String, targetDate: String): String? {
        val key = "$tileId|$targetDate|${CopernicusCatalogClient.FILTER_VERSION}"
        readFreshEntry(key)?.let { return it.sceneDate }

        val newLookup = CompletableFuture<CopernicusSceneDateCacheEntry>()
        val activeLookup = inFlightLookups.putIfAbsent(key, newLookup)
        if (activeLookup != null) return await(activeLookup).sceneDate

        try {
            readFreshEntry(key)?.let {
                newLookup.complete(it)
                return it.sceneDate
            }
            val entry = CopernicusSceneDateCacheEntry(
                catalogClient.findLatestSceneDate(tileId, targetDate),
                currentTimeMillis()
            )
            if (entry.sceneDate != null) cacheStore.put(key, entry)
            newLookup.complete(entry)
            return entry.sceneDate
        } catch (failure: Throwable) {
            newLookup.completeExceptionally(failure)
            throw failure
        } finally {
            inFlightLookups.remove(key, newLookup)
        }
    }

    fun resolvePreviousSceneDate(tileId: String, currentSceneDate: String): String? {
        val previousDate = previousDate(currentSceneDate)
        return resolveSceneDate(tileId, previousDate)?.takeIf { it < currentSceneDate }
    }

    private fun readFreshEntry(key: String): CopernicusSceneDateCacheEntry? {
        val entry = cacheStore.get(key) ?: return null
        val age = currentTimeMillis() - entry.cachedAtMillis
        return entry.takeIf { age in 0 until CACHE_TTL_MILLIS }
    }

    private fun previousDate(value: String): String {
        val formatter = SimpleDateFormat(DATE_FORMAT, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = formatter.parse(value) ?: throw IllegalArgumentException("Invalid Copernicus scene date")
        require(formatter.format(parsed) == value) { "Invalid Copernicus scene date" }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, -1)
        }
        return formatter.format(calendar.time)
    }

    private fun await(future: CompletableFuture<CopernicusSceneDateCacheEntry>): CopernicusSceneDateCacheEntry {
        return try {
            future.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    companion object {
        const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val DATE_FORMAT = "yyyy-MM-dd"
    }
}