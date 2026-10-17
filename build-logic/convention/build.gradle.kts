plugins {
    `kotlin-dsl`
}

group = "com.jianzhang.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:8.10.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.20")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.1.20-1.0.32")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.56.2")
}