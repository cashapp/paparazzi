/*
 * Copyright (C) 2024 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi

import org.junit.runner.Runner
import kotlin.reflect.KClass

/**
 * Names the JUnit runner [PaparazziRunner] should build around the sandboxed test class.
 *
 * `@RunWith` can only name one runner, so a second annotation is needed to compose [PaparazziRunner]
 * with a parameterized or otherwise custom runner:
 *
 * ```kotlin
 * @RunWith(PaparazziRunner::class)
 * @SandboxedRunWith(TestParameterInjector::class)
 * class MyTest(@TestParameter private val config: Config) { ... }
 * ```
 *
 * The named runner is loaded and constructed *inside* the sandbox, so its reflection over the test
 * class — constructor parameters, method parameters, enum values — stays on the sandbox side of the
 * boundary throughout. Defaults to `BlockJUnit4ClassRunner`.
 *
 * Deliberately a top-level annotation rather than nested inside [PaparazziRunner]. [PaparazziRunner]
 * must be shared with the host while everything around it is sandboxed, and a nested annotation
 * would land on the other side of that split, leaving the two halves to disagree on the
 * `InnerClasses` attribute with `IncompatibleClassChangeError`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class SandboxedRunWith(val value: KClass<out Runner>)
