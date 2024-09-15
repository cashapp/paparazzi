package app.cash.paparazzi.gradle.reporting

import java.util.TreeSet

/**
 * Custom ClassTestResults based on Gradle's ClassTestResults
 */
internal class ClassTestResults(
  override val name: String,
  private val packageResults: PackageTestResults
) : CompositeTestResults(packageResults) {
  val results: MutableSet<TestResult> = TreeSet()

  override val title: String = String.format("Class %s", name)
  val simpleName: String
    get() {
      val pos = name.lastIndexOf(".")
      return if (pos != -1) {
        name.substring(pos + 1)
      } else {
        name
      }
    }

  fun getPackageResults(): PackageTestResults {
    return packageResults
  }

  fun addTest(
    testName: String,
    duration: Long,
    project: String,
    flavor: String,
    diffImages: List<ScreenshotDiffImage>?
  ): TestResult {
    val test = TestResult(
      testName,
      duration,
      project,
      flavor,
      diffImages,
      this
    )
    results.add(test)
    addVariant(project, flavor, test)
    return addTest(test)
  }
}
