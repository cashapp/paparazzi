package app.cash.paparazzi

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class SnapshotTest {
  @Test
  fun genericPreviewParameterNamesAreEscaped() {
    assertThat(snapshot("previewTests[43.MyPreview_ClosedRange<Float>_0]").toFileName(extension = "png"))
      .isEqualTo("app.cash.paparazzi_PreviewTest_previewTests[43.MyPreview_ClosedRange%3CFloat%3E_0].png")
  }

  @Test
  fun filesystemInvalidMethodCharactersAreEscaped() {
    assertThat(snapshot("test[<>:\"/\\|?*]").toFileName(extension = "png"))
      .isEqualTo("app.cash.paparazzi_PreviewTest_test[%3C%3E%3A%22%2F%5C%7C%3F%2A].png")
  }

  @Test
  fun escapedMethodNamesRemainDistinct() {
    val filenames = listOf("test<Float>", "test%3CFloat%3E", "test_Float_", "test%253CFloat%253E")
      .map { snapshot(it).toFileName(extension = "png") }

    assertThat(filenames.toSet()).hasSize(4)
    assertThat(filenames[1]).isEqualTo("app.cash.paparazzi_PreviewTest_test%253CFloat%253E.png")
  }

  @Test
  fun ordinaryMethodNamesKeepExistingFilenames() {
    assertThat(snapshot("previewTests[0.Café_中文]").toFileName(extension = "png"))
      .isEqualTo("app.cash.paparazzi_PreviewTest_previewTests[0.Café_中文].png")
    assertThat(snapshot("test with spaces").toFileName(extension = "png"))
      .isEqualTo("app.cash.paparazzi_PreviewTest_test_with_spaces.png")
  }

  @Test
  fun customDelimiterAndExtensionArePreserved() {
    assertThat(snapshot("test<Float>").copy(name = "Loading State").toFileName(delimiter = "-", extension = "temp.png"))
      .isEqualTo("app.cash.paparazzi-PreviewTest-test%3CFloat%3E-loading-state.temp.png")
  }

  @Test
  fun explicitSnapshotNamesStillRejectInvalidCharacters() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      snapshot("test").copy(name = "invalid<name>").toFileName(extension = "png")
    }
    assertThat(error).hasMessageThat().isEqualTo("Supplied snapshot name contains invalid characters ('invalid<name>')")
  }

  private fun snapshot(methodName: String) =
    Snapshot(
      name = null,
      testName = TestName("app.cash.paparazzi", "PreviewTest", methodName),
      timestamp = Date(0)
    )
}
