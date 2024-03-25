package app.cash.paparazzi.sample

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.ImageSize
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class ScrollingTest {

  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.V_SCROLL,
    imageSize = ImageSize.FullBleed
  )

  @Test
  fun verticalScrolling() {
    val scrollView = ScrollView(paparazzi.context)
    val linearLayout = LinearLayout(paparazzi.context)
    linearLayout.orientation = LinearLayout.VERTICAL
    repeat(1000) {
      val textView = TextView(paparazzi.context)
      textView.setText("Hello world $it")
      linearLayout.addView(textView)
    }
    scrollView.addView(linearLayout)

    paparazzi.snapshot(scrollView)
  }
}
