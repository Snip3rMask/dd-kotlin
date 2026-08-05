package msr.mirudl.shared.util

actual fun htmlToText(html: String): String = android.text.Html.fromHtml(html).toString()
