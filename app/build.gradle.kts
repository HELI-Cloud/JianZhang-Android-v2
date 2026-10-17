import java.util.Properties

plugins {
    id("jianzhang.android.application")
}

android {
    namespace = "com.jianzhang.app"

    defaultConfig {
        applicationId = "com.jianzhang.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val pf = rootProject.file("keystore.properties")
            if (pf.exists()) {
                pf.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("release")
            signingConfig = if (releaseSigning?.storeFile != null) releaseSigning else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    doLast {
        val config = android.signingConfigs.findByName("release")
        val store = config?.storeFile
        if (store == null || !store.exists()) {
            throw GradleException(
                "release 签名缺失：请配置 keystore.properties 与签名文件；拒绝使用 debug 签名产出发布包",
            )
        }
    }
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(checkReleaseSigning)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:overview"))
    implementation(project(":feature:ledger"))
    implementation(project(":feature:record"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:planning"))
    implementation(project(":feature:analysis"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:lock"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.haze)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
