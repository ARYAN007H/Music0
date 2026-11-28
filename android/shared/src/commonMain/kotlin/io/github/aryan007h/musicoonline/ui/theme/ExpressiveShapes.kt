package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure corner-radius specifications for Material 3 Expressive segmented list cards
 * and connected button groups, decoupled from Compose for deterministic testing.
 */
data class SegmentedCornerSpec(
    val topStartDp: Float,
    val topEndDp: Float,
    val bottomEndDp: Float,
    val bottomStartDp: Float,
) {
    fun toRoundedCornerShape(): RoundedCornerShape = RoundedCornerShape(
        topStart = topStartDp.dp,
        topEnd = topEndDp.dp,
        bottomEnd = bottomEndDp.dp,
        bottomStart = bottomStartDp.dp,
    )
}

object ExpressiveSegmentedMath {
    /**
     * Vertical M3 Expressive segmented list item corners (Android 16 / Pixel Settings style):
     * - Single item: all corners [outerDp]
     * - First item: top [outerDp], bottom [innerDp]
     * - Middle item: all corners [innerDp]
     * - Last item: top [innerDp], bottom [outerDp]
     */
    fun verticalListCorners(
        index: Int,
        count: Int,
        outerDp: Float = 24f,
        innerDp: Float = 6f,
    ): SegmentedCornerSpec {
        if (count <= 1) return SegmentedCornerSpec(outerDp, outerDp, outerDp, outerDp)
        val clamped = index.coerceIn(0, count - 1)
        return when (clamped) {
            0 -> SegmentedCornerSpec(outerDp, outerDp, innerDp, innerDp)
            count - 1 -> SegmentedCornerSpec(innerDp, innerDp, outerDp, outerDp)
            else -> SegmentedCornerSpec(innerDp, innerDp, innerDp, innerDp)
        }
    }

    /**
     * Horizontal M3 Expressive Connected Button Group corners:
     * - Selected segment morphs into a full pill ([pillDp] on all 4 corners)
     * - Unselected leading segment has [outerDp] on left, [innerDp] on right
     * - Unselected middle segment has [innerDp] on all corners
     * - Unselected trailing segment has [innerDp] on left, [outerDp] on right
     */
    fun connectedButtonCorners(
        index: Int,
        count: Int,
        selected: Boolean,
        outerDp: Float = 22f,
        innerDp: Float = 8f,
        pillDp: Float = 24f,
    ): SegmentedCornerSpec {
        if (selected || count <= 1) return SegmentedCornerSpec(pillDp, pillDp, pillDp, pillDp)
        val clamped = index.coerceIn(0, count - 1)
        return when (clamped) {
            0 -> SegmentedCornerSpec(outerDp, innerDp, innerDp, outerDp)
            count - 1 -> SegmentedCornerSpec(innerDp, outerDp, outerDp, innerDp)
            else -> SegmentedCornerSpec(innerDp, innerDp, innerDp, innerDp)
        }
    }

    /**
     * Polar radius modulation for M3 Expressive organic scallop / clover / burst polygons.
     * Guaranteed strictly positive and bounded inside `[1 - amplitude, 1]`.
     */
    fun scallopRadiusFactor(angleRad: Double, lobes: Int, amplitude: Float): Float {
        val safeLobes = lobes.coerceAtLeast(3)
        val safeAmp = amplitude.coerceIn(0.01f, 0.25f)
        val wave = (cos(angleRad * safeLobes) * 0.5 + 0.5).toFloat()
        return (1f - safeAmp) + wave * safeAmp
    }
}

/**
 * Material 3 Expressive scalloped polygon (`Cookie` / `Clover` / `SoftBurst` / `Sunny`).
 * Used intentionally for hero moments: brand mark, profile avatar, artist frames,
 * active palette badges, and radio emblems.
 */
data class ExpressiveScallopShape(
    val lobes: Int = 8,
    val amplitude: Float = 0.085f,
    val rotationDeg: Float = 0f,
    val samples: Int = 120,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }

        val cx = w / 2f
        val cy = h / 2f
        val maxR = min(cx, cy)
        val rotRad = rotationDeg * PI / 180.0
        val count = samples.coerceAtLeast(36)
        val path = Path()

        for (i in 0 until count) {
            val theta = (2.0 * PI * i) / count
            val rFactor = ExpressiveSegmentedMath.scallopRadiusFactor(theta, lobes, amplitude)
            val r = maxR * rFactor
            val angle = theta + rotRad
            val x = cx + (cos(angle) * r).toFloat()
            val y = cy + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Material 3 Expressive Arch shape: full semicircular top crown with crisp rounded
 * base corners — great for curated editorial cards and station accents.
 */
data class ExpressiveArchShape(
    private val bottomCornerRadius: Dp = 18.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }

        val topR = min(w / 2f, h * 0.55f)
        val bottomR = with(density) { bottomCornerRadius.toPx() }.coerceIn(0f, min(w, h) / 2f)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, w, h),
                topLeft = CornerRadius(topR, topR),
                topRight = CornerRadius(topR, topR),
                bottomRight = CornerRadius(bottomR, bottomR),
                bottomLeft = CornerRadius(bottomR, bottomR),
            ),
        )
    }
}

/** Curated Material 3 Expressive geometric shape presets. */
object ExpressiveShapes {
    val Cookie8: Shape = ExpressiveScallopShape(lobes = 8, amplitude = 0.085f)
    val Cookie10: Shape = ExpressiveScallopShape(lobes = 10, amplitude = 0.07f)
    val SoftBurst12: Shape = ExpressiveScallopShape(lobes = 12, amplitude = 0.065f)
    val Clover4: Shape = ExpressiveScallopShape(lobes = 4, amplitude = 0.12f, rotationDeg = 45f)
    val Flower6: Shape = ExpressiveScallopShape(lobes = 6, amplitude = 0.105f, rotationDeg = 30f)
    val Arch: Shape = ExpressiveArchShape(18.dp)

    fun artistFrame(index: Int): Shape = when (index % 3) {
        0 -> Cookie8
        1 -> Clover4
        else -> Cookie10
    }

    fun radioEmblem(index: Int): Shape = when (index % 4) {
        0 -> Cookie8
        1 -> Clover4
        2 -> SoftBurst12
        else -> Flower6
    }
}
