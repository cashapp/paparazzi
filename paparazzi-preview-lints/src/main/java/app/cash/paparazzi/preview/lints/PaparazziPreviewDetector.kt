package app.cash.paparazzi.preview.lints

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.DEFINITION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility

public class PaparazziPreviewDetector : Detector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> = listOf(PAPARAZZI_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == DEFINITION || super.isApplicableAnnotationUsage(type)

  override fun inheritAnnotation(annotation: String): Boolean = false

  @Suppress("UnstableApiUsage")
  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo
  ) {
    val qualifiedName = annotationInfo.qualifiedName
    if (qualifiedName != PAPARAZZI_ANNOTATION) return

    val annotatedMethod = annotationInfo.annotation.uastParent as? UMethod
      ?: throw IllegalStateException("Expected annotated method given declared target type")

    val annotatedMethodName = annotatedMethod.name
    val hasComposable = annotatedMethod.annotations.any { it.qualifiedName == COMPOSABLE_ANNOTATION }
    if (!hasComposable) {
      context.report(
        issue = COMPOSABLE_NOT_DETECTED,
        scope = element,
        location = context.getLocation(element),
        message = "$annotatedMethodName is not annotated with @Composable."
      )
    }

    val hasPreview = annotatedMethod.annotations.any { it.qualifiedName == PREVIEW_ANNOTATION }
    if (!hasPreview) {
      context.report(
        issue = PREVIEW_NOT_DETECTED,
        scope = element,
        location = context.getLocation(element),
        message = "$annotatedMethodName is not annotated with @Preview."
      )
    }

    if (annotatedMethod.visibility == UastVisibility.PRIVATE) {
      context.report(
        issue = PRIVATE_PREVIEW_DETECTED,
        scope = element,
        location = context.getLocation(element),
        message = "$annotatedMethodName is private. Make it internal or public to generate a snapshot."
      )
    }

    val hasPreviewParameter = annotatedMethod.parameters.any {
      it.annotations.any { it.qualifiedName == PREVIEW_PARAMETER_ANNOTATION }
    }
    if (hasPreviewParameter) {
      context.report(
        issue = PREVIEW_PARAMETERS_NOT_SUPPORTED,
        scope = element,
        location = context.getLocation(element),
        message = "@Preview of $annotatedMethodName uses PreviewParameters which aren't currently supported."
      )
    }
  }

  internal companion object {
    private const val PAPARAZZI_ANNOTATION = "app.cash.paparazzi.annotations.Paparazzi"
    private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
    private const val PREVIEW_ANNOTATION = "androidx.compose.ui.tooling.preview.Preview"
    private const val PREVIEW_PARAMETER_ANNOTATION = "androidx.compose.ui.tooling.preview.PreviewParameter"

    val COMPOSABLE_NOT_DETECTED: Issue = Issue.create(
      id = "ComposableAnnotationNotFound",
      briefDescription = "Composable Annotation not found",
      explanation = "Paparazzi Previews require a @Composable annotation to be applied.",
      category = Category.CUSTOM_LINT_CHECKS,
      priority = 10,
      severity = Severity.ERROR,
      implementation = Implementation(
        PaparazziPreviewDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )

    val PREVIEW_NOT_DETECTED: Issue = Issue.create(
      id = "PreviewAnnotationNotFound",
      briefDescription = "Preview Annotation not found",
      explanation = "Paparazzi Previews require a @Preview annotation to be applied.",
      category = Category.CUSTOM_LINT_CHECKS,
      priority = 10,
      severity = Severity.ERROR,
      implementation = Implementation(
        PaparazziPreviewDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )

    val PRIVATE_PREVIEW_DETECTED: Issue = Issue.create(
      id = "PrivatePreviewDetected",
      briefDescription = "@Preview of private Composable detected",
      explanation = "Paparazzi Previews does not support private Composables.",
      category = Category.CUSTOM_LINT_CHECKS,
      priority = 10,
      severity = Severity.ERROR,
      implementation = Implementation(
        PaparazziPreviewDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )

    val PREVIEW_PARAMETERS_NOT_SUPPORTED: Issue = Issue.create(
      id = "PreviewParametersNotSupported",
      briefDescription = "Preview Parameters not supported",
      explanation = "Paparazzi Previews does not support Preview Parameters.",
      category = Category.CUSTOM_LINT_CHECKS,
      priority = 10,
      severity = Severity.ERROR,
      implementation = Implementation(
        PaparazziPreviewDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
        Scope.JAVA_FILE_SCOPE
      )
    )
  }
}
