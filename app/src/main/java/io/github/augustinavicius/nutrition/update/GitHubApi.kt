package io.github.augustinavicius.nutrition.update

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * api.github.com — release metadata.
 *
 * The repository is public, so these are unauthenticated calls: no token, no sign-in, nothing
 * to store on the device. Anonymous requests are rate-limited per IP (60/hour), which is far
 * more than a periodic update check needs. Asset bytes are fetched with raw OkHttp so the
 * download can report progress.
 */
interface GitHubApi {

    /**
     * Newest releases first, both channels together — the caller filters to the one it wants.
     *
     * `releases/latest` is deliberately not used: it hides pre-releases, which is exactly
     * where the development channel lives, and it 404s on a repository that has only those.
     */
    @Headers(ACCEPT_JSON, API_VERSION)
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = DEFAULT_PAGE_SIZE,
    ): List<GhRelease>

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val ACCEPT_JSON = "Accept: application/vnd.github+json"
        const val API_VERSION = "X-GitHub-Api-Version: 2022-11-28"

        /** Enough history to find the newest release on either channel in one request. */
        const val DEFAULT_PAGE_SIZE = 30
    }
}
