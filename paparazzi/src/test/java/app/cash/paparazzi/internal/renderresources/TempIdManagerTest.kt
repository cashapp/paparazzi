package app.cash.paparazzi.internal.renderresources

import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TempIdManagerTest(private val useRBytecodeParsing: Boolean) {
  @Test
  fun testDynamicIds() {
    val idManager = StubbedResourceIdManager(useRBytecodeParsing)
    val initialGeneration = idManager.generation

    val stringResourceReference = ResourceReference(RES_AUTO, ResourceType.STRING, "string")
    val styleResourceReference = ResourceReference(RES_AUTO, ResourceType.STYLE, "style")
    val layoutResourceReference = ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")

    val stringId = idManager.getOrGenerateId(stringResourceReference)
    val styleId = idManager.getOrGenerateId(styleResourceReference)
    val layoutId = idManager.getOrGenerateId(layoutResourceReference)

    assertThat(stringId).isEqualTo(idManager.getOrGenerateId(stringResourceReference))
    assertThat(stringResourceReference).isEqualTo(idManager.findById(stringId))

    assertThat(styleId).isEqualTo(idManager.getOrGenerateId(styleResourceReference))
    assertThat(styleResourceReference).isEqualTo(idManager.findById(styleId))

    assertThat(layoutId).isEqualTo(idManager.getOrGenerateId(layoutResourceReference))
    assertThat(layoutResourceReference).isEqualTo(idManager.findById(layoutId))

    assertWithMessage("Generation must be constant if no calls to resetDynamicIds happened")
      .that(idManager.generation)
      .isEqualTo(initialGeneration)
  }

  @Test
  fun testResetDynamicIds() {
    val idManager = StubbedResourceIdManager(useRBytecodeParsing)
    var lastGeneration = idManager.generation
    idManager.resetDynamicIds()

    assertThat(idManager.generation).isNotEqualTo(lastGeneration)
    lastGeneration = idManager.generation

    val stringResource1 = ResourceReference(RES_AUTO, ResourceType.STRING, "string1")
    val stringResource2 = ResourceReference(RES_AUTO, ResourceType.STRING, "string2")
    val stringResource3 = ResourceReference(RES_AUTO, ResourceType.STRING, "string3")

    val id1 = idManager.getOrGenerateId(stringResource1)
    val id2 = idManager.getOrGenerateId(stringResource2)
    val id3 = idManager.getOrGenerateId(stringResource3)
    assertThat(id2).isNotEqualTo(id1)
    assertThat(id3).isNotEqualTo(id1)
    assertThat(id3).isNotEqualTo(id2)

    idManager.resetDynamicIds()
    assertThat(idManager.generation).isNotEqualTo(lastGeneration)

    // They should be all gone now.
    assertThat(idManager.findById(id1)).isNull()
    assertThat(idManager.findById(id2)).isNull()
    assertThat(idManager.findById(id3)).isNull()

    // Check in different order. These should be new IDs.
    assertThat(idManager.getOrGenerateId(stringResource3)).isNotEqualTo(id3)
    assertThat(idManager.getOrGenerateId(stringResource1)).isNotEqualTo(id1)
    assertThat(idManager.getOrGenerateId(stringResource2)).isNotEqualTo(id2)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useRBytecodeParsing={0}")
    fun userRBytecodeParsing() = listOf(false, true)
  }
}

/**
 * [ResourceIdManager] implementation with fixed final ids. Convenient for testing.
 *
 * @param useRBytecodeParsing When true, the R classes belonging to this Module will be loaded using
 *   bytecode parsing and not reflection.
 * @param frameworkResourceIdsProvider [FrameworkResourceIdsProvider] used to obtain the framework R class ids.
 */
internal open class StubbedResourceIdManager internal constructor(
  useRBytecodeParsing: Boolean,
  frameworkResourceIdsProvider: FrameworkResourceIdsProvider = FrameworkResourceIdsProvider.getInstance()
) : TempIdManager(ResourceIdManagerModelModule.noNamespacingApp(useRBytecodeParsing), false, frameworkResourceIdsProvider)
