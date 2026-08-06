package msr.mirudl.shared.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import msr.mirudl.shared.model.DownloadRecord

object DownloadEntryStore {

    private const val KEY = "entries"

    @Serializable
    private data class JsonRecord(
        val uri: String = "",
        val filePath: String = "",
        val title: String = "",
        val parent: String = "",
        val size: Long = 0L,
        val completedAt: Long = 0L
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun all(storage: AppStorage): List<DownloadRecord> {
        val raw = storage.getString(KEY, "[]")
        return try {
            val arr = json.decodeFromString<List<JsonRecord>>(raw)
            arr.map { j ->
                DownloadRecord(
                    uri = j.uri.ifEmpty { null },
                    filePath = j.filePath.ifEmpty { null },
                    title = j.title.ifEmpty { null },
                    parent = j.parent.ifEmpty { null },
                    size = j.size,
                    completedAt = j.completedAt
                )
            }.sortedByDescending { it.completedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(storage: AppStorage, entry: DownloadRecord?) {
        if (entry == null) return
        val items = all(storage).toMutableList()
        val key = entry.key()
        val idx = items.indexOfFirst { it.key() == key }
        if (idx >= 0) items[idx] = entry else items.add(0, entry)
        save(storage, items)
    }

    fun remove(storage: AppStorage, entry: DownloadRecord?) {
        if (entry == null) return
        val key = entry.key()
        val out = all(storage).filter { it.key() != key }
        save(storage, out)
    }

    fun removeAll(storage: AppStorage, entries: List<DownloadRecord>?) {
        if (entries.isNullOrEmpty()) return
        val keys = entries.mapNotNull { it.key() }.toSet()
        val out = all(storage).filter { it.key() !in keys }
        save(storage, out)
    }

    private fun save(storage: AppStorage, items: List<DownloadRecord>) {
        val arr = items.map { e ->
            JsonRecord(
                uri = e.uri ?: "",
                filePath = e.filePath ?: "",
                title = e.title ?: "",
                parent = e.parent ?: "",
                size = e.size,
                completedAt = e.completedAt
            )
        }
        storage.setString(KEY, json.encodeToString(arr))
    }
}
