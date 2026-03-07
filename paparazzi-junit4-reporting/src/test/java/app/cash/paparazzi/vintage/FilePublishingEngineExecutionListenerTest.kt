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

import app.cash.paparazzi.PaparazziReportingBridge
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.TestTag
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import java.io.File
import java.util.Optional

class FilePublishingEngineExecutionListenerTest {
  private val delegate = RecordingEngineExecutionListener()
  private val listener = FilePublishingEngineExecutionListener(delegate)

  @AfterEach
  fun clearPublisher() {
    PaparazziReportingBridge.currentPublisher.remove()
  }

  // ── Publisher lifecycle ───────────────────────────────────────────────────

  @Test
  fun publisherInstalled_whenLeafTestStarts() {
    listener.executionStarted(leafDescriptor("test-1"))

    assertThat(PaparazziReportingBridge.currentPublisher.get()).isNotNull()
  }

  @Test
  fun publisherCleared_whenLeafTestFinishes() {
    val descriptor = leafDescriptor("test-1")
    listener.executionStarted(descriptor)
    listener.executionFinished(descriptor, TestExecutionResult.successful())

    assertThat(PaparazziReportingBridge.currentPublisher.get()).isNull()
  }

  @Test
  fun publisherNotInstalled_forSuiteNode() {
    listener.executionStarted(suiteDescriptor("suite-1"))

    assertThat(PaparazziReportingBridge.currentPublisher.get()).isNull()
  }

  @Test
  fun publisherNotCleared_whenSuiteNodeFinishes() {
    // Install a publisher manually to verify suites don't clear it.
    val sentinel: (File) -> Unit = {}
    PaparazziReportingBridge.currentPublisher.set(sentinel)

    val suite = suiteDescriptor("suite-1")
    listener.executionStarted(suite)
    listener.executionFinished(suite, TestExecutionResult.successful())

    assertThat(PaparazziReportingBridge.currentPublisher.get()).isSameInstanceAs(sentinel)
  }

  // ── File publishing ───────────────────────────────────────────────────────

  @Test
  fun fileEntryPublished_onDelegate_whenBridgePublishesFile() {
    listener.executionStarted(leafDescriptor("test-1"))

    val file = File.createTempFile("snapshot", ".png")
    PaparazziReportingBridge.publishFile(file)

    assertThat(delegate.publishedFiles).hasSize(1)
    assertThat(delegate.publishedFiles[0].path).isEqualTo(file.toPath())
  }

  @Test
  fun fileEntryMediaType_isPng() {
    listener.executionStarted(leafDescriptor("test-1"))

    PaparazziReportingBridge.publishFile(File.createTempFile("snapshot", ".png"))

    assertThat(delegate.publishedFiles[0].mediaType.orElse(null)).isEqualTo("image/png")
  }

  @Test
  fun multipleFiles_publishedToDelegate() {
    listener.executionStarted(leafDescriptor("test-1"))

    val file1 = File.createTempFile("snap1", ".png")
    val file2 = File.createTempFile("snap2", ".png")
    PaparazziReportingBridge.publishFile(file1)
    PaparazziReportingBridge.publishFile(file2)

    assertThat(delegate.publishedFiles.map { it.path })
      .containsExactly(file1.toPath(), file2.toPath())
      .inOrder()
  }

  @Test
  fun noFilesPublished_whenBridgeNotCalled() {
    listener.executionStarted(leafDescriptor("test-1"))
    listener.executionFinished(leafDescriptor("test-1"), TestExecutionResult.successful())

    assertThat(delegate.publishedFiles).isEmpty()
  }

  // ── Delegation of all other events ───────────────────────────────────────

  @Test
  fun executionStarted_delegated() {
    val descriptor = leafDescriptor("test-1")
    listener.executionStarted(descriptor)

    assertThat(delegate.startedDescriptors).containsExactly(descriptor)
  }

  @Test
  fun executionFinished_delegated() {
    val descriptor = leafDescriptor("test-1")
    val result = TestExecutionResult.successful()
    listener.executionStarted(descriptor)
    listener.executionFinished(descriptor, result)

    assertThat(delegate.finishedDescriptors).containsExactly(descriptor)
    assertThat(delegate.finishedResults).containsExactly(result)
  }

