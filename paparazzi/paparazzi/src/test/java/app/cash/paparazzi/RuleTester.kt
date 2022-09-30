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

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Use this rule to test implementations of [TestRule] that aren't instance variables in the test
 * class. For example, this may be useful to test different configurations of the same [Paparazzi]
 * in a single test function.
 */
class RuleTester : TestRule {
  private var currentDescription: Description? = null

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        currentDescription = description
        try {
          base.evaluate()
        } finally {
          currentDescription = null
        }
      }
    }
  }

  fun test(rule: TestRule, block: () -> Unit) {
    val description = currentDescription ?: error("this test rule isn't running?")
    val statement = object : Statement() {
      override fun evaluate() {
        block()
      }
    }
    rule.apply(statement, description).evaluate()
  }
}
