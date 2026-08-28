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
package app.cash.paparazzi.runnerfixtures

import app.cash.paparazzi.PaparazziRunner
import app.cash.paparazzi.SandboxedRunWith
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

/**
 * Test classes driven by `PaparazziRunnerFeaturesTest` through `JUnitCore`, never collected
 * directly — some of them fail on purpose. The `:paparazzi` test task excludes this package.
 *
 * They assert from the inside, so a violation surfaces as a JUnit failure the caller can assert on.
 * That is simpler than reaching across the class loader boundary to read the sandbox's statics.
 */
internal const val SANDBOX_LOADER = "app.cash.paparazzi.internal.sandbox.SandboxClassLoader"

/** Fails unless the calling code is running inside a sandbox. */
internal fun requireSandboxed(where: String) {
  val actual = object {}.javaClass.classLoader!!.javaClass.name
  check(actual == SANDBOX_LOADER) { "$where ran under $actual, expected the sandbox loader" }
}

@RunWith(PaparazziRunner::class)
class IgnoreFixture {
  @Test
  fun runs() = Unit

  @Ignore("deliberately skipped")
  @Test
  fun skipped(): Unit = throw AssertionError("An @Ignore'd test must not execute")
}

@RunWith(PaparazziRunner::class)
class ExpectedExceptionFixture {
  @Test(expected = IllegalStateException::class)
  fun throwsExpected() {
    throw IllegalStateException("expected")
  }
}

@RunWith(PaparazziRunner::class)
class TimeoutFixture {
  @Test(timeout = 100)
  fun exceedsTimeout() {
    Thread.sleep(10_000)
  }

  @Test(timeout = 30_000)
  fun withinTimeoutStaysInTheSandbox() {
    // FailOnTimeout moves the body onto a fresh thread. A thread inherits its context class loader
    // from its creator, so this only holds because run() wraps the entire delegate.
    requireSandboxed("a @Test(timeout) body")
  }
}

@RunWith(PaparazziRunner::class)
class AssumptionFixture {
  @Test
  fun skippedByAssumption() {
    assumeTrue("assumption deliberately fails", false)
    throw AssertionError("Unreachable")
  }
}

@RunWith(PaparazziRunner::class)
class ClassLifecycleFixture {
  @Test
  fun trivial() = Unit

  companion object {
    @JvmStatic
    @BeforeClass
    fun beforeClass() = requireSandboxed("@BeforeClass")

    @JvmStatic
    @AfterClass
    fun afterClass() = requireSandboxed("@AfterClass")
  }
}

@RunWith(PaparazziRunner::class)
class ClassRuleFixture {
  @Test
  fun trivial() = Unit

  companion object {
    @JvmField
    @ClassRule
    val rule: ExternalResource = object : ExternalResource() {
      override fun before() = requireSandboxed("@ClassRule")
    }
  }
}

/** A stand-in for third-party runners such as `TestParameterInjector`. */
class RecordingRunner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
  init {
    used = true
    loaderName = RecordingRunner::class.java.classLoader!!.javaClass.name
  }

  companion object {
    @JvmStatic
    var used: Boolean = false

    @JvmStatic
    var loaderName: String? = null
  }
}

@RunWith(PaparazziRunner::class)
@SandboxedRunWith(RecordingRunner::class)
class CustomRunnerFixture {
  @Test
  fun delegateWasUsedAndIsItselfSandboxed() {
    check(RecordingRunner.used) { "The delegate runner was not used" }
    check(RecordingRunner.loaderName == SANDBOX_LOADER) {
      "Delegate runner loaded by ${RecordingRunner.loaderName}, expected the sandbox loader"
    }
  }
}

@RunWith(PaparazziRunner::class)
class FilterFixture {
  @Test
  fun first() = Unit

  @Test
  fun second() = Unit
}
