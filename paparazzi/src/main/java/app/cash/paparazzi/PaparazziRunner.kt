/*
 * Copyright (C) 2024 Square, Inc.
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

package app.cash.paparazzi

import app.cash.paparazzi.internal.sandbox.Sandbox
import app.cash.paparazzi.internal.sandbox.SandboxConfiguration
import app.cash.paparazzi.internal.sandbox.SandboxManager
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.manipulation.Sortable
import org.junit.runner.manipulation.Sorter
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.function.Supplier

/**
 * A JUnit 4 runner that executes a test class inside an isolated layoutlib sandbox.
 *
 * ```kotlin
 * @RunWith(PaparazziRunner::class)
 * class MyTest {
 *   @get:Rule val paparazzi = Paparazzi()
 *
 *   @Test fun launch() { paparazzi.snapshot { ... } }
 * }
 * ```
 *
 * ### Why this has to be a runner
 *
 * [Paparazzi] is a `TestRule`, and a rule is powerless here: by the time it runs, the test class and
 * every lambda in it have already been defined by the application class loader. A `@Composable`
 * lambda captured in a test body holds a reference to *that* loader's `androidx.compose` classes,
 * so handing it to a sandbox with its own copy fails with `ClassCastException`. Only a runner sits
 * early enough to reload the test class itself.
 *
 * ### How it works
 *
 * This is a *delegating* runner. It reloads the test class in the sandbox, then builds an ordinary
 * JUnit runner around that reloaded class and forwards to it.
 *
 * The alternative — subclassing [BlockJUnit4ClassRunner] and rewriting `methodBlock` to target the
 * sandboxed class, as Robolectric does — means reimplementing JUnit's statement assembly, and
 * everything not reimplemented silently keeps operating on the *host* class. `@BeforeClass` and
 * `@ClassRule` in particular are assembled by `ParentRunner.classBlock`, so they would run outside
 * the sandbox. Delegating instead means JUnit assembles everything itself, from the sandboxed
 * class, and `@Ignore`, `@Test(timeout)`, `@Test(expected)`, `@BeforeClass`, `@AfterClass`,
 * `@ClassRule`, `@Rule` and assumptions all behave exactly as they normally would.
 *
 * Robolectric needs the subclass approach because it may pick a different sandbox per test method.
 * Paparazzi keeps one sandbox per configuration, so the whole runner can be hoisted inside.
 *
 * ### Other runners
 *
 * Because the delegate is chosen by name and built inside the sandbox, runners such as
 * `TestParameterInjector` or `Parameterized` compose with this one — see [SandboxedRunWith].
 */
public class PaparazziRunner(testClass: Class<*>) : Runner(), Filterable, Sortable {
  private val sandbox: Sandbox = sandbox()

  private val delegate: Runner = sandbox.runOnSandbox {
    val bootstrappedClass = sandbox.bootstrappedClass(testClass)
    val runnerClass = sandbox.loadClass(delegateRunnerName(testClass))

    val constructor = try {
      runnerClass.getConstructor(Class::class.java)
    } catch (e: NoSuchMethodException) {
      throw IllegalStateException(
        "${runnerClass.name} cannot be used with @SandboxedRunWith: a delegate runner needs a " +
          "public constructor taking a single Class argument.",
        e
      )
    }

    try {
      constructor.newInstance(bootstrappedClass) as Runner
    } catch (e: InvocationTargetException) {
      // Unwrap, or the real reason - usually a JUnit InitializationError listing validation
      // failures - is buried behind a reflection wrapper.
      throw IllegalStateException(
        "${runnerClass.name} failed to initialise for ${testClass.name} inside the sandbox: " +
          "${e.targetException}",
        e.targetException
      )
    }
  }

  override fun getDescription(): Description = delegate.description

  override fun testCount(): Int = delegate.testCount()

  /**
   * Runs the delegate with the sandbox's loader installed as the thread context class loader.
   *
   * The whole run is wrapped rather than each test, so `@BeforeClass`, `@ClassRule` and the test
   * bodies all see the same loader. Threads started underneath inherit it, which matters for
   * `@Test(timeout)`: JUnit's `FailOnTimeout` moves the test body to a new thread.
   */
  override fun run(notifier: RunNotifier) {
    sandbox.runOnSandbox { delegate.run(notifier) }
  }

  /**
   * Forwards Gradle's `--tests` filtering to the delegate.
   *
   * Without this every filtered run would report "no tests found", because the host cannot see the
   * sandboxed runner's children.
   */
  @Throws(NoTestsRemainException::class)
  override fun filter(filter: Filter) {
    val filterable = delegate as? Filterable ?: throw NoTestsRemainException()
    sandbox.runOnSandbox { filterable.filter(filter) }
  }

  override fun sort(sorter: Sorter) {
    (delegate as? Sortable)?.let { sortable -> sandbox.runOnSandbox { sortable.sort(sorter) } }
  }

  private fun delegateRunnerName(testClass: Class<*>): String {
    val annotation = testClass.getAnnotation(SandboxedRunWith::class.java)
      ?: return BlockJUnit4ClassRunner::class.java.name
    // Read the name only. The KClass itself belongs to the host loader; the sandbox must resolve
    // its own copy so the runner's reflection stays on one side of the boundary.
    return annotation.value.java.name
  }

  private fun sandbox(): Sandbox {
    val sandbox = sandboxManager.getSandbox(
      configuration = SandboxConfiguration.forRunner(),
      layoutlibRuntimeRoot = layoutlibRuntimeRoot()
    )
    installNativeLibDir(sandbox)
    return sandbox
  }

  /**
   * Tells the sandbox's `Renderer` where its private JNI libraries live.
   *
   * Everything else `Renderer` needs arrives via system properties, but those are process-global
   * and this value is per-sandbox by construction, so it is injected as a static instead.
   */
  private fun installNativeLibDir(sandbox: Sandbox) {
    // A supplier, so the ~26 MB copy happens only if the test actually renders.
    val runtime = sandbox.loadClass("app.cash.paparazzi.internal.SandboxRuntime")
    runtime.getDeclaredMethod("setNativeLibDirSupplier", Supplier::class.java)
      .invoke(null, Supplier { sandbox.nativeLibDir?.absolutePath })
    // Windows renames the DLLs to make them unique, so layoutlib's hardcoded list needs remapping.
    runtime.getDeclaredMethod("setNativeLibraryRenamesSupplier", Supplier::class.java)
      .invoke(null, Supplier { sandbox.nativeLibraryRenames })
  }

  private fun layoutlibRuntimeRoot(): File? = System.getProperty("paparazzi.layoutlib.runtime.root")?.let(::File)

  private companion object {
    /**
     * One manager for the JVM. Sandboxes outlive individual runners because they cannot be
     * reclaimed anyway; sharing them is what keeps the leak bounded.
     */
    private val sandboxManager = SandboxManager().also { manager ->
      Runtime.getRuntime().addShutdownHook(
        Thread {
          // Removes the staged native libraries from disk. The in-memory cost is unrecoverable.
          manager.close()
        }
      )
    }
  }
}
