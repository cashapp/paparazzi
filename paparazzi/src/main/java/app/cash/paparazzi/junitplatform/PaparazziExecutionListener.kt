package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry

/**
 * Wraps the [EngineExecutionListener] passed in by JUnit Platform so that
 * [PaparazziSnapshotReporter] can know which test is currently executing.
 * All events are forwarded to the delegate unchanged.
 */
internal class PaparazziExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String?) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    if (testDescriptor.isTest) {
      PaparazziSnapshotReporter.setCurrentTest(testDescriptor)
    }
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(
    testDescriptor: TestDescriptor,
    testExecutionResult: TestExecutionResult
  ) {
    delegate.executionFinished(testDescriptor, testExecutionResult)
    if (testDescriptor.isTest) {
      PaparazziSnapshotReporter.setCurrentTest(null)
    }
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }
}
