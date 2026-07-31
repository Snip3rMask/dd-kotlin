package msr.mirudl.shared.model

/**
 * Anime search/browse result item.
 *
 * Mirrors the original `msr.mirudl.app.AnimeItem` (Java) field-for-field.
 * `@JvmField` keeps every property a plain public field on the JVM (so
 * existing Java call sites like `item.title = ...` keep working
 * unchanged), and `@JvmOverloads` generates a no-arg constructor overload
 * so `new AnimeItem()` still works from Java.
 *
 * `implements Serializable` was dropped on purpose: the original never
 * actually relied on it (fields are always extracted individually into
 * Intent extras, the whole object is never serialized) — see
 * MIGRATION_PLAN.md Phase 1.1. Dropping it also avoids a java.io.*
 * dependency in commonMain, which would break the iOS target later.
 */
data class AnimeItem @JvmOverloads constructor(
    @JvmField var id: String? = null,
    @JvmField var title: String? = null,
    @JvmField var thumbnail: String? = null,
    @JvmField var rating: String? = null,
    @JvmField var url: String? = null,
    @JvmField var type: String? = null,
    @JvmField var year: String? = null,
    @JvmField var status: String? = null,
    @JvmField var episodes: Int = 0
)
