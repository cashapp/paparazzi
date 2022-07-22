package app.cash.paparazzi.plugin.test

object HotSpotHeapDumper {
  private val mBean: Any by lazy {
    // val server = ManagementFactory.getPlatformMBeanServer()

    val managementFactoryClass = Class.forName("java.lang.management.ManagementFactory")
    val mBeanServer = managementFactoryClass.getMethod("getPlatformMBeanServer").invoke(null)

    // ManagementFactory.newPlatformMXBeanProxy(
    //  server,
    //  "com.sun.management:type=HotSpotDiagnostic",
    //  HotSpotDiagnosticMXBean::class.java
    // )

    val hotSpotDiagnosticMXBeanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean")
    managementFactoryClass
      .getMethod(
        "newPlatformMXBeanProxy",
        Class.forName("javax.management.MBeanServerConnection"),
        HOTSPOT_BEAN_NAME.javaClass,
        hotSpotDiagnosticMXBeanClass.javaClass
      )
      .invoke(null, mBeanServer, HOTSPOT_BEAN_NAME, hotSpotDiagnosticMXBeanClass)
  }

  fun dumpHeap(fileName: String) {
    // mBean.dumpHeap(fileName, LIVE)

    Class.forName("com.sun.management.HotSpotDiagnosticMXBean")
      .getMethod("dumpHeap", String::class.java, Boolean::class.javaPrimitiveType)
      .invoke(mBean, fileName, LIVE)
  }

  private const val HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic"
  private const val LIVE = true
}
