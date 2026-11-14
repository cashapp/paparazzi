package app.cash.paparazzi.internal

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.internal.resources.AppResourceRepository
import app.cash.paparazzi.internal.resources.FrameworkResourceRepository
import app.cash.paparazzi.internal.resources.pseudolocalizeIfNeeded
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Paths

class FrameworkResourceCacheTest {
  @Test
  fun reusesEquivalentConfigurationsWithoutMixingLocales() {
    val repository = repository()
    val cache = FrameworkResourceCache()
    val normal = DeviceConfig.NEXUS_5.folderConfiguration
    val original = cache.get(repository, normal)
    val originalCancel = original.getValue(ResourceType.STRING)["cancel"]!!.value
    assertThat(cache.get(repository, FolderConfiguration.copyOf(normal))).isSameInstanceAs(original)
    for (locale in listOf("en-rXA", "ar-rXB")) {
      val configuration = DeviceConfig.NEXUS_5.copy(locale = locale).folderConfiguration
      val expected = repository.getConfiguredResources(configuration)
        .pseudolocalizeIfNeeded(configuration.localeQualifier)
        .row(ResourceNamespace.ANDROID)
      val actual = cache.get(repository, configuration)
      assertThat(actual.getValue(ResourceType.STRING)["cancel"]!!.value)
        .isEqualTo(expected.getValue(ResourceType.STRING)["cancel"]!!.value)
      assertThat(actual).isNotSameInstanceAs(original)
    }
    assertThat(cache.get(repository, normal)).isSameInstanceAs(original)
    assertThat(original.getValue(ResourceType.STRING)["cancel"]!!.value).isEqualTo(originalCancel)
  }

  @Test
  fun evictsTheLeastRecentlyUsedConfigurationAtExactlyEightEntries() {
    val repository = repository()
    val cache = FrameworkResourceCache()
    val configurations = (100..108).map {
      DeviceConfig.NEXUS_5.copy(screenWidth = it).folderConfiguration
    }
    val entries = configurations.take(8).map { cache.get(repository, it) }
    for (index in entries.indices) {
      assertThat(cache.get(repository, configurations[index])).isSameInstanceAs(entries[index])
    }

    // Refresh the oldest entry, so the second entry must be evicted by the ninth insertion.
    assertThat(cache.get(repository, configurations[0])).isSameInstanceAs(entries[0])
    val ninth = cache.get(repository, configurations[8])
    assertThat(cache.get(repository, configurations[0])).isSameInstanceAs(entries[0])
    for (index in 2..7) {
      assertThat(cache.get(repository, configurations[index])).isSameInstanceAs(entries[index])
    }
    assertThat(cache.get(repository, configurations[8])).isSameInstanceAs(ninth)
    assertThat(cache.get(repository, configurations[1])).isNotSameInstanceAs(entries[1])
  }

  @Test
  fun invalidatesWhenTheRepositoryChanges() {
    val repository = repository()
    val cache = FrameworkResourceCache()
    val configuration = DeviceConfig.NEXUS_5.folderConfiguration
    val original = cache.get(repository, configuration)
    assertThat(cache.get(repository(), configuration)).isNotSameInstanceAs(original)
    assertThat(cache.get(repository, configuration)).isNotSameInstanceAs(original)
  }

  @Test
  fun builderCopiesShareFrameworkResourcesButKeepResolverAndThemeStateIndependent() {
    val logger = PaparazziLogger()
    val device = DeviceConfig.NEXUS_5.copy(locale = "en-rXA")
    val builder = SessionParamsBuilder(
      layoutlibCallback = PaparazziCallback(logger, "app.cash.paparazzi", emptyList()),
      logger = logger,
      frameworkResources = repository(),
      assetRepository = PaparazziAssetRepository(),
      projectResources = AppResourceRepository.create(emptyList(), emptyList(), emptyList()),
      deviceConfig = device
    ).withTheme("android:Theme.Material")
    val original = builder.build().resources
    val copied = builder.copy(deviceConfig = device.copy())
      .withTheme("android:Theme.Material.Light")
      .build().resources

    // Pseudolocalization creates new values on every cache miss, unlike ordinary repository values.
    val cancel = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STRING, "cancel")
    val originalCancel = original.getUnresolvedResource(cancel)
    assertThat(originalCancel).isNotNull()
    assertThat(copied.getUnresolvedResource(cancel)).isSameInstanceAs(originalCancel)
    assertThat(copied).isNotSameInstanceAs(original)
    assertThat(original.defaultTheme?.name).isEqualTo("Theme.Material")
    assertThat(copied.defaultTheme?.name).isEqualTo("Theme.Material.Light")

    original.applyStyle(copied.defaultTheme!!, true)
    assertThat(original.allThemes).containsExactly(copied.defaultTheme, original.defaultTheme)
    original.clearAllThemes()
    assertThat(original.allThemes).isEmpty()
    assertThat(copied.allThemes).containsExactly(copied.defaultTheme)
    assertThat(builder.build().resources.defaultTheme?.name).isEqualTo("Theme.Material")
  }

  private fun repository() =
    FrameworkResourceRepository.create(
      resourceDirectoryOrFile = Paths.get("src/test/resources/framework/framework_res.jar"),
      languagesToLoad = emptySet(),
      useCompiled9Patches = false
    )
}
