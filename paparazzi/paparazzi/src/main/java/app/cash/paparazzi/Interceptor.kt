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

/**
 * Participate in Paparazzi's process of transforming a view into an image.
 *
 * Implementations may operate on the input view, the returned bitmap, or both.
 */
interface Interceptor {
  fun intercept(chain: Chain): BufferedImage

  interface Chain {
    val deviceConfig: DeviceConfig
    val view: View
    val snapshot: Snapshot
    val frameIndex: Int
    val frameCount: Int
    val fps: Int
    fun proceed(view: View): BufferedImage
  }
}
