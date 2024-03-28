package app.cash.paparazzi.gradle

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

@CacheableRule
abstract class AndroidJvmAndJvmCompatible : AttributeCompatibilityRule<KotlinPlatformType> {
  override fun execute(t: CompatibilityCheckDetails<KotlinPlatformType>) {
    val consumer = t.consumerValue ?: return
    val producer = t.producerValue ?: return
    if (consumer == KotlinPlatformType.jvm && producer == KotlinPlatformType.androidJvm) {
      t.compatible()
    }
  }
}
