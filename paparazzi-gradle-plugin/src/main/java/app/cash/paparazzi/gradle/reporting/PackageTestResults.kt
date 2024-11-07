package app.cash.paparazzi.gradle.reporting

import java.util.TreeMap

/**
 * The test results for a given package.
 */
internal class PackageTestResults(
  name: String,
  model: AllTestResults
) : CompositeTestResults(model) {
  val name: String = name.ifEmpty { DEFAULT_PACKAGE }
  private val classes: MutableMap<String, ClassTestResults> = TreeMap()

  override val title: String
    get() = if (name == DEFAULT_PACKAGE) "Default package" else ("Package $name")

  override val baseUrl: String
    get() = "packages/$name.html"

  fun getClasses(): Collection<ClassTestResults> = classes.values

  fun addTest(
    classId: Long,
    className: String,
    classDisplayName: String = className,
    testName: String,
    testDisplayName: String = testName,
    duration: Long
  ): TestResult {
    val classResults = addClass(classId, className, classDisplayName)
    return addTest(classResults.addTest(testName, testDisplayName, duration)!!)
  }

  fun addClass(classId: Long, className: String, classDisplayName: String = className): ClassTestResults {
    var classResults = classes[className]
    if (classResults == null) {
      classResults = ClassTestResults(classId, className, classDisplayName, this)
      classes[className] = classResults
    }
    return classResults
  }

  companion object {
    private const val DEFAULT_PACKAGE = "default-package"
  }
}
