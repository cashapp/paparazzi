package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File

public class PreviewProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): PreviewProcessor = PreviewProcessor(environment)
}

public class PreviewProcessor(
  private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

  override fun process(resolver: Resolver): List<KSAnnotated> {
    // Due to codgen and multi-round processing of ksp
    // https://kotlinlang.org/docs/ksp-multi-round.html
    if (resolver.getAllFiles().any { it.fileName.contains("PaparazziPreviews") } ||
      resolver.getNewFiles().toList().isEmpty()
    ) {
      "Skipping subsequent run due to PaparazziPreviews.kt already created and caused ksp re-run".log()
      return emptyList()
    }

    val allFiles = resolver.getAllFiles().toList()
    if (allFiles.isEmpty()) return emptyList()

    val env = EnvironmentOptions(
      namespace = environment.options["app.cash.paparazzi.preview.namespace"]!!
    )

    val dependencies = Dependencies(true, *allFiles.toTypedArray())
    val isTestSourceSet = env.discoverVariant(dependencies).endsWith("UnitTest")

    return resolver.getSymbolsWithAnnotation("androidx.compose.runtime.Composable")
      .findPaparazzi()
      .also { functions ->
        "found ${functions.count()} function(s)".log()
        PaparazziPoet.buildFiles(functions, isTestSourceSet, env).forEach { file ->
          "writing file: ${file.packageName}.${file.name}.kt".log()
          file.writeTo(environment.codeGenerator, dependencies)
        }
      }
      .filterNot { it.validate() }
      .toList()
  }

  private fun EnvironmentOptions.discoverVariant(dependencies: Dependencies): String {
    environment.codeGenerator.createNewFile(dependencies, namespace, "paparazziVariant", "txt")
    val file = environment.codeGenerator.generatedFile.first()
    val fileSeparator = Regex.escape(File.separator)
    val variantNameRegex = Regex("ksp$fileSeparator(.+)${fileSeparator}resources")
    return (variantNameRegex.find(file.absolutePath)?.groups?.get(1)?.value ?: "")
      .also {
        it.log()
        file.writeText(it)
      }
  }

  private fun String.log() = environment.logger.info("PaparazziProcessor - $this")
}
