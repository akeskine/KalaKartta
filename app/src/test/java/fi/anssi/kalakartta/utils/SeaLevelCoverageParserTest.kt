package fi.anssi.kalakartta.utils

import fi.anssi.kalakartta.data.SeaLevelSample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class SeaLevelCoverageParserTest {
    @Test
    fun parsesWatlevFieldAndConvertsMillimetersToCentimeters() {
        val observations = parseSeaLevelCoverage(parserFor("""
            <gml:GridSeriesObservation xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:swe="http://www.opengis.net/swe/2.0">
                <gml:domainSet><gml:SimpleMultiPoint>
                    <gml:positions>60.0 24.0 1000 60.0 24.0 1600 60.0 24.0 2200</gml:positions>
                </gml:SimpleMultiPoint></gml:domainSet>
                <gml:rangeType><swe:DataRecord>
                    <swe:field name="TW"><swe:Quantity/></swe:field>
                    <swe:field name="WATLEV"><swe:Quantity/></swe:field>
                    <swe:field name="WLEVN2K_PT1S_INSTANT"><swe:Quantity/></swe:field>
                </swe:DataRecord></gml:rangeType>
                <gml:rangeSet><gml:DataBlock>
                    <gml:doubleOrNilReasonTupleList>12.0 500 600 13.0 NaN 610 14.0 525 620</gml:doubleOrNilReasonTupleList>
                </gml:DataBlock></gml:rangeSet>
            </gml:GridSeriesObservation>
        """.trimIndent()))

        assertEquals(2, observations.size)
        assertEquals(60.0, observations[0].latitude, 0.0)
        assertEquals(24.0, observations[0].longitude, 0.0)
        assertEquals(1_000_000L, observations[0].sample.time)
        assertEquals(50L, observations[0].sample.seaLevel)
        assertEquals(53L, observations[1].sample.seaLevel)
    }

    @Test
    fun missingWatlevFieldProducesNoObservations() {
        val observations = parseSeaLevelCoverage(parserFor("""
            <gml:GridSeriesObservation xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:swe="http://www.opengis.net/swe/2.0">
                <gml:domainSet><gml:SimpleMultiPoint>
                    <gml:positions>60.0 24.0 1000</gml:positions>
                </gml:SimpleMultiPoint></gml:domainSet>
                <gml:rangeType><swe:DataRecord>
                    <swe:field name="TW"><swe:Quantity/></swe:field>
                </swe:DataRecord></gml:rangeType>
                <gml:rangeSet><gml:DataBlock>
                    <gml:doubleOrNilReasonTupleList>12.0</gml:doubleOrNilReasonTupleList>
                </gml:DataBlock></gml:rangeSet>
            </gml:GridSeriesObservation>
        """.trimIndent()))

        assertTrue(observations.isEmpty())
    }

    @Test
    fun nearestStationSuppliesTenMinuteCatchValueAndHourlyHistory() {
        val hourMillis = 60 * 60 * 1000L
        val tenMinuteMillis = 10 * 60 * 1000L
        val caughtAt = 12 * hourMillis + 17 * 60 * 1000L
        val roundedCatchTime = caughtAt / tenMinuteMillis * tenMinuteMillis
        val hourlyObservations = (6L..18L).map { hour ->
            SeaLevelObservation(
                60.0,
                24.0,
                SeaLevelSample(hour * hourMillis, hour)
            )
        }

        val result = selectNearestSeaLevelStation(
            observations = hourlyObservations + SeaLevelObservation(
                61.0,
                25.0,
                SeaLevelSample(12 * hourMillis, 80L)
            ),
            catchObservations = listOf(
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(roundedCatchTime - tenMinuteMillis, 40L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(roundedCatchTime, 41L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(roundedCatchTime + tenMinuteMillis, 42L)),
                SeaLevelObservation(61.0, 25.0, SeaLevelSample(roundedCatchTime, 80L))
            ),
            latitude = 60.01,
            longitude = 24.01,
            caughtAt = caughtAt,
            catchTargetTime = roundedCatchTime,
            stations = listOf(WeatherStation("100539", "Kemi Ajos", 60.0, 24.0))
        )

        assertEquals(41L, result?.seaLevel)
        assertEquals(roundedCatchTime, result?.seaLevelTime)
        assertEquals("100539:Kemi Ajos", result?.seaLevelStation)
        assertEquals(12, result?.seaLevelSamples?.size)
        assertTrue(result!!.seaLevelSamples.zipWithNext().all { (first, second) ->
            second.time - first.time == hourMillis
        })
    }

    private fun parserFor(xml: String): XmlPullParser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(xml))
    }
}