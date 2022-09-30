/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.paparazzi

import android.view.View
import java.awt.image.BufferedImage

internal data class RealChain(
  val nextIndex: Int,
  val interceptors: List<Interceptor>,
  override val deviceConfig: DeviceConfig,
  override val view: View,
  override val snapshot: Snapshot,
  override val frameIndex: Int,
  override val frameCount: Int,
  override val fps: Int
) : Interceptor.Chain {
  override fun proceed(view: View): BufferedImage {
    val nextChain = copy(nextIndex = nextIndex + 1, view = view)
    return interceptors[nextIndex].intercept(nextChain)
  }
}
