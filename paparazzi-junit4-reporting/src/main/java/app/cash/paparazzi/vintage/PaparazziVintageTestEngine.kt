/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.vintage

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.PaparazziReportingBridge
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.vintage.engine.VintageTestEngine
import java.util.Optional

/**
 * A [TestEngine] that wraps [VintageTestEngine] and intercepts the
 * [EngineExecutionListener] to publish snapshot images to Gradle's HTML test
 * report via [EngineExecutionListener.fileEntryPublished].
 *
 * This engine uses the ID `"paparazzi-vintage"` (not `"junit-vintage"`) because
 * the JUnit Platform reserves the `"junit-"` prefix for official engines.
 * [VintageTestEngine] is excluded by the Paparazzi Gradle plugin so that JUnit 4
 * tests only run once (through this engine rather than both engines).
 *
 * Requires Gradle 9.4+ for file attachments to appear in the HTML test report.
 */
public class PaparazziVintageTestEngine : TestEngine {
  private val delegate = VintageTestEngine()

  // Use a distinct ID rather than "junit-vintage"; the JUnit Platform's
  // EngineIdValidator forbids third-party engines from using the "junit-" prefix
  // with a well-known ID unless their class name matches exactly.
  override fun getId(): String = "paparazzi-vintage"

  override fun getGroupId(): Optional<String> = Optional.of("app.cash.paparazzi")

  override fun getArtifactId(): Optional<String> = Optional.of("paparazzi-junit4-reporting")

  override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor =
    delegate.discover(request, uniqueId)

  @Suppress("DEPRECATION") // ExecutionRequest 3-arg constructor; loses CancellationToken
  override fun execute(request: ExecutionRequest) {
    val wrapped = FilePublishingEngineExecutionListener(request.engineExecutionListener)
    delegate.execute(
      ExecutionRequest(
        request.rootTestDescriptor,
        wrapped,
        request.configurationParameters
      )
    )
  }
}

/**
 * Wraps an [EngineExecutionListener] to set/clear [PaparazziReportingBridge.currentPublisher]
 * around each leaf test, so that [Paparazzi.reportOutputFile] can emit
 * [EngineExecutionListener.fileEntryPublished] events for snapshot files.
 */
internal class FilePublishingEngineExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun executionStarted(descriptor: TestDescriptor) {
    if (descriptor.isTest) {
      PaparazziReportingBridge.currentPublisher.set { file ->
        delegate.fileEntryPublished(descriptor, FileEntry.from(file.toPath(), "image/png"))
      }
    }
    delegate.executionStarted(descriptor)
  }

  override fun executionFinished(descriptor: TestDescriptor, result: TestExecutionResult) {
    delegate.executionFinished(descriptor, result)
    if (descriptor.isTest) {
      PaparazziReportingBridge.currentPublisher.remove()
    }
  }

  override fun dynamicTestRegistered(descriptor: TestDescriptor) =
    delegate.dynamicTestRegistered(descriptor)

  override fun executionSkipped(descriptor: TestDescriptor, reason: String) =
    delegate.executionSkipped(descriptor, reason)

  override fun reportingEntryPublished(descriptor: TestDescriptor, entry: ReportEntry) =
    delegate.reportingEntryPublished(descriptor, entry)

  override fun fileEntryPublished(descriptor: TestDescriptor, file: FileEntry) =
    delegate.fileEntryPublished(descriptor, file)
}
