package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.ImageSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Before
import org.junit.Ignore
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
        "The Android Gradle library plugin must be applied for Paparazzi to be configured."
    )
  }

  @Test
  fun declareAndroidPluginAfter() {
    val fixtureRoot = File("src/test/projects/declare-android-plugin-after")

    val result = gradleRunner
        .withArguments("preparePaparazziDebugResources", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
  }

  @Test
  fun missingPlatformDirTest() {
    val fixtureRoot = File("src/test/projects/missing-platform-dir")

    val result = gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .forwardOutput()
        .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":testDebug")).isNull()
    assertThat(result.output).contains("java.io.FileNotFoundException")
    assertThat(result.output).contains("platforms" + File.separator + "android-oops")
  }

  @Test
  fun flagDebugLinkedObjectsIsOff() {
    val fixtureRoot = File("src/test/projects/flag-debug-linked-objects-off")

    val result = gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.output).doesNotContain("Objects still linked from the DelegateManager:")
  }

  @Test
  fun flagDebugLinkedObjectsIsOn() {
    val fixtureRoot = File("src/test/projects/flag-debug-linked-objects-on")

    val result = gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.output).contains("Objects still linked from the DelegateManager:")
  }

  @Test
  fun cacheable() {
    val fixtureRoot = File("src/test/projects/cacheable")

    val firstRun = gradleRunner
        .withArguments("testDebug", "--build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isNotEqualTo(FROM_CACHE)
    }

    fixtureRoot.resolve("build").deleteRecursively()

    val secondRun = gradleRunner
        .withArguments("testDebug", "--build-cache", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE)
    }

    fixtureRoot.resolve("build-cache").deleteRecursively()
  }

  @Test
  fun configurationCache() {
    val fixtureRoot = File("src/test/projects/configuration-cache")

    // check to avoid plugin regressions that might affect Gradle's configuration caching
    // https://docs.gradle.org/current/userguide/configuration_cache.html
    gradleRunner
      .withArguments("testDebug", "--configuration-cache", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun interceptViewEditMode() {
    val fixtureRoot = File("src/test/projects/edit-mode-intercept")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun record() {
    val fixtureRoot = File("src/test/projects/record-mode")

    val result = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")

    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")
    assertThat(snapshot.exists()).isTrue()

    val snapshotWithLabel = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record_label.png")
    assertThat(snapshotWithLabel.exists()).isTrue()

    snapshotsDir.deleteRecursively()
  }

  @Test
  fun recordAllVariants() {
    val fixtureRoot = File("src/test/projects/record-mode")

    val result = gradleRunner
            .withArguments("recordPaparazzi", "--stacktrace")
            .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":recordPaparazziDebug")).isNotNull()
    assertThat(result.task(":recordPaparazziRelease")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")
    snapshotsDir.deleteRecursively()
  }

  @Test
  fun recordMultiModuleProject() {
    val fixtureRoot = File("src/test/projects/record-mode-multiple-modules")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
        .withArguments("module:recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot, moduleRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(moduleRoot, "src/test/snapshots")

    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")
    assertThat(snapshot.exists()).isTrue()

    val snapshotWithLabel = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record_label.png")
    assertThat(snapshotWithLabel.exists()).isTrue()

    snapshotsDir.deleteRecursively()
  }

  @Test
  fun recordModeSingleTestOfMany() {
    val fixtureRoot = File("src/test/projects/record-mode-multiple-tests")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
        .withArguments("module:recordPaparazziDebug", "--tests=*recordSecond", "--stacktrace")
        .runFixture(fixtureRoot, moduleRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(moduleRoot, "src/test/snapshots")

    val firstSnapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_recordFirst.png")
    assertThat(firstSnapshot.exists()).isFalse()

    val secondSnapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_recordSecond_label.png")
    assertThat(secondSnapshot.exists()).isTrue()

    snapshotsDir.deleteRecursively()
  }

  @Test
  fun rerunOnResourceChange() {
    val fixtureRoot = File("src/test/projects/rerun-resource-change")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")
    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")

    val valuesDir = File(fixtureRoot, "src/main/res/values/")
    val destResourceFile = File(valuesDir, "colors.xml")
    val firstResourceFile = File(fixtureRoot, "src/test/resources/colors1.xml")
    val secondResourceFile = File(fixtureRoot, "src/test/resources/colors2.xml")

    // Original resource
    firstResourceFile.copyTo(destResourceFile, overwrite = false)

    // Take 1
    val firstRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(firstRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    assertThat(snapshot.exists()).isTrue()

    val firstRunBytes = snapshot.readBytes()

    // Update resource
    secondResourceFile.copyTo(destResourceFile, overwrite = true)

    // Take 2
    val secondRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(secondRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }
    assertThat(snapshot.exists()).isTrue()

    val secondRunBytes = snapshot.readBytes()

    // should be different colors
    assertThat(firstRunBytes).isNotEqualTo(secondRunBytes)

    snapshotsDir.deleteRecursively()
    valuesDir.deleteRecursively()
  }

  @Test
  fun rerunOnAssetChange() {
    val fixtureRoot = File("src/test/projects/rerun-asset-change")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")
    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")

    val assetsDir = File(fixtureRoot, "src/main/assets/")
    val destAssetFile = File(assetsDir, "secret.txt")
    val firstAssetFile = File(fixtureRoot, "src/test/resources/secret1.txt")
    val secondAssetFile = File(fixtureRoot, "src/test/resources/secret2.txt")

    // Original asset
    firstAssetFile.copyTo(destAssetFile, overwrite = false)

    // Take 1
    val firstRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(firstRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    assertThat(snapshot.exists()).isTrue()

    val firstRunBytes = snapshot.readBytes()

    // Update asset
    secondAssetFile.copyTo(destAssetFile, overwrite = true)

    // Take 2
    val secondRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(secondRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }
    assertThat(snapshot.exists()).isTrue()

    val secondRunBytes = snapshot.readBytes()

    // should be different
    assertThat(firstRunBytes).isNotEqualTo(secondRunBytes)

    snapshotsDir.deleteRecursively()
    assetsDir.deleteRecursively()
  }

  @Test
  fun rerunOnReportDeletion() {
    val fixtureRoot = File("src/test/projects/rerun-report")

    val reportDir = File(fixtureRoot, "build/reports/paparazzi")
    val reportHtml = File(reportDir, "index.html")
    assertThat(reportHtml.exists()).isFalse()

    // Take 1
    val firstRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .forwardOutput()
        .runFixture(fixtureRoot) { build() }

    with(firstRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    assertThat(reportHtml.exists()).isTrue()

    // Remove report
    reportDir.deleteRecursively()

    // Take 2
    val secondRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(secondRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }
    assertThat(reportHtml.exists()).isTrue()

    reportDir.deleteRecursively()

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")
    snapshotsDir.deleteRecursively()
  }

  @Test
  fun rerunOnSnapshotDeletion() {
    val fixtureRoot = File("src/test/projects/rerun-snapshots")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots")
    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")
    assertThat(snapshot.exists()).isFalse()

    // Take 1
    val firstRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .forwardOutput()
        .runFixture(fixtureRoot) { build() }

    with(firstRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    assertThat(snapshot.exists()).isTrue()

    // Remove snapshot
    snapshotsDir.deleteRecursively()

    // Take 2
    val secondRunResult = gradleRunner
        .withArguments("recordPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    with(secondRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }
    assertThat(snapshot.exists()).isTrue()

    snapshotsDir.deleteRecursively()
  }

  @Test
  fun verifySuccess() {
    val fixtureRoot = File("src/test/projects/verify-mode-success")

    val result = gradleRunner
        .withArguments("verifyPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }

  @Test
  fun verifyAllVariants() {
    val fixtureRoot = File("src/test/projects/verify-mode-success")

    val result = gradleRunner
        .withArguments("verifyPaparazzi", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":verifyPaparazziDebug")).isNotNull()
    assertThat(result.task(":verifyPaparazziRelease")).isNotNull()
  }

  @Test
  fun verifyFailure() {
    val fixtureRoot = File("src/test/projects/verify-mode-failure")

    val result = gradleRunner
        .withArguments("verifyPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val failureDir = File(fixtureRoot, "out/failures")
    val delta = File(failureDir, "delta-app.cash.paparazzi.plugin.test_VerifyTest_verify.png")
    assertThat(delta.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/expected_delta.png")
    assertThat(delta).isSimilarTo(goldenImage).withDefaultThreshold()

    failureDir.deleteRecursively()
  }

  @Test
  fun verifySuccessMultiModule() {
    val fixtureRoot = File("src/test/projects/verify-mode-success-multiple-modules")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
        .withArguments("module:verifyPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot, moduleRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()
  }

  @Test
  fun verifyFailureMultiModule() {
    val fixtureRoot = File("src/test/projects/verify-mode-failure-multiple-modules")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
        .withArguments("module:verifyPaparazziDebug", "--stacktrace")
        .runFixture(fixtureRoot, moduleRoot) { buildAndFail() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val failureDir = File(moduleRoot, "out/failures")
    val delta = File(failureDir, "delta-app.cash.paparazzi.plugin.test_VerifyTest_verify.png")
    assertThat(delta.exists()).isTrue()

    val goldenImage = File(moduleRoot, "src/test/resources/expected_delta.png")
    assertThat(delta).isSimilarTo(goldenImage).withDefaultThreshold()

    failureDir.deleteRecursively()
  }

  @Test
  fun verifyRenderingModes() {
    val fixtureRoot = File("src/test/projects/verify-rendering-modes")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles().apply { sortBy { it.lastModified() }}
    assertThat(snapshots!!).hasLength(3)

    val normal = File(fixtureRoot, "src/test/resources/normal.png")
    val horizontalScroll = File(fixtureRoot, "src/test/resources/horizontal_scroll.png")
    val verticalScroll = File(fixtureRoot, "src/test/resources/vertical_scroll.png")

    assertThat(snapshots[0]).isSimilarTo(normal).withDefaultThreshold()
    assertThat(snapshots[1]).isSimilarTo(horizontalScroll).withDefaultThreshold()
    assertThat(snapshots[2]).isSimilarTo(verticalScroll).withDefaultThreshold()
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
    assertThat(resourceFileContents[0]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[1]).isEqualTo("build/intermediates/merged_res/debug")
    assertThat(resourceFileContents[4]).isEqualTo("build/intermediates/merged_assets/debug/out")
    assertThat(resourceFileContents[6]).isEqualTo("app.cash.paparazzi.plugin.test")
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
    assertThat(resourceFileContents[0]).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(resourceFileContents[1]).isEqualTo("build/intermediates/merged_res/debug")
    assertThat(resourceFileContents[4]).isEqualTo("build/intermediates/merged_assets/debug/out")
    assertThat(resourceFileContents[6]).isEqualTo("app.cash.paparazzi.plugin.test")
  }

  @Test
  fun verifyTargetSdkIsSameAsCompileSdk() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
        .withArguments("compileDebugUnitTestJavaWithJavac", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[2]).isEqualTo("30")
    assertThat(resourceFileContents[3]).isEqualTo("platforms/android-30/")
  }

  @Test
  fun verifyTargetSdkIsDifferentFromCompileSdk() {
    val fixtureRoot = File("src/test/projects/different-target-sdk")

    val result = gradleRunner
        .withArguments("compileDebugUnitTestJavaWithJavac", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.txt")
    assertThat(resourcesFile.exists()).isTrue()

    val resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents[2]).isEqualTo("29")
    assertThat(resourceFileContents[3]).isEqualTo("platforms/android-30/")
  }

  @Test
  fun verifyOpenAssets() {
    val fixtureRoot = File("src/test/projects/open-assets")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun openTransitiveAssets() {
    val fixtureRoot = File("src/test/projects/open-transitive-assets")
    val moduleRoot = File(fixtureRoot, "consumer")

    gradleRunner
      .withArguments("consumer:testDebug", "--stacktrace")
      .runFixture(fixtureRoot, moduleRoot) { build() }
  }

  @Test
  fun verifySnapshot() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/launch.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyVectorDrawables() {
    val fixtureRoot = File("src/test/projects/verify-svgs")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_up.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun withoutAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-missing")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshotFile = File(snapshotsDir, "6dc7ccef5e1decc563e334e3d809fd68e6f6b122.png")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_present.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  @Ignore
  fun withMaterialComponents() {
    val fixtureRoot = File("src/test/projects/material-components-present")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/button.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun customFontsInXml() {
    val fixtureRoot = File("src/test/projects/custom-fonts-xml")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/textviews.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun textAppearancesInCode() {
    val fixtureRoot = File("src/test/projects/text-appearances-code")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/textappearances.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun textAppearancesInXml() {
    val fixtureRoot = File("src/test/projects/text-appearances-xml")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/textappearances.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyAaptAttrResourceParsingInCode() {
    val fixtureRoot = File("src/test/projects/verify-aapt-code")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/card_chip.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyAaptAttrResourceParsingInXml() {
    val fixtureRoot = File("src/test/projects/verify-aapt-xml")

    gradleRunner
        .withArguments("testDebug", "--stacktrace")
        .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/card_chip.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun nonTransitiveResources() {
    val fixtureRoot = File("src/test/projects/non-transitive-resources")
    val moduleRoot = File(fixtureRoot, "module")

    gradleRunner
      .withArguments("module:testDebug", "--stacktrace")
      .runFixture(fixtureRoot, moduleRoot) { build() }

    val snapshotsDir = File(moduleRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(moduleRoot, "src/test/resources/five_bucks.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun nonTransitiveResourcesNoDeps() {
    val fixtureRoot = File("src/test/projects/non-transitive-resources-no-deps")

    val result = gradleRunner
      .withArguments("testDebug")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.output).doesNotContain("java.lang.ClassNotFoundException")
  }

  @Test
  fun compose() {
    val fixtureRoot = File("src/test/projects/compose")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/compose_fonts.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  private fun GradleRunner.runFixture(
    projectRoot: File,
    moduleRoot: File = projectRoot,
    action: GradleRunner.() -> BuildResult
  ): BuildResult {
    val settings = File(projectRoot, "settings.gradle")
    if (!settings.exists()) {
      settings.createNewFile()
      settings.deleteOnExit()
    }

    val mainSourceRoot = File(moduleRoot, "src/main")
    val manifest = File(mainSourceRoot, "AndroidManifest.xml")
    if (!mainSourceRoot.exists() || !manifest.exists()) {
      mainSourceRoot.mkdirs()
      manifest.createNewFile()
      manifest.writeText("""<manifest package="app.cash.paparazzi.plugin.test"/>""")
      manifest.deleteOnExit()
    }

    val gradleProperties = File(projectRoot, "gradle.properties")
    if (!gradleProperties.exists()) {
      gradleProperties.createNewFile()
      gradleProperties.writeText("android.useAndroidX=true")
      gradleProperties.deleteOnExit()
    }

    return withProjectDir(projectRoot).action()
  }
}
