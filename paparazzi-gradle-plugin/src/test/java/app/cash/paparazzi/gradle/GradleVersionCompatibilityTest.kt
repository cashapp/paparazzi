package app.cash.paparazzi.gradle

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(TestParameterInjector::class)
class GradleVersionCompatibilityTest {
  @TestParameter("9.1.0", "9.4.1")
  lateinit var gradleVersion: String

  private lateinit var gradleRunner: GradleRunner

  @Before
  fun setUp() {
    gradleRunner = GradleRunner.create()
      .withPluginClasspath()
      .withGradleVersion(gradleVersion)
  }

  @Test
  fun verifySuccess() {
    val fixtureRoot = File("src/test/projects/verify-mode-success")

    val result = gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }
}
