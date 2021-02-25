/*
 * Copyright (C) 2020 Square, Inc.
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

import android.content.ClipData
import android.graphics.Rect
import android.graphics.Region
import android.os.Bundle
import android.os.IBinder
import android.util.MergedConfiguration
import android.view.DisplayCutout.ParcelableWrapper
import android.view.IWindow
import android.view.IWindowId
import android.view.IWindowSession
import android.view.InputChannel
import android.view.InsetsState
import android.view.SurfaceControl
import android.view.WindowManager.LayoutParams

class PaparazziWindowSession : IWindowSession.Stub() {
  override fun addToDisplay(
    p0: IWindow?,
    p1: Int,
    p2: LayoutParams?,
    p3: Int,
    p4: Int,
    p5: Rect?,
    p6: Rect?,
    p7: Rect?,
    p8: Rect?,
    p9: ParcelableWrapper?,
    p10: InputChannel?,
    p11: InsetsState?
  ): Int {
    return 0
  }

  override fun relayout(
    p0: IWindow?,
    p1: Int,
    p2: LayoutParams?,
    p3: Int,
    p4: Int,
    p5: Int,
    p6: Int,
    p7: Long,
    p8: Rect?,
    p9: Rect?,
    p10: Rect?,
    p11: Rect?,
    p12: Rect?,
    p13: Rect?,
    p14: Rect?,
    p15: ParcelableWrapper?,
    p16: MergedConfiguration?,
    p17: SurfaceControl?,
    p18: InsetsState?
  ): Int {
    return 0
  }

  override fun addToDisplayWithoutInputChannel(
    p0: IWindow?,
    p1: Int,
    p2: LayoutParams?,
    p3: Int,
    p4: Int,
    p5: Rect?,
    p6: Rect?,
    p7: InsetsState?
  ): Int {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun remove(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun prepareToReplaceWindows(
    p0: IBinder?,
    p1: Boolean
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun outOfMemory(p0: IWindow?): Boolean {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun setTransparentRegion(
    p0: IWindow?,
    p1: Region?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun setInsets(
    p0: IWindow?,
    p1: Int,
    p2: Rect?,
    p3: Rect?,
    p4: Region?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun getDisplayFrame(
    p0: IWindow?,
    p1: Rect?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun finishDrawing(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun setInTouchMode(p0: Boolean) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun getInTouchMode(): Boolean {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun performHapticFeedback(
    p0: Int,
    p1: Boolean
  ): Boolean {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun performDrag(
    p0: IWindow?,
    p1: Int,
    p2: SurfaceControl?,
    p3: Int,
    p4: Float,
    p5: Float,
    p6: Float,
    p7: Float,
    p8: ClipData?
  ): IBinder {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun reportDropResult(
    p0: IWindow?,
    p1: Boolean
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun cancelDragAndDrop(
    p0: IBinder?,
    p1: Boolean
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun dragRecipientEntered(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun dragRecipientExited(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun setWallpaperPosition(
    p0: IBinder?,
    p1: Float,
    p2: Float,
    p3: Float,
    p4: Float
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun wallpaperOffsetsComplete(p0: IBinder?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun setWallpaperDisplayOffset(
    p0: IBinder?,
    p1: Int,
    p2: Int
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun sendWallpaperCommand(
    p0: IBinder?,
    p1: String?,
    p2: Int,
    p3: Int,
    p4: Int,
    p5: Bundle?,
    p6: Boolean
  ): Bundle {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun wallpaperCommandComplete(
    p0: IBinder?,
    p1: Bundle?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun onRectangleOnScreenRequested(
    p0: IBinder?,
    p1: Rect?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun getWindowId(p0: IBinder?): IWindowId {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun pokeDrawLock(p0: IBinder?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun startMovingTask(
    p0: IWindow?,
    p1: Float,
    p2: Float
  ): Boolean {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun finishMovingTask(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun updatePointerIcon(p0: IWindow?) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun reparentDisplayContent(
    p0: IWindow?,
    p1: SurfaceControl?,
    p2: Int
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun updateDisplayContentLocation(
    p0: IWindow?,
    p1: Int,
    p2: Int,
    p3: Int
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun updateTapExcludeRegion(
    p0: IWindow?,
    p1: Int,
    p2: Region?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun insetsModified(
    p0: IWindow?,
    p1: InsetsState?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }

  override fun reportSystemGestureExclusionChanged(
    p0: IWindow?,
    p1: MutableList<Rect>?
  ) {
    throw UnsupportedOperationException("Minimum IWindowSession methods are supported.")
  }
}