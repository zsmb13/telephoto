import org.jetbrains.compose.compose

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(compose("org.jetbrains.compose.ui:ui-util"))
      api(compose.foundation)
      implementation(compose.desktop.currentOs)
      implementation(compose.components.resources)
      implementation(projects.zoomable)
    }
  }

  jvm()
}
