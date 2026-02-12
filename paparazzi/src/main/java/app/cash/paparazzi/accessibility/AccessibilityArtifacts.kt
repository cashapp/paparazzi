package app.cash.paparazzi.accessibility

internal const val ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME = "accessibility-hierarchy.json"

internal fun List<String>.toAccessibilityHierarchyArtifact(): String =
  if (size == 1) single() else joinToString(prefix = "[", separator = ",", postfix = "]")
