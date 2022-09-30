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

import android.util.Size
import app.cash.paparazzi.internal.ImageUtils
import java.awt.image.BufferedImage

class ResizeInterceptor private constructor(
  val size: (Size) -> Size,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): BufferedImage {
    val rendered = chain.proceed(chain.view)
    val sourceSize = Size(rendered.width, rendered.height)
    val targetSize = size(sourceSize)
    return ImageUtils.scale(rendered, targetSize.width, targetSize.height)
  }

  companion object {
    /** Scales images up or down until the largest dimension is [size]. */
    fun maxAnyDimension(size: Int): ResizeInterceptor {
      return ResizeInterceptor { sourceSize ->
        val maxDimension = maxOf(sourceSize.width, sourceSize.height)
        val scale = size.toDouble() / maxDimension
        Size(
          maxOf(1, (scale * sourceSize.width).toInt()),
          maxOf(1, (scale * sourceSize.height).toInt()),
        )
      }
    }

    fun fixedSize(width: Int, height: Int): ResizeInterceptor {
      require(width >= 1 && height >= 1)
      return ResizeInterceptor { Size(width, height) }
    }

    fun fixedWidth(width: Int): ResizeInterceptor {
      require(width >= 1)
      return ResizeInterceptor { sourceSize ->
        val scale = width.toDouble() / sourceSize.width
        Size(width, maxOf(1, (scale * sourceSize.height).toInt()))
      }
    }

    fun fixedHeight(height: Int): ResizeInterceptor {
      require(height >= 1)
      return ResizeInterceptor { sourceSize ->
        val scale = height.toDouble() / sourceSize.height
        Size(maxOf(1, (scale * sourceSize.width).toInt()), height)
      }
    }
  }
}
