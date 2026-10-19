package app.cash.paparazzi.accessibility

internal const val ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME = "accessibility-hierarchy.json"

internal fun List<String>.toAccessibilityHierarchyArtifact(): String =
  joinToString(
    prefix = "{\n  \"frames\": [\n",
    separator = ",\n",
    postfix = "\n  ]\n}"
  ) { it.prependIndent("    ") }
