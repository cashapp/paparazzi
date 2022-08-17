package app.cash.paparazzi.annotation.processor

import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.processor.models.PaparazziModel
import app.cash.paparazzi.annotation.processor.poetry.Imports
import app.cash.paparazzi.annotation.processor.poetry.buildConstructorParams
import app.cash.paparazzi.annotation.processor.poetry.buildInitializer
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

object PaparazziPoet {

  fun buildFile(
    model: PaparazziModel,
    index: Int
  ): FileSpec {
    val className = model.functionName + "Test${if (model.showClassIndex) "_$index" else ""}"

    return FileSpec.builder(model.packageName, "${Paparazzi::class.simpleName}_$className")
      .addType(
        TypeSpec.classBuilder(className)
          .addRunWithAnnotation()
          .addInjectedConstructor(model)
          .addPaparazziProperty(model)
          .addTestFunction(model)
          .build()
      )
      .build()
  }

  private fun TypeSpec.Builder.addRunWithAnnotation() = addAnnotation(
    AnnotationSpec.builder(Imports.JUnit.runWith)
      .addMember("%T::class", Imports.TestInject.testParameterInjector)
      .build()
  )

  private fun TypeSpec.Builder.addInjectedConstructor(model: PaparazziModel) = apply {
    model.buildConstructorParams().let { (params, properties, types) ->
      FunSpec.constructorBuilder()
        .addParameters(params)
        .build()
        .let(::primaryConstructor)

      addProperties(properties)
      addTypes(types)
    }
  }

  private fun TypeSpec.Builder.addPaparazziProperty(model: PaparazziModel) = apply {
    PropertySpec.builder("paparazzi", Imports.Paparazzi.paparazzi)
      .addAnnotation(
        AnnotationSpec.builder(Imports.JUnit.rule)
          .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
          .build()
      )
      .initializer(model.buildInitializer())
      .build()
      .let(::addProperty)
  }

  private fun TypeSpec.Builder.addTestFunction(model: PaparazziModel) = apply {
    val codeBuilder = CodeBlock.builder()

    codeBuilder
      .addStatement("paparazzi.snapshot {")
      .indent()
      .apply {
        if (model.composableWrapper != null) {
          val wrapperParam = if (model.composableWrapper.value == null) "Unit" else "wrapperParam"
          addStatement("%T().wrap($wrapperParam) {", model.composableWrapper.wrapper.toTypeName())
          indent()
        }
      }
      .addStatement(
        "%L(${if (model.previewParam != null) "previewParam" else ""})",
        model.functionName
      )
      .apply {
        if (model.composableWrapper != null) {
          unindent()
          addStatement("}")
        }
      }
      .unindent()
      .addStatement("}")

    FunSpec.builder(model.testName.ifEmpty { "default" }.lowercase())
      .addAnnotation(Imports.JUnit.test)
      .addCode(codeBuilder.build())
      .build()
      .let(::addFunction)
  }
}
