package msr.mirudl.shared.network

import kotlinx.coroutines.runBlocking
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.EpisodeItem
import msr.mirudl.shared.model.VideoSource

/**
 * Synchronous JVM bridge for the shared [MiruClient] — wraps every
 * suspend function with [runBlocking] so existing Java callers in
 * `app` work unchanged.
 *
 * Delete once all callers convert to Kotlin coroutines (Phase 5).
 */
object MiruClientAndroid {
    @JvmStatic val BASE: String get() = MiruClient.BASE

    @JvmStatic fun search(query: String): List<AnimeItem> =
        runBlocking { MiruClient.search(query) }

    @JvmStatic fun browseCurrentlyAiring(): List<AnimeItem> =
        runBlocking { MiruClient.browseCurrentlyAiring() }

    @JvmStatic fun getEpisodes(animeId: String): List<EpisodeItem> =
        runBlocking { MiruClient.getEpisodes(animeId) }

    @JvmStatic fun getEpisodesWithSeasons(animeId: String): List<EpisodeItem> =
        runBlocking { MiruClient.getEpisodesWithSeasons(animeId) }

    @JvmStatic fun getEpisodeLanguages(episodeId: Int): List<VideoSource> =
        runBlocking { MiruClient.getEpisodeLanguages(episodeId) }

    @JvmStatic fun resolveHlsFromEmbed(embedUrl: String): String? =
        runBlocking { MiruClient.resolveHlsFromEmbed(embedUrl) }

    @JvmStatic fun getQualities(masterUrl: String): List<VideoSource> =
        runBlocking { MiruClient.getQualities(masterUrl) }

    @JvmStatic fun getAnimeTitle(animeId: String): String? =
        runBlocking { MiruClient.getAnimeTitle(animeId) }
}
