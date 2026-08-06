package msr.mirudl.shared.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Android implementation of [FileStorage] — SAF (`DocumentFile`) for the
 * user-selected download folder, plain `File` for the cache/temp dir,
 * exactly like the original Java `HlsDownloader` file helpers.
 *
 * Call [init] once with an application context before first use.
 */
object AndroidFileStorage : FileStorage {

    private lateinit var appContext: Context

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun cacheDir(): String = appContext.cacheDir.absolutePath

    override fun openInput(path: String): InputStream? = try {
        if (isContentUri(path)) {
            appContext.contentResolver.openInputStream(Uri.parse(path))
        } else {
            FileInputStream(File(path))
        }
    } catch (e: Exception) {
        null
    }

    override fun openOutput(path: String): OutputStream? = try {
        if (isContentUri(path)) {
            appContext.contentResolver.openOutputStream(Uri.parse(path), "w")
        } else {
            FileOutputStream(File(path))
        }
    } catch (e: Exception) {
        null
    }

    override fun createDir(parent: String, name: String): String? = try {
        if (isContentUri(parent)) {
            val parentDoc = DocumentFile.fromTreeUri(appContext, Uri.parse(parent)) ?: return null
            val existing = parentDoc.findFile(name)
            if (existing != null && existing.isDirectory) existing.uri.toString()
            else parentDoc.createDirectory(name)?.uri?.toString()
        } else {
            val dir = File(parent, name)
            if (dir.exists() && dir.isDirectory) dir.absolutePath
            else if (dir.mkdirs()) dir.absolutePath
            else null
        }
    } catch (e: Exception) {
        null
    }

    override fun findFile(parent: String, name: String): String? = try {
        if (isContentUri(parent)) {
            val parentDoc = DocumentFile.fromTreeUri(appContext, Uri.parse(parent)) ?: return null
            parentDoc.findFile(name)?.uri?.toString()
        } else {
            val f = File(parent, name)
            if (f.exists()) f.absolutePath else null
        }
    } catch (e: Exception) {
        null
    }

    override fun createFile(parent: String, mimeType: String, name: String): String? = try {
        if (isContentUri(parent)) {
            val parentDoc = DocumentFile.fromTreeUri(appContext, Uri.parse(parent)) ?: return null
            parentDoc.findFile(name)?.delete()
            parentDoc.createFile(mimeType, name)?.uri?.toString()
        } else {
            val f = File(parent, name)
            if (f.createNewFile()) f.absolutePath else null
        }
    } catch (e: Exception) {
        null
    }

    override fun deleteFile(path: String): Boolean = try {
        if (isContentUri(path)) {
            DocumentFile.fromSingleUri(appContext, Uri.parse(path))?.delete() ?: false
        } else {
            deleteRecursive(File(path))
        }
    } catch (e: Exception) {
        false
    }

    override fun size(path: String): Long = try {
        if (isContentUri(path)) {
            DocumentFile.fromSingleUri(appContext, Uri.parse(path))?.length() ?: 0L
        } else {
            File(path).length()
        }
    } catch (e: Exception) {
        0L
    }

    private fun isContentUri(path: String): Boolean = path.startsWith("content://")

    private fun deleteRecursive(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }
}
