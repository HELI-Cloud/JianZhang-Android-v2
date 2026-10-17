plugins {
    id("jianzhang.android.library")
}

android {
    namespace = "com.jianzhang.core.model"
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
}