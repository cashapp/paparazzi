# `@Paparazzi`
An annotation to generate Paparazzi tests for composable functions.

## Basic Usage
In your test directory, define a composable method and apply the annotation. The annotation processor will handle generating a test class for this composable without you having to worry about all the boilerplate.

```kotlin
@Paparazzi
fun MyViewTest() {
  MyView(title = "Hello, Annotation")
}
```

The annotation exposes a [number of parameters](./src/main/java/app/cash/paparazzi/annotation/api/Paparazzi.kt) to configure Paparazzi as you see fit.

```kotlin
@Paparazzi(
  name = "big text pixel scroll",
  fontScale = 3.0f,
  deviceConfig = DeviceConfig.PIXEL_5,
  renderingMode = RenderingMode.V_SCROLL,
  theme = "android:Theme.Material.Fullscreen",
)
```

## Composable Previews
If you're already using preview functions to visualize your composable UI, then you can simply annotate that function to create a test.

```kotlin
@Paparazzi
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello, Annotation")
}
```

*A word of caution about this approach: Previews are meant as a developer tool and can change to help visualize different scenarios. Changing a preview will likely invalidate a test and could create confusion.*

## Annotation Composition
Want to create more than one test for a composable function?
Do you have a highly-configured annotation that you want to re-use on multiple functions?

If you answered *yes* to either of those questions, you should consider defining a custom annotation as a fixture to hold your `@Paparazzi` definitions.

```kotlin
@Paparazzi(
  name = "pixel5",
  deviceConfig = DeviceConfig.PIXEL_5,
)
@Paparazzi(
  name = "large text",
  fontScale = 2.0f,
)
annotation class MyPaparazzi

@MyPaparazzi
@Composable
fun MyViewTest() {
  MyView(title = "Hello Paparazzi, in multiple ways")
}
```
This style of composition can even be nested, if desired.

```kotlin
@Paparazzi(
  name = "pixel5",
  deviceConfig = DeviceConfig.PIXEL_5,
)
@Paparazzi(
  name = "large text",
  fontScale = 2.0f,
)
annotation class MyPaparazzi

@Paparazzi(
  name = "huge text",
  fontScale = 4.0f,
)
@MyPaparazzi
annotation class MyHugePaparazzi
```

## Composable Wrapping
It might be necessary for you to wrap your composable with additional logic to prepare it for the Paparazzi test.
In this case, a configuration param called `composableWrapper` can be set to do just that.
To do this, implement the provided `ComposableWrapper` interface.

```kotlin
class BlueBoxComposableWrapper : ComposableWrapper {
  @Composable
  override fun wrap(content: @Composable () -> Unit) {
    Box(
      modifier = Modifier
        .wrapContentSize()
        .background(Color.Blue)
        .padding(24.dp)
    ) {
      content()
    }
  }
}

@Paparazzi(
  name = "blue box",
  composableWrapper = BlueBoxComposableWrapper::class
)
@Composable
fun MyViewTest() {
  MyView(title = "Hello Paparazzi, in a blue box")
}
```

## Test Parameter Injection
The `@Paparazzi` annotation also utilizes `TestParameterInjector` to conveniently execute multiple tests on a handful of configuration parameters.

### Font Scaling
The annotation exposes the `fontScales` configuration which accepts an array of `Float` values. Setting this parameter will override the value set in the (non-plural) `fontScale` parameter.

```kotlin
@Paparazzi(
  name = "fontScaling",
  fontScales = [1.0f, 2.0f, 3.0f]
)
```

### `@PreviewParameter`
If you've applied the annotation to a `@Preview` function that accepts a parameter using `@PreviewParameter`, then the values of that provider will be converted to injected parameters sent through your test.

```kotlin
@Paparazzi
@Preview
@Composable
fun MyViewPreview(@PreviewParameter(MyTitleProvider::class) title: String) {
  MyView(title = title)
}

class MyTitleProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf("Hello", "Paparazzi")
}
```

### Values Composable Wrapper
You can alternatively create a composable wrapper by extending the `ValuesComposableWrapper<T>` class.
This allows you to provide a set of values to your wrapper, similar to how you would with `PreviewParameterProvider`.
These values are mapped to an injected parameter in your test and passed into your composable wrapper.

```kotlin
class MyThemeComposableWrapper : ValuesComposableWrapper<MyTheme>() {
  override val values = sequenceOf(MyTheme.LIGHT, MyTheme.DARK)

  @Composable
  override fun wrap(
    value: MyTheme,
    content: @Composable () -> Unit
  ) {
    MySimpleTheme(value) {
      content()
    }
  }
}

@Paparazzi(
  name = "themed",
  composableWrapper = MyThemeComposableWrapper::class
)
@Composable
fun MyViewTest() {
  MyView(title = "Hello Paparazzi, wrapped in different themes")
}
```

## Sample
See [the sample](../../sample/src/main/java/app/cash/paparazzi/sample) for working implementations
