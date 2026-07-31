package msr.mirudl.shared.model

/**
 * A single episode entry (search result / episode list row).
 *
 * Mirrors the original `msr.mirudl.app.EpisodeItem` (Java) field-for-field.
 * `@JvmField` keeps every property a plain public field on the JVM (so
 * existing Java call sites like `ep.selected = true` keep working
 * unchanged), and `@JvmOverloads` generates a no-arg constructor overload
 * so `new EpisodeItem()` still works from Java.
 *
 * `implements Serializable` (and the `transient` on `selected`) was
 * dropped on purpose: verified nothing actually serializes the whole
 * object (no `putExtra`/`getSerializableExtra` usage anywhere) — see
 * MIGRATION_PLAN.md Phase 1.2. Dropping it also avoids a java.io.*
 * dependency in commonMain, which would've broken the future iOS target.
 */
data class EpisodeItem @JvmOverloads constructor(
    @JvmField var id: Int = 0,
    @JvmField var number: Int = 0,
    @JvmField var number2: Int = 0,
    @JvmField var filler: Boolean = false,
    @JvmField var title: String? = null,
    @JvmField var embedUrl: String? = null,
    @JvmField var hlsUrl: String? = null,
    @JvmField var language: String? = null,
    @JvmField var langName: String? = null,
    @JvmField var selected: Boolean = false
) {
    fun getLabel(): String {
        if (number2 > 0 && number2 != number) {
            return "$number-$number2"
        }
        return number.toString()
    }
}
