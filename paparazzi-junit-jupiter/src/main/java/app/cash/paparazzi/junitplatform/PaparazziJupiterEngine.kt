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

import org.junit.jupiter.engine.JupiterTestEngine
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId

/**
 * Delegates to [JupiterTestEngine], publishing Paparazzi's snapshot diffs as JUnit Platform
 * `FileEntry` attachments so they appear in the attachments tab of Gradle 9.4+ test reports.
 *
 * The plugin adds this artifact and excludes the `junit-jupiter` engine when
 * `app.cash.paparazzi.reportType=native` and JUnit 5 support is requested.
 */
public class PaparazziJupiterEngine : TestEngine {
  private val delegate = JupiterTestEngine()

  override fun getId(): String = ID

  override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor =
    delegate.discover(request, uniqueId)

  // Jupiter display names are author-controlled via @DisplayName; the reflective name is stable.
  override fun execute(request: ExecutionRequest): Unit =
    delegate.execute(paparazziAttachmentRequest(request) { _, source -> source.methodName })
}

private const val ID: String = "app.cash.paparazzi.jupiter"
