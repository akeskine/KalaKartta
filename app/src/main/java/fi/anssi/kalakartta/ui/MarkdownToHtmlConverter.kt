package fi.anssi.kalakartta.ui

import java.util.Locale

/** Käyttöohjeen Markdown-muunnos ja sisäisten ankkurien normalisointi ilman Android-riippuvuuksia. */
object MarkdownToHtmlConverter {

    fun convert(markdown: String): String {
        var html = markdown

        html = html.replace(Regex("\\[([^\\]]+)\\]\\(#([^\\)]+)\\)"), "<a href=\"#$2\">$1</a>")
        html = html.replace(Regex("(?m)^### (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            "<h3 id=\"${anchorId(title)}\">$title</h3>"
        }
        html = html.replace(Regex("(?m)^## (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            "<h2 id=\"${anchorId(title)}\">$title</h2>"
        }
        html = html.replace(Regex("(?m)^# (.*)$")) { matchResult ->
            val title = matchResult.groupValues[1]
            "<h1 id=\"${anchorId(title)}\">$title</h1>"
        }
        html = html.replace(Regex("(?m)^- (.*)$"), "<li>$1</li>")
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
        return html.replace("\n\n", "<br><br>")
    }

    fun anchorId(title: String): String = title
        .lowercase(Locale.ROOT)
        .trim()
        .replace("ä", "a")
        .replace("ö", "o")
        .replace("å", "a")
        .replace(" ", "-")
        .replace(Regex("[^a-z0-9-]"), "")

    fun matchesAnchor(line: String, anchor: String): Boolean {
        val normalizedLine = anchorId(line)
        return normalizedLine == anchor ||
            normalizedLine.endsWith("-$anchor") ||
            anchor.endsWith("-$normalizedLine")
    }
}
