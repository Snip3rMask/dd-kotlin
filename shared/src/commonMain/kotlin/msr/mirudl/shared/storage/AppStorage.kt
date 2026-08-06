package msr.mirudl.shared.storage

/**
 * Platform-agnostic key-value storage abstraction.
 * Android actual backed by SharedPreferences; other platforms
 * provide their own implementations later (Phase 7 iOS, Phase 8 Desktop).
 *
 * Prefs file names and key strings are kept byte-identical to the
 * original Java code so existing user data is never lost.
 */
interface AppStorage {

    fun getString(key: String, defaultValue: String): String

    fun setString(key: String, value: String)

    fun getInt(key: String, defaultValue: Int): Int

    fun setInt(key: String, value: Int)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun setBoolean(key: String, value: Boolean)

    fun getLong(key: String, defaultValue: Long): Long

    fun setLong(key: String, value: Long)
}
