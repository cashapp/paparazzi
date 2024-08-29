package app.cash.paparazzi.gradle.reporting

/**
 * VariantTestResults to accumulate results per variant
 */
internal class VariantTestResults(
  override var name: String,
  parent: CompositeTestResults?
) : CompositeTestResults(parent) {
  override val title = name
}
