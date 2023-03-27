import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.accessibility.AccessibilityState
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_RECT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

class AccessibilityRowView(context: Context) : LinearLayout(context) {

  private val textView: TextView
  private val badgeView: View
  private val badgeDrawable: GradientDrawable

  init {
    val margin = context.dip(8)
    val innerMargin = context.dip(4)

    orientation = HORIZONTAL
    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.WRAP_CONTENT
    )
    setPaddingRelative(margin, innerMargin, margin, innerMargin)

    badgeView = View(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        context.dip(DEFAULT_RECT_SIZE),
        context.dip(DEFAULT_RECT_SIZE)
      )

      // Evaluate the color after the view has been measured. This is necessary for compose element ids to be set, which drives element colors.
      // val color = accessibilityState.elements[index].color.toColorInt()
      badgeDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.WHITE, Color.WHITE) // Initial Color.WHITE values are overwritten by the color set in the AccessibilityOverlayView
      ).apply {
        cornerRadius = context.dip(DEFAULT_RECT_SIZE / 4f)
      }
      setPaddingRelative(innerMargin, innerMargin, innerMargin, innerMargin)
      background = badgeDrawable
    }

    textView = TextView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      textSize = DEFAULT_TEXT_SIZE
      setTextColor(DEFAULT_TEXT_COLOR.toColorInt())
      setPaddingRelative(innerMargin, 0, innerMargin, 0)
    }

    addView(badgeView)
    addView(textView)
  }

  fun updateForElement(element: AccessibilityState.Element) {
    badgeDrawable.setColor(element.color.toColorInt())
    textView.text = element.renderString()
  }
}

private fun Context.dip(value: Float): Float =
  TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics
  )

private fun Context.dip(value: Int): Int = dip(value.toFloat()).toInt()
