package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ComposeLifecycleOwnerTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun lifecycleOwnerAvailableWithRendererExtension() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }
}
