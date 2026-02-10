package app.cash.paparazzi.accessibility

import android.view.View
import android.view.WindowManager
import android.view.WindowManagerImpl

internal const val ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED =
  "app.cash.paparazzi.accessibility.hierarchy.artifacts.enabled"

internal class AccessibilityHierarchyGenerator(
  private val collector: AccessibilityElementCollector = AccessibilityElementCollector()
) {
  fun generate(rootView: View): String? {
    if (System.getProperty(ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)?.toBoolean() != true) {
      return null
    }

    val windowManager = rootView.context.getSystemService(WindowManager::class.java) as WindowManagerImpl
    val elements = collector.collect(
      rootView = rootView,
      windowManagerRootView = windowManager.currentRootView
    )
    return collector.toHierarchyString(elements)
  }
}
