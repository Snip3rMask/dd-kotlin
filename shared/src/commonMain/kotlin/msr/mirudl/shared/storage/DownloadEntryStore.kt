package msr.mirudl.shared.storage

import kotlinx.serialization.Serializable
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
}
