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
package app.cash.paparazzi.internal.interceptors;

import net.bytebuddy.asm.Advice;

/**
 * Workaround for layoutlib 16.2.1 bug where {@code WindowManagerImpl.removeView} does not
 * null-check the result of {@code ViewGroup.buildOrderedChildList()} before calling
 * {@code size()} on it.
 *
 * <p>This occurs when a Compose {@code Dialog} is torn down during lifecycle destruction:
 * after the Dialog's view is removed from the window, {@code buildOrderedChildList()} returns
 * null (because there are 0 children), and the subsequent {@code size()} call throws a
 * {@link NullPointerException}. By that point the view has already been removed; only the
 * {@code ViewRootImpl} child reassignment fails, which is safe to ignore.
 *
 * <p>This advice is applied via {@link Advice} as a visitor wrapper, which preserves the
 * original method body. A {@link MethodDelegation}-based interceptor cannot be used here
 * because {@code removeView} implements an interface method with no super implementation
 * to delegate to via {@code @SuperCall}.
 *
 * <p>https://github.com/cashapp/paparazzi/issues/2373
 */
public class WindowManagerImplRemoveViewAdvice {
  private WindowManagerImplRemoveViewAdvice() {
    throw new AssertionError();
  }

  /**
   * Invoked when {@code removeView} exits with a {@link NullPointerException}. Nulling the
   * thrown value (via the {@code readOnly = false} write-back) suppresses the exception so
   * teardown completes cleanly.
   */
  @Advice.OnMethodExit(onThrowable = NullPointerException.class)
  public static void suppressNullPointerException(@Advice.Thrown(readOnly = false) Throwable throwable) {
    throwable = null;
  }
}
