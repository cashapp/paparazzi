/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.ViewInfo
import java.awt.image.BufferedImage

internal data class RenderResult(
  val result: Result,
  val systemViews: List<ViewInfo>,
  val rootViews: List<ViewInfo>,
  val image: BufferedImage
)

internal fun RenderSession.toResult(): RenderResult {
  return RenderResult(result, systemRootViews.toList(), rootViews.toList(), copyImage())
}

/**
 * Copies the logical render bounds before returning Layoutlib's backing buffer to its reuse pool.
 *
 * Layoutlib transfers ownership of a recyclable image to the caller, which must close it after
 * displaying or copying its pixels. The compatible raster below mirrors Layoutlib's standalone
 * copy implementation so pooled allocation padding does not become part of the snapshot.
 *
 * @see <a href="https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-main/layoutlib-api/src/main/java/com/android/ide/common/rendering/api/RecyclableImage.java">RecyclableImage</a>
 * @see <a href="https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-main/layoutlib-api/src/main/java/com/android/ide/common/rendering/api/StandaloneRecyclableImage.java">StandaloneRecyclableImage</a>
 */
internal fun RenderSession.copyImage(): BufferedImage {
  val recyclableImage = getRecyclableImage() ?: return image
  return recyclableImage.use {
    val image = it.getImage()
    val raster = image.raster.createCompatibleWritableRaster()
    BufferedImage(image.colorModel, image.copyData(raster), image.isAlphaPremultiplied, null)
  }
}
