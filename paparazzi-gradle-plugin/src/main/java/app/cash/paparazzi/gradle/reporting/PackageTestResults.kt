package app.cash.paparazzi.gradle.reporting

import java.util.TreeMap

/**
 * Custom PackageTestResults based on Gradle's PackageTestResults
 */
internal class PackageTestResults(
  override var name: String = DEFAULT_PACKAGE,
  model: AllTestResults?
) : CompositeTestResults(model) {

  private val classes: MutableMap<String, ClassTestResults?> =
    TreeMap<String, ClassTestResults?>()
  override val title: String
    get() = if (name == DEFAULT_PACKAGE) {
      "Default package"
    } else {
      String.format(
        "Package %s",
        name
      )
    }

  fun getClasses(): Collection<ClassTestResults?> = classes.values

  fun addTest(
    className: String,
    testName: String,
    duration: Long,
    project: String,
    flavor: String,
    diffImages: List<ScreenshotDiffImage>?
  ): TestResult {
    val classResults: ClassTestResults = addClass(className)
    val testResult: TestResult = addTest(
      classResults.addTest(testName, duration, project, flavor, diffImages)
    )
    standardError.forEach {
      classResults.addStandardError(it)
    }
    standardOutput.forEach {
      classResults.addStandardOutput(it)
    }
    addVariant(project, flavor, testResult)
    return testResult
  }

  fun addClass(className: String): ClassTestResults {
    var classResults: ClassTestResults? =
      classes[className]
    if (classResults == null) {
      classResults =
        ClassTestResults(className, this)
      standardError.forEach {
        classResults.addStandardError(it)
      }
      standardOutput.forEach {
        classResults.addStandardOutput(it)
      }
      classes[className] = classResults
    }
    return classResults
  }

  public companion object {
    private const val DEFAULT_PACKAGE = "default-package"
  }
}
