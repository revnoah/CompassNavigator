package com.revnoah.compassnavigator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val declinationDegrees: Float? = null,
    val provider: String? = null,
    val message: String? = null
)

class CompassView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 17, 23) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(219, 225, 232)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(142, 154, 168)
        strokeCap = Paint.Cap.ROUND
    }
    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 28f * density
    }
    private val subcardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(174, 185, 198)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 15f * density
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 54f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(167, 180, 194)
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 17f * density
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(177, 189, 201)
        textAlign = Paint.Align.LEFT
        textSize = 13f * density
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 80, 72)
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val southNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(73, 156, 233)
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 32, 41) }
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(53, 66, 80)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private var magneticHeading = 0f
    private var trueHeading = 0f
    private var hasHeading = false
    private var sensorMessage = "Acquiring compass"
    private var locationState = LocationState(message = "Acquiring geographic info")

    fun setHeading(magnetic: Float, geographic: Float) {
        magneticHeading = magnetic
        trueHeading = geographic
        hasHeading = true
        sensorMessage = "Magnetic heading"
        invalidate()
    }

    fun setSensorState(message: String) {
        sensorMessage = message
        invalidate()
    }

    fun setLocationState(state: LocationState) {
        locationState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val topInset = 76f * density
        val dialRadius = min(width * 0.42f, (height - 260f * density) * 0.32f).coerceAtLeast(120f * density)
        val cx = width / 2f
        val cy = topInset + dialRadius + 34f * density

        drawDial(canvas, cx, cy, dialRadius)
        drawHeadingReadout(canvas, cx, cy, dialRadius)
        drawInfoPanel(canvas, cx, cy + dialRadius + 46f * density)
    }

    private fun drawDial(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.68f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 32)
            style = Paint.Style.FILL
        })

        val rotationOffset = if (hasHeading) magneticHeading else 0f
        for (degree in 0 until 360 step 5) {
            val major = degree % 30 == 0
            val cardinal = degree % 90 == 0
            tickPaint.strokeWidth = when {
                cardinal -> 3.5f * density
                major -> 2.2f * density
                else -> 1.2f * density
            }
            val angle = Math.toRadians((degree - rotationOffset - 90f).toDouble())
            val outer = radius - 8f * density
            val inner = radius - when {
                cardinal -> 28f * density
                major -> 21f * density
                else -> 14f * density
            }
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                tickPaint
            )
        }

        val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        labels.forEach { (label, degree) ->
            val angle = Math.toRadians((degree - rotationOffset - 90f).toDouble())
            val paint = if (label == "N") cardinalPaint.apply { color = Color.rgb(235, 80, 72) } else cardinalPaint.apply { color = Color.WHITE }
            canvas.drawText(
                label,
                cx + cos(angle).toFloat() * (radius - 52f * density),
                cy + sin(angle).toFloat() * (radius - 52f * density) + 10f * density,
                paint
            )
        }

        val sublabels = listOf("NE" to 45f, "SE" to 135f, "SW" to 225f, "NW" to 315f)
        sublabels.forEach { (label, degree) ->
            val angle = Math.toRadians((degree - rotationOffset - 90f).toDouble())
            canvas.drawText(
                label,
                cx + cos(angle).toFloat() * (radius - 53f * density),
                cy + sin(angle).toFloat() * (radius - 53f * density) + 5f * density,
                subcardinalPaint
            )
        }

        canvas.drawLine(cx, cy, cx, cy - radius * 0.55f, needlePaint)
        canvas.drawLine(cx, cy, cx, cy + radius * 0.42f, southNeedlePaint)
        canvas.drawCircle(cx, cy, 8f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(cx, cy, 3f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 17, 23) })
    }

    private fun drawHeadingReadout(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val heading = if (hasHeading) "${magneticHeading.roundToInt().floorMod(360)}°" else "--°"
        canvas.drawText(heading, cx, cy + 18f * density, headingPaint)
        canvas.drawText(directionName(magneticHeading), cx, cy + 44f * density, labelPaint)
        canvas.drawText(sensorMessage, cx, cy + radius + 30f * density, labelPaint)
    }

    private fun drawInfoPanel(canvas: Canvas, cx: Float, top: Float) {
        val horizontalMargin = 18f * density
        val panelWidth = width - horizontalMargin * 2f
        val panelHeight = min(250f * density, height - top - 18f * density).coerceAtLeast(170f * density)
        val left = horizontalMargin
        val rect = RectF(left, top, left + panelWidth, top + panelHeight)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, panelPaint)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, panelStrokePaint)

        var y = top + 32f * density
        drawRow(canvas, left + 18f * density, y, "MAGNETIC", "${magneticHeading.roundToInt().floorMod(360)}° ${directionName(magneticHeading)}")
        y += 38f * density
        drawRow(canvas, left + 18f * density, y, "GEOGRAPHIC", if (hasHeading) "${trueHeading.roundToInt().floorMod(360)}° true" else "--")
        y += 38f * density

        val state = locationState
        if (state.latitude != null && state.longitude != null) {
            drawRow(canvas, left + 18f * density, y, "LAT / LON", "%.5f, %.5f".format(state.latitude, state.longitude))
            y += 38f * density
            val altitude = state.altitudeMeters?.let { "%.0f m".format(it) } ?: "unknown altitude"
            val accuracy = state.accuracyMeters?.let { " +/- %.0f m".format(it) } ?: ""
            drawRow(canvas, left + 18f * density, y, "ALT / ACCURACY", altitude + accuracy)
            y += 38f * density
            val declination = state.declinationDegrees?.let { "%+.1f°".format(it) } ?: "--"
            drawRow(canvas, left + 18f * density, y, "DECLINATION", "$declination magnetic to true")
            y += 34f * density
            canvas.drawText("Source: ${state.provider}", left + 18f * density, y, smallPaint)
        } else {
            val message = state.message ?: "Acquiring geographic info"
            drawTextLeftFit(canvas, message, left + 18f * density, width - 36f * density, y + 2f * density, valuePaint)
            y += 34f * density
            drawTextLeftFit(
                canvas,
                "Grant location access to show coordinates and true north.",
                left + 18f * density,
                width - 36f * density,
                y,
                smallPaint
            )
        }
    }

    private fun drawRow(canvas: Canvas, x: Float, y: Float, label: String, value: String) {
        smallPaint.color = Color.rgb(145, 158, 172)
        canvas.drawText(label, x, y, smallPaint)
        drawTextRightFit(canvas, value, x + 112f * density, width - 36f * density, y, valuePaint)
    }

    private fun drawTextRightFit(canvas: Canvas, text: String, left: Float, right: Float, y: Float, paint: Paint) {
        val originalSize = paint.textSize
        val originalAlign = paint.textAlign
        val maxWidth = (right - left).coerceAtLeast(40f * density)
        while (paint.measureText(text) > maxWidth && paint.textSize > 11f * density) {
            paint.textSize -= 1f * density
        }
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, right, y, paint)
        paint.textAlign = originalAlign
        paint.textSize = originalSize
    }

    private fun drawTextLeftFit(canvas: Canvas, text: String, left: Float, right: Float, y: Float, paint: Paint) {
        val originalSize = paint.textSize
        val originalAlign = paint.textAlign
        val maxWidth = (right - left).coerceAtLeast(40f * density)
        while (paint.measureText(text) > maxWidth && paint.textSize > 10f * density) {
            paint.textSize -= 1f * density
        }
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, left, y, paint)
        paint.textAlign = originalAlign
        paint.textSize = originalSize
    }

    private fun directionName(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((degrees / 45f) + 0.5f).toInt().floorMod(directions.size)
        return directions[index]
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}
