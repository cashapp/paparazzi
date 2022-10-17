package app.cash.paparazzi.annotation.processor

import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.processor.models.PaparazziModel
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
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
    return resolver.findPaparazziFunctions()
      .map { (function, annotations) ->
        val models = function.accept(PaparazziVisitor(annotations, logger), Unit)
        writeFiles(models, resolver)
        function
      }
      .filterNot { it.validate() }
      .toList()
  }

  private fun Resolver.findPaparazziFunctions() =
    getSymbolsWithAnnotation("androidx.compose.runtime.Composable")
      .filterIsInstance<KSFunctionDeclaration>()
      .map { Pair(it, it.annotations.findPaparazzi()) }
      .filter { (_, annotations) -> annotations.count() > 0 }

  private fun writeFiles(models: Sequence<PaparazziModel>, resolver: Resolver) {
    val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())

    models.forEachIndexed { i, model ->
      val file = PaparazziPoet.buildFile(model, i)

      val fileOS = codeGenerator.createNewFile(dependencies, file.packageName, file.name)
      OutputStreamWriter(fileOS, StandardCharsets.UTF_8).use(file::writeTo)
    }
  }

  /**
   * when the same annotations are applied higher in the tree, an endless recursive lookup can occur.
   * using a stack to keep to a record of each symbol lets us break when we hit one we've already encountered
   *
   * ie:
   * @Bottom
   * annotation class Top
   *
   * @Top
   * annotation class Bottom
   *
   * @Bottom
   * fun SomeFun()
   */
  private fun Sequence<KSAnnotation>.findPaparazzi(stack: Set<KSAnnotation> = setOf()): Sequence<KSAnnotation> {
    val direct = filter { it.isPaparazzi() }
    val indirect = filterNot { it.isPaparazzi() || stack.contains(it) }
      .map { it.parentAnnotations().findPaparazzi(stack.plus(it)) }
      .flatten()
    return direct.plus(indirect)
  }

  private fun KSAnnotation.parentAnnotations() = declaration().annotations

  private fun KSAnnotation.isPaparazzi() =
    qualifiedName() == Paparazzi::class.qualifiedName.toString()

  private fun KSAnnotation.qualifiedName() = declaration().qualifiedName?.asString() ?: ""

  private fun KSAnnotation.declaration() = annotationType.resolve().declaration
}
