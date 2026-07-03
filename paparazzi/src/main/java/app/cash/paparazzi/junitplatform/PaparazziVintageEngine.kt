package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.vintage.engine.VintageTestEngine

/**
 * JUnit Platform [TestEngine] that wraps [VintageTestEngine] so Paparazzi can
 * intercept the test lifecycle and emit snapshot diff / accessibility artifacts as
 * JUnit Platform metadata (`fileEntryPublished` / `publishEntry`). Those events
 * surface in Gradle 9.4+ test reports in the `attachments` and `data` tabs.
 *
 * Currently a delegating stub; emission logic will be added in a follow-up.
 */
public class PaparazziVintageEngine : TestEngine {
  private val delegate = VintageTestEngine()

  override fun getId(): String = ID

  override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor =
    delegate.discover(request, uniqueId)

  override fun execute(request: ExecutionRequest) {
    // TODO: intercept lifecycle, emit fileEntryPublished/publishEntry to surface
    //  snapshot diff images and accessibility overlays in the native test report.
    delegate.execute(request)
  }

  internal companion object {
    const val ID: String = "app.cash.paparazzi.vintage"
  }
}
