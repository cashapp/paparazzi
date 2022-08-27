package app.cash.paparazzi.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.widget.FrameLayout
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

class AccessibilityOverlayView(context: Context) : FrameLayout(context) {

  private val accessibilityElements = mutableListOf<AccessibilityElement>()
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val strokePaint = Paint().apply {
    strokeWidth = 2f
    style = Paint.Style.STROKE
  }

  init {
    // Required for onDraw to be called
    setWillNotDraw(false)
  }

  fun addElement(element: AccessibilityElement) {
    accessibilityElements.add(element)
    invalidate()
  }

  fun addElements(elements: Collection<AccessibilityElement>) {
    accessibilityElements.addAll(elements)
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    accessibilityElements.forEach {
      paint.color = it.color.toColorInt()
      canvas.drawRect(it.bounds, paint)

      strokePaint.color = it.color.toColorInt()
      strokePaint.alpha = RenderSettings.DEFAULT_RENDER_ALPHA * 2
      canvas.drawRect(it.bounds, strokePaint)
    }
  }

  data class AccessibilityElement(
    val color: java.awt.Color,
    val bounds: Rect
  )
}
