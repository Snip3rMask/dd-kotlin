package msr.mirudl.shared.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of [AppStorage] backed by SharedPreferences.
 *
 * @param context  Application or Activity context — will NOT leak the Activity.
 * @param prefsName  The SharedPreferences file name. Must stay byte-identical
 *                   to the original Java constants to preserve user data:
 *                   - `"mirudl_settings"`      (StorageSettings)
 *                   - `"mirudl_downloads"`     (DownloadEntryStore)
 *                   - `"mirudl_update_checker"`(UpdateChecker)
 */
class AndroidAppStorage(
    context: Context,
    private val prefsName: String
) : AppStorage {

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    override fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    override fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}
