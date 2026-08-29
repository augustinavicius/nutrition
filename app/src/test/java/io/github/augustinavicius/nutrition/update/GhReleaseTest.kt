package io.github.augustinavicius.nutrition.update

import io.github.augustinavicius.nutrition.data.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GhReleaseTest {

    @Test
    fun `version code is read from the marker the workflow writes`() {
        val release = GhRelease(tagName = "v1.0.7", body = "versionCode: 42\n\nFixed the scanner.")
        assertEquals(42, release.versionCode)
    }

    @Test
    fun `the marker wins over the tag`() {
        val release = GhRelease(tagName = "v1.0.7", body = "versionCode: 42")
        assertEquals(42, release.versionCode)
    }

    @Test
    fun `a hand-made release falls back to the trailing number in the tag`() {
        assertEquals(7, GhRelease(tagName = "v1.0.7").versionCode)
        assertEquals(19, GhRelease(tagName = "release-19", body = "no marker here").versionCode)
    }

    @Test
    fun `a tag with no number at all reports no version`() {
        assertNull(GhRelease(tagName = "nightly").versionCode)
    }

    @Test
    fun `release notes hide the machine-readable marker`() {
        val release = GhRelease(tagName = "v1.0.7", body = "versionCode: 42\n\nFixed the scanner.\n")
        assertEquals("Fixed the scanner.", release.notes)
    }

    @Test
    fun `the apk asset is picked out of the attachments`() {
        val release = GhRelease(
            tagName = "v1.0.7",
            assets = listOf(
                GhAsset(id = 1, name = "mapping.txt", size = 10),
                GhAsset(id = 2, name = "nutrition-1.0.7.APK", size = 20),
            ),
        )
        assertEquals(2L, release.apkAsset!!.id)
    }

    @Test
    fun `a release with no apk offers nothing to install`() {
        assertNull(GhRelease(tagName = "v1", assets = listOf(GhAsset(name = "notes.txt"))).apkAsset)
    }

    @Test
    fun `a real releases payload decodes with the production json settings`() {
        val payload = """
            {
              "id": 12345,
              "tag_name": "v1.0.42",
              "name": "1.0.42",
              "body": "versionCode: 42\nBuilt from commit abc1234.",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-08-29T09:00:00Z",
              "html_url": "https://github.com/owner/repo/releases/tag/v1.0.42",
              "unmapped_field": 1,
              "assets": [
                {
                  "id": 987,
                  "name": "nutrition-1.0.42.apk",
                  "size": 24591010,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/owner/repo/releases/download/v1.0.42/x.apk"
                }
              ]
            }
        """.trimIndent()

        val release = Network.json.decodeFromString(GhRelease.serializer(), payload)
        assertEquals(42, release.versionCode)
        assertEquals(987L, release.apkAsset!!.id)
        assertEquals(24591010L, release.apkAsset!!.size)
        assertEquals("Built from commit abc1234.", release.notes)
    }

    @Test
    fun `a device flow error response decodes without an access token`() {
        val payload = """{"error":"authorization_pending","error_description":"Pending"}"""
        val token = Network.json.decodeFromString(GhAccessToken.serializer(), payload)
        assertNull(token.accessToken)
        assertEquals("authorization_pending", token.error)
    }
}
