package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.MethodSource
import java.io.File

/**
 * Wraps the [EngineExecutionListener] passed in by JUnit Platform.
 *
 * On `executionFinished` for leaf test descriptors, scans Paparazzi's failure
 * directory for any diff images produced during the test method and emits each
 * one as a [FileEntry] attachment on that test descriptor. Diff files are
 * discovered by filesystem convention: Paparazzi writes them to
 * `paparazzi.failures.dir` with names of the form
 * `delta-<packageName>_<simpleClassName>_<methodName>[_<label>].png`. The
 * matching test class+method are read from the descriptor's [MethodSource].
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
    // Emit attachments before forwarding the finished event — Gradle's binary
    // result store associates published events with the still-current test.
    if (testDescriptor.isTest) {
      emitDiffAttachments(testDescriptor)
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }

  private fun emitDiffAttachments(testDescriptor: TestDescriptor) {
    val failureDirPath = System.getProperty(SYSTEM_PROPERTY_FAILURES_DIR) ?: return
    val failureDir = File(failureDirPath)
    if (!failureDir.isDirectory) return

    val source = testDescriptor.source.orElse(null) as? MethodSource ?: return
    val packageName = source.className.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleClassName = source.className.substringAfterLast('.')
    val methodName = source.methodName.replace(Regex("\\s"), "_")

    val prefix = "delta-${packageName}_${simpleClassName}_${methodName}"

    failureDir.listFiles()
      ?.filter { file ->
        val name = file.name
        if (!name.startsWith(prefix) || !name.endsWith(".png")) return@filter false
        // Ensure prefix is followed by '.' (no label) or '_' (label suffix) so
        // a method like `testThing` doesn't accidentally match `testThingExtra`.
        val boundary = name.getOrNull(prefix.length)
        boundary == '.' || boundary == '_'
      }
      ?.forEach { file ->
        delegate.fileEntryPublished(
          testDescriptor,
          FileEntry.from(file.toPath(), "image/png")
        )
      }
  }

  private companion object {
    const val SYSTEM_PROPERTY_FAILURES_DIR: String = "paparazzi.failures.dir"
  }
}
