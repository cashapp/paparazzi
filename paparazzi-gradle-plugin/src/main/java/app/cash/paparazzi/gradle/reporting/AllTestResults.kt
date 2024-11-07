package app.cash.paparazzi.gradle.reporting

import java.util.TreeMap

/**
 * The model for the test report.
 */
internal class AllTestResults : CompositeTestResults(null) {
  private val packages: MutableMap<String, PackageTestResults> = TreeMap()

  override val title: String
    get() = "Test Summary"

  override val baseUrl: String
    get() = "index.html"

  fun getPackages(): Collection<PackageTestResults> = packages.values

  fun addTest(
    classId: Long,
    className: String,
    classDisplayName: String = className,
    testName: String,
    testDisplayName: String = testName,
    duration: Long
  ): TestResult {
    val packageResults = addPackageForClass(className)
    return addTest(
      packageResults.addTest(
        classId, className, classDisplayName, testName, testDisplayName, duration
      )
    )
  }

  fun addTestClass(classId: Long, className: String, classDisplayName: String = className): ClassTestResults {
    return addPackageForClass(className).addClass(classId, className, classDisplayName)
  }

  private fun addPackageForClass(className: String): PackageTestResults {
    var packageName = className.substringBeforeLast(".")
    if (packageName == className) {
      packageName = ""
    }
    return addPackage(packageName)
  }

  private fun addPackage(packageName: String): PackageTestResults {
    var packageResults = packages[packageName]
    if (packageResults == null) {
      packageResults = PackageTestResults(packageName, this)
      packages[packageName] = packageResults
    }
    return packageResults
  }
}
