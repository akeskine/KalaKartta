package fi.anssi.kalakartta.service

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException

class TalkingClockScoreProviderTest {
    @Test fun contentResolverReadsTheWholeUnicodeScoreThroughAPipe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = "<LAULU>[A2:173:58:231] käänt</LAULU> Kello on yhdeksäntoista. " + " ".repeat(12386)
        val token = TalkingClockScoreStore.put(text)
        val uri = Uri.parse("content://${context.packageName}.talking-clock-scores/$token")
        try {
            assertEquals(text,context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            assertEquals(text,context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            assertThrows(FileNotFoundException::class.java) { context.contentResolver.openFileDescriptor(uri,"w") }
        } finally {
            TalkingClockScoreStore.remove(token)
        }
        assertThrows(FileNotFoundException::class.java) { context.contentResolver.openInputStream(uri) }
    }
}
