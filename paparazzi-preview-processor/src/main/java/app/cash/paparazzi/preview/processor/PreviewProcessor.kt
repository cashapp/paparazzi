package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

public class PreviewProcessorProvider : SymbolProcessorProvider {
  override fun create(
    environment: SymbolProcessorEnvironment
  ): PreviewProcessor = PreviewProcessor(environment)
}

public class PreviewProcessor(
  private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> = emptyList()
}
