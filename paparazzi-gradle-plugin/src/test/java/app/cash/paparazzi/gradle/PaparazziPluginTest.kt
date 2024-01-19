package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.ImageSubject.Companion.assertThat
import app.cash.paparazzi.gradle.PrepareResourcesTask.Config
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.source
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PaparazziPluginTest {
  private val filesToDelete = mutableListOf<File>()

  private lateinit var gradleRunner: GradleRunner

  @Before
  fun setUp() {
    gradleRunner = GradleRunner.create()
      .withPluginClasspath()
  }

  @After
  fun tearDown() {
    filesToDelete.forEach(File::deleteRecursively)
  }

  @Test
  fun androidApplicationPlugin() {
    val fixtureRoot = File("src/test/projects/supports-application-modules")

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
    assertThat(result.output).doesNotContain(
      "Currently, Paparazzi only works in Android library -- not application -- modules. " +
        "See https://github.com/cashapp/paparazzi/issues/107"
    )

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/launch.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun androidDynamicFeaturePlugin() {
    val fixtureRoot = File("src/test/projects/supports-dynamic-feature-modules")

    val result = gradleRunner
      .withArguments(":dynamic_feature:testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":dynamic_feature:preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":dynamic_feature:testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "dynamic_feature/build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "dynamic_feature/src/test/resources/launch.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun missingSupportedPlugins() {
    val fixtureRoot = File("src/test/projects/missing-supported-plugins")

    val result = gradleRunner
      .withArguments("preparePaparazziDebugResources", "--stacktrace")
      .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNull()
    assertThat(result.output).contains(
      "One of com.android.application, com.android.library, com.android.dynamic-feature must be applied for Paparazzi to work properly."
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
  fun kotlinMultiplatformPluginWithAndroidTarget() {
    val fixtureRoot = File("src/test/projects/multiplatform-plugin-with-android")

    val result = gradleRunner
      .withArguments("preparePaparazziDebugResources", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
  }

  @Test
  fun kotlinMultiplatformPluginWithoutAndroidTarget() {
    val fixtureRoot = File("src/test/projects/multiplatform-plugin-without-android")

    val result = gradleRunner
      .withArguments("preparePaparazziDebugResources", "--stacktrace")
      .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNull()
    assertThat(result.output).contains(
      "There must be an Android target configured when using Paparazzi with the Kotlin Multiplatform Plugin"
    )
  }

  @Test
  fun excludeAndroidTestSourceSets() {
    val fixtureRoot = File("src/test/projects/exclude-androidtest")

    val result = gradleRunner
      .withArguments("preparePaparazziDebugResources", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
  }

  @Test
  fun prepareResourcesCaching() {
    val fixtureRoot = File("src/test/projects/prepare-resources-task-caching")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val firstRun = gradleRunner
      .withArguments("testRelease", "testDebug", "--build-cache", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isNotEqualTo(FROM_CACHE)
    }

    with(firstRun.task(":preparePaparazziReleaseResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isNotEqualTo(FROM_CACHE)
    }

    var resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()
    var resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents.any { it.contains("release") }).isFalse()

    resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/release/resources.json")
    assertThat(resourcesFile.exists()).isTrue()
    resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents.any { it.contains("debug") }).isFalse()

    // delete now (regardless of future cleanup)
    buildDir.deleteRecursively()

    val secondRun = gradleRunner
      .withArguments("testDebug", "--build-cache", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE)
    }

    resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()
    resourceFileContents = resourcesFile.readLines()
    assertThat(resourceFileContents.any { it.contains("release") }).isFalse()
  }

  @Test
  fun customBuildDir() {
    val fixtureRoot = File("src/test/projects/custom-build-dir")
    fixtureRoot.resolve("custom").registerForDeletionOnExit()

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "custom/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val snapshotsDir = File(fixtureRoot, "custom/reports/paparazzi/debug/images")
    assertThat(snapshotsDir.exists()).isTrue()
  }

  @Test
  fun customReportDir() {
    val fixtureRoot = File("src/test/projects/custom-report-dir")
    fixtureRoot.resolve("custom").registerForDeletionOnExit()

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val snapshotsDir = File(fixtureRoot, "custom/our-reports/paparazzi/debug/images")
    assertThat(snapshotsDir.exists()).isTrue()
  }

  @Test
  fun buildClassAccess() {
    val fixtureRoot = File("src/test/projects/build-class")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "custom/reports/paparazzi/debug/images")
    assertThat(snapshotsDir.exists()).isFalse()
  }

  @Test
  fun buildClassNextSdkAccess() {
    val fixtureRoot = File("src/test/projects/build-class-next-sdk")

    // Paparazzi detects Android platform dir contents to be static. Therefore, it re-runs only on
    // compileSdk changes.  Sandbox previews are an exception, so let's disable caching for this
    // test task.
    gradleRunner
      .withArguments("testDebug", "-Dorg.gradle.caching=false", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "custom/reports/paparazzi/debug/images")
    assertThat(snapshotsDir.exists()).isFalse()
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
    assertThat(result.output).contains("Missing platform version oops")
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
    // this is only a warning message, so subsequent runs would otherwise be UP-TO-DATE
    fixtureRoot.resolve("build").registerForDeletionOnExit()

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.output).contains("Objects still linked from the DelegateManager:")
  }

  @Test
  fun cacheable() {
    val fixtureRoot = File("src/test/projects/cacheable")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val firstRun = gradleRunner
      .withArguments("testDebug", "--build-cache", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isNotEqualTo(FROM_CACHE)
    }

    buildDir.deleteRecursively()

    val secondRun = gradleRunner
      .withArguments("testDebug", "--build-cache", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE)
    }
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

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")
    assertThat(snapshot.exists()).isTrue()

    val snapshotWithLabel = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record_label.png")
    assertThat(snapshotWithLabel.exists()).isTrue()
  }

  @Test
  fun recordAllVariants() {
    val fixtureRoot = File("src/test/projects/record-mode")
    File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

    val result = gradleRunner
      .withArguments("recordPaparazzi", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":recordPaparazziDebug")).isNotNull()
    assertThat(result.task(":recordPaparazziRelease")).isNotNull()
  }

  @Test
  fun recordMultiModuleProject() {
    val fixtureRoot = File("src/test/projects/record-mode-multiple-modules")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
      .withArguments("module:recordPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(moduleRoot, "src/test/snapshots").registerForDeletionOnExit()

    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")
    assertThat(snapshot.exists()).isTrue()

    val snapshotWithLabel = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record_label.png")
    assertThat(snapshotWithLabel.exists()).isTrue()
  }

  @Test
  fun recordModeSingleTestOfMany() {
    val fixtureRoot = File("src/test/projects/record-mode-multiple-tests")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
      .withArguments("module:recordPaparazziDebug", "--tests=*recordSecond", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(moduleRoot, "src/test/snapshots").registerForDeletionOnExit()

    val firstSnapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_recordFirst.png")
    assertThat(firstSnapshot.exists()).isFalse()

    val secondSnapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_recordSecond_label.png")
    assertThat(secondSnapshot.exists()).isTrue()
  }

  @Test
  fun rerunOnResourceChange() {
    val fixtureRoot = File("src/test/projects/rerun-resource-change")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()
    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")

    val valuesDir = File(fixtureRoot, "src/main/res/values/").registerForDeletionOnExit()
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
  }

  @Test
  fun rerunOnAssetChange() {
    val fixtureRoot = File("src/test/projects/rerun-asset-change")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()
    val snapshot = File(snapshotsDir, "images/app.cash.paparazzi.plugin.test_RecordTest_record.png")

    val assetsDir = File(fixtureRoot, "src/main/assets/").registerForDeletionOnExit()
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
  }

  @Test
  fun rerunOnReportDeletion() {
    val fixtureRoot = File("src/test/projects/rerun-report")
    val reportDir = File(fixtureRoot, "build/reports/paparazzi/debug").registerForDeletionOnExit()
    val reportHtml = File(reportDir, "index.html")
    assertThat(reportHtml.exists()).isFalse()

    File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

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
  }

  @Test
  fun rerunOnSnapshotDeletion() {
    val fixtureRoot = File("src/test/projects/rerun-snapshots")

    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()
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
  }

  @Test
  fun rerunTestsOnPropertyChange() {
    val fixtureRoot = File("src/test/projects/rerun-property-change")

    // Take 1
    val firstRunResult = gradleRunner
      .withArguments("testDebugUnitTest", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    // Take 2
    val secondRunResult = gradleRunner
      .withArguments("recordPaparazziDebug", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }

    // Take 3
    val thirdRunResult = gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(thirdRunResult.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // not UP-TO-DATE
    }
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

    val failureDir = File(fixtureRoot, "build/paparazzi/failures").registerForDeletionOnExit()
    val delta = File(failureDir, "delta-app.cash.paparazzi.plugin.test_VerifyTest_verify.png")
    assertThat(delta.exists()).isTrue()

    val goldenImage = File(fixtureRoot, "src/test/resources/expected_delta.png")
    assertThat(delta).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifySuccessMultiModule() {
    val fixtureRoot = File("src/test/projects/verify-mode-success-multiple-modules")

    val result = gradleRunner
      .withArguments("module:verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()
  }

  @Test
  fun verifyFailureMultiModule() {
    val fixtureRoot = File("src/test/projects/verify-mode-failure-multiple-modules")
    val moduleRoot = File(fixtureRoot, "module")

    val result = gradleRunner
      .withArguments("module:verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { buildAndFail() }

    assertThat(result.task(":module:testDebugUnitTest")).isNotNull()

    val failureDir = File(moduleRoot, "build/paparazzi/failures").registerForDeletionOnExit()
    val delta = File(failureDir, "delta-app.cash.paparazzi.plugin.test_VerifyTest_verify.png")
    assertThat(delta.exists()).isTrue()

    val goldenImage = File(moduleRoot, "src/test/resources/expected_delta.png")
    assertThat(delta).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyRenderingModes() {
    val fixtureRoot = File("src/test/projects/verify-rendering-modes")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles().apply { sortBy { it.lastModified() } }
    assertThat(snapshots!!).hasLength(3)

    val normal = File(fixtureRoot, "src/test/resources/normal.png")
    val horizontalScroll = File(fixtureRoot, "src/test/resources/horizontal_scroll.png")
    val verticalScroll = File(fixtureRoot, "src/test/resources/vertical_scroll.png")

    assertThat(snapshots[0]).isSimilarTo(normal).withDefaultThreshold()
    assertThat(snapshots[1]).isSimilarTo(horizontalScroll).withDefaultThreshold()
    assertThat(snapshots[2]).isSimilarTo(verticalScroll).withDefaultThreshold()
  }

  @Test
  fun widgets() {
    val fixtureRoot = File("src/test/projects/widgets")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val widgetImage = File(fixtureRoot, "src/test/resources/widget.png")
    val fullScreenImage = File(fixtureRoot, "src/test/resources/full_screen.png")
    assertThat(snapshots[0]).isSimilarTo(widgetImage).withDefaultThreshold()
    assertThat(snapshots[1]).isSimilarTo(fullScreenImage).withDefaultThreshold()
  }

  @Test
  fun lifecycleOwnerUsages() {
    val fixtureRoot = File("src/test/projects/lifecycle-usages")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(3)
  }

  @Test
  fun verifyResourcesGeneratedForJavaProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
      .withArguments(":consumer:compileDebugUnitTestJavaWithJavac", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":consumer:preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "consumer/build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val config = resourcesFile.loadConfig()
    assertThat(config.mainPackage).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(config.resourcePackageNames).containsExactly(
      "app.cash.paparazzi.plugin.test",
      "com.example.mylibrary",
      "app.cash.paparazzi.plugin.test.module1",
      "app.cash.paparazzi.plugin.test.module2"
    )
    assertThat(config.projectResourceDirs).containsExactly("src/main/res", "src/debug/res")
    assertThat(config.moduleResourceDirs).containsExactly(
      "../module1/build/intermediates/packaged_res/debug",
      "../module2/build/intermediates/packaged_res/debug"
    )
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external/res\$")
  }

  @Test
  fun verifyResourcesGeneratedForKotlinProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-kotlin")

    val result = gradleRunner
      .withArguments(":consumer:compileDebugUnitTestKotlin", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":consumer:preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "consumer/build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val config = resourcesFile.loadConfig()
    assertThat(config.mainPackage).isEqualTo("app.cash.paparazzi.plugin.test")
    assertThat(config.resourcePackageNames).containsExactly(
      "app.cash.paparazzi.plugin.test",
      "com.example.mylibrary",
      "app.cash.paparazzi.plugin.test.module1",
      "app.cash.paparazzi.plugin.test.module2"
    )
    assertThat(config.projectResourceDirs).containsExactly("src/main/res", "src/debug/res")
    assertThat(config.moduleResourceDirs).containsExactly(
      "../module1/build/intermediates/packaged_res/debug",
      "../module2/build/intermediates/packaged_res/debug"
    )
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external/res\$")
  }

  @Test
  fun verifyResourcesUpdatedWhenLocalResourceChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-local-resources-change")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val valuesDir = File(fixtureRoot, "src/main/res/values/").registerForDeletionOnExit()
    val destResourceFile = File(valuesDir, "colors.xml")
    val firstResourceFile = File(fixtureRoot, "src/test/resources/colors1.xml")
    val secondResourceFile = File(fixtureRoot, "src/test/resources/colors2.xml")

    // Original resource
    firstResourceFile.copyTo(destResourceFile, overwrite = false)

    val firstRun = gradleRunner
      .withArguments("testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    with(firstRun.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.projectResourceDirs).containsExactly("src/main/res", "src/debug/res")

    buildDir.deleteRecursively()

    // Update resource
    secondResourceFile.copyTo(destResourceFile, overwrite = true)

    val secondRun = gradleRunner
      .withArguments(":testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE) // paths didn't change
    }
    with(secondRun.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // but contents did
    }

    config = resourcesFile.loadConfig()
    assertThat(config.projectResourceDirs).containsExactly("src/main/res", "src/debug/res")
  }

  @Test
  fun verifyResourcesUpdatedWhenModuleResourceChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-module-resources-change")
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val consumerModuleRoot = File(fixtureRoot, "consumer")
    val buildDir = consumerModuleRoot.resolve("build").registerForDeletionOnExit()

    val producerModuleRoot = File(fixtureRoot, "producer")
    val valuesDir = File(producerModuleRoot, "src/main/res/values/").registerForDeletionOnExit()
    val destResourceFile = File(valuesDir, "colors.xml")
    val firstResourceFile = File(producerModuleRoot, "src/test/resources/colors1.xml")
    val secondResourceFile = File(producerModuleRoot, "src/test/resources/colors2.xml")

    // Original resource
    firstResourceFile.copyTo(destResourceFile, overwrite = false)

    val firstRun = gradleRunner
      .withArguments(":consumer:testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":consumer:preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }
    with(firstRun.task(":consumer:testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(consumerModuleRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.moduleResourceDirs).containsExactly("../producer/build/intermediates/packaged_res/debug")

    buildDir.deleteRecursively()

    // Update resource
    secondResourceFile.copyTo(destResourceFile, overwrite = true)

    val secondRun = gradleRunner
      .withArguments(":consumer:testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":consumer:preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE) // paths didn't change
    }
    with(secondRun.task(":consumer:testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // but contents did
    }

    config = resourcesFile.loadConfig()
    assertThat(config.moduleResourceDirs).containsExactly("../producer/build/intermediates/packaged_res/debug")
  }

  @Test
  fun verifyResourcesUpdatedWhenExternalDependencyChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-aar-resources-change")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    System.setProperty("isFirstRun", "true")

    val firstRun = gradleRunner
      .withArguments(":preparePaparazziDebugResources", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external1/res\$")

    buildDir.deleteRecursively()

    System.setProperty("isFirstRun", "false")

    val secondRun = gradleRunner
      .withArguments(":preparePaparazziDebugResources", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    config = resourcesFile.loadConfig()
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external2/res\$")
  }

  @Test
  fun verifyAssetsUpdatedWhenLocalAssetChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-local-assets-change")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val assetsDir = File(fixtureRoot, "src/main/assets/").registerForDeletionOnExit()
    val destAssetFile = File(assetsDir, "secret.txt")
    val firstAssetFile = File(fixtureRoot, "src/test/resources/secret1.txt")
    val secondAssetFile = File(fixtureRoot, "src/test/resources/secret2.txt")

    // Original asset
    firstAssetFile.copyTo(destAssetFile, overwrite = false)

    val firstRun = gradleRunner
      .withArguments("testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    with(firstRun.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.projectAssetDirs).containsExactly("src/main/assets", "src/debug/assets")

    buildDir.deleteRecursively()

    // Update asset
    secondAssetFile.copyTo(destAssetFile, overwrite = true)

    val secondRun = gradleRunner
      .withArguments(":testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE) // paths didn't change
    }

    with(secondRun.task(":testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // but contents did
    }

    config = resourcesFile.loadConfig()
    assertThat(config.projectAssetDirs).containsExactly("src/main/assets", "src/debug/assets")
  }

  @Test
  fun verifyAssetsUpdatedWhenModuleAssetChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-module-assets-change")
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    val consumerModuleRoot = File(fixtureRoot, "consumer")
    val buildDir = consumerModuleRoot.resolve("build").registerForDeletionOnExit()

    val producerModuleRoot = File(fixtureRoot, "producer")
    val assetsDir = File(producerModuleRoot, "src/main/assets/").registerForDeletionOnExit()
    val destAssetFile = File(assetsDir, "secret.txt")
    val firstAssetFile = File(producerModuleRoot, "src/test/resources/secret1.txt")
    val secondAssetFile = File(producerModuleRoot, "src/test/resources/secret2.txt")

    // Original asset
    firstAssetFile.copyTo(destAssetFile, overwrite = false)

    val firstRun = gradleRunner
      .withArguments(":consumer:testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":consumer:preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    with(firstRun.task(":consumer:testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(consumerModuleRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.projectAssetDirs).containsExactly(
      "src/main/assets",
      "src/debug/assets",
      "../producer/build/intermediates/library_assets/debug/out"
    )

    buildDir.deleteRecursively()

    // Update asset
    secondAssetFile.copyTo(destAssetFile, overwrite = true)

    val secondRun = gradleRunner
      .withArguments(":consumer:testDebug", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":consumer:preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(FROM_CACHE) // paths didn't change
    }

    with(secondRun.task(":consumer:testDebugUnitTest")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS) // but contents did
    }

    config = resourcesFile.loadConfig()
    assertThat(config.projectAssetDirs).containsExactly(
      "src/main/assets",
      "src/debug/assets",
      "../producer/build/intermediates/library_assets/debug/out"
    )
  }

  @Test
  fun verifyAssetsUpdatedWhenExternalDependencyChanges() {
    val fixtureRoot = File("src/test/projects/verify-update-aar-assets-change")
    val buildDir = fixtureRoot.resolve("build").registerForDeletionOnExit()
    fixtureRoot.resolve("build-cache").registerForDeletionOnExit()

    System.setProperty("isFirstRun", "true")

    val firstRun = gradleRunner
      .withArguments(":preparePaparazziDebugResources", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(firstRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")

    var config = resourcesFile.loadConfig()
    assertThat(config.aarAssetDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external1/assets\$")

    buildDir.deleteRecursively()

    System.setProperty("isFirstRun", "false")

    val secondRun = gradleRunner
      .withArguments(":preparePaparazziDebugResources", "--build-cache", "--stacktrace")
      .forwardOutput()
      .runFixture(fixtureRoot) { build() }

    with(secondRun.task(":preparePaparazziDebugResources")) {
      assertThat(this).isNotNull()
      assertThat(this!!.outcome).isEqualTo(SUCCESS)
    }

    config = resourcesFile.loadConfig()
    assertThat(config.aarAssetDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/transforms-4/[0-9a-f]{32}/transformed/external2/assets\$")
  }

  @Test
  fun verifyTargetSdkIsSameAsCompileSdk() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
      .withArguments(":consumer:compileDebugUnitTestJavaWithJavac", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":consumer:preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "consumer/build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val config = resourcesFile.loadConfig()
    assertThat(config.targetSdkVersion).isEqualTo("34")
    assertThat(config.platformDir).isEqualTo("platforms/android-34/")
  }

  @Test
  fun verifyTargetSdkIsDifferentFromCompileSdk() {
    val fixtureRoot = File("src/test/projects/different-target-sdk")

    val result = gradleRunner
      .withArguments("compileDebugUnitTestJavaWithJavac", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()

    val resourcesFile = File(fixtureRoot, "build/intermediates/paparazzi/debug/resources.json")
    assertThat(resourcesFile.exists()).isTrue()

    val config = resourcesFile.loadConfig()
    assertThat(config.targetSdkVersion).isEqualTo("29")
    assertThat(config.platformDir).isEqualTo("platforms/android-34/")
  }

  @Test
  fun verifyOpenAssets() {
    val fixtureRoot = File("src/test/projects/open-assets")

    gradleRunner
      .withArguments("consumer:testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun verifySnapshot() {
    val fixtureRoot = File("src/test/projects/verify-snapshot")

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/arrow_up.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyRecyclerView() {
    val fixtureRoot = File("src/test/projects/verify-recyclerview")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/recycler_view.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun withoutAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-missing")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshotFile = File(snapshotsDir, "9d3c31a9c79a363c26dc352b59e9e77083c300a7.png")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
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

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/card_chip.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun verifyAaptAttrResourceParsingInCompose() {
    val fixtureRoot = File("src/test/projects/verify-aapt-compose")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/compose_card_chip.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun ninePatch() {
    val fixtureRoot = File("src/test/projects/nine-patch")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/nine_patch.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun transitiveResources() {
    val fixtureRoot = File("src/test/projects/transitive-resources")
    val moduleRoot = File(fixtureRoot, "module")

    gradleRunner
      .withArguments("module:testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(moduleRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(moduleRoot, "src/test/resources/five_bucks.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun compose() {
    val fixtureRoot = File("src/test/projects/compose")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/compose_fonts.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun composeRecomposition() {
    val fixtureRoot = File("src/test/projects/compose-recomposition")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun composeViewTreeLifecycle() {
    val fixtureRoot = File("src/test/projects/compose-lifecycle-owner")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)
  }

  @Test
  fun composeWear() {
    val fixtureRoot = File("src/test/projects/compose-wear")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    val goldenImage = File(fixtureRoot, "src/test/resources/compose_wear.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun composeA11y() {
    val fixtureRoot = File("src/test/projects/compose-a11y")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()
    assertThat(snapshots!!).hasLength(1)

    val snapshotImage = snapshots[0]
    var goldenImage = File(fixtureRoot, "src/test/resources/compose_a11y.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
    goldenImage = File(fixtureRoot, "src/test/resources/compose_a11y_change_hierarchy_order.png")
    assertThat(snapshotImage).isSimilarTo(goldenImage).withDefaultThreshold()
  }

  @Test
  fun immSoftInputInteraction() {
    val fixtureRoot = File("src/test/projects/imm-soft-input")
    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun configIsUpdatable() {
    val fixtureRoot = File("src/test/projects/update-paparazzi-config")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val pixel3SnapshotImage = snapshots[0]
    val nexus7SnapshotImage = snapshots[1]
    val pixel3GoldenImage = File(fixtureRoot, "src/test/resources/pixel_3_launch.png")
    val nexus7GoldenImage = File(fixtureRoot, "src/test/resources/nexus_7_launch.png")
    assertThat(pixel3SnapshotImage).isSimilarTo(pixel3GoldenImage).withDefaultThreshold()
    assertThat(nexus7SnapshotImage).isSimilarTo(nexus7GoldenImage).withDefaultThreshold()
  }

  @Test
  fun localeQualifier() {
    val fixtureRoot = File("src/test/projects/locale-qualifier")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val localeDefaultSnapshotImage = snapshots[0]
    val localeEnGBSnapshotImage = snapshots[1]
    val localeDefaultGoldenImage = File(fixtureRoot, "src/test/resources/locale_default.png")
    val localeEnGBGoldenImage = File(fixtureRoot, "src/test/resources/locale_en_gb.png")
    assertThat(localeDefaultSnapshotImage).isSimilarTo(localeDefaultGoldenImage).withDefaultThreshold()
    assertThat(localeEnGBSnapshotImage).isSimilarTo(localeEnGBGoldenImage).withDefaultThreshold()
  }

  @Test
  fun layoutDirection() {
    val fixtureRoot = File("src/test/projects/layout-direction")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val localeDefaultRtlSnapshotImage = snapshots[0]
    val localeArSnapshotImage = snapshots[1]
    val localeDefaultRtlGoldenImage = File(fixtureRoot, "src/test/resources/locale_default_rtl.png")
    val localeArGoldenImage = File(fixtureRoot, "src/test/resources/locale_ar.png")
    assertThat(localeDefaultRtlSnapshotImage).isSimilarTo(localeDefaultRtlGoldenImage).withDefaultThreshold()
    assertThat(localeArSnapshotImage).isSimilarTo(localeArGoldenImage).withDefaultThreshold()
  }

  @Test
  fun nightModeCompose() {
    val fixtureRoot = File("src/test/projects/night-mode-compose")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val lightModeSnapshotImage = snapshots[0]
    val darkModeSnapshotImage = snapshots[1]
    val lightModeGoldenImage = File(fixtureRoot, "src/test/resources/light_mode.png")
    val darkModeGoldenImage = File(fixtureRoot, "src/test/resources/dark_mode.png")
    assertThat(lightModeSnapshotImage).isSimilarTo(lightModeGoldenImage).withDefaultThreshold()
    assertThat(darkModeSnapshotImage).isSimilarTo(darkModeGoldenImage).withDefaultThreshold()
  }

  @Test
  fun nightModeXml() {
    val fixtureRoot = File("src/test/projects/night-mode-xml")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val lightModeSnapshotImage = snapshots[0]
    val darkModeSnapshotImage = snapshots[1]
    val lightModeGoldenImage = File(fixtureRoot, "src/test/resources/light_mode.png")
    val darkModeGoldenImage = File(fixtureRoot, "src/test/resources/dark_mode.png")
    assertThat(lightModeSnapshotImage).isSimilarTo(lightModeGoldenImage).withDefaultThreshold()
    assertThat(darkModeSnapshotImage).isSimilarTo(darkModeGoldenImage).withDefaultThreshold()
  }

  @Test
  fun disabledUnitTestVariant() {
    val fixtureRoot = File("src/test/projects/disabled-unit-test-variant")
    gradleRunner
      .withArguments("testDebug")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun verifyCoroutineDelay() {
    val fixtureRoot = File("src/test/projects/coroutine-delay-main")

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }

  @Test
  fun accessibilityErrorsLogged() {
    val fixtureRoot = File("src/test/projects/validate-accessibility")
    // this is only a warning message, so subsequent runs would otherwise be UP-TO-DATE
    fixtureRoot.resolve("build").registerForDeletionOnExit()

    val result = gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.output).contains(
      "\u001B[33mAccessibility issue of type LOW_CONTRAST on no-id:\u001B[0m " +
        "The item's text contrast ratio is 1.00. This ratio is based on a text color of #FFFFFF " +
        "and background color of #FFFFFF. Consider increasing this item's text contrast ratio to " +
        "4.50 or greater."
    )
  }

  @Test
  fun jacoco() {
    val fixtureRoot = File("src/test/projects/jacoco")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val jacocoExecutionData = File(fixtureRoot, "build/jacoco/testDebugUnitTest.exec")
    assertThat(jacocoExecutionData.exists()).isTrue()
  }

  @Test
  fun screenOrientation() {
    val fixtureRoot = File("src/test/projects/verify-orientation")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val portraitSnapshotImage = snapshots[0]
    val portraitGoldenImage = File(fixtureRoot, "src/test/resources/portrait_orientation.png")
    assertThat(portraitSnapshotImage).isSimilarTo(portraitGoldenImage).withDefaultThreshold()

    val landscapeSnapshotImage = snapshots[1]
    val landscapeGoldenImage = File(fixtureRoot, "src/test/resources/landscape_orientation.png")
    assertThat(landscapeSnapshotImage).isSimilarTo(landscapeGoldenImage).withDefaultThreshold()
  }

  @Test
  fun screenRound() {
    val fixtureRoot = File("src/test/projects/verify-screen-round")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFiles()?.sortedBy { it.lastModified() }
    assertThat(snapshots!!).hasSize(2)

    val roundSnapshot = snapshots[0]
    val roundGoldenImage = File(fixtureRoot, "src/test/resources/round.png")
    assertThat(roundSnapshot).isSimilarTo(roundGoldenImage).withDefaultThreshold()

    val notRoundSnapshot = snapshots[1]
    val notRoundGoldenImage = File(fixtureRoot, "src/test/resources/not_round.png")
    assertThat(notRoundSnapshot).isSimilarTo(notRoundGoldenImage).withDefaultThreshold()
  }

  private fun File.loadConfig() = source().buffer().use { CONFIG_ADAPTER.fromJson(it)!! }

  private fun GradleRunner.runFixture(
    projectRoot: File,
    action: GradleRunner.() -> BuildResult
  ): BuildResult {
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

  private fun File.registerForDeletionOnExit() = apply { filesToDelete += this }

  companion object {
    private val CONFIG_ADAPTER =
      Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()!!.adapter(Config::class.java)
    private val MATCHES_PATTERN = Correspondence.from<String, String>(
      { actual, expected -> actual.matches(expected.toRegex()) }, "matches"
    )
  }
}
