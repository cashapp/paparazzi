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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.internal.runners.statements.RunAfters
import org.junit.internal.runners.statements.RunBefores
import org.junit.rules.MethodRule
import org.junit.rules.RunRules
import org.junit.rules.TestRule
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass
import java.io.File

/**
 * A JUnit 4 runner that executes each test inside an isolated layoutlib sandbox.
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
 * early enough to reload the test class itself, which is precisely why Robolectric owns the runner
 * too.
 *
 * So [methodBlock] rebuilds the entire per-method statement — instance, `@Before`s, `@After`s and
 * rules — against the sandbox's copy of the test class. JUnit's own types are shared with the host
 * (see [SandboxConfiguration.forRunner]), so [Statement], [FrameworkMethod] and `Description` cross
 * the boundary unchanged and reporting works normally.
 *
 * ### What this does and does not buy
 *
 * All test classes share one sandbox per configuration. It is *not* one sandbox per test class:
 * layoutlib's `JNI_OnLoad` pins its class loader permanently, so every additional sandbox costs
 * roughly 40 MB that is never reclaimed. See [SandboxManager] for the measurements.
 *
 * The practical consequence is that this does **not** give each test clean framework statics — the
 * existing per-test resets in `PaparazziSdk` are still doing that job, and still need to. What it
 * does give is a layoutlib instance that is fully separate from the host class loader, and the
 * ability to run more than one layoutlib configuration in a single JVM.
 */
public class PaparazziRunner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
  override fun methodBlock(method: FrameworkMethod): Statement {
    val sandbox = sandbox()
    val sandboxTestClass = TestClass(sandbox.bootstrappedClass(testClass.javaClass))

    val test = sandboxTestClass.onlyConstructor.newInstance()
    val sandboxMethod = FrameworkMethod(sandboxTestClass.javaClass.getMethod(method.name))

    var statement: Statement = org.junit.internal.runners.statements.InvokeMethod(sandboxMethod, test)
    statement = possiblyExpectingExceptions(sandboxMethod, test, statement)
    statement = withSandboxBefores(sandboxTestClass, test, statement)
    statement = withSandboxAfters(sandboxTestClass, test, statement)
    statement = withSandboxRules(sandboxTestClass, method, test, statement)

    // The context class loader matters for ServiceLoader lookups inside layoutlib and Compose.
    return object : Statement() {
      override fun evaluate() {
        sandbox.runOnSandbox { statement.evaluate() }
      }
    }
  }

  /**
   * `BlockJUnit4ClassRunner`'s own `withBefores` resolves `@Before` methods from the *host* test
   * class, which would then be invoked against a sandbox instance and fail with
   * "object is not an instance of declaring class". Hence the reimplementation against
   * [sandboxTestClass].
   */
  private fun withSandboxBefores(sandboxTestClass: TestClass, test: Any, statement: Statement): Statement {
    val befores = sandboxTestClass.getAnnotatedMethods(Before::class.java)
    return if (befores.isEmpty()) statement else RunBefores(statement, befores, test)
  }

  private fun withSandboxAfters(sandboxTestClass: TestClass, test: Any, statement: Statement): Statement {
    val afters = sandboxTestClass.getAnnotatedMethods(After::class.java)
    return if (afters.isEmpty()) statement else RunAfters(statement, afters, test)
  }

  /**
   * Collects `@Rule` members from the sandboxed instance.
   *
   * The rules are the sandbox's own objects — a sandboxed [Paparazzi] driving a sandboxed
   * `PaparazziSdk` — but they implement the host's [TestRule], because `org.junit` is delegated.
   * That shared interface is what lets the two halves cooperate at all.
   */
  private fun withSandboxRules(
    sandboxTestClass: TestClass,
    method: FrameworkMethod,
    test: Any,
    statement: Statement
  ): Statement {
    val description = describeChild(method)

    val methodRules = ArrayList<MethodRule>().apply {
      addAll(sandboxTestClass.getAnnotatedMethodValues(test, Rule::class.java, MethodRule::class.java))
      addAll(sandboxTestClass.getAnnotatedFieldValues(test, Rule::class.java, MethodRule::class.java))
    }
    val testRules = ArrayList<TestRule>().apply {
      addAll(sandboxTestClass.getAnnotatedMethodValues(test, Rule::class.java, TestRule::class.java))
      addAll(sandboxTestClass.getAnnotatedFieldValues(test, Rule::class.java, TestRule::class.java))
    }

    var result = statement
    for (rule in methodRules) {
      // A member can implement both interfaces; JUnit applies such a rule once, as a TestRule.
      if (testRules.none { it === rule }) result = rule.apply(result, method, test)
    }
    return if (testRules.isEmpty()) result else RunRules(result, testRules, description)
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
    val nativeLibDir = sandbox.nativeLibDir ?: return
    sandbox.loadClass("app.cash.paparazzi.internal.SandboxRuntime")
      .getDeclaredMethod("setNativeLibDir", String::class.java)
      .invoke(null, nativeLibDir.absolutePath)
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
