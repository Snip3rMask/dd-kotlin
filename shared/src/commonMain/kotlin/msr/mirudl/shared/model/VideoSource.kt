package msr.mirudl.shared.model

/**
 * A single playable/downloadable video quality+language variant.
 *
 * Mirrors the original `msr.mirudl.app.VideoSource` (Java) exactly:
 * - The 2-arg Java constructor `VideoSource(quality, url)` defaulted
 *   `language="jpn"`, `server="MiruDL"` — same defaults here via
 *   `@JvmOverloads`, so both the original 2-arg and 4-arg call sites in
 *   `HlsDownloader`/`MiruClient` keep compiling unchanged.
 * - `source` and `subtitleUrl` were verified unused everywhere except
 *   their own assignment in the original file (never read downstream) —
 *   kept as-is for exact parity rather than removed, per the
 *   no-behavior-change rule in MIGRATION_PLAN.md Phase 1.3. `source` is
 *   always `"primary"` in the original, same here.
 * - `implements Serializable` dropped — verified unused (no
 *   `putExtra`/`getSerializableExtra` anywhere for this type), and avoids
 *   a java.io.* dependency in commonMain that would break the iOS target
 *   later.
 */
data class VideoSource @JvmOverloads constructor(
    @JvmField var quality: String,
    @JvmField var url: String,
    @JvmField var language: String = "jpn",
    @JvmField var server: String = "MiruDL"
) {
    @JvmField
    var source: String = "primary"

    @JvmField
    var subtitleUrl: String? = null
}
