package app.cash.paparazzi.accessibility

import android.view.View
import app.cash.paparazzi.Flags

internal class AccessibilityHierarchyGenerator(
  private val collector: AccessibilityElementCollector = AccessibilityElementCollector()
) {
  fun generate(windowRoots: List<View>, width: Int, height: Int): AccessibilityFrame? {
    if (System.getProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)?.toBoolean() != true) {
      return null
    }

    return AccessibilityFrame(
      elements = collector.collect(windowRoots),
      width = width,
      height = height
    )
  }
}
