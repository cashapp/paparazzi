package app.cash.paparazzi.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import java.util.Collections
import java.util.WeakHashMap

/**
 * Ported from: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/ComposeViewAdapter.kt?q=ComposeViewAdapter
 *
 * A wrapper layout for compose-based layouts which allows [android.view.WindowManagerImpl] to find
 * a composable root
 */
internal class ComposeViewAdapter(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  private var slotTableRecord = CompositionDataRecord.create()

  init {
    /**
     * Needed as [android.view.WindowManagerImpl] uses the view root background color to set the WindowManagerImpl view's background color.
     * If we set this as transparent, the WindowManagerImpl view will be transparent as well and correctly renders the window above content.
     */
    setBackgroundColor(Color.TRANSPARENT)
  }

  @OptIn(UiToolingDataApi::class)
  override fun onViewAdded(child: View?) {
    println("TEST - childCount $childCount $child")
    if (child is AbstractComposeView) {
      if (child is ComposeView) {
        child.setContent {
          Inspectable(
            compositionDataRecord = slotTableRecord,
            content = { child.Content() },
          )
        }
      }
    }

    super.onViewAdded(child)
  }

  @OptIn(UiToolingDataApi::class)
  override fun onLayout(
    changed: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
  ) {

    val slotTrees = slotTableRecord.store.map { it.asTree() }
    val animationSearchClass = Class.forName("androidx.compose.ui.tooling.animation.AnimationSearch")
    val constructor = animationSearchClass.declaredConstructors.first()
    constructor.isAccessible = true

    val animationSearch = constructor.newInstance({
      Class.forName("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
        .declaredConstructors.first()
        .also { it.isAccessible = true }
        .newInstance({
          // Invalidate the descendants of this ComposeViewAdapter's only
          // grandchild
          // (an AndroidOwner) when setting the clock time to make sure the
          // Compose
          // Preview will animate when the states are read inside the draw scope.
          val child = getChildAt(0) as ComposeView
          (child.getChildAt(0) as? ViewRootForTest)?.invalidateDescendants()
          // Send pending apply notifications to ensure the animation duration
          // will
          // be read in the correct frame.
          Snapshot.sendApplyNotifications()
        })
    }, { requestLayout() })

    val hasAnimations = animationSearchClass.declaredMethods.first { it.name == "searchAny" }
      .also { it.isAccessible = true }
      .invoke(animationSearch, slotTrees)

    println("Has animations: $hasAnimations")

    super.onLayout(changed, left, top, right, bottom)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    slotTableRecord = CompositionDataRecord.create()
  }
}

/** Storage for the preview generated [CompositionData]s. */
internal interface CompositionDataRecord {
  val store: Set<CompositionData>

  companion object {
    fun create(): CompositionDataRecord = CompositionDataRecordImpl()
  }
}

private class CompositionDataRecordImpl : CompositionDataRecord {
  override val store: MutableSet<CompositionData> = Collections.newSetFromMap(WeakHashMap())
}

@Composable
internal fun Inspectable(
  compositionDataRecord: CompositionDataRecord,
  content: @Composable () -> Unit,
) {
  currentComposer.collectParameterInformation()
  val store = (compositionDataRecord as CompositionDataRecordImpl).store
  store.add(currentComposer.compositionData)
  CompositionLocalProvider(
    LocalInspectionMode provides true,
    LocalInspectionTables provides store,
    content = content,
  )
}

