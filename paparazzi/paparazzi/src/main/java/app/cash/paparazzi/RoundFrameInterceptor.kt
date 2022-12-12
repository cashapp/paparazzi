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

import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

internal object RoundFrameInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): BufferedImage {
    val image = chain.proceed(chain.view)
    val result = BufferedImage(image.width, image.height, image.type)
    val g = result.createGraphics()
    try {
      g.clip = Ellipse2D.Float(0f, 0f, image.height.toFloat(), image.width.toFloat())
      g.drawImage(image, 0, 0, image.width, image.height, null)
      return result
    } finally {
      g.dispose()
    }
  }
}
