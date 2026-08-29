package io.github.augustinavicius.nutrition.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.augustinavicius.nutrition.core.Format
import kotlin.math.roundToInt
import io.github.augustinavicius.nutrition.core.Nutrients
import io.github.augustinavicius.nutrition.ui.theme.CarbsColor
import io.github.augustinavicius.nutrition.ui.theme.FatColor
import io.github.augustinavicius.nutrition.ui.theme.ProteinColor

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
    }
}

/**
 * A ring showing energy eaten against the day's goal.
 *
 * Past 100 % the ring stays full and turns to the error colour rather than wrapping around,
 * because a second lap reads as "nearly done" at a glance — the opposite of the truth.
 */
@Composable
fun CalorieRing(
    consumed: Double,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (goal > 0) (consumed / goal).toFloat() else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "calorieRing")
    val over = fraction > 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val remaining = goal - consumed

    Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(168.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arc,
                startAngle = 135f,
                sweepAngle = 270f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Format.kcal(kotlin.math.abs(remaining)),
                style = MaterialTheme.typography.displaySmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (over) "kcal over" else "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${Format.kcal(consumed)} / $goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MacroBar(
    label: String,
    grams: Double,
    goalGrams: Int?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = goalGrams?.takeIf { it > 0 }?.let { (grams / it).toFloat() } ?: 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "macro-$label")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Whole grams only: three of these sit side by side, and a macro read to two
            // decimals is noise that pushes the label into wrapping.
            val eaten = grams.roundToInt()
            Text(
                text = goalGrams?.let { "$eaten / $it g" } ?: "$eaten g",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun MacroRow(
    nutrients: Nutrients,
    modifier: Modifier = Modifier,
    proteinGoal: Int? = null,
    carbsGoal: Int? = null,
    fatGoal: Int? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroBar("Protein", nutrients.protein, proteinGoal, ProteinColor, Modifier.weight(1f))
        MacroBar("Carbs", nutrients.carbs, carbsGoal, CarbsColor, Modifier.weight(1f))
        MacroBar("Fat", nutrients.fat, fatGoal, FatColor, Modifier.weight(1f))
    }
}

/** Compact "P 12 · C 30 · F 4" line used in dense list rows. */
@Composable
fun MacroSummaryText(nutrients: Nutrients, modifier: Modifier = Modifier) {
    Text(
        text = "P ${Format.amount(nutrients.protein)} · " +
            "C ${Format.amount(nutrients.carbs)} · " +
            "F ${Format.amount(nutrients.fat)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
