# `@Paparazzi`
An annotation used to generate Paparazzi snapshots for composable preview functions.

## Installation
Add the following to your `build.gradle` file

```groovy
apply plugin: 'app.cash.paparazzi.preview'
```

## Basic Usage
Apply the annotation alongside an existing preview method. The annotation processor will generate a manifest of information about this method and the previews applied.

```kotlin
import app.cash.paparazzi.preview.Paparazzi

@Paparazzi
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello, Paparazzi Annotation")
}
```

Run `:recordPaparazziDebug` in your module to generate preview snapshots (and optionally verify them using `:verifyPaparazziDebug`) as you normally would.

A test class to generate snapshots for annotated previews will automatically be generated.
If you prefer to define a custom snapshot test, you mey disable test generation by adding the following to your `gradle.properties` file.

```properties
app.cash.paparazzi.annotation.generateTestClass=false
```

You may implement your own test class, as shown below, to create snapshots for all previews included in the generated manifest (`paparazziAnnotations`).

```kotlin
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.preview.PaparazziPreviewData
import app.cash.paparazzi.preview.PaparazziValuesProvider
import app.cash.paparazzi.preview.deviceConfig
import app.cash.paparazzi.preview.snapshot
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class PreviewTests(
  @TestParameter(valuesProvider = PreviewConfigValuesProvider::class)
  private val preview: PaparazziPreviewData,
) {
  private class PreviewConfigValuesProvider : PaparazziValuesProvider(paparazziPreviews)

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = preview.deviceConfig(),
    renderingMode = SHRINK,
  )

  @Test
  fun preview() {
    paparazzi.snapshot(preview)
  }
}
```

## Preview Parameter
If your preview function accepts a parameter using `@PreviewParameter`, then snapshots will be created for each combination of preview / param.

```kotlin
@Paparazzi
@Preview
@Composable
fun MyViewPreview(@PreviewParameter(MyTitleProvider::class) title: String) {
  MyView(title = title)
}

class MyTitleProvider : PreviewParameterProvider<String> {
  override val values = sequenceOf("Hello", "Paparazzi", "Annotation")
}
```

## Composable Wrapping
If you need to apply additional UI treatment around your previews, you may provide a composable wrapper within the test.

```kotlin
paparazzi.snapshot(preview) { content ->
  Box(modifier = Modifier.background(Color.Gray)) {
    content()
  }
}
```

## Preview Composition
If you have multiple preview annotations applied to a function, or have them nested behind a custom annotation, they will all be included in the snapshot manifest.

```kotlin
@Paparazzi
@ScaledThemedPreviews
@Composable
fun MyViewPreview() {
  MyView(title = "Hello, Paparazzi Annotation")
}

@Preview(name = "small light", fontScale = 1f, uiMode = Configuration.UI_MODE_NIGHT_NO, device = PIXEL_3_XL)
@Preview(name = "small dark", fontScale = 1f, uiMode = Configuration.UI_MODE_NIGHT_YES, device = PIXEL_3_XL)
@Preview(name = "large light", fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO, device = PIXEL_3_XL)
@Preview(name = "large dark", fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES, device = PIXEL_3_XL)
annotation class ScaledThemedPreviews
```
