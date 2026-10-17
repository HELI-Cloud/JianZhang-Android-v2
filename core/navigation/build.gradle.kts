plugins {
    id("jianzhang.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.jianzhang.core.navigation"
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
}