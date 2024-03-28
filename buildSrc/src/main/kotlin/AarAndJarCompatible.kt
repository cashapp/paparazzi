package app.cash.paparazzi.gradle

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements

@CacheableRule
abstract class AarAndJarCompatible : AttributeCompatibilityRule<LibraryElements> {
  override fun execute(t: CompatibilityCheckDetails<LibraryElements>) {
    val consumer = t.consumerValue ?: return
    val producer = t.producerValue ?: return
    if (consumer.name == LibraryElements.JAR && producer.name == "aar") {
      t.compatible()
    }
    if (consumer.name == LibraryElements.CLASSES && producer.name == "aar") {
      t.compatible()
    }
  }
}
