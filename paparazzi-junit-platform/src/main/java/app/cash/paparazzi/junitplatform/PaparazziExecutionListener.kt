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
 * On `executionFinished` for leaf test descriptors, scans Paparazzi's
 * filesystem outputs for artifacts produced during the test method and emits
 * each one as a [FileEntry] attachment on that test descriptor. Today this
 * covers two sources:
 *  - Snapshot diff PNGs in `paparazzi.failures.dir`, prefixed `delta-`
 *  - Accessibility SVGs in `paparazzi.attachments.dir`, prefixed `a11y-`
 *
 * Matching is by test class+method derived from the descriptor's [MethodSource].
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
      emitAttachments(testDescriptor)
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }

  private fun emitAttachments(testDescriptor: TestDescriptor) {
    val source = testDescriptor.source.orElse(null) as? MethodSource ?: return
    val packageName = source.className.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleClassName = source.className.substringAfterLast('.')
    val methodName = source.methodName.replace(Regex("\\s"), "_")
    val testKey = "${packageName}_${simpleClassName}_${methodName}"

    emitMatching(
      dirSystemProperty = SYSTEM_PROPERTY_FAILURES_DIR,
      testDescriptor = testDescriptor,
      filePrefix = "delta-$testKey",
      fileExtension = ".png",
      mediaType = "image/png"
    )
    emitMatching(
      dirSystemProperty = SYSTEM_PROPERTY_ATTACHMENTS_DIR,
      testDescriptor = testDescriptor,
      filePrefix = "a11y-$testKey",
      fileExtension = ".svg",
      mediaType = "image/svg+xml"
    )
  }

  private fun emitMatching(
    dirSystemProperty: String,
    testDescriptor: TestDescriptor,
    filePrefix: String,
    fileExtension: String,
    mediaType: String
  ) {
    val dirPath = System.getProperty(dirSystemProperty) ?: return
    val dir = File(dirPath)
    if (!dir.isDirectory) return

    dir.listFiles()
      ?.filter { file ->
        val name = file.name
        if (!name.startsWith(filePrefix) || !name.endsWith(fileExtension)) return@filter false
        // Ensure prefix is followed by '.' (no label) or '_' (label suffix) so
        // a method like `testThing` doesn't accidentally match `testThingExtra`.
        val boundary = name.getOrNull(filePrefix.length)
        boundary == '.' || boundary == '_'
      }
      ?.forEach { file ->
        delegate.fileEntryPublished(
          testDescriptor,
          FileEntry.from(file.toPath(), mediaType)
        )
      }
  }

  private companion object {
    const val SYSTEM_PROPERTY_FAILURES_DIR: String = "paparazzi.failures.dir"
    const val SYSTEM_PROPERTY_ATTACHMENTS_DIR: String = "paparazzi.attachments.dir"
  }
}
