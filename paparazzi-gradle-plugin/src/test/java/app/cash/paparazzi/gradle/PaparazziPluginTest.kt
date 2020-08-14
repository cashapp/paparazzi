package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.ImageSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
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
  }

  @Test
  fun missingPlugin() {
    val fixtureRoot = File("src/test/projects/missing-plugin")

    val result = gradleRunner
        .withArguments("preparePaparazziDebugResources", "--stacktrace")
        .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNull()
    assertThat(result.output).contains(
        "The Android Gradle library/application plugin must be applied before the Paparazzi plugin."
    )
  }

  @Test
  fun verifyResourcesGeneratedForJavaProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
        .withArguments("compileDebugUnitTestJavaWithJavac", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Library")
    assertThat(resourceFileContents[1]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[2]).endsWith(
        "src/test/projects/verify-resources-java/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifyResourcesGeneratedForKotlinProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-kotlin")

    val result = gradleRunner
        .withArguments("compileDebugUnitTestKotlin", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Library")
    assertThat(resourceFileContents[1]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[2]).endsWith(
        "src/test/projects/verify-resources-kotlin/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifyResourcesGeneratedForKotlinAppProject() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-app")

    val result = gradleRunner
        .withArguments("compileDebugUnitTestKotlin", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugAppResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Application")
    assertThat(resourceFileContents[1]).isEqualTo("app.cash.paparazzi.plugin.test")
    //for apps, the res-dir is where we copy the resources to (from the APK)
    assertThat(resourceFileContents[2]).endsWith(
        "src/test/projects/verify-snapshot-app/build/intermediates/paparazzi/debug/apk_dump"
    )
    assertThat(resourceFileContents[3]).isEqualTo("28")
    //android SDK location
    assertThat(resourceFileContents[4]).endsWith("/platforms/android-28/")
    //report location
    assertThat(resourceFileContents[5]).endsWith("src/test/projects/verify-snapshot-app/build/reports/paparazzi/debugUnitTest")
    //apk location
    assertThat(resourceFileContents[6]).endsWith("src/test/projects/verify-snapshot-app/build/outputs/apk/debug/verify-snapshot-app-debug.apk")
    //merged values location
    assertThat(resourceFileContents[7]).endsWith("src/test/projects/verify-snapshot-app/build/intermediates/incremental/mergeDebugResources/merged.dir")

    val copiedValues = File(fixtureRoot, "build/intermediates/paparazzi/debug/apk_dump/res/values/values.xml")
    assertThat(copiedValues).exists()
    assertThat(copiedValues.isFile).isTrue()
  }

  @Test
  fun verifySnapshot_withoutFonts() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
            .withArguments("testDebug", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "8a7d289fef47bf8f177554eaa491fcfdf4fe1edf.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/launch_without_fonts.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun verifySnapshotForApp() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-app")

    val result = gradleRunner
            .withArguments("testDebug", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugAppResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "8a7d289fef47bf8f177554eaa491fcfdf4fe1edf.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/launch_without_fonts.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun verifySnapshot() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "8a7d289fef47bf8f177554eaa491fcfdf4fe1edf.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/launch_without_fonts.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun verifyVectorDrawables() {
    val fixtureRoot = File("src/test/projects/verify-svgs")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "c955e956af7c3127cffd96b9b7160aa609cfde23.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_up.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun withoutAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-missing")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "f76880c9f1f1fcae4d3cfcc870496b9abe3ee81b.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_missing.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun withAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-present")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "be373e00b3ff3a319e40b9d798b1d2a1e40dec7d.png")
    assertThat(snapshotFile.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_present.png")
    val actualFileBytes = Files.readAllBytes(snapshotFile.toPath())
    val expectedFileBytes = Files.readAllBytes(goldenImage.toPath())

    assertThat(actualFileBytes).isEqualTo(expectedFileBytes)
  }

  @Test
  fun customFontsInXml() {
    val fixtureRoot = File("src/test/projects/custom-fonts-xml")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/textviews.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun customFontsInCode() {
    val fixtureRoot = File("src/test/projects/custom-fonts-code")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/textviews.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  private fun GradleRunner.runFixture(
    root: File,
    action: GradleRunner.() -> BuildResult
  ): BuildResult {
    val settings = File(root, "settings.gradle")
    if (!settings.exists()) {
      settings.createNewFile()
      settings.deleteOnExit()
    }

    val mainSourceRoot = File(root, "src/main")
    val manifest = File(mainSourceRoot, "AndroidManifest.xml")
    if (!manifest.exists()) {
      mainSourceRoot.mkdirs()
      manifest.createNewFile()
      manifest.writeText("""<manifest package="app.cash.paparazzi.plugin.test"/>""")
      manifest.deleteOnExit()
    }

    val gradleProperties = File(root, "gradle.properties")
    if (!gradleProperties.exists()) {
      gradleProperties.createNewFile()
      gradleProperties.writeText("android.useAndroidX=true")
      gradleProperties.deleteOnExit()
    }

    return withProjectDir(root).action()
  }
}