package io.github.augustinavicius.nutrition.data.repo

import android.util.Log
import io.github.augustinavicius.nutrition.core.Food
import io.github.augustinavicius.nutrition.core.FoodSource
import io.github.augustinavicius.nutrition.data.db.FoodDao
import io.github.augustinavicius.nutrition.data.db.SeedFoods
import io.github.augustinavicius.nutrition.data.db.toDomain
import io.github.augustinavicius.nutrition.data.db.toEntity
import io.github.augustinavicius.nutrition.data.off.OffProduct
import io.github.augustinavicius.nutrition.data.off.OpenFoodFactsApi
import io.github.augustinavicius.nutrition.data.off.toFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException

/** What a scanned barcode turned out to be. */
sealed interface BarcodeResult {
    /** Ready to log: found locally, or fetched from Open Food Facts with usable nutrition. */
    data class Found(val food: Food, val fromCache: Boolean) : BarcodeResult

    /** The product exists upstream but has no nutrition facts, so the user must fill them in. */
    data class NeedsDetails(val draft: Food) : BarcodeResult

    data class Unknown(val barcode: String) : BarcodeResult

    data class Error(val barcode: String, val message: String) : BarcodeResult
}

class FoodRepository(
    private val dao: FoodDao,
    private val api: OpenFoodFactsApi,
) {

    fun observeRecent(limit: Int = 30): Flow<List<Food>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun searchLocal(query: String): Flow<List<Food>> =
        dao.search(query.trim()).map { list -> list.map { it.toDomain() } }

    fun observeFood(id: Long): Flow<Food?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun food(id: Long): Food? = dao.byId(id)?.toDomain()

    suspend fun save(food: Food): Long {
        val existingId = food.barcode
            ?.let { dao.byBarcode(it) }
            ?.id
            ?.takeIf { food.id == 0L }
        val toStore = if (existingId != null) food.copy(id = existingId) else food
        return dao.upsert(toStore.toEntity())
    }

    suspend fun delete(food: Food) = dao.delete(food.toEntity())

    suspend fun markUsed(id: Long) = dao.markUsed(id)

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /**
     * Resolves a scanned barcode: the local cache first (instant and works offline), then
     * Open Food Facts. Products fetched from the network are cached so the next scan of the
     * same item needs no round trip.
     */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        dao.byBarcode(barcode)?.let { return BarcodeResult.Found(it.toDomain(), fromCache = true) }

        val product: OffProduct? = try {
            val response = api.product(barcode, OpenFoodFactsApi.FIELDS)
            if (response.status != 1) null else response.product
        } catch (e: HttpException) {
            if (e.code() == 404) null else return BarcodeResult.Error(barcode, httpMessage(e))
        } catch (e: IOException) {
            return BarcodeResult.Error(barcode, "No connection. Check your network and try again.")
        } catch (e: Exception) {
            Log.w(TAG, "Barcode lookup failed", e)
            return BarcodeResult.Error(barcode, e.message ?: "Lookup failed")
        }

        val food = product?.toFood() ?: return BarcodeResult.Unknown(barcode)

        return if (product.nutriments.hasAnyData && food.perServing.kcal > 0) {
            val id = save(food)
            BarcodeResult.Found(food.copy(id = id), fromCache = false)
        } else {
            BarcodeResult.NeedsDetails(food)
        }
    }

    /** Text search against Open Food Facts. Local results are handled separately by [searchLocal]. */
    suspend fun searchRemote(query: String, page: Int = 1, pageSize: Int = 25): Result<List<Food>> = try {
        val response = api.search(query.trim(), pageSize, page, OpenFoodFactsApi.FIELDS)
        Result.success(
            response.products
                .mapNotNull { it.toFood() }
                .filter { it.perServing.kcal > 0 }
                .distinctBy { it.barcode }
        )
    } catch (e: IOException) {
        Result.failure(IOException("No connection. Check your network and try again.", e))
    } catch (e: HttpException) {
        Result.failure(IOException(httpMessage(e), e))
    } catch (e: Exception) {
        Log.w(TAG, "Remote search failed", e)
        Result.failure(e)
    }

    /** Populates the starter pantry the first time the app runs. */
    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        dao.insertAll(SeedFoods.all())
    }

    /** Caches a food that came from search results so it can be logged and reused offline. */
    suspend fun cacheRemote(food: Food): Long {
        if (food.id != 0L) return food.id
        food.barcode?.let { code -> dao.byBarcode(code)?.let { return it.id } }
        return dao.upsert(food.copy(source = FoodSource.OPEN_FOOD_FACTS).toEntity())
    }

    private fun httpMessage(e: HttpException): String = when (e.code()) {
        in 500..599 -> "Open Food Facts is having trouble (HTTP ${e.code()}). Try again shortly."
        429 -> "Too many requests — wait a moment and try again."
        else -> "Request failed (HTTP ${e.code()})."
    }

    private companion object {
        const val TAG = "FoodRepository"
    }
}
