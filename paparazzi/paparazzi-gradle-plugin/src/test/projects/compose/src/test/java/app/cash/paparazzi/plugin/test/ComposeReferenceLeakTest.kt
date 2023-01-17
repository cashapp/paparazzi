package app.cash.paparazzi.plugin.test

import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import java.lang.ref.WeakReference

class ComposeReferenceLeakTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun cleansUpComposeReferences() {
    weakComposeView = WeakReference(
      ComposeView(paparazzi.context).apply {
        setContent {
          HelloPaparazzi()
        }

        paparazzi.snapshot(this)
      }
    )
  }

  companion object {
    private lateinit var weakComposeView: WeakReference<ComposeView>

    @AfterClass
    @JvmStatic
    fun teardown() {
      assert(weakComposeView.get() != null)

      System.gc()

      assert(weakComposeView.get() == null)
    }
  }
}
