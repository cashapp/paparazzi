package app.cash.paparazzi.gradle

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PaparazziPluginTest {
  private lateinit var gradleRunner: GradleRunner
  @Before
  fun setUp() {
    gradleRunner = GradleRunner.create()
        .withPluginClasspath()
        .withArguments("preparePaparazziDebugResources", "--stacktrace")
  }

  @Test
  fun missingPlugin() {
    val fixtureRoot = File("src/test/projects/missing-plugin")

    val result = gradleRunner.runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNull()
    assertThat(result.output).contains(
        "The Android Gradle library plugin must be applied before the Paparazzi plugin."
    )
  }

  @Test
  fun verifyResources() {
    val fixtureRoot = File("src/test/projects/verify-resources")

    val result = gradleRunner.runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[1]).endsWith(
        "src/test/projects/verify-resources/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifySnapshot() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner.runFixture(fixtureRoot) {
      withArguments("testDebug")
          .build()
    }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshotFile = File(snapshotsDir, "06eed37f8377a96128efdbfd47e28b24ecac09e6.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/launch.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  private fun GradleRunner.runFixture(
    root: File,
    action: GradleRunner.() -> BuildResult
  ): BuildResult {
    var generatedSettings = false
    val settings = File(root, "settings.gradle")
    var generatedGradleProperties = false
    val gradleProperties = File(root, "gradle.properties")
    return try {
      if (!settings.exists()) {
        settings.createNewFile()
        generatedSettings = true
      }

      if (!gradleProperties.exists()) {
        val rootGradleProperties = File("../gradle.properties")
        if (!rootGradleProperties.exists()) {
          fail("Root gradle.properties doesn't exist at $rootGradleProperties.")
        }
        val versionName = rootGradleProperties.useLines { lines ->
          lines.firstOrNull { it.startsWith("VERSION_NAME") }
        }
        if (versionName == null) {
          fail("Root gradle.properties is missing the VERSION_NAME entry.")
        }
        gradleProperties.createNewFile()
        gradleProperties.writeText(versionName!!)
        generatedGradleProperties = true
      } else {
        gradleProperties.useLines { lines ->
          if (lines.none { it.startsWith("VERSION_NAME") }) {
            fail("Fixture's gradle.properties has to include the VERSION_NAME entry.")
          }
        }
      }

      withProjectDir(root).action()
    } finally {
      if (generatedSettings) settings.delete()
      if (generatedGradleProperties) gradleProperties.delete()
    }
  }
}