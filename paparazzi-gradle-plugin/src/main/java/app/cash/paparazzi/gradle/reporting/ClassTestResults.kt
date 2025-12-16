package app.cash.paparazzi.gradle.reporting

import java.util.TreeSet

/**
 * Test results for a given class.
 */
internal class ClassTestResults(
  val id: Long,
  val name: String,
  private val displayName: String? = name,
  val packageResults: PackageTestResults
) : CompositeTestResults(
  packageResults
) {
  private val results: MutableSet<TestResult> = TreeSet()
  override val baseUrl: String = "classes/${name.safeFilename()}.html"
  override val title: String
    get() = if (name == displayName) "Class $name" else displayName!!

  val reportName: String
    get() =
      if (displayName != null && displayName != name) {
        displayName
      } else {
        simpleName
      }

  val simpleName: String
    get() {
      val simpleName = name.substringAfterLast(".")
      if (simpleName == "") {
        return name
      }
      return simpleName
    }

  val testResults: Collection<TestResult>
    get() = results

  fun addTest(testName: String, testDisplayName: String, duration: Long): TestResult? {
    val test = TestResult(testName, testDisplayName, duration, this)
    results += test
    return addTest(test)
  }

  companion object {
    internal fun String.safeFilename(): String {
      // The regex needs careful handling of backslashes and special characters
      return replace("[\"<>|\\:*?/]+", "-")
        .replace("[\\000-\\031]+", "")
    }
  }
}
