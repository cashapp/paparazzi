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

import org.junit.platform.engine.ExecutionRequest

/**
 * Returns [request] rewired so that Paparazzi's snapshot diffs are published as attachments on the
 * tests that produced them.
 *
 * Called by the engine wrappers in `paparazzi-junit-vintage` and `paparazzi-junit-jupiter`; not
 * intended for use outside them. The plugin only puts those artifacts on the test runtime
 * classpath when `app.cash.paparazzi.reportType=native`, so reaching here already means native
 * mode.
 */
public fun paparazziAttachmentRequest(request: ExecutionRequest): ExecutionRequest =
  // Forward the whole request: the 3-arg factory substitutes a disabled CancellationToken and an
  // absent store. Deliberately INTERNAL API, so a JUnit bump breaks loudly, not silently.
  ExecutionRequest.create(
    request.rootTestDescriptor,
    PaparazziExecutionListener(request.engineExecutionListener),
    request.configurationParameters,
    request.outputDirectoryCreator,
    request.store,
    request.cancellationToken
  )
