package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.ImageSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Test
import java.io.File

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
        .withArguments("clean", "preparePaparazziDebugResources", "--no-build-cache", "--stacktrace")
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
        .withArguments("clean", "compileDebugUnitTestJavaWithJavac", "--no-build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Library")
    assertThat(resourceFileContents[1]).isEqualTo("VerifyAgainstGolden")
    assertThat(resourceFileContents[2]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[3]).endsWith(
        "src/test/projects/verify-resources-java/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifyResourcesGeneratedForJavaProjectOverride() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
        .withArguments("clean", "compileDebugUnitTestJavaWithJavac", "--stacktrace", "-PPAPARAZZI_OVERWRITE_GOLDEN")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Library")
    assertThat(resourceFileContents[1]).isEqualTo("GenerateToGolden")
    assertThat(resourceFileContents[2]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[3]).endsWith(
        "src/test/projects/verify-resources-java/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifyResourcesGeneratedForKotlinProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-kotlin")

    val result = gradleRunner
        .withArguments("clean", "compileDebugUnitTestKotlin", "--no-build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Library")
    assertThat(resourceFileContents[1]).isEqualTo("VerifyAgainstGolden")
    assertThat(resourceFileContents[2]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[3]).endsWith(
        "src/test/projects/verify-resources-kotlin/build/intermediates/res/merged/debug"
    )
  }

  @Test
  fun verifyResourcesGeneratedForKotlinAppProject() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-app")

    val result = gradleRunner
        .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugAppResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[0]).isEqualTo("Application")
    assertThat(resourceFileContents[2]).isEqualTo("app.cash.paparazzi.plugin.test")
    //for apps, the res-dir is where we copy the resources to (from the APK)
    assertThat(resourceFileContents[3]).endsWith(
        "src/test/projects/verify-snapshot-app/build/intermediates/paparazzi/debug/apk_dump/res"
    )
    assertThat(resourceFileContents[4]).endsWith(
            "src/test/projects/verify-snapshot-app/build/intermediates/paparazzi/debug/apk_dump/assets"
    )
    assertThat(resourceFileContents[5]).isEqualTo("28")
    //android SDK location
    assertThat(resourceFileContents[6]).endsWith("/platforms/android-28/")
    //report location
    assertThat(resourceFileContents[7]).endsWith("src/test/projects/verify-snapshot-app/build/reports/paparazzi/debugUnitTest")
    //golden images
    assertThat(resourceFileContents[8]).endsWith("paparazzi/paparazzi-gradle-plugin/src/test/projects/verify-snapshot-app/paparazzi/debugUnitTest")
    //apk location
    assertThat(resourceFileContents[9]).endsWith("src/test/projects/verify-snapshot-app/build/outputs/apk/debug/verify-snapshot-app-debug.apk")
    //merged values location
    assertThat(resourceFileContents[10]).endsWith("src/test/projects/verify-snapshot-app/build/intermediates/incremental/mergeDebugResources/merged.dir")

    val copiedValues = File(fixtureRoot, "build/intermediates/paparazzi/debug/apk_dump/res/values/values.xml")
    assertThat(copiedValues).exists()
    assertThat(copiedValues.isFile).isTrue()
  }

  @Test
  fun verifySnapshot_withoutFonts() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun verifySnapshotForApp() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-app")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugAppResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun verifySnapshot() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
        .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun failIfMissingGolden() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-missing-golden")

    val goldenImage = File(fixtureRoot, "paparazzi/debugUnitTest/app.cash.paparazzi.plugin.test.LaunchViewTest/testViews/launch.png")
    goldenImage.delete()

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")!!.outcome).isEqualTo(TaskOutcome.FAILED)

    assertThat(goldenImage.exists()).isFalse()
  }

  @Test
  fun generateGoldenImage() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-missing-golden")

    val goldenImagesFolder = File(fixtureRoot, "paparazzi/debugUnitTest")
    goldenImagesFolder.deleteRecursively()

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--stacktrace", "-PPAPARAZZI_OVERWRITE_GOLDEN")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    assertThat(goldenImagesFolder.exists()).isTrue()
  }

  @Test
  fun failIfGoldenImageNotTheSame() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-wrong-golden")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.FAILED)
  }

  @Test
  fun passIfGoldenImageIsTheSame() {
    val fixtureRoot = File("src/test/projects/verify-snapshot-matched-golden")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun verifyVectorDrawables() {
    val fixtureRoot = File("src/test/projects/verify-svgs")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun withoutAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-missing")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "083264a016d1047ef71c856ba97ef283f139bf8c.png")
    assertThat(snapshotFile.exists()).isTrue()
  }

  @Test
  fun withAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-present")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshotFile = File(snapshotsDir, "e116180609e8a6879f3ac4174a80c74a5303b987.png")
    assertThat(snapshotFile.exists()).isTrue()
  }

  @Test
  fun customFontsInXml() {
    val fixtureRoot = File("src/test/projects/custom-fonts-xml")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)
  }

  @Test
  fun customFontsInCode() {
    val fixtureRoot = File("src/test/projects/custom-fonts-code")

    val result = gradleRunner
            .withArguments("clean", "testDebug", "--no-build-cache", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debugUnitTest/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)
  }

  private fun GradleRunner.runFixture(
    root: File,
    action: GradleRunner.() -> BuildResult
  ): BuildResult {
    File(root, "build").deleteRecursively()

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