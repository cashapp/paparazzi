package app.cash.paparazzi

import app.cash.paparazzi.internal.Renderer
import app.cash.paparazzi.internal.SessionParamsBuilder
import org.junit.rules.ExternalResource

open class RendererScope : ExternalResource() {
  internal lateinit var renderer: Renderer
  internal lateinit var sessionParamsBuilder: SessionParamsBuilder
  val isInitialized get() = ::renderer.isInitialized
  override fun after() { renderer.close() }
}