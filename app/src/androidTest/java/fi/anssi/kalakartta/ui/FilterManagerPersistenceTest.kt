package fi.anssi.kalakartta.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterManagerPersistenceTest {

    private lateinit var filterManager: FilterManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        filterManager = FilterManager(context)
        filterManager.clearFilters()
    }

    @After
    fun tearDown() {
        filterManager.clearFilters()
    }

    @Test
    fun saveAndReadRoundTripPreservesAllFilterFields() {
        val expected = FilterManager.Filters(
            startDate = 1_000L,
            endDate = 2_000L,
            annualStartMonth = 3,
            annualStartDay = 4,
            annualEndMonth = 5,
            annualEndDay = 6,
            startTimeMinutes = 60,
            endTimeMinutes = 1_200,
            windMin = 1.5f,
            windMax = 8.5f,
            pressureMin = 995.5f,
            pressureMax = 1_025.5f,
            waterTempMin = 4.5f,
            waterTempMax = 18.5f,
            moonPhaseMin = 0.2f,
            moonPhaseMax = 0.8f,
            speciesId = "PERCH",
            otherSpecies = "Test species",
            placeTypeId = "ROCK",
            freeText = "Test note",
            fisherman = "Test fisherman",
            onlyCaughtFish = true,
            onlyFishPoints = true,
            onlyNonFishPoints = true,
            weightMin = 500L,
            weightMax = 4_500L,
            lengthMin = 205L,
            lengthMax = 655L,
            weightLengthOperator = "AND",
            latNorth = 60.5,
            latSouth = 59.5,
            lonEast = 25.5,
            lonWest = 24.5
        )

        filterManager.saveFilters(expected)

        assertEquals(expected, filterManager.getFilters())
    }

    @Test
    fun clearFiltersRemovesOptionalValuesAndDisablesFlags() {
        filterManager.saveFilters(
            FilterManager.Filters(
                speciesId = "PIKE",
                freeText = "to clear",
                onlyCaughtFish = true,
                onlyFishPoints = true
            )
        )

        filterManager.clearFilters()

        assertEquals(FilterManager.Filters(), filterManager.getFilters())
        assertFalse(filterManager.hasActiveFilters())
    }
}
