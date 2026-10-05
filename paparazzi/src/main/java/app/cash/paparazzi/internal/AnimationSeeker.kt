/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.ViewRootForTest
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Seeks Compose animations to an exact play time, the way Android Studio's Animation Preview does.
 *
 * Content is composed under [Content], which records the slot tables so the animations can be found.
 * [seek] then hands the tables to ui-tooling's `AnimationSearch`, which subscribes every supported
 * animation (Transition, AnimatedVisibility, AnimatedContent, InfiniteTransition, animate*AsState,
 * animateContentSize) to a `PreviewAnimationClock`, and sets that clock's time.
 *
 * Seeking sets each animation's play time directly, so the result does not depend on the frame
 * an animation started in. Animations the search does not support (Animatable, LaunchedEffect
 * loops) are not seeked and stay at the frame clock's time.
 *
 * `AnimationSearch` and `PreviewAnimationClock` are Kotlin-internal to `androidx.compose.ui:ui-tooling`,
 * so they are reached by reflection; they are not a stable API and may change between Compose releases.
 *
 * Mirrors how ui-tooling's ComposeViewAdapter sets up the clock for Android Studio:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/ComposeViewAdapter.android.kt
 */
internal class AnimationSeeker {
  private val store: MutableSet<CompositionData> =
    Collections.newSetFromMap(WeakHashMap())

  private var clock: Any? = null

  @Composable
  fun Content(content: @Composable () -> Unit) {
    // Same contract as ui-tooling's Inspectable: keep parameter info and collect every
    // composition's (including subcompositions') slot table.
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/Inspectable.android.kt
    currentComposer.collectParameterInformation()
    store.add(currentComposer.compositionData)
    CompositionLocalProvider(
      LocalInspectionMode provides true,
      LocalInspectionTables provides store,
      content = content
    )
  }

  /** Seeks every supported animation found under [root] to [timeMillis]. */
  fun seek(root: View, timeMillis: Long) {
    val clock = clock ?: attach(root).also { clock = it }
    Tooling.setClockTime.invoke(clock, timeMillis)
  }

  fun dispose() {
    clock?.let { Tooling.dispose.invoke(it) }
    clock = null
    store.clear()
  }

  // Equivalent of ComposeViewAdapter's clock setup and findAndTrackAnimations:
  // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/ComposeViewAdapter.android.kt
  private fun attach(root: View): Any {
    val requestLayout = { root.requestLayout() }
    val applySnapshot = {
      root.forEachViewRootForTest { it.invalidateDescendants() }
      Snapshot.sendApplyNotifications()
    }
    val clock = Tooling.newClock(requestLayout, applySnapshot)
    val search = Tooling.newSearch({ clock }, applySnapshot)
    val groups = store.map { Tooling.asTree.invoke(null, it) }
    if (Tooling.searchAny.invoke(search, groups) as Boolean) {
      Tooling.attachAllAnimations.invoke(search, groups)
    }
    return clock
  }

  private fun View.forEachViewRootForTest(block: (ViewRootForTest) -> Unit) {
    if (this is ViewRootForTest) block(this)
    if (this is ViewGroup) {
      for (i in 0 until childCount) getChildAt(i).forEachViewRootForTest(block)
    }
  }

  /**
   * Reflective handles to ui-tooling's internal animation classes:
   * - https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/animation/PreviewAnimationClock.android.kt
   * - https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/animation/AnimationSearch.android.kt
   * - https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling-data/src/jvmAndAndroidMain/kotlin/androidx/compose/ui/tooling/data/SlotTree.jvmAndAndroid.kt
   */
  private object Tooling {
    private val clockClass = load("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
    private val searchClass = load("androidx.compose.ui.tooling.animation.AnimationSearch")

    // The callbacks differ between ui-tooling versions. 1.8 takes a single time-changed callback for
    // the clock and an onSeek callback for the search; 1.12 takes requestLayout and applySnapshot
    // for the clock and only the clock provider for the search.
    private val clockConstructor = clockClass.function0Constructor()
    private val searchConstructor = searchClass.function0Constructor()

    fun newClock(requestLayout: () -> Unit, applySnapshot: () -> Unit): Any =
      if (clockConstructor.parameterCount == 2) {
        clockConstructor.newInstance(requestLayout, applySnapshot)
      } else {
        clockConstructor.newInstance(applySnapshot)
      }

    fun newSearch(clock: () -> Any, onSeek: () -> Unit): Any =
      if (searchConstructor.parameterCount == 2) {
        searchConstructor.newInstance(clock, onSeek)
      } else {
        searchConstructor.newInstance(clock)
      }

    val setClockTime: Method = clockClass.getMethod("setClockTime", Long::class.javaPrimitiveType)
    val dispose: Method = clockClass.getMethod("dispose")

    val searchAny: Method = searchClass.getMethod("searchAny", Collection::class.java)
    val attachAllAnimations: Method = searchClass.getMethod("attachAllAnimations", Collection::class.java)

    val asTree: Method = load("androidx.compose.ui.tooling.data.SlotTreeKt")
      .getMethod("asTree", CompositionData::class.java)

    private fun Class<*>.function0Constructor() =
      constructors
        .filter { c -> c.parameterCount > 0 && c.parameterTypes.all { it == Function0::class.java } }
        .maxBy { it.parameterCount }

    private fun load(name: String): Class<*> =
      try {
        Class.forName(name)
      } catch (e: ClassNotFoundException) {
        throw IllegalStateException(
          "Seeking animations requires androidx.compose.ui:ui-tooling and " +
            "androidx.compose.animation:animation-tooling-internal on the test classpath, " +
            "at the same version as the rest of Compose.",
          e
        )
      }
  }
}
