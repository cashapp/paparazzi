package app.cash.paparazzi.plugin.test

import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

// Run longer names before their shorter prefixes to exercise attachment collisions.
class ReverseNameRunner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
  override fun computeTestMethods(): List<FrameworkMethod> = super.computeTestMethods().sortedByDescending { it.name }
}

@RunWith(ReverseNameRunner::class)
class NameCollisionTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun render_detail() {
    val verifying = System.getProperty("paparazzi.test.verify") == "true"
    snapshot(if (verifying) "changed" else "golden")
  }

  @Test
  fun render() {
    snapshot("golden")
  }

  @Test
  fun collide_detail() {
    val verifying = System.getProperty("paparazzi.test.verify") == "true"
    snapshot(if (verifying) "changed-first" else "golden-collide")
  }

  @Test
  fun collide() {
    val verifying = System.getProperty("paparazzi.test.verify") == "true"
    snapshot(if (verifying) "changed-second" else "golden-collide", name = "detail")
  }

  private fun snapshot(value: String, name: String? = null) {
    paparazzi.snapshot(
      TextView(paparazzi.context).apply {
        text = value
        textSize = 24f
      },
      name = name
    )
  }
}
