package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProgressRing(
    progress: Float, // 0f to 1f
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "ProgressRing"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sizeMin = size.minDimension
            val radius = (sizeMin - strokeWidth.toPx()) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background
            drawCircle(
                color = backgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth.toPx())
            )

            // Foreground path
            val sweepAngle = animatedProgress * 360f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun ModernBarChart(
    data: List<Float>, // Values
    labels: List<String>, // Labels
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedValues = data.map { valState ->
        animateFloatAsState(targetValue = valState, animationSpec = tween(800), label = "BarValue")
    }

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val paddingLeft = 40.dp.toPx()
        val paddingBottom = 24.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingRight = 16.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        // Draw background grid lines (horizontal)
        val gridLines = 4
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            val y = paddingTop + chartHeight * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            // Value labels
            val textLayoutResult = textMeasurer.measure(
                text = String.format("%.1f", ratio * maxVal),
                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.6f))
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(8.dp.toPx(), y - textLayoutResult.size.height / 2)
            )
        }

        // Draw Bars
        val barCount = data.size
        val spacing = chartWidth / (barCount * 2)
        val barWidth = chartWidth / barCount - spacing

        for (i in 0 until barCount) {
            val animVal = animatedValues[i].value
            val barHeight = chartHeight * (animVal / maxVal)
            val x = paddingLeft + spacing / 2 + i * (barWidth + spacing)
            val y = paddingTop + chartHeight - barHeight

            // Rounded rectangle bar
            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(
                            offset = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        ),
                        topLeft = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        topRight = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        bottomLeft = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        bottomRight = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                )
            }

            // Draw bar with gradient
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.6f))
                )
            )

            // Draw label
            if (i < labels.size) {
                val labelLayout = textMeasurer.measure(
                    text = labels[i],
                    style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = textColor)
                )
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(x + barWidth / 2 - labelLayout.size.width / 2, size.height - paddingBottom + 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun ModernLineChart(
    data: List<Float>, // Values
    labels: List<String>, // Labels
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
    gridColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "LineChartAnim"
    )

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val maxVal = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val paddingLeft = 40.dp.toPx()
        val paddingBottom = 24.dp.toPx()
        val paddingTop = 16.dp.toPx()
        val paddingRight = 16.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        // Draw horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            val y = paddingTop + chartHeight * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            val textLayoutResult = textMeasurer.measure(
                text = String.format("%.1f", ratio * maxVal),
                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.6f))
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(8.dp.toPx(), y - textLayoutResult.size.height / 2)
            )
        }

        // Draw Line & Gradient Area
        val points = mutableListOf<Offset>()
        val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

        for (i in data.indices) {
            val x = paddingLeft + i * stepX
            val y = paddingTop + chartHeight * (1f - (data[i] / maxVal))
            points.add(Offset(x, y))
        }

        if (points.isNotEmpty()) {
            val linePath = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val pPrev = points[i - 1]
                    val pCurr = points[i]
                    // Cubic bezier smoothing
                    val controlX1 = pPrev.x + (pCurr.x - pPrev.x) / 2
                    val controlY1 = pPrev.y
                    val controlX2 = pPrev.x + (pCurr.x - pPrev.x) / 2
                    val controlY2 = pCurr.y
                    cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                }
            }

            // Draw gradient below line
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, paddingTop + chartHeight)
                lineTo(points.first().x, paddingTop + chartHeight)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
                )
            )

            // Draw line
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw data points & values
            for (i in points.indices) {
                val point = points[i]
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = point
                )

                // Label at bottom
                if (i < labels.size) {
                    val labelLayout = textMeasurer.measure(
                        text = labels[i],
                        style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = textColor)
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(point.x - labelLayout.size.width / 2, size.height - paddingBottom + 4.dp.toPx())
                    )
                }
            }
        }
    }
}
