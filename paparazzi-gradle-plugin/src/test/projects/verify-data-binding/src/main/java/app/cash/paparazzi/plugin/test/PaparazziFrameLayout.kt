package app.cash.paparazzi.plugin.test

import android.content.Context
import android.widget.FrameLayout
import app.cash.paparazzi.plugin.test.databinding.MergeLayoutBinding

class PaparazziFrameLayout(context: Context) : FrameLayout(context) {
  init {
    inflate(context, R.layout.merge_layout, this)
    MergeLayoutBinding.bind(this)
  }
}
