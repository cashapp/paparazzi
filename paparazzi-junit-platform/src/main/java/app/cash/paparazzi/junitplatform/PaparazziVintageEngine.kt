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
package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.vintage.engine.VintageTestEngine

/**
 * JUnit Platform [TestEngine] that wraps [VintageTestEngine] so Paparazzi can intercept the
 * test lifecycle and emit snapshot diffs as JUnit Platform metadata (`fileEntryPublished`).
 * Those events surface in the `attachments` tab of Gradle 9.4+ test reports.
 *
 * Only active when the `paparazzi.reportType` system property is `native`. Otherwise
 * execution falls through to the delegate as a pass-through.
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
    delegate.execute(wrappedRequest)
  }
}

// Declared at file scope rather than in a companion: `const val` in an `internal companion
// object` still emits public static fields onto the enclosing class's ABI.
private const val ID: String = "app.cash.paparazzi.vintage"
private const val SYSTEM_PROPERTY_REPORT_TYPE: String = "paparazzi.reportType"
private const val REPORT_TYPE_NATIVE: String = "native"
