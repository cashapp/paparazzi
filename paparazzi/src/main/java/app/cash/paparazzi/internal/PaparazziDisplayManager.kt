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

import android.content.pm.ParceledListSlice
import android.graphics.Point
import android.hardware.display.BrightnessConfiguration
import android.hardware.display.Curve
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.hardware.display.IVirtualDisplayCallback
import android.hardware.display.WifiDisplayStatus
import android.media.projection.IMediaProjection
import android.view.DisplayInfo
import android.view.Surface

class PaparazziDisplayManager : IDisplayManager.Stub() {
  override fun getPreferredWideGamutColorSpaceId(): Int {
    return 0
  }

  override fun registerCallback(p0: IDisplayManagerCallback?) {
  }

  override fun getDisplayInfo(p0: Int): DisplayInfo {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getDisplayIds(): IntArray {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun isUidPresentOnDisplay(
    p0: Int,
    p1: Int
  ): Boolean {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun startWifiDisplayScan() {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun stopWifiDisplayScan() {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun connectWifiDisplay(p0: String?) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun disconnectWifiDisplay() {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun renameWifiDisplay(
    p0: String?,
    p1: String?
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun forgetWifiDisplay(p0: String?) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun pauseWifiDisplay() {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun resumeWifiDisplay() {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getWifiDisplayStatus(): WifiDisplayStatus {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun requestColorMode(
    p0: Int,
    p1: Int
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun createVirtualDisplay(
    p0: IVirtualDisplayCallback?,
    p1: IMediaProjection?,
    p2: String?,
    p3: String?,
    p4: Int,
    p5: Int,
    p6: Int,
    p7: Surface?,
    p8: Int,
    p9: String?
  ): Int {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun resizeVirtualDisplay(
    p0: IVirtualDisplayCallback?,
    p1: Int,
    p2: Int,
    p3: Int
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun setVirtualDisplaySurface(
    p0: IVirtualDisplayCallback?,
    p1: Surface?
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun releaseVirtualDisplay(p0: IVirtualDisplayCallback?) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun setVirtualDisplayState(
    p0: IVirtualDisplayCallback?,
    p1: Boolean
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getStableDisplaySize(): Point {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getBrightnessEvents(p0: String?): ParceledListSlice<*> {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getAmbientBrightnessStats(): ParceledListSlice<*> {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun setBrightnessConfigurationForUser(
    p0: BrightnessConfiguration?,
    p1: Int,
    p2: String?
  ) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getBrightnessConfigurationForUser(p0: Int): BrightnessConfiguration {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getDefaultBrightnessConfiguration(): BrightnessConfiguration {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun setTemporaryBrightness(p0: Int) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun setTemporaryAutoBrightnessAdjustment(p0: Float) {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

  override fun getMinimumBrightnessCurve(): Curve {
    throw UnsupportedOperationException("Minimum IDisplayManager methods are supported.")
  }

}
