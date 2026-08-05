package msr.mirudl.shared.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
