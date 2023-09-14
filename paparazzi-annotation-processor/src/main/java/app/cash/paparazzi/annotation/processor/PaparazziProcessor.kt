package app.cash.paparazzi.annotation.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

class PaparazziProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment) = PaparazziProcessor(environment)
}

class PaparazziProcessor(
  private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> = emptyList()
}
