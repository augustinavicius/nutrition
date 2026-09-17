package io.github.augustinavicius.nutrition.core

/**
 * A bundle of nutrition facts for some concrete amount of food.
 *
 * Optional nutrients are nullable rather than zero so "not reported by the source"
 * stays distinguishable from "genuinely contains none" — a distinction that matters
 * when summing a day: an unknown fibre value must not silently read as 0 g.
 */
data class Nutrients(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** The saturated share of [fat]; packaging states it as "of which saturates". */
    val satFat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodiumMg: Double? = null,
) {
    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        satFat = satFat?.times(factor),
        fiber = fiber?.times(factor),
        sugar = sugar?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
    )

    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        satFat = sumOrNull(satFat, other.satFat),
        fiber = sumOrNull(fiber, other.fiber),
        sugar = sumOrNull(sugar, other.sugar),
        sodiumMg = sumOrNull(sodiumMg, other.sodiumMg),
    )

    private companion object {
        fun sumOrNull(a: Double?, b: Double?): Double? =
            if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients(0.0, 0.0, 0.0, 0.0), Nutrients::plus)
