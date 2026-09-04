package app.cash.paparazzi.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

annotation class GenerateGreeting

class GreetingProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
    GreetingProcessor(environment.codeGenerator)
}

class GreetingProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
  private var done = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (done) return emptyList()
    val symbols = resolver
      .getSymbolsWithAnnotation("app.cash.paparazzi.processor.GenerateGreeting")
      .toList()
    if (symbols.isEmpty()) return emptyList()
    done = true
    codeGenerator.createNewFile(
      Dependencies(false),
      "app.cash.paparazzi.plugin.test",
      "Greeting"
    ).bufferedWriter().use { writer ->
      writer.write("package app.cash.paparazzi.plugin.test\n\n")
      writer.write("object Greeting { const val TEXT: String = \"generated-by-ksp\" }\n")
    }
    return emptyList()
  }
}
