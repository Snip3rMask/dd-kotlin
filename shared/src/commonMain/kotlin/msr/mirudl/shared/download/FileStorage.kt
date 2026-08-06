package msr.mirudl.shared.download

import java.io.InputStream
import java.io.OutputStream

/**
 * Platform-agnostic file storage for the download engine — a port of the
 * file helpers inside `app`'s `HlsDownloader.java` (temp/cache dir +
 * SAF document writing).
 *
 * Two kinds of "path" are supported:
 * - absolute local file paths (cache/temp dir, segment files), and
 * - platform document URIs kept as strings (Android SAF content URIs;
 *   other platforms define their own semantics in Phase 7/8).
 *
 * No implementation is wired up yet — callers arrive in 4.5/4.6.
 */
interface FileStorage {

    /** Absolute path of the app's cache directory (temp segment files). */
    fun cacheDir(): String

    /** Opens an input stream for a local file path or document URI, or null. */
    fun openInput(path: String): InputStream?

    /** Opens an output stream (truncate) for a local file path or document URI, or null. */
    fun openOutput(path: String): OutputStream?

    /** Creates directory `name` under `parent`, or returns the existing one; null on failure. */
    fun createDir(parent: String, name: String): String?

    /** Returns the child `name` under `parent` if it exists, else null. */
    fun findFile(parent: String, name: String): String?

    /** Creates a new file under `parent`; returns its path/URI, or null on failure. */
    fun createFile(parent: String, mimeType: String, name: String): String?

    /** Recursively deletes a local file/dir or a document; true on success. */
    fun deleteFile(path: String): Boolean

    /** Size in bytes of a local file or document; 0 if missing. */
    fun size(path: String): Long
}
