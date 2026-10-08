package dev.raiseexception.odin.reporting.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

private const val FULL_CIRCLE_DEGREES = 360f
private const val TOP_START_DEGREES = -90f
private const val RING_THICKNESS_DP = 22

data class DonutSlice(val share: BigDecimal, val color: Color)

@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, centerContent: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringThickness = RING_THICKNESS_DP.dp.toPx()
            val diameter = size.minDimension - ringThickness
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            var startAngle = TOP_START_DEGREES
            slices.forEach { slice ->
                val sweepAngle = slice.share.toFloat() * FULL_CIRCLE_DEGREES
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = ringThickness),
                )
                startAngle += sweepAngle
            }
        }
        centerContent()
    }
}
