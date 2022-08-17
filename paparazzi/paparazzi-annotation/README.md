# `@Paparazzi`
An annotation to turn `@Preview` (or no-arg) composable functions into Paparazzi tests.

## Basic Usage
If you're already using preview functions to visualize your composable UI, then you can simply annotate that function to create a test!

Example:
```kotlin
@Paparazzi
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello World")
}
```

*Note: It's not required to be a preview function. Any no-arg `@Composable fun` will work.*

The annotation exposes a [number of parameters](./src/main/java/app/cash/paparazzi/annotation/api/Paparazzi.kt) to configure Paparazzi as you see fit.

Example:
```kotlin
@Paparazzi(
  name = "big text pixel scroll",
  fontScale = 3.0f,
  deviceConfig = DeviceConfig.PIXEL_5,
  renderingMode = RenderingMode.V_SCROLL,
  theme = "android:Theme.Material.Fullscreen",
)
```

## Annotation Composition
Want to create more than one test for a composable function?
Do you have a highly-configured annotation that you want to re-use on multiple functions?

If you answered *yes* to either of those questions, you should consider defining a custom annotation as a fixture to hold your `@Paparazzi` definitions!

Example:
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
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello World")
}
```
This style of composition can even be nested, if you desire.

Example:
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
The easiest way to do this is to extend the provided `DefaultComposableWrapper` class and implement the `wrap()` function.

Example:
```kotlin
class BlueBoxComposableWrapper : DefaultComposableWrapper() {
  @Composable
  override fun wrap(
    value: Unit,
    content: @Composable () -> Unit
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize()
        .background(Color.Green)
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
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello World, in a blue box")
}
```
*You might notice the `wrap()` function is accepting `value: Unit` as a parameter. See below for examples on providing that wrapper with custom configuration.*

## Test Parameter Injection
The `@Paparazzi` annotation also utilizes `TestParameterInjector` to conveniently execute multiple tests on a handful of configuration parameters.

### Font Scaling
The annotation exposes the `fontScales` configuration which accepts an array of `Float` values. Setting this parameter will override the value set in the (non-plural) `fontScale` parameter.

Example:
```kotlin
@Paparazzi(
  name = "fontScaling",
  fontScales = [1.0f, 2.0f, 3.0f]
)
```

### `@PreviewParameter`
If you've applied the annotation to a `@Preview` function that accepts a parameter using `@PreviewParameter`, then the values of that provider will be converted to injected parameters sent through your test.

Example:
```kotlin
@Paparazzi
@Preview
@Composable
fun MyViewPreview(@PreviewParameter(MyTitleProvider::class) title: String) {
  MyView(title = title)
}

class MyTitleProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf("Hello", "World")
}
```

### Composable Wrapper
You can alternatively create a composable wrapper by simply implementing the base `ComposableWrapper<T>` interface.
This allows you to provide a set of values to your wrapper, similar to how you would with `PreviewParameterProvider`.
These values are mapped to an injected parameter in your test and passed into your composable wrapper.

Example:
```kotlin
class MyThemeComposableWrapper : ComposableWrapper<MyTheme> {
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
  composableWrapper = ThemeComposableWrapper::class
)
@Preview
@Composable
fun MyViewPreview() {
  MyView(title = "Hello World")
}
```

## Sample
See [the sample](../../sample/src/main/java/app/cash/paparazzi/sample) for working implementations
