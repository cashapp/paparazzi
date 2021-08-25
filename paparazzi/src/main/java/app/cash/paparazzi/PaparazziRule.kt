package app.cash.paparazzi

import app.cash.paparazzi.agent.AgentTestRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// TODO: Better name! Paparazzi is already a rule. This is an opt-in rule to allow for @ClassRule
//  usage on Paparazzi without losing the test name.
class PaparazziRule(
  private val paparazzi: Paparazzi,
) : TestRule {
  override fun apply(
    base: Statement,
    description: Description,
  ): Statement {
    val statement = object : Statement() {
      override fun evaluate() {
        paparazzi.setDescription(description)
        base.evaluate()
      }
    }

    val outerRule = AgentTestRule()
    return outerRule.apply(statement, description)
  }
}
