package app.cash.paparazzi.plugin.test

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class RoundView(
  context: Context,
  attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

  init {
    inflate(context, R.layout.custom_view, this)
  }
}
