package app.cash.paparazzi.annotations

import androidx.compose.runtime.Composable

public sealed interface PaparazziPreviewData {

  public data class Default(
    val snapshotName: String,
    val preview: PreviewData,
    val composable: @Composable () -> Unit
  ) : PaparazziPreviewData {
    override fun toString(): String = buildList {
      add(snapshotName)
      preview.toString().takeIf { it.isNotEmpty() }?.let(::add)
    }.joinToString(",")
  }

  public data class Provider<T>(
    val snapshotName: String,
    val preview: PreviewData,
    val composable: @Composable (T) -> Unit,
    val previewParameter: PreviewParameterData<T>
  ) : PaparazziPreviewData {
    override fun toString(): String = buildList {
      add(snapshotName)
      preview.toString().takeIf { it.isNotEmpty() }?.let(::add)
      add(previewParameter.toString())
    }.joinToString(",")

    public fun withPreviewParameterIndex(index: Int): Provider<T> = copy(previewParameter = previewParameter.copy(index = index))
  }

  public data object Empty : PaparazziPreviewData {
    override fun toString(): String = "Empty"
  }

  public data class Error(
    val snapshotName: String,
    val preview: PreviewData,
    val message: String
  ) : PaparazziPreviewData {
    override fun toString(): String = buildList {
      add(snapshotName)
      preview.toString().takeIf { it.isNotEmpty() }?.let(::add)
    }.joinToString(",")
  }
}

public data class PreviewData(
  val fontScale: Float? = null,
  val device: String? = null,
  val widthDp: Int? = null,
  val heightDp: Int? = null,
  val uiMode: Int? = null,
  val locale: String? = null,
  val backgroundColor: String? = null
) {
  override fun toString(): String = buildList {
    fontScale?.fontScale()?.displayName()?.let(::add)
    uiMode?.lightDarkName()?.let(::add)
    uiMode?.uiModeName()?.let(::add)
    device?.let {
      if (it != DEFAULT_DEVICE_ID) {
        add(it.substringAfterLast(":"))
      }
    }
    widthDp?.let { add("w_$it") }
    heightDp?.let { add("h_$it") }
    locale?.let(::add)
    backgroundColor?.let { add("bg_$it") }
  }.takeIf { it.isNotEmpty() }
    ?.joinToString(",")
    ?: ""
}

public data class PreviewParameterData<T>(
  val name: String,
  val values: Sequence<T>,
  val index: Int = 0
) {
  override fun toString(): String = "$name$index"
}
