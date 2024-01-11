package app.cash.paparazzi

import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

class PaparazziTestRule : ExternalResource() {
  private val tmpFolder = TemporaryFolder.builder().assureDeletion().build()
  private val reportDirKey = "paparazzi.snapshot.dir"
  private var oldReportDir: String? = null

  internal lateinit var paparazzi: Paparazzi

  override fun before() {
    tmpFolder.create()

    oldReportDir = System.getProperty(reportDirKey)
    System.setProperty(reportDirKey, tmpFolder.newFolder().path)

    paparazzi = Paparazzi()
  }

  override fun after() {
    tmpFolder.delete()

    if (oldReportDir == null) {
      System.clearProperty(reportDirKey)
    } else {
      System.setProperty(reportDirKey, oldReportDir!!)
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        before()

        val errors: MutableList<Throwable> = ArrayList()
        try {
          paparazzi.apply(base, description).evaluate()
        } catch (t: Throwable) {
          errors.add(t)
        } finally {
          try {
            after()
          } catch (t: Throwable) {
            errors.add(t)
          }
        }
        MultipleFailureException.assertEmpty(errors)
      }
    }
  }
}
