package app.cash.paparazzi.internal

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.internal.resources.AppResourceRepository
import app.cash.paparazzi.internal.resources.makeAarRepositoryFromExplodedAar
import app.cash.paparazzi.internal.resources.pseudolocalizeIfNeeded
import app.cash.paparazzi.internal.resources.resolveProjectPath
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectResourceCacheTest {
  @Test
  fun reusesEquivalentConfigurationsAndMatchesUncachedSelection() {
    val repository = repository()
    val cache = ProjectResourceCache()
    val configuration = DeviceConfig.NEXUS_5.folderConfiguration

    val cached = cache.get(repository, configuration)
    assertThat(cache.get(repository, FolderConfiguration.copyOf(configuration))).isSameInstanceAs(cached)

    val uncached = repository.getConfiguredResources(configuration).rowMap()
    assertThat(cached.keys).isEqualTo(uncached.keys)
    for ((namespace, byType) in uncached) {
      for ((type, values) in byType) {
        assertThat(cached.getValue(namespace).getValue(type).values().map { it.name })
          .containsExactlyElementsIn(values.values().map { it.name })
      }
    }
  }

  @Test
  fun keepsPseudolocalesSeparateFromOrdinaryLocales() {
    val repository = repository()
    val cache = ProjectResourceCache()
    val normal = DeviceConfig.NEXUS_5.folderConfiguration
    val original = cache.get(repository, normal)
    val originalValue = original.stringValue("string_name")

    for (locale in listOf("en-rXA", "ar-rXB")) {
      val configuration = DeviceConfig.NEXUS_5.copy(locale = locale).folderConfiguration
      val expected = repository.getConfiguredResources(configuration)
        .pseudolocalizeIfNeeded(configuration.localeQualifier)
        .rowMap()
      val actual = cache.get(repository, configuration)
      assertThat(actual).isNotSameInstanceAs(original)
      assertThat(actual.stringValue("string_name")).isEqualTo(expected.stringValue("string_name"))
      assertThat(actual.stringValue("string_name")).isNotEqualTo(originalValue)
    }

    assertThat(cache.get(repository, normal)).isSameInstanceAs(original)
    assertThat(original.stringValue("string_name")).isEqualTo(originalValue)
  }

  @Test
  fun evictsTheLeastRecentlyUsedConfigurationAtCapacity() {
    val repository = repository()
    val cache = ProjectResourceCache(capacity = 2)
    val (first, second, third) = (100..102).map {
      DeviceConfig.NEXUS_5.copy(screenWidth = it).folderConfiguration
    }

    val firstEntry = cache.get(repository, first)
    val secondEntry = cache.get(repository, second)
    // Refresh the first entry, so the second one is evicted when the third is inserted.
    assertThat(cache.get(repository, first)).isSameInstanceAs(firstEntry)
    cache.get(repository, third)

    assertThat(cache.get(repository, first)).isSameInstanceAs(firstEntry)
    assertThat(cache.get(repository, second)).isNotSameInstanceAs(secondEntry)
  }

  @Test
  fun invalidatesWhenTheRepositoryChanges() {
    val repository = repository()
    val cache = ProjectResourceCache()
    val configuration = DeviceConfig.NEXUS_5.folderConfiguration
    val original = cache.get(repository, configuration)

    assertThat(cache.get(repository(), configuration)).isNotSameInstanceAs(original)
    assertThat(cache.get(repository, configuration)).isNotSameInstanceAs(original)
  }

  private fun ConfiguredResources.stringValue(name: String): String? =
    getValue(ResourceNamespace.RES_AUTO).getValue(ResourceType.STRING)[name]?.value

  private fun repository() =
    AppResourceRepository.create(
      localResourceDirectories = listOf(resolveProjectPath("src/test/resources/folders/res").toFile()),
      moduleResourceDirectories = emptyList(),
      libraryRepositories = listOf(makeAarRepositoryFromExplodedAar("my_aar_lib"))
    )
}
