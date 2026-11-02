package app.cash.paparazzi.internal.layoutlib

import app.cash.paparazzi.agent.AgentInstaller
import org.junit.Test

class LayoutlibPatchTest {
  @Test
  fun patchesTargetsLoadedBeforeInstallation() {
    val loader = LayoutlibPatch::class.java.classLoader
    for (name in listOf(
      "android.view.View",
      "android.view.ViewRootImpl",
      "com.android.layoutlib.bridge.impl.RenderSessionImpl",
      "com.android.layoutlib.bridge.impl.BridgeWindowSession"
    )) {
      Class.forName(name, false, loader)
    }

    // Retransformation must pass JVM verification without loading Android classes to compute frames.
    LayoutlibPatch.install(AgentInstaller.install())
    LayoutlibPatch.verifyApplied()
    LayoutlibPatch.install(AgentInstaller.install())
    LayoutlibPatch.verifyApplied()
  }
}
