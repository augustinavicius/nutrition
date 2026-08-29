package io.github.augustinavicius.nutrition.update

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

/** api.github.com — release metadata. Asset bytes are fetched with raw OkHttp for progress. */
interface GitHubApi {

    @Headers(ACCEPT_JSON, API_VERSION)
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GhRelease

    @Headers(ACCEPT_JSON, API_VERSION)
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GhRelease>

    @Headers(ACCEPT_JSON, API_VERSION)
    @GET("user")
    suspend fun user(@Header("Authorization") authorization: String): GhUser

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val ACCEPT_JSON = "Accept: application/vnd.github+json"
        const val API_VERSION = "X-GitHub-Api-Version: 2022-11-28"
    }
}

/** github.com — the OAuth device flow lives here rather than on the API host. */
interface GitHubOAuthApi {

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String,
    ): GhDeviceCode

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = GRANT_TYPE,
    ): GhAccessToken

    companion object {
        const val BASE_URL = "https://github.com/"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

        /** Private release assets need repo-scoped read access. */
        const val SCOPE = "repo"
    }
}
