package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry

/**
 * Wraps the [EngineExecutionListener] passed in by JUnit Platform.
 *
 * On `executionFinished` for leaf test descriptors, scans Paparazzi's failure
 * directory for any diff images produced during the test method and emits each
 * one as a [FileEntry] attachment on that test descriptor. Diff files are
 * discovered by filesystem convention: Paparazzi writes them to
 * `paparazzi.failures.dir` with names derived from the test class+method.
 *
 * All other events are forwarded to the delegate unchanged.
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
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(
    testDescriptor: TestDescriptor,
    testExecutionResult: TestExecutionResult
  ) {
    if (testDescriptor.isTest) {
      // TODO Phase 2c.2: scan paparazzi.failures.dir for diff files matching this test
      //  descriptor's class+method, and emit fileEntryPublished for each.
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }
}
