package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.vintage.engine.VintageTestEngine

/**
 * JUnit Platform [TestEngine] that wraps [VintageTestEngine] so Paparazzi can
 * intercept the test lifecycle and emit snapshot diff / accessibility artifacts
 * as JUnit Platform metadata (`fileEntryPublished` / `reportingEntryPublished`).
 * Those events surface in Gradle 9.4+ test reports in the `attachments` and
 * `data` tabs.
 *
 * Only active when the `paparazzi.reportType` system property is `native`.
 * Otherwise execution falls through to the delegate as a pass-through.
 */
public class PaparazziVintageEngine : TestEngine {
  private val delegate = VintageTestEngine()

  override fun getId(): String = ID

  override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor =
    delegate.discover(request, uniqueId)

  override fun execute(request: ExecutionRequest) {
    if (System.getProperty(SYSTEM_PROPERTY_REPORT_TYPE) != REPORT_TYPE_NATIVE) {
      delegate.execute(request)
      return
    }

    val wrappedListener = PaparazziExecutionListener(request.engineExecutionListener)
    val wrappedRequest = ExecutionRequest.create(
      request.rootTestDescriptor,
      wrappedListener,
      request.configurationParameters
    )
    PaparazziSnapshotReporter.setActive(wrappedListener)
    try {
      delegate.execute(wrappedRequest)
    } finally {
      PaparazziSnapshotReporter.setActive(null)
    }
  }

  internal companion object {
    const val ID: String = "app.cash.paparazzi.vintage"
    const val SYSTEM_PROPERTY_REPORT_TYPE: String = "paparazzi.reportType"
    const val REPORT_TYPE_NATIVE: String = "native"
  }
}
