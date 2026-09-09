package app.cash.paparazzi.accessibility

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

internal class AccessibilityOverlayDrawable(
  private val collectElements: () -> Collection<AccessibilityElement>
) : Drawable() {
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val strokePaint = Paint().apply {
    strokeWidth = 2f
    style = Paint.Style.STROKE
  }

  override fun draw(canvas: Canvas) {
    // Compose can perform placement during dispatchDraw, after pre-draw listeners have run.
    // Read bounds when the foreground is drawn so they describe the rendered content.
    collectElements().forEach {
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
