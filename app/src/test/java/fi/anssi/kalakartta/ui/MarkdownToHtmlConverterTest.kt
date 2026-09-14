package fi.anssi.kalakartta.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownToHtmlConverterTest {

    @Test
    fun convertsHeadingsLinksListsAndBoldText() {
        val html = MarkdownToHtmlConverter.convert(
            "# Käyttöohje\n\n## Asetukset\n\n- **Valitse** asetus\n\n[Palaa](#käyttöohje)"
        )

        assertTrue(html.contains("<h1 id=\"kayttoohje\">Käyttöohje</h1>"))
        assertTrue(html.contains("<h2 id=\"asetukset\">Asetukset</h2>"))
        assertTrue(html.contains("<li><b>Valitse</b> asetus</li>"))
        assertTrue(html.contains("<a href=\"#käyttöohje\">Palaa</a>"))
    }

    @Test
    fun normalizesFinnishCharactersAndMatchesAnchors() {
        assertTrue(MarkdownToHtmlConverter.matchesAnchor("Käyttöohje", "kayttoohje"))
        assertTrue(MarkdownToHtmlConverter.matchesAnchor("Asetukset", "ohje-asetukset"))
        assertFalse(MarkdownToHtmlConverter.matchesAnchor("Asetukset", "kayttoohje"))
    }
}
