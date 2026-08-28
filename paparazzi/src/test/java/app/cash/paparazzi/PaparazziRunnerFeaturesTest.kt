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

import app.cash.paparazzi.runnerfixtures.AssumptionFixture
import app.cash.paparazzi.runnerfixtures.ClassLifecycleFixture
import app.cash.paparazzi.runnerfixtures.ClassRuleFixture
import app.cash.paparazzi.runnerfixtures.CustomRunnerFixture
import app.cash.paparazzi.runnerfixtures.ExpectedExceptionFixture
import app.cash.paparazzi.runnerfixtures.FilterFixture
import app.cash.paparazzi.runnerfixtures.IgnoreFixture
import app.cash.paparazzi.runnerfixtures.TimeoutFixture
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.manipulation.Filter
import org.junit.runners.BlockJUnit4ClassRunner

/**
 * Covers the JUnit features that a hand-written `methodBlock` override would silently drop.
 *
 * Every one of these works because [PaparazziRunner] delegates to a real JUnit runner built around
 * the sandboxed class, rather than reassembling statements itself. `@BeforeClass` and `@ClassRule`
 * are the clearest examples: they are assembled by `ParentRunner.classBlock`, which a `methodBlock`
 * override never touches, so they would have run against the host's classes.
 *
 * The fixtures assert from the inside and some fail deliberately, so they are run through
 * [JUnitCore] here rather than being collected by Gradle.
 */
class PaparazziRunnerFeaturesTest {
  @Test
  fun ignoredTestsAreSkippedNotRun() {
    val result = JUnitCore.runClasses(IgnoreFixture::class.java)

    assertThat(result.runCount).isEqualTo(1)
    assertThat(result.ignoreCount).isEqualTo(1)
    assertThat(result.failures).isEmpty()
  }

  @Test
  fun expectedExceptionsAreHonoured() {
    val result = JUnitCore.runClasses(ExpectedExceptionFixture::class.java)

    assertThat(result.failures).isEmpty()
  }

  @Test
  fun timeoutsFireAndTheTimeoutThreadStaysInTheSandbox() {
    val result = JUnitCore.runClasses(TimeoutFixture::class.java)

    assertThat(result.runCount).isEqualTo(2)
    // exceedsTimeout must fail; withinTimeoutStaysInTheSandbox must not.
    assertThat(result.failures.map { it.description.methodName }).containsExactly("exceedsTimeout")
    assertThat(result.failures.single().message).contains("timed out")
  }

  @Test
  fun failedAssumptionsSkipRatherThanFail() {
    val result = JUnitCore.runClasses(AssumptionFixture::class.java)

    assertThat(result.failures).isEmpty()
  }

  @Test
  fun beforeClassAndAfterClassRunInsideTheSandbox() {
    val result = JUnitCore.runClasses(ClassLifecycleFixture::class.java)

    assertThat(result.failures.map { it.message }).isEmpty()
    assertThat(result.runCount).isEqualTo(1)
  }

  @Test
  fun classRulesRunInsideTheSandbox() {
    val result = JUnitCore.runClasses(ClassRuleFixture::class.java)

    assertThat(result.failures.map { it.message }).isEmpty()
    assertThat(result.runCount).isEqualTo(1)
  }

  @Test
  fun delegateRunnerCanBeReplacedAndIsItselfSandboxed() {
    val result = JUnitCore.runClasses(CustomRunnerFixture::class.java)

    assertThat(result.failures.map { it.message }).isEmpty()
    assertThat(result.runCount).isEqualTo(1)
  }

  @Test
  fun filteringSelectsASingleTest() {
    val runner = PaparazziRunner(FilterFixture::class.java)

    runner.filter(
      Filter.matchMethodDescription(
        Description.createTestDescription(FilterFixture::class.java, "second")
      )
    )

    // Without Filterable delegation, Gradle's --tests would report "no tests found".
    assertThat(runner.testCount()).isEqualTo(1)
    assertThat(runner.description.children.single().methodName).isEqualTo("second")
  }

  @Test
  fun descriptionUsesTheOriginalClassNameSoReportingIsUnaffected() {
    val runner = PaparazziRunner(FilterFixture::class.java)

    assertThat(runner.description.className).isEqualTo(FilterFixture::class.java.name)
    assertThat(runner.description.children.map { it.methodName })
      .containsExactly("first", "second")
  }

  @Test
  fun negativeControl_theSandboxAssertionsAreNotVacuous() {
    // Run the same fixture through a plain JUnit runner, bypassing PaparazziRunner entirely. Its
    // @BeforeClass must then fail, which is what proves the passing cases above mean something.
    val result = JUnitCore().run(BlockJUnit4ClassRunner(ClassLifecycleFixture::class.java))

    assertThat(result.failures).isNotEmpty()
    assertThat(result.failures.first().message).contains("expected the sandbox loader")
  }
}
