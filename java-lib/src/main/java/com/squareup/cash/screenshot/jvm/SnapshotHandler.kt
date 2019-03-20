package com.squareup.cash.screenshot.jvm

import java.awt.image.BufferedImage
import java.io.Closeable

interface SnapshotHandler : Closeable {
  fun add(
    shot: Shot,
    image: BufferedImage
  )
}