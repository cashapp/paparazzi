package app.cash.paparazzi.internal.layoutlib

import android.widget.TextView
import app.cash.paparazzi.PaparazziTestRule
import com.android.layoutlib.bridge.impl.RenderAction
import org.junit.Assert.assertTrue
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
    val queue = RenderAction.getCurrentContext().sessionInteractiveData.handlerMessageQueue
    assertTrue(queue is DeterministicHandlerMessageQueue)
    testRule.paparazzi.snapshot(TextView(testRule.paparazzi.context).apply { text = "Hello" })
  }

  companion object {
    @JvmStatic
    @BeforeClass
    fun preload() {
      listOf(
        "android.view.ViewRootImpl",
        "com.android.layoutlib.bridge.impl.RenderSessionImpl",
        "com.android.layoutlib.bridge.impl.BridgeWindowSession",
        "com.android.layoutlib.bridge.SessionInteractiveData"
      ).forEach { Class.forName(it, false, PreloadedLayoutlibTargetsTest::class.java.classLoader) }
    }
  }
}
