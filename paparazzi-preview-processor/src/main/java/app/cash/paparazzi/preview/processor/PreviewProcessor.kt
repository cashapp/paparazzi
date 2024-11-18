package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File

public class PreviewProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): PreviewProcessor = PreviewProcessor(environment)
}

public class PreviewProcessor(
  private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
  private val logger = environment.logger
  private var invoked = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (invoked) {
      // Only need a single round
      return emptyList()
    }
    invoked = true

    val allFiles = resolver.getAllFiles().toList()

    val namespace = environment.options["app.cash.paparazzi.preview.namespace"]!!

    val dependencies = Dependencies(true, *allFiles.toTypedArray())
    val isTestSourceSet = discoverVariant(namespace, dependencies).endsWith("UnitTest")

    return resolver.getSymbolsWithAnnotation("app.cash.paparazzi.annotations.Paparazzi")
      .filterIsInstance<KSFunctionDeclaration>()
      .also { functions ->
        logger.log("found ${functions.count()} function(s)")
        PaparazziPoet(logger, namespace).buildFiles(functions, isTestSourceSet).forEach { file ->
          logger.log("writing file: ${file.packageName}.${file.name}.kt")
          file.writeTo(environment.codeGenerator, dependencies)
        }
      }
      .filterNot { it.validate() }
      .toList()
  }

  private fun discoverVariant(namespace: String, dependencies: Dependencies): String {
    environment.codeGenerator.createNewFile(dependencies, namespace, "paparazziVariant", "txt")
    val file = environment.codeGenerator.generatedFile.first()
    val fileSeparator = Regex.escape(File.separator)
    val variantNameRegex = Regex("ksp$fileSeparator(.+)${fileSeparator}resources")
    return (variantNameRegex.find(file.absolutePath)?.groups?.get(1)?.value ?: "")
      .also { file.writeText(it) }
  }

  private fun KSPLogger.log(message: String) = info("PreviewProcessor - $message")
}
