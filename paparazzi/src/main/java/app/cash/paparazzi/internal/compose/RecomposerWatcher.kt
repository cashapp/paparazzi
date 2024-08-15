@file:OptIn(ExperimentalComposeUiApi::class)

package app.cash.paparazzi.internal.compose

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import app.cash.paparazzi.PaparazziSdk.Companion.isPresentInClasspath
import app.cash.paparazzi.internal.PaparazziLogger
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN
import kotlin.coroutines.CoroutineContext

@InternalComposeUiApi
internal class RecomposerWatcher(
  private val mainDispatcher: CoroutineContext,
  private val paparazziLogger: PaparazziLogger
) {

  private var recomposer: Any? = null
  private var frameLimit: Int = 1 // TODO: Configure with Test rule
  private var additionalRenderLimit: Int = 1 // TODO: Configure with TestRule

  fun register() {
    // By default, Compose UI uses its own implementation of CoroutineDispatcher, `AndroidUiDispatcher`.
    // Since this dispatcher does not provide its own implementation of Delay, it will default to using DefaultDelay which runs
    // async to our test Handler. By initializing Recomposer with Dispatchers.Main, Delay will now be backed by our test Handler,
    // synchronizing expected behavior.
    if (hasComposeRuntime) {
      WindowRecomposerPolicy.setFactory {
        val windowRecomposer = it.createLifecycleAwareWindowRecomposer(mainDispatcher)
        recomposer = windowRecomposer
        return@setFactory windowRecomposer
      }
    }
  }

  private fun renderForIdleRecomposer(
    frame: Int,
    additionalRenderCount: Int,
    result: Result,
    onAdditionalRender: () -> Result
  ): Result {
    if (hasComposeRuntime && result.status != Result.Status.ERROR_UNKNOWN) {
      // If there is a recomposition that needs to happen, we need to trigger it within the context of the first frame.
      val recomposerInstance = recomposer as? Recomposer
      val hasPendingWork = recomposerInstance?.hasPendingWork
      if (frame < frameLimit && hasPendingWork == true) {
        if (additionalRenderCount >= additionalRenderLimit) {
          paparazziLogger.info(
            "Additional render count exceeded for frame $frame. Recomposer may have an infinite looping animation or state change. Bailing out..."
          )
          return result
        }
        val newResult = onAdditionalRender()
        if (newResult.status == ERROR_UNKNOWN) {
          throw newResult.exception
        }

        return renderForIdleRecomposer(frame, additionalRenderCount + 1, newResult, onAdditionalRender)
      }

      if (hasPendingWork == true) {
        paparazziLogger.warning(
          "Pending work detected for frame $frame. This may cause unexpected results in your generated snapshots. ${recomposerInstance.changeCount}"
        )
      }
    }

    return result
  }

  fun onRenderResult(frame: Int, result: Result, onAdditionalRender: () -> Result): Result =
    renderForIdleRecomposer(frame, 0, result, onAdditionalRender)

  fun unregister() {
    recomposer = null
  }

  private val hasComposeRuntime: Boolean = isPresentInClasspath(
    "androidx.compose.runtime.snapshots.SnapshotKt",
    "androidx.compose.ui.platform.AndroidUiDispatcher"
  )
}
