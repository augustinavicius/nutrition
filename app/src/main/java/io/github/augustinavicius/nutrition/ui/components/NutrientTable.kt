package io.github.augustinavicius.nutrition.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.augustinavicius.nutrition.core.Format
import io.github.augustinavicius.nutrition.core.Nutrients

@Composable
fun NutrientTable(nutrients: Nutrients, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NutrientRow("Energy", "${Format.kcal(nutrients.kcal)} kcal", emphasise = true)
        NutrientRow("Fat", Format.grams(nutrients.fat))
        nutrients.satFat?.let { NutrientRow("of which saturates", Format.grams(it), indented = true) }
        NutrientRow("Carbohydrate", Format.grams(nutrients.carbs))
        nutrients.sugar?.let { NutrientRow("of which sugars", Format.grams(it), indented = true) }
        nutrients.fiber?.let { NutrientRow("Fibre", Format.grams(it)) }
        NutrientRow("Protein", Format.grams(nutrients.protein))
        nutrients.sodiumMg?.let { NutrientRow("Sodium", Format.milligrams(it)) }
    }
}

@Composable
fun NutrientRow(
    label: String,
    value: String,
    emphasise: Boolean = false,
    indented: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = if (indented) "    $label" else label,
            style = if (emphasise) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (indented) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = value,
            style = if (emphasise) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasise) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
