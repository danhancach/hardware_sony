package com.sonyericsson.soundenhancement.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.roundToInt

// Custom View bo can bang do hoa Dolby 10 dai tan chuan Material 3 Expressive
class DolbyGeqView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_GAIN = -5.0f
        const val MAX_GAIN = 5.0f
        val FREQUENCIES = arrayOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    }

    // Mang do loi 10 dai tan (dB, tu -5.0 den +5.0)
    private val bandGains = FloatArray(10) { 0.0f }

    private var selectedBand = -1
    private var isDragging = false

    var onGainsChangedListener: ((FloatArray) -> Unit)? = null

    // Mau sac Material 3
    private var colorPrimary: Int = Color.parseColor("#4C8DF6")
    private var colorSecondary: Int = Color.parseColor("#8E919A")
    private var colorTrack: Int = Color.parseColor("#383B46")
    private var colorZeroLine: Int = Color.parseColor("#60636D")
    private var colorText: Int = Color.parseColor("#C4C6D0")
    private var colorThumbBorder: Int = Color.parseColor("#2B2930")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val curveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valueBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val curvePath = Path()
    private val curveFillPath = Path()
    private val bandCentersX = FloatArray(10)

    init {
        resolveColors()

        trackPaint.apply {
            color = colorTrack
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(3f)
            strokeCap = Paint.Cap.ROUND
        }

        zeroLinePaint.apply {
            color = colorZeroLine
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(1f)
        }

        curvePaint.apply {
            color = colorPrimary
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = CornerPathEffect(dpToPx(16f))
        }

        curveFillPaint.apply {
            color = (colorPrimary and 0x00FFFFFF) or 0x22000000
            style = Paint.Style.FILL
            pathEffect = CornerPathEffect(dpToPx(16f))
        }

        thumbPaint.apply {
            color = colorPrimary
            style = Paint.Style.FILL
        }

        thumbBorderPaint.apply {
            color = colorThumbBorder
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(2f)
        }

        textPaint.apply {
            color = colorText
            textSize = dpToPx(11f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        valueBadgePaint.apply {
            color = colorPrimary
            textSize = dpToPx(11f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    private fun resolveColors() {
        // Doc token Material 3 dong theo theme Light/Dark va Monet hien tai
        colorPrimary = MaterialColors.getColor(
            this,
            AppCompatR.attr.colorPrimary,
            Color.parseColor("#4C8DF6")
        )
        colorSecondary = MaterialColors.getColor(
            this,
            MaterialR.attr.colorOnSurfaceVariant,
            Color.parseColor("#8E919A")
        )
        colorText = colorSecondary
        colorTrack = MaterialColors.getColor(
            this,
            MaterialR.attr.colorSurfaceContainerHighest,
            Color.parseColor("#383B46")
        )
        colorZeroLine = MaterialColors.getColor(
            this,
            MaterialR.attr.colorOutlineVariant,
            Color.parseColor("#60636D")
        )
        colorThumbBorder = MaterialColors.getColor(
            this,
            MaterialR.attr.colorSurfaceContainerHigh,
            Color.parseColor("#2B2930")
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resolveColors()
        trackPaint.color = colorTrack
        zeroLinePaint.color = colorZeroLine
        curvePaint.color = colorPrimary
        curveFillPaint.color = (colorPrimary and 0x00FFFFFF) or 0x22000000
        thumbPaint.color = colorPrimary
        thumbBorderPaint.color = colorThumbBorder
        textPaint.color = colorText
        valueBadgePaint.color = colorPrimary
    }

    fun getBandGains(): FloatArray {
        return bandGains.clone()
    }

    fun setBandGains(gains: FloatArray) {
        val size = minOf(gains.size, bandGains.size)
        for (i in 0 until size) {
            bandGains[i] = gains[i].coerceIn(MIN_GAIN, MAX_GAIN)
        }
        invalidate()
    }

    fun resetGains() {
        for (i in bandGains.indices) {
            bandGains[i] = 0.0f
        }
        invalidate()
        onGainsChangedListener?.invoke(bandGains.clone())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = paddingStart + dpToPx(16f)
        val paddingRight = paddingEnd + dpToPx(16f)
        val paddingTop = paddingTop + dpToPx(24f)
        val paddingBottom = paddingBottom + dpToPx(26f)

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom

        if (availableWidth <= 0 || availableHeight <= 0) return

        val centerY = paddingTop + (availableHeight / 2f)
        val topLimitY = paddingTop
        val bottomLimitY = paddingTop + availableHeight
        val halfRange = availableHeight / 2f

        // 1. Ve duong trung gian 0dB va duong bien do tham chieu
        canvas.drawLine(paddingLeft, centerY, width - paddingRight, centerY, zeroLinePaint)

        // 2. Tinh toan toa do X cua tung dai tan
        val stepX = availableWidth / (bandGains.size - 1)
        for (i in bandGains.indices) {
            bandCentersX[i] = paddingLeft + (i * stepX)
        }

        // 3. Ve track doc cho tung band
        for (i in bandGains.indices) {
            val cx = bandCentersX[i]
            canvas.drawLine(cx, topLimitY, cx, bottomLimitY, trackPaint)
        }

        // 4. Tao duong cong noi giua cac diem do loi (spline curve)
        curvePath.reset()
        curveFillPath.reset()

        val pointsY = FloatArray(bandGains.size)
        for (i in bandGains.indices) {
            // gain = 5.0 -> topLimitY; gain = -5.0 -> bottomLimitY
            val normalized = (bandGains[i] - MIN_GAIN) / (MAX_GAIN - MIN_GAIN)
            pointsY[i] = bottomLimitY - (normalized * availableHeight)
        }

        curvePath.moveTo(bandCentersX[0], pointsY[0])
        curveFillPath.moveTo(bandCentersX[0], centerY)
        curveFillPath.lineTo(bandCentersX[0], pointsY[0])

        for (i in 1 until bandGains.size) {
            val prevX = bandCentersX[i - 1]
            val prevY = pointsY[i - 1]
            val curX = bandCentersX[i]
            val curY = pointsY[i]

            val controlX1 = prevX + (curX - prevX) / 2f
            val controlY1 = prevY
            val controlX2 = prevX + (curX - prevX) / 2f
            val controlY2 = curY

            curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, curX, curY)
            curveFillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, curX, curY)
        }

        curveFillPath.lineTo(bandCentersX.last(), centerY)
        curveFillPath.close()

        canvas.drawPath(curveFillPath, curveFillPaint)
        canvas.drawPath(curvePath, curvePaint)

        // 5. Ve nut dieu khien (thumb) va nhan tan so cho tung band
        val thumbRadius = dpToPx(7f)
        val selectedRadius = dpToPx(9.5f)

        for (i in bandGains.indices) {
            val cx = bandCentersX[i]
            val cy = pointsY[i]
            val isCurrentSelected = (i == selectedBand && isDragging)

            val currentRadius = if (isCurrentSelected) selectedRadius else thumbRadius

            // Ve thumb
            canvas.drawCircle(cx, cy, currentRadius, thumbPaint)
            canvas.drawCircle(cx, cy, currentRadius, thumbBorderPaint)

            // Nhan tan so duoi chan
            val labelY = height - dpToPx(8f)
            canvas.drawText(FREQUENCIES[i], cx, labelY, textPaint)

            // Neu dang keo band nay, hien thi badge gia tri dB phia tren
            if (isCurrentSelected) {
                val dbVal = (bandGains[i] * 10f).roundToInt() / 10f
                val sign = if (dbVal > 0) "+" else ""
                val text = "$sign$dbVal dB"
                val badgeY = (cy - dpToPx(14f)).coerceAtLeast(dpToPx(16f))
                canvas.drawText(text, cx, badgeY, valueBadgePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val band = findClosestBand(event.x)
                if (band != -1) {
                    selectedBand = band
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateGainFromY(band, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedBand != -1) {
                    // Cho phep keo ngang chuyen dai tan linh hoat
                    val band = findClosestBand(event.x)
                    if (band != -1) {
                        selectedBand = band
                    }
                    updateGainFromY(selectedBand, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    selectedBand = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    onGainsChangedListener?.invoke(bandGains.clone())
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findClosestBand(touchX: Float): Int {
        var closest = -1
        var minDistance = Float.MAX_VALUE
        val maxTouchTolerance = dpToPx(36f)

        for (i in bandCentersX.indices) {
            val dist = abs(touchX - bandCentersX[i])
            if (dist < minDistance && dist <= maxTouchTolerance) {
                minDistance = dist
                closest = i
            }
        }
        return closest
    }

    private fun updateGainFromY(bandIndex: Int, touchY: Float) {
        val paddingTop = paddingTop + dpToPx(24f)
        val paddingBottom = paddingBottom + dpToPx(26f)
        val availableHeight = height - paddingTop - paddingBottom
        if (availableHeight <= 0) return

        val normalized = 1.0f - ((touchY - paddingTop) / availableHeight).coerceIn(0f, 1f)
        val rawGain = MIN_GAIN + (normalized * (MAX_GAIN - MIN_GAIN))

        // Lam tron den buoc 0.5 dB
        val rounded = (rawGain * 2f).roundToInt() / 2f
        val clamped = rounded.coerceIn(MIN_GAIN, MAX_GAIN)

        if (bandGains[bandIndex] != clamped) {
            bandGains[bandIndex] = clamped
            invalidate()
            onGainsChangedListener?.invoke(bandGains.clone())
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
