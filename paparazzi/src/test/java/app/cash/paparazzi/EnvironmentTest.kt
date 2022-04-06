package app.cash.paparazzi

import java.io.File
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnvironmentTest {
  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  private lateinit var resourceFile: File
  private val androidSdkRoot = androidHome() // Not sure how to fake System.env
  private val currentDir = "/tmp/testDir"
  private val relativePlatformPath = "platforms/android-32/"
  private val relativeResDir = "./build/intermediates/merged_res"
  private val relativeAssetsDir = "./build/intermediates/merged_assets"
  private val platformDataDir = "./build/intermediates/paparazzi/platform-32"
  private val customBuildDir = "/tmp/custom-build-dir/"
  private val expected = Environment(
    platformDir = Paths.get(androidSdkRoot).resolve(relativePlatformPath).toString(),
    reportOutputDir = customBuildDir,
    appTestDir = currentDir,
    resDir = "$currentDir/$relativeResDir",
    assetsDir = "$currentDir/$relativeAssetsDir",
    packageName = "com.example",
    compileSdkVersion = 29,
    platformDataDir = platformDataDir,
    resourcePackageNames = listOf("com.example", "com.example.feature1", "com.example.feature2"),
  )

  @Before
  fun setup() {
    resourceFile = tempFolder.newFile()
    resourceFile.bufferedWriter().use {
      it.write(expected.packageName)
      it.newLine()
      it.write(Paths.get(expected.appTestDir).resolve(relativeResDir).toString())
      it.newLine()
      it.write(expected.compileSdkVersion.toString())
      it.newLine()
      it.write(relativePlatformPath)
      it.newLine()
      it.write(Paths.get(expected.appTestDir).resolve(relativeAssetsDir).toString())
      it.newLine()
      it.write(platformDataDir)
      it.newLine()
      it.write(expected.resourcePackageNames.joinToString(","))
      it.newLine()
      it.write(customBuildDir)
      it.newLine()
    }

    System.setProperty("paparazzi.test.resources", resourceFile.absolutePath)
    System.setProperty("user.dir", expected.appTestDir)
  }

  @Test
  fun happyPath() {
    val actual = detectEnvironment()
    assertThat(actual).isEqualTo(expected)
  }
}