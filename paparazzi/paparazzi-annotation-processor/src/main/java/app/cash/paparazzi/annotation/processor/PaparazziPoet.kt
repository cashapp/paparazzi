package app.cash.paparazzi.annotation.processor

import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.processor.models.PaparazziModel
import app.cash.paparazzi.annotation.processor.poetry.Imports
import app.cash.paparazzi.annotation.processor.poetry.buildConstructorProperty
import app.cash.paparazzi.annotation.processor.poetry.buildInitializer
import app.cash.paparazzi.annotation.processor.poetry.buildProviderClass
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
    val className = model.functionName + "Test${if (index > 0) index else ""}"

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
    val primaryConstructorBuilder = FunSpec.constructorBuilder()
    val constructorProperties = mutableListOf<PropertySpec>()

    if (model.composableWrapper != null) {
      addType(model.composableWrapper.buildProviderClass())

      primaryConstructorBuilder.addParameter(
        "wrapperParam",
        model.composableWrapper.value.toTypeName()
      )
      constructorProperties.add(model.composableWrapper.buildConstructorProperty())
    }

    if (model.previewParam != null) {
      addType(model.previewParam.buildProviderClass())

      primaryConstructorBuilder.addParameter(
        "previewParam",
        model.previewParam.type.toTypeName()
      )
      constructorProperties.add(model.previewParam.buildConstructorProperty())
    }

    primaryConstructor(primaryConstructorBuilder.build())
    addProperties(constructorProperties)
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
          addStatement("%T().wrap(wrapperParam) {", model.composableWrapper.wrapper.toTypeName())
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
      // .apply {
      //   if (model.wrapperGeneric != null) {
      //     addParameter(
      //       ParameterSpec.builder("param", model.wrapperGeneric.toTypeName())
      //         .addAnnotation(Imports.TestInject.testParameter)
      //         .build()
      //     )
      //   }
      // }
      .addCode(codeBuilder.build())
      .build()
      .let(::addFunction)
  }
}
