package app.cash.paparazzi.annotations

import android.content.res.Configuration
import androidx.compose.runtime.Composable

public object PaparazziPreviewDefaults {
  public const val DEVICE_ID: String = "id:pixel_5"
}

/**
 * Represents composables annotated with @Paparazzi annotation
 *
 * Default - Represents a composable with no parameters
 * Provider - Represents a composable with parameters using @PreviewParameter
 * Empty - Represents a configuration with zero annotated composables
 * Error - Represents an error state with a message if the composable is misconfgured (ex. private Composable function)
 */
public sealed interface PaparazziPreviewData {

  public data class Default(
    val snapshotName: String,
    val preview: PreviewData,
    val composable: @Composable () -> Unit
  ) : PaparazziPreviewData {
    override fun toString(): String =
      buildList {
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
    override fun toString(): String =
      buildList {
        add(snapshotName)
        preview.toString().takeIf { it.isNotEmpty() }?.let(::add)
        add(previewParameter.toString())
      }.joinToString(",")

    public fun withPreviewParameterIndex(index: Int): Provider<T> =
      copy(previewParameter = previewParameter.copy(index = index))
  }

  public data object Empty : PaparazziPreviewData

  public data class Error(
    val snapshotName: String,
    val preview: PreviewData,
    val message: String
  ) : PaparazziPreviewData {
    override fun toString(): String =
      buildList {
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
  override fun toString(): String =
    buildList {
      fontScale?.fontScale()?.displayName()?.let(::add)
      uiMode?.lightDarkName()?.let(::add)
      uiMode?.uiModeName()?.let(::add)
      device?.let {
        if (it != PaparazziPreviewDefaults.DEVICE_ID) {
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

/**
 * Maps [fontScale] to enum values similar to Preview
 * see:
https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/compose-designer/src/com/android/tools/idea/compose/pickers/preview/enumsupport/PsiEnumValues.kt
 */
internal fun Float.fontScale() =
  FontScale.values().find { this == it.value } ?: FontScale.CUSTOM.apply { value = this@fontScale }

internal enum class FontScale(var value: Float?) {
  DEFAULT(1f),
  SMALL(0.85f),
  LARGE(1.15f),
  LARGEST(1.30f),
  CUSTOM(null);

  fun displayName() =
    when (this) {
      CUSTOM -> "fs_$value"
      else -> name
    }
}

internal fun Int.lightDarkName() =
  when (this and Configuration.UI_MODE_NIGHT_MASK) {
    Configuration.UI_MODE_NIGHT_NO -> "Light"
    Configuration.UI_MODE_NIGHT_YES -> "Dark"
    else -> null
  }

internal fun Int.uiModeName() =
  when (this and Configuration.UI_MODE_TYPE_MASK) {
    Configuration.UI_MODE_TYPE_NORMAL -> "Normal"
    Configuration.UI_MODE_TYPE_CAR -> "Car"
    Configuration.UI_MODE_TYPE_DESK -> "Desk"
    Configuration.UI_MODE_TYPE_APPLIANCE -> "Appliance"
    Configuration.UI_MODE_TYPE_WATCH -> "Watch"
    Configuration.UI_MODE_TYPE_VR_HEADSET -> "VR_Headset"
    else -> null
  }
