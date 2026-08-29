package io.github.augustinavicius.nutrition.data

import io.github.augustinavicius.nutrition.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Open Food Facts asks every client to identify itself so they can contact abusive
     * callers instead of blocking the whole app; GitHub simply logs it.
     */
    private val userAgent = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "NutritionTracker/${BuildConfig.VERSION_NAME} " +
                        "(Android; +https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO})",
                )
                .build()
        )
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgent)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
