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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }

  @Test
  fun androidDynamicFeaturePlugin() {
    val fixtureRoot = File("src/test/projects/supports-dynamic-feature-modules")

    val result = gradleRunner
      .withArguments(":dynamic_feature:verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":dynamic_feature:preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":dynamic_feature:testDebugUnitTest")).isNotNull()
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
  fun configurationCacheWorksWithGeneratedSources() {
    val fixtureRoot = File("src/test/projects/configuration-cache-generated-sources")

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
    File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun deleteSnapshots() {
    val fixtureRoot = File("src/test/projects/delete-snapshots")
    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

    val snapshotName1 = "app.cash.paparazzi.plugin.test_DeleteTest_delete.png"
    val snapshotName2 = "app.cash.paparazzi.plugin.test_DeleteTest_delete_label.png"
    val firstGoldenFile = File(fixtureRoot, "src/test/resources/$snapshotName1")
    val secondGoldenFile = File(fixtureRoot, "src/test/resources/$snapshotName2")

    val snapshot = File(snapshotsDir, "images/$snapshotName1")
    val snapshotWithLabel = File(snapshotsDir, "images/$snapshotName2")

    firstGoldenFile.copyTo(snapshot, overwrite = false)
    secondGoldenFile.copyTo(snapshotWithLabel, overwrite = false)

    assertThat(snapshot.exists()).isTrue()
    assertThat(snapshotWithLabel.exists()).isTrue()

    gradleRunner
      .withArguments("deletePaparazziSnapshots", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(snapshot.exists()).isFalse()
    assertThat(snapshotWithLabel.exists()).isFalse()
  }

  @Test
  fun cleanRecord() {
    val fixtureRoot = File("src/test/projects/clean-record")
    val snapshotsDir = File(fixtureRoot, "src/test/snapshots").registerForDeletionOnExit()

    val snapshotName1 = "app.cash.paparazzi.plugin.test_CleanRecordTest_clean.png"
    val snapshotName2 = "app.cash.paparazzi.plugin.test_CleanRecordTest_clean_keep.png"
    val firstGoldenFile = File(fixtureRoot, "src/test/resources/$snapshotName1")
    val secondGoldenFile = File(fixtureRoot, "src/test/resources/$snapshotName2")

    val snapshotToBeDeleted = File(snapshotsDir, "images/$snapshotName1")
    val snapshotToBeKept = File(snapshotsDir, "images/$snapshotName2")

    firstGoldenFile.copyTo(snapshotToBeDeleted, overwrite = false)
    secondGoldenFile.copyTo(snapshotToBeKept, overwrite = false)

    assertThat(snapshotToBeDeleted.exists()).isTrue()
    assertThat(snapshotToBeKept.exists()).isTrue()

    val result = gradleRunner
      .withArguments("cleanRecordPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":deletePaparazziSnapshots")).isNotNull()
    assertThat(result.task(":recordPaparazziDebug")).isNotNull()

    assertThat(snapshotToBeDeleted.exists()).isFalse()
    assertThat(snapshotToBeKept.exists()).isTrue()
  }

  @Test
  fun widgets() {
    val fixtureRoot = File("src/test/projects/widgets")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun lifecycleOwnerUsages() {
    val fixtureRoot = File("src/test/projects/lifecycle-usages")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    val snapshotsDir = File(fixtureRoot, "build/reports/paparazzi/debug/images")
    val snapshots = snapshotsDir.listFilesSorted()
    assertThat(snapshots!!).hasSize(3)
  }

  @Test
  fun verifyResourcesGeneratedForJavaProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-java")

    val result = gradleRunner
      .withArguments(":consumer:testDebug", "--stacktrace")
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
    assertThat(config.projectResourceDirs).containsExactly(
      "src/main/res",
      "src/debug/res",
      "build/generated/res/resValues/debug",
      "build/generated/res/extra"
    )
    assertThat(config.moduleResourceDirs).containsExactly(
      "../module1/build/intermediates/packaged_res/debug/packageDebugResources",
      "../module2/build/intermediates/packaged_res/debug/packageDebugResources"
    )
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external/res\$")
  }

  @Test
  fun verifyResourcesGeneratedForKotlinProject() {
    val fixtureRoot = File("src/test/projects/verify-resources-kotlin")

    val result = gradleRunner
      .withArguments(":consumer:testDebug", "--stacktrace")
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
    assertThat(config.projectResourceDirs).containsExactly(
      "src/main/res",
      "src/debug/res",
      "build/generated/res/resValues/debug",
      "build/generated/res/extra"
    )
    assertThat(config.moduleResourceDirs).containsExactly(
      "../module1/build/intermediates/packaged_res/debug/packageDebugResources",
      "../module2/build/intermediates/packaged_res/debug/packageDebugResources"
    )
    assertThat(config.aarExplodedDirs)
      .comparingElementsUsing(MATCHES_PATTERN)
      .containsExactly("^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external/res\$")
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
    assertThat(config.projectResourceDirs).containsExactly(
      "src/main/res",
      "src/debug/res",
      "build/generated/res/resValues/debug"
    )

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
    assertThat(config.projectResourceDirs).containsExactly(
      "src/main/res",
      "src/debug/res",
      "build/generated/res/resValues/debug"
    )
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
    assertThat(config.moduleResourceDirs).containsExactly("../producer/build/intermediates/packaged_res/debug/packageDebugResources")

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
    assertThat(config.moduleResourceDirs).containsExactly("../producer/build/intermediates/packaged_res/debug/packageDebugResources")
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
      .containsExactly(
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external1/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/core-1.10.0/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/annotation-experimental-1.3.0/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/lifecycle-runtime-2.3.1/res\$"
      )

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
      .containsExactly(
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external2/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/core-1.10.1/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/annotation-experimental-1.3.0/res\$",
        "^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/lifecycle-runtime-2.3.1/res\$"
      )
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
      "../producer/build/intermediates/library_assets/debug/packageDebugAssets/out"
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
      "../producer/build/intermediates/library_assets/debug/packageDebugAssets/out"
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
      .containsExactly("^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external1/assets\$")

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
      .containsExactly("^caches/[0-9]{1,2}.[0-9](.[0-9])?/transforms/[0-9a-f]{32}/transformed/external2/assets\$")
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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }

  @Test
  fun verifyGif() {
    val fixtureRoot = File("src/test/projects/verify-gif")

    val result = gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    assertThat(result.task(":preparePaparazziDebugResources")).isNotNull()
    assertThat(result.task(":testDebugUnitTest")).isNotNull()
  }

  @Test
  fun verifyVectorDrawables() {
    val fixtureRoot = File("src/test/projects/verify-svgs")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun verifyRecyclerView() {
    val fixtureRoot = File("src/test/projects/verify-recyclerview")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun withoutAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-missing")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun withAppCompat() {
    val fixtureRoot = File("src/test/projects/appcompat-present")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  @Ignore
  fun withMaterialComponents() {
    val fixtureRoot = File("src/test/projects/material-components-present")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun customFonts() {
    val fixtureRoot = File("src/test/projects/custom-fonts")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun textAppearances() {
    val fixtureRoot = File("src/test/projects/text-appearances")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun verifyAaptAttrResourceParsing() {
    val fixtureRoot = File("src/test/projects/verify-aapt")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun ninePatch() {
    val fixtureRoot = File("src/test/projects/nine-patch")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun transitiveResources() {
    val fixtureRoot = File("src/test/projects/transitive-resources")

    gradleRunner
      .withArguments("module:verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun compose() {
    val fixtureRoot = File("src/test/projects/compose")
    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun composeLeaks() {
    val fixtureRoot = File("src/test/projects/compose-leaks")

    gradleRunner
      .withArguments("testDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun composeA11y() {
    val fixtureRoot = File("src/test/projects/compose-a11y")
    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun scaledVersusFullDeviceResolution() {
    val fixtureRoot = File("src/test/projects/device-resolution")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun localeQualifier() {
    val fixtureRoot = File("src/test/projects/locale-qualifier")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun layoutDirection() {
    val fixtureRoot = File("src/test/projects/layout-direction")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun nightMode() {
    val fixtureRoot = File("src/test/projects/night-mode")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
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
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun screenRound() {
    val fixtureRoot = File("src/test/projects/verify-screen-round")

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
  }

  @Test
  fun imageHash() {
    val fixtureRoot = File("src/test/projects/image-hash")

    gradleRunner
      .withArguments("recordPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }

    gradleRunner
      .withArguments("verifyPaparazziDebug", "--stacktrace")
      .runFixture(fixtureRoot) { build() }
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

  private fun File.listFilesSorted() = listFiles()?.sortedBy { it.lastModified() }

  companion object {
    private val CONFIG_ADAPTER =
      Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()!!.adapter(Config::class.java)
    private val MATCHES_PATTERN = Correspondence.from<String, String>(
      { actual, expected -> actual.matches(expected.toRegex()) }, "matches"
    )
  }
}
