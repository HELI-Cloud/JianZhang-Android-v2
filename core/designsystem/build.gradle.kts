plugins {
    id("jianzhang.android.library")

}

android {
    namespace = "com.jianzhang.core.designsystem"
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.haze)
    implementation(libs.androidx.core.ktx)
}