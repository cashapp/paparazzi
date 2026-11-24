package app.cash.paparazzi.preview.lints

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.google.auto.service.AutoService

@AutoService(value = [IssueRegistry::class])
public class PaparazziPreviewIssueRegistry : IssueRegistry() {
  override val issues: List<Issue> = listOf(
    PaparazziPreviewDetector.COMPOSABLE_NOT_DETECTED,
    PaparazziPreviewDetector.PREVIEW_NOT_DETECTED,
    PaparazziPreviewDetector.PRIVATE_PREVIEW_DETECTED,
    PaparazziPreviewDetector.PREVIEW_PARAMETERS_NOT_SUPPORTED
  )

  override val api: Int = CURRENT_API

  override val vendor: Vendor = Vendor(
    vendorName = "cashapp/paparazzi",
    identifier = "app.cash.paparazzi",
    feedbackUrl = "https://github.com/cashapp/paparazzi/issues"
  )
}
