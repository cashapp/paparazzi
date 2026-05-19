package app.cash.paparazzi.gradle.artifacts

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Creates a dependency scope configuration, used for declaring dependencies.
 * See also [resolvableConfiguration] and [consumableConfiguration].
 */
internal fun Project.dependencyScopeConfiguration(
  configurationName: String
): NamedDomainObjectProvider<out Configuration> {
  return configurations.dependencyScope(configurationName)
}

/**
 * Creates a resolvable configuration, used by projects to consume the dependencies that they
 * declare on the [dependencyScopeConfiguration] configurations.
 */
internal fun Project.resolvableConfiguration(
  configurationName: String,
  dependencyScopeConfiguration: Configuration,
  configureAction: Action<in Configuration>
): NamedDomainObjectProvider<out Configuration> {
  return configurations.resolvable(configurationName) { r ->
    r.extendsFrom(dependencyScopeConfiguration)
    configureAction.execute(r)
  }
}

/**
 * Creates a consumable configuration, enabling projects to produce artifacts for consuming projects,
 * which have declared a dependency on _this_ project using the [dependencyScopeConfiguration]
 * configuration.
 */
internal fun Project.consumableConfiguration(
  configurationName: String,
  dependencyScopeConfiguration: Configuration? = null,
  configureAction: Action<in Configuration>
): NamedDomainObjectProvider<out Configuration> {
  return configurations.consumable(configurationName) { c ->
    dependencyScopeConfiguration?.let { c.extendsFrom(it) }
    configureAction.execute(c)
  }
}
