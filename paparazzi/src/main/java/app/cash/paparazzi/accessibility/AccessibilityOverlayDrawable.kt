package app.cash.paparazzi.accessibility

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

internal class AccessibilityOverlayDrawable : Drawable() {
  private var accessibilityElements = mutableListOf<AccessibilityElement>()
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val strokePaint = Paint().apply {
    strokeWidth = 2f
    style = Paint.Style.STROKE
  }

  fun updateElements(elements: Collection<AccessibilityElement>) {
    accessibilityElements.clear()
    accessibilityElements += elements
    invalidateSelf()
  }

  override fun draw(canvas: Canvas) {
    accessibilityElements.forEach {
      paint.color = it.color.toColorInt()

      canvas.drawRect(it.displayBounds, paint)

      strokePaint.color = it.color.toColorInt()
      strokePaint.alpha = RenderSettings.DEFAULT_RENDER_ALPHA * 2
      canvas.drawRect(it.displayBounds, strokePaint)
    }
  }

  override fun setAlpha(alpha: Int) = Unit
  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  @Deprecated("Not used", ReplaceWith("255"))
  override fun getOpacity(): Int = PixelFormat.OPAQUE
}
