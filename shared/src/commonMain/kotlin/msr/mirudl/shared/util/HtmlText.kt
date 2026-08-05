package msr.mirudl.shared.util

/**
 * Strips HTML tags and decodes entities, mirroring
 * `android.text.Html.fromHtml(...).toString()` as used by the original
 * `MiruClient.java` title parsing.
 */
expect fun htmlToText(html: String): String
