package msr.mirudl.shared.util

/**
 * Desktop approximation of Android's `Html.fromHtml`: strips tags and
 * decodes the common entities the site uses. Revisit in Phase 8 once a
 * real desktop UI exists to compare against.
 */
actual fun htmlToText(html: String): String {
    if (html.isEmpty()) return html
    var text = html.replace(Regex("<[^>]*>"), "")
    text = text.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
    return text.trim()
}
