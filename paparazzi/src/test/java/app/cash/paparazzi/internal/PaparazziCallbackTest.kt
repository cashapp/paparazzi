package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.resourcefixture.R
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType.STRING
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PaparazziCallbackTest {
  @Test
  fun absentDependencyDoesNotPreventLoadingOtherResourceIds() {
    val logger = PaparazziLogger()
    val callback = PaparazziCallback(
      logger,
      "app.cash.paparazzi",
      listOf("removed.before", "app.cash.paparazzi.internal.resourcefixture", "removed.after", "app.cash.paparazzi")
    )

    callback.initResources()

    val reference = ResourceReference(RES_AUTO, STRING, "greeting")
    assertThat(callback.resolveResourceId(R.string.greeting)).isEqualTo(reference)
    assertThat(callback.getOrGenerateResourceId(reference)).isEqualTo(R.string.greeting)
    logger.assertNoErrors()
  }

  @Test
  fun absentApplicationResourceClassStillFails() {
    val callback = PaparazziCallback(PaparazziLogger(), "missing.application", listOf("missing.application"))

    val failure = assertThrows(ClassNotFoundException::class.java) {
      callback.initResources()
    }

    assertThat(failure).hasMessageThat().isEqualTo("missing.application.R")
  }

  @Test
  fun brokenDependencyResourceClassStillFails() {
    val callback = PaparazziCallback(
      PaparazziLogger(),
      "app.cash.paparazzi",
      listOf("app.cash.paparazzi.internal.brokenresource")
    )

    val failure = assertThrows(ExceptionInInitializerError::class.java) {
      callback.initResources()
    }

    assertThat(failure.cause).hasMessageThat().isEqualTo("Broken R class initializer")
  }
}
