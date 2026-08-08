package msr.mirudl.shared.download

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop (JVM) implementation of [FileStorage] — plain `java.io.File`
 * for everything (no SAF on desktop).
 *
 * The cache directory lives under the user's home folder so temp
 * segment files survive app restarts but can be cleaned up manually
 * if needed.
 */
object DesktopFileStorage : FileStorage {

    private val baseDir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".mirudl").apply { mkdirs() }
    }

    override fun cacheDir(): String {
        val cache = File(baseDir, "cache")
        cache.mkdirs()
        return cache.absolutePath
    }

    override fun openInput(path: String): InputStream? = try {
        FileInputStream(File(path))
    } catch (e: Exception) {
        null
    }

    override fun openOutput(path: String): OutputStream? = try {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file)
    } catch (e: Exception) {
        null
    }

    override fun createDir(parent: String, name: String): String? = try {
        val dir = File(parent, name)
        if (dir.exists() && dir.isDirectory) dir.absolutePath
        else if (dir.mkdirs()) dir.absolutePath
        else null
    } catch (e: Exception) {
        null
    }

    override fun findFile(parent: String, name: String): String? {
        val f = File(parent, name)
        return if (f.exists()) f.absolutePath else null
    }

    override fun createFile(parent: String, mimeType: String, name: String): String? = try {
        val f = File(parent, name)
        if (f.createNewFile()) f.absolutePath else null
    } catch (e: Exception) {
        null
    }

    override fun deleteFile(path: String): Boolean {
        return deleteRecursive(File(path))
    }

    override fun size(path: String): Long {
        val f = File(path)
        return if (f.exists()) f.length() else 0L
    }

    private fun deleteRecursive(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }
}
