package app.cash.paparazzi.internal.layoutlib

import android.widget.TextView
import app.cash.paparazzi.PaparazziTestRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/**
 * Another test in the same JVM can load a patch target before Paparazzi's first render, e.g.
 * mocking an Activity reflects over it and loads `ViewRootImpl`. The patch must still apply.
 *
 * Runs in its own JVM (`testPreloadedLayoutlibTargets`) so nothing renders before [preload].
 */
class PreloadedLayoutlibTargetsTest {
  @get:Rule
  val testRule = PaparazziTestRule()

  @Test
  fun patchesTargetsLoadedBeforeFirstRender() {
    testRule.paparazzi.snapshot(TextView(testRule.paparazzi.context).apply { text = "Hello" })
  }

  companion object {
    @JvmStatic
    @BeforeClass
    fun preload() {
      listOf(
        "android.view.ViewRootImpl",
        "com.android.layoutlib.bridge.impl.RenderSessionImpl",
        "com.android.layoutlib.bridge.impl.BridgeWindowSession"
      ).forEach { Class.forName(it, false, PreloadedLayoutlibTargetsTest::class.java.classLoader) }
    }
  }
}
