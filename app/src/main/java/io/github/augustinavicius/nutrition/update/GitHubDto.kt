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

    /** Release notes with the machine-readable marker line stripped out. */
    val notes: String
        get() = body.orEmpty().lineSequence()
            .filterNot { BODY_VERSION_CODE.containsMatchIn(it) }
            .joinToString("\n")
            .trim()
}

/** The release workflow stamps this marker into every release body. */
private val BODY_VERSION_CODE = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
private val TRAILING_NUMBER = Regex("""(\d+)\s*$""")

@Serializable
data class GhAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
)

@Serializable
data class GhUser(
    val login: String = "",
    val name: String? = null,
)

@Serializable
data class GhDeviceCode(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String = "https://github.com/login/device",
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
data class GhAccessToken(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val interval: Int? = null,
)
