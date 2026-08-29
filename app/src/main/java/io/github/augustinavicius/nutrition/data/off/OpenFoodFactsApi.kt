package io.github.augustinavicius.nutrition.data.off

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String,
    ): OffProductResponse

    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") terms: String,
        @Query("page_size") pageSize: Int,
        @Query("page") page: Int,
        @Query("fields") fields: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        /** Asking for only what the app maps keeps responses small on mobile data. */
        const val FIELDS =
            "code,product_name,product_name_en,generic_name,brands,quantity," +
                "image_front_small_url,image_small_url,nutriments"
    }
}
