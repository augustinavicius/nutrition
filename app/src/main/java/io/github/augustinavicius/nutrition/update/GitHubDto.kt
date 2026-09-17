package io.github.augustinavicius.nutrition.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhRelease(
    val id: Long = 0,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GhAsset> = emptyList(),
) {
    val apkAsset: GhAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    /**
     * The versionCode this release carries.
     *
     * The release workflow writes a `versionCode: N` line into the release body, which is
     * the authoritative source. Tag names are used only as a fallback so a release made by
     * hand still works.
     */
    val versionCode: Int?
        get() = body?.let { BODY_VERSION_CODE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: TRAILING_NUMBER.find(tagName)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The channel this release was cut for.
     *
     * The workflow stamps `channel: <id>` into the body alongside the version marker. A
     * release made by hand carries no marker, so GitHub's own pre-release flag decides:
     * that is what the workflow sets the two apart with anyway.
     */
    val channel: UpdateChannel
        get() = BODY_CHANNEL.find(body.orEmpty())?.groupValues?.get(1)
            ?.let(UpdateChannel::fromId)
            ?: if (prerelease) UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE

    /**
     * Whether this release is offered to someone following [channel].
     *
     * Development follows everything: it is the channel for people who want the newest build,
     * and a stable release is newer than the development one that preceded it.
     */
    fun isOn(followed: UpdateChannel): Boolean =
        followed == UpdateChannel.DEVELOPMENT || channel == UpdateChannel.STABLE

    /** Release notes with the machine-readable marker lines stripped out. */
    val notes: String
        get() = body.orEmpty().lineSequence()
            .filterNot { BODY_VERSION_CODE.containsMatchIn(it) || BODY_CHANNEL.containsMatchIn(it) }
            .joinToString("\n")
            .trim()
}

/** The release workflow stamps these markers into every release body. */
private val BODY_VERSION_CODE = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
private val BODY_CHANNEL = Regex("""channel\s*[:=]\s*([A-Za-z]+)""", RegexOption.IGNORE_CASE)
private val TRAILING_NUMBER = Regex("""(\d+)\s*$""")

@Serializable
data class GhAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
)
