package app.cash.paparazzi

import app.cash.paparazzi.agent.AgentTestRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PaparazziRule(
	private val paparazzi: Paparazzi = Paparazzi()
) : TestRule {

	override fun apply(
		base: Statement,
		description: Description
	): Statement {
		val statement = object : Statement() {
			override fun evaluate() {
				paparazzi.prepare(description.toTestName())
				try {
					base.evaluate()
				} finally {
					paparazzi.close()
				}
			}
		}

		paparazzi.registerInterceptors()

		val outerRule = AgentTestRule()
		return outerRule.apply(statement, description)
	}
}

private fun Description.toTestName(): TestName {
	val fullQualifiedName = className
	val packageName = fullQualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
	val className = fullQualifiedName.substringAfterLast('.')
	return TestName(packageName, className, methodName)
}
