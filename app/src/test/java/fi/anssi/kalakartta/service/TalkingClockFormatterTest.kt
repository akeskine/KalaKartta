package fi.anssi.kalakartta.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class TalkingClockFormatterTest {

    @Test
    fun testFormatTimeFinnish() {
        val service = TalkingClockService()
        val method: Method = TalkingClockService::class.java.getDeclaredMethod("formatTimeFinnish", Int::class.java, Int::class.java)
        method.isAccessible = true

        fun format(h: Int, m: Int): String = method.invoke(service, h, m) as String

        assertEquals("Kello on kaksikymmentäyksi viisikymmentä.", format(21, 50))
        assertEquals("Kello on yhdeksän.", format(9, 0))
        assertEquals("Kello on yksi kymmenen.", format(1, 10))
        assertEquals("Kello on nolla.", format(0, 0))
        assertEquals("Kello on kaksi nolla viisi.", format(2, 5))
        assertEquals("Kello on yksi nolla yksi.", format(1, 1))
        assertEquals("Kello on kaksikymmentäyksi nolla neljä.", format(21, 4))
        assertEquals("Kello on kaksitoista kolmekymmentäyksi.", format(12, 31))
        assertEquals("Kello on kolmetoista neljäkymmentäviisi.", format(13, 45))
        assertEquals("Kello on kaksikymmentäkolme viisikymmentäyhdeksän.", format(23, 59))
    }
}
