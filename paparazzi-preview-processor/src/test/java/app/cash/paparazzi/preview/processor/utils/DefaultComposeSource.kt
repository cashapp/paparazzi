package app.cash.paparazzi.preview.processor.utils

import com.tschuchort.compiletesting.SourceFile

val DefaultComposeSource =
  listOf(
    SourceFile.kotlin(
      "Composable.kt",
      """
          package androidx.compose.runtime

          /**
           * [Composable] functions are the fundamental building blocks of an application built with Compose.
           *
           * [Composable] can be applied to a function or lambda to indicate that the function/lambda can be
           * used as part of a composition to describe a transformation from application data into a
           * tree or hierarchy.
           *
           * Annotating a function or expression with [Composable] changes the type of that function or
           * expression. For example, [Composable] functions can only ever be called from within another
           * [Composable] function. A useful mental model for [Composable] functions is that an implicit
           * "composable context" is passed into a [Composable] function, and is done so implicitly when it
           * is called from within another [Composable] function.  This "context" can be used to store
           * information from previous executions of the function that happened at the same logical point of
           * the tree.
           */
          @MustBeDocumented
          @Retention(AnnotationRetention.BINARY)
          @Target(
              // function declarations
              // @Composable fun Foo() { ... }
              // lambda expressions
              // val foo = @Composable { ... }
              AnnotationTarget.FUNCTION,

              // type declarations
              // var foo: @Composable () -> Unit = { ... }
              // parameter types
              // foo: @Composable () -> Unit
              AnnotationTarget.TYPE,

              // composable types inside of type signatures
              // foo: (@Composable () -> Unit) -> Unit
              AnnotationTarget.TYPE_PARAMETER,

              // composable property getters and setters
              // val foo: Int @Composable get() { ... }
              // var bar: Int
              //   @Composable get() { ... }
              AnnotationTarget.PROPERTY_GETTER
          )
          annotation class Composable
        """
    ),
    SourceFile.kotlin(
      "PreviewAnnotation.kt",
      """
          package androidx.compose.ui.tooling.preview


          /**
           * [Preview] can be applied to either of the following:
           * - @[Composable] methods with no parameters to show them in the Android Studio preview.
           * - Annotation classes, that could then be used to annotate @[Composable] methods or other
           * annotation classes, which will then be considered as indirectly annotated with that [Preview].
           *
           * The annotation contains a number of parameters that allow to define the way the @[Composable]
           * will be rendered within the preview.
           *
           * The passed parameters are only read by Studio when rendering the preview.
           *
           * @param name Display name of this preview allowing to identify it in the panel.
           * @param group Group name for this @[Preview]. This allows grouping them in the UI and display only
           * one or more of them.
           * @param apiLevel API level to be used when rendering the annotated @[Composable]
           * @param widthDp Max width in DP the annotated @[Composable] will be rendered in. Use this to
           * restrict the size of the rendering viewport.
           * @param heightDp Max height in DP the annotated @[Composable] will be rendered in. Use this to
           * restrict the size of the rendering viewport.
           * @param locale Current user preference for the locale, corresponding to
           * [locale](https://d.android.com/guide/topics/resources/providing-resources.html#LocaleQualifier) resource
           * qualifier. By default, the `default` folder will be used.
           * @param fontScale User preference for the scaling factor for fonts, relative to the base
           * density scaling.
           * @param showSystemUi If true, the status bar and action bar of the device will be displayed.
           * The @[Composable] will be render in the context of a full activity.
           * @param showBackground If true, the @[Composable] will use a default background color.
           * @param backgroundColor The 32-bit ARGB color int for the background or 0 if not set
           * @param uiMode Bit mask of the ui mode as per [android.content.res.Configuration.uiMode]
           * @param device Device string indicating the device to use in the preview. See the available
           * devices in [Devices].
           * @param wallpaper Integer defining which wallpaper from those available in Android Studio
           * to use for dynamic theming.
           */
          @MustBeDocumented
          @Retention(AnnotationRetention.BINARY)
          @Target(
              AnnotationTarget.ANNOTATION_CLASS,
              AnnotationTarget.FUNCTION
          )
          @Repeatable
          annotation class Preview(
            val name: String = "",
            val group: String = "",
            val apiLevel: Int = -1,
            val widthDp: Int = -1,
            val heightDp: Int = -1,
            val locale: String = "",
            val fontScale: Float = 1f,
            val showSystemUi: Boolean = false,
            val showBackground: Boolean = false,
            val backgroundColor: Long = 0,
            val uiMode: Int = 0,
            val device: String = "",
            val wallpaper: Int = 0,
          )
        """
    ),
    SourceFile.kotlin(
      "Paparazzi.kt",
      """
                  package app.cash.paparazzi.annotations

                  @Target(AnnotationTarget.FUNCTION)
                  @Retention(AnnotationRetention.BINARY)
                  annotation class Paparazzi
                 """
    ),
    SourceFile.kotlin(
      "PreviewParameter.kt",
      """
        package androidx.compose.ui.tooling.preview

        import kotlin.jvm.JvmDefaultWithCompatibility
        import kotlin.reflect.KClass

        /**
         * Interface to be implemented by any provider of values that you want to be injected as @[Preview]
         * parameters. This allows providing sample information for previews.
         */
        @JvmDefaultWithCompatibility
        interface PreviewParameterProvider<T> {
            /**
             * [Sequence] of values of type [T] to be passed as @[Preview] parameter.
             */
            val values: Sequence<T>

            /**
             * Returns the number of elements in the [values] [Sequence].
             */
            val count get() = values.count()
        }

        /**
         * [PreviewParameter] can be applied to any parameter of a @[Preview].
         *
         * @param provider A [PreviewParameterProvider] class to use to inject values to the annotated
         * parameter.
         * @param limit Max number of values from [provider] to inject to this parameter.
         */
        annotation class PreviewParameter(
            val provider: KClass<out PreviewParameterProvider<*>>,
            val limit: Int = Int.MAX_VALUE
        )
      """.trimIndent()
    )
  )
