package msr.mirudl.shared.util

/**
 * Percent-encodes a value for use in a URL query, mirroring
 * `java.net.URLEncoder.encode(value, "UTF-8")` (keeps the `+`-for-space
 * behavior) as used by the original `MiruClient.java` `encode()` helper.
 */
expect fun urlEncode(value: String): String
