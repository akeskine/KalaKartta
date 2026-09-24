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
    fun nearestAvailableStationSuppliesCurrentLevelAndItsHistory() {
        val result = selectNearestSeaLevelStation(
            observations = listOf(
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(1_000L, 40L)),
                SeaLevelObservation(60.0, 24.0, SeaLevelSample(2_000L, 41L)),
                SeaLevelObservation(61.0, 25.0, SeaLevelSample(2_000L, 80L))
            ),
            latitude = 60.01,
            longitude = 24.01,
            caughtAt = 1_900L
        )

        assertEquals(41L, result?.seaLevel)
        assertEquals(2, result?.seaLevelSamples?.size)
    }

    private fun parserFor(xml: String): XmlPullParser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(xml))
    }
}