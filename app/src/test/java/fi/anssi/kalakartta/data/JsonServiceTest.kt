package fi.anssi.kalakartta.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.*

class JsonServiceTest {

    @Test
    fun testIsCustomIcon() {
        val service = JsonService()
        
        val method = JsonService::class.java.getDeclaredMethod("isCustomIcon", String::class.java)
        method.isAccessible = true
        
        fun isCustomIcon(path: String): Boolean = method.invoke(service, path) as Boolean

        assertTrue(isCustomIcon("/data/user/0/fi.anssi.kalakartta/files/custom_icon_123.png"))
        assertTrue(isCustomIcon("custom_icon_123.png")) // Now should be true if it contains "/" or we update logic
        assertTrue(!isCustomIcon("ahven"))
        assertTrue(!isCustomIcon(""))
    }

    @Test
    fun testGetIconFileName() {
        val service = JsonService()
        val method = JsonService::class.java.getDeclaredMethod("getIconFileName", String::class.java)
        method.isAccessible = true
        
        fun getIconFileName(path: String): String = method.invoke(service, path) as String

        assertEquals("custom_icon_123.png", getIconFileName("/data/user/0/fi.anssi.kalakartta/files/custom_icon_123.png"))
        assertEquals("ahven", getIconFileName("ahven"))
    }
}
