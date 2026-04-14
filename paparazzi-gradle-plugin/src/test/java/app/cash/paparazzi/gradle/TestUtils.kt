package app.cash.paparazzi.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

fun GradleRunner.runFixture(projectRoot: File, action: GradleRunner.() -> BuildResult): BuildResult {
  val settings = File(projectRoot, "settings.gradle")
  val gradleProperties = File(projectRoot, "gradle.properties")
  var generatedSettings = false
  var generatedGradleProperties = false

  return try {
    if (!settings.exists()) {
      settings.createNewFile()
      settings.writeText("apply from: \"../test.settings.gradle\"")
      generatedSettings = true
    }

    if (!gradleProperties.exists()) {
      gradleProperties.createNewFile()
      gradleProperties.writeText(
        """
          |android.useAndroidX=true
          |android.dependencyResolutionAtConfigurationTime.disallow=true
        """.trimMargin()
      )
      generatedGradleProperties = true
    }

    withProjectDir(projectRoot).action()
  } finally {
    if (generatedSettings) settings.delete()
    if (generatedGradleProperties) gradleProperties.delete()
  }
}