  @Test
  fun executionSkipped_delegated() {
    val descriptor = leafDescriptor("test-1")
    listener.executionSkipped(descriptor, "disabled")

    assertThat(delegate.skippedDescriptors).containsExactly(descriptor)
    assertThat(delegate.skippedReasons).containsExactly("disabled")
  }

  @Test
  fun dynamicTestRegistered_delegated() {
    val descriptor = leafDescriptor("dynamic-1")
    listener.dynamicTestRegistered(descriptor)

    assertThat(delegate.dynamicDescriptors).containsExactly(descriptor)
  }

  @Test
  fun reportingEntryPublished_delegated() {
    val descriptor = leafDescriptor("test-1")
    val entry = ReportEntry.from("key", "value")
    listener.reportingEntryPublished(descriptor, entry)

    assertThat(delegate.reportEntries).containsExactly(entry)
  }

  @Test
  fun fileEntryPublished_delegated_whenCalledDirectly() {
    val descriptor = leafDescriptor("test-1")
    val file = File.createTempFile("snapshot", ".png")
    val entry = FileEntry.from(file.toPath(), "image/png")
    listener.fileEntryPublished(descriptor, entry)

    assertThat(delegate.publishedFiles).containsExactly(entry)
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private fun leafDescriptor(id: String): TestDescriptor = FakeTestDescriptor(id, isTest = true)
  private fun suiteDescriptor(id: String): TestDescriptor = FakeTestDescriptor(id, isTest = false)
}

private class FakeTestDescriptor(
  private val id: String,
  private val isTest: Boolean
) : TestDescriptor {
  override fun getUniqueId(): UniqueId = UniqueId.root("test", id)
  override fun getDisplayName(): String = id
  override fun getType(): TestDescriptor.Type =
    if (isTest) TestDescriptor.Type.TEST else TestDescriptor.Type.CONTAINER
  override fun getSource(): Optional<TestSource> = Optional.empty()
  override fun getChildren(): MutableSet<TestDescriptor> = mutableSetOf()
  override fun getParent(): Optional<TestDescriptor> = Optional.empty()
  override fun setParent(parent: TestDescriptor?) {}
  override fun addChild(descriptor: TestDescriptor) {}
  override fun removeChild(descriptor: TestDescriptor) {}
  override fun removeFromHierarchy() {}
  override fun findByUniqueId(uniqueId: UniqueId): Optional<TestDescriptor> = Optional.empty()
  override fun getTags(): MutableSet<TestTag> = mutableSetOf()
}

private class RecordingEngineExecutionListener : EngineExecutionListener {
  val startedDescriptors = mutableListOf<TestDescriptor>()
  val finishedDescriptors = mutableListOf<TestDescriptor>()
  val finishedResults = mutableListOf<TestExecutionResult>()
  val skippedDescriptors = mutableListOf<TestDescriptor>()
  val skippedReasons = mutableListOf<String>()
  val dynamicDescriptors = mutableListOf<TestDescriptor>()
  val reportEntries = mutableListOf<ReportEntry>()
  val publishedFiles = mutableListOf<FileEntry>()

  override fun executionStarted(descriptor: TestDescriptor) {
    startedDescriptors += descriptor
  }

  override fun executionFinished(descriptor: TestDescriptor, result: TestExecutionResult) {
    finishedDescriptors += descriptor
    finishedResults += result
  }

  override fun executionSkipped(descriptor: TestDescriptor, reason: String) {
    skippedDescriptors += descriptor
    skippedReasons += reason
  }

  override fun dynamicTestRegistered(descriptor: TestDescriptor) {
    dynamicDescriptors += descriptor
  }

  override fun reportingEntryPublished(descriptor: TestDescriptor, entry: ReportEntry) {
    reportEntries += entry
  }

  override fun fileEntryPublished(descriptor: TestDescriptor, file: FileEntry) {
    publishedFiles += file
  }
}
