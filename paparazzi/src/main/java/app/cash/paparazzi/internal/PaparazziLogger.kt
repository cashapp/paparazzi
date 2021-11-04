/*
 * Copyright (C) 2019 Square, Inc.
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

import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.utils.ILogger
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.Logger.getLogger

/**
 * This logger delegates to java.util.Logging.
 *
 * TODO: gather logged messages and fail successful tests that have unexpected errors.
 */
internal class PaparazziLogger : ILayoutLog, ILogger {
  private val logger: Logger = getLogger(Paparazzi::class.java.name)

  override fun error(
    throwable: Throwable?,
    format: String?,
    vararg args: Any
  ) {
    logger.log(Level.SEVERE, format?.format(args), throwable)
  }

  override fun warning(
    format: String,
    vararg args: Any
  ) {
    logger.log(Level.WARNING, format, args)
  }

  override fun info(
    format: String,
    vararg args: Any
  ) {
    logger.log(Level.INFO, format, args)
  }

  override fun verbose(
    format: String,
    vararg args: Any
  ) {
    logger.log(Level.FINE, format, args)
  }

  override fun fidelityWarning(
    tag: String?,
    message: String?,
    throwable: Throwable?,
    cookie: Any?,
    data: Any?
  ) {
    logger.log(Level.WARNING, "$tag: $message", throwable)
  }

  override fun error(
    tag: String?,
    message: String?,
    viewCookie: Any?,
    data: Any?
  ) {
    logger.log(Level.SEVERE, "$tag: $message")
  }

  override fun error(
    tag: String?,
    message: String?,
    throwable: Throwable?,
    viewCookie: Any?,
    data: Any?
  ) {
    logger.log(Level.SEVERE, "$tag: $message", throwable)
  }

  override fun warning(
    tag: String?,
    message: String?,
    viewCookie: Any?,
    data: Any?
  ) {
    logger.log(Level.WARNING, "$tag: $message")
  }

  override fun logAndroidFramework(priority: Int, tag: String?, message: String?) {
    logger.log(Level.INFO, "$tag [$priority]: $message")
  }
}