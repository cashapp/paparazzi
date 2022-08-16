package app.cash.paparazzi.processor

import app.cash.paparazzi.api.Paparazzi
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class PaparazziProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return PaparazziProcessor(environment.codeGenerator, environment.logger)
  }
}

class PaparazziProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger
) : SymbolProcessor {

  override fun process(resolver: Resolver): List<KSAnnotated> {
    return resolver.findFunctions(Paparazzi::class.qualifiedName.toString())
      .onEach { function ->
        val models = function.accept(PaparazziVisitor(logger), Unit)
        writeFiles(models, resolver)
      }
      .filterNot { it.validate() }
      .toList()
  }

  private fun Resolver.findFunctions(annotationName: String): Sequence<KSFunctionDeclaration> {
    val symbols = getSymbolsWithAnnotation(annotationName)

    val direct = symbols.filterIsInstance<KSFunctionDeclaration>()

    // combined annotations are indirectly applied to a function via ANNOTATION_CLASS targets
    val indirect = symbols.filterIsInstance<KSClassDeclaration>()
      .map { findFunctions(it.qualifiedName!!.asString()) }
      .flatten()

    return direct.plus(indirect).distinct()
  }

  private fun writeFiles(models: Sequence<PaparazziModel>, resolver: Resolver) {
    val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())

    models.forEachIndexed { i, model ->
      val file = PaparazziPoet.buildFile(model, i)

      val fileOS = codeGenerator.createNewFile(dependencies, file.packageName, file.name)
      OutputStreamWriter(fileOS, StandardCharsets.UTF_8).use(file::writeTo)
    }
  }
}
