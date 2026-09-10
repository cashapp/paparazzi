package app.cash.paparazzi.plugin.test

import android.content.Context
import android.widget.FrameLayout


/**
 * layoutlib measures the content with an UNSPECIFIED height on the V_SCROLL expanding pass, which
 * Compose scroll containers reject outright. Bound it so the content can report its natural
 * height.
 */
internal class BoundedLayout(context: Context) : FrameLayout(context) {
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(bounded(widthMeasureSpec), bounded(heightMeasureSpec))
  }

  private fun bounded(spec: Int) =
    if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) {
      MeasureSpec.makeMeasureSpec(MAX_DIMENSION, MeasureSpec.AT_MOST)
    } else {
      spec
    }

  private companion object {
    private const val MAX_DIMENSION = 0xFFFF
  }
}
