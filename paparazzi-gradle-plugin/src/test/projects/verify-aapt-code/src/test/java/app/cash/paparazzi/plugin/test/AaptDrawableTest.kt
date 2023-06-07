package app.cash.paparazzi.plugin.test

import android.util.TypedValue
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.appcompat.widget.AppCompatImageView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class AaptDrawableTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun aaptEmbeddedDrawable() {
    val imageView = AppCompatImageView(paparazzi.context).apply {
      layoutParams = LayoutParams(dip(140), dip(140))
        .apply { gravity = CENTER }
    }
    imageView.setImageResource(R.drawable.card_chip)
    val wrapped = FrameLayout(paparazzi.context).apply {
      layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
      addView(imageView)
    }
    paparazzi.snapshot(wrapped)
  }

  private fun View.dip(value: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics
    ).toInt()
}
