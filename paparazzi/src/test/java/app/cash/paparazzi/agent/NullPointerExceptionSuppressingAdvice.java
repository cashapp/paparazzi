/*
 * Copyright (C) 2025 Square, Inc.
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
package app.cash.paparazzi.agent;

import net.bytebuddy.asm.Advice;

/**
 * Test advice that suppresses a {@link NullPointerException} thrown by the instrumented
 * method, mirroring the {@code WindowManagerImpl.removeView} workaround.
 */
public final class NullPointerExceptionSuppressingAdvice {
  private NullPointerExceptionSuppressingAdvice() {
    throw new AssertionError();
  }

  @Advice.OnMethodExit(onThrowable = NullPointerException.class)
  public static void suppressNullPointerException(@Advice.Thrown(readOnly = false) Throwable throwable) {
    throwable = null;
  }
}
