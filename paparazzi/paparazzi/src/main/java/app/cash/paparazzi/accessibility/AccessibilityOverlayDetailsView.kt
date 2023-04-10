package app.cash.paparazzi.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.widget.FrameLayout
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt
import java.lang.Float.max

class AccessibilityOverlayDetailsView(context: Context) : FrameLayout(context) {
  private val accessibilityElements = mutableListOf<AccessibilityState.Element>()
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val textPaint = Paint().apply {
    style = Paint.Style.FILL
    color = DEFAULT_TEXT_COLOR.toColorInt()
    textSize = context.sp(RenderSettings.DEFAULT_TEXT_SIZE)
  }
  private val margin = context.dip(8f)
  private val innerMargin = margin / 2f
  private val rectSize = context.dip(RenderSettings.DEFAULT_RECT_SIZE)
  private val cornerRadius = rectSize / 4f

  init {
    // Required for onDraw to be called
    setWillNotDraw(false)

    setBackgroundColor(RenderSettings.DEFAULT_DESCRIPTION_BACKGROUND_COLOR.toColorInt())
  }

  fun addElement(element: AccessibilityState.Element) {
    accessibilityElements.add(element)
    invalidate()
  }

  fun addElements(elements: Collection<AccessibilityState.Element>) {
    accessibilityElements.addAll(elements)
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    var lastYCoord = innerMargin
    val textBounds = Rect()
    val textPaint = TextPaint(textPaint)

    accessibilityElements.forEach {
      paint.color = it.color.toColorInt()
      val badge = RectF(margin, lastYCoord, margin + rectSize, lastYCoord + rectSize)
      canvas.drawRoundRect(badge, cornerRadius, cornerRadius, paint)

      canvas.save()

      val text = it.renderString().orEmpty()
      val textLayout = StaticLayout.Builder.obtain(
        text,
        0,
        text.length,
        textPaint,
        width
      ).setEllipsize(TextUtils.TruncateAt.END)
        .build()

      canvas.save()
      val textX = badge.right + innerMargin
      val textY = badge.top
      canvas.translate(textX, textY)
      textLayout.draw(canvas)
      canvas.restore()

      lastYCoord = max(badge.bottom + margin, textLayout.height.toFloat())
    }
  }
}

private fun Context.sp(value: Float): Float =
  TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value,
    resources.displayMetrics
  )

private fun Context.dip(value: Float): Float =
  TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics
  )

private fun Context.dip(value: Int): Int = dip(value.toFloat()).toInt()
