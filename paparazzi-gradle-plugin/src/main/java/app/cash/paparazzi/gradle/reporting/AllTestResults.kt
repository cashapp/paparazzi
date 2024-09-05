package app.cash.paparazzi.gradle.reporting

import java.util.TreeMap

/**
 *
 * Custom test results based on Gradle's AllTestResults
 */
internal class AllTestResults : CompositeTestResults(null) {

  private val packages: MutableMap<String, PackageTestResults> =
    TreeMap<String, PackageTestResults>()
  override val title: String = "Test Summary"

  override val name = null

  fun getPackages(): Collection<PackageTestResults> {
    return packages.values
  }

  fun addTest(
    className: String,
    testName: String,
    duration: Long,
    project: String,
    flavor: String,
    diffImages: List<ScreenshotDiffImage>?
  ): TestResult {
    val packageResults: PackageTestResults = addPackageForClass(className)
    val testResult: TestResult = addTest(
      packageResults.addTest(className, testName, duration, project, flavor, diffImages)
    )
    addVariant(project, flavor, testResult)
    return testResult
  }

  private fun addPackageForClass(className: String): PackageTestResults {
    val pos = className.lastIndexOf(".")
    val packageName = if (pos != -1) {
      className.substring(0, pos)
    } else {
      ""
    }
    return addPackage(packageName)
  }

  private fun addPackage(packageName: String): PackageTestResults {
    var packageResults: PackageTestResults? =
      packages[packageName]
    if (packageResults == null) {
      packageResults = PackageTestResults(packageName, this)
      standardError.forEach { packageResults.addStandardError(it) }
      standardOutput.forEach { packageResults.addStandardOutput(it) }
      packages[packageName] = packageResults
    }
    return packageResults
  }
}
