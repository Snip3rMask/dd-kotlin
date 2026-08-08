package msr.mirudl.shared.storage

import java.util.prefs.Preferences

/**
 * Desktop (JVM) implementation of [AppStorage] backed by Java Preferences.
 *
 * Uses `Preferences.userRoot().node(name)` which stores data in the
 * OS-native preferences location (macOS plist, Windows Registry,
 * Linux `~/.java/.userPrefs/`).
 *
 * @param prefsName  The preferences node name. Must stay byte-identical
 *                   to the Android SharedPreferences file names so that
 *                   the same key strings are used across platforms.
 */
class DesktopAppStorage(
    private val prefsName: String
) : AppStorage {

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node(prefsName)
    }

    override fun getString(key: String, defaultValue: String): String {
        return prefs.get(key, defaultValue)
    }

    override fun setString(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun setInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun setBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    override fun setLong(key: String, value: Long) {
        prefs.putLong(key, value)
    }
}
