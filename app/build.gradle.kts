import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * 正式包的签名信息放在项目根目录的 keystore.properties 里（该文件已被 .gitignore 排除，不会上传）。
 * 文件不存在时（例如别人只拿到源码）也能正常编译，只是打不出正式包 —— 不影响调试包。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.nana.workbench"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nana.workbench"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 第一版先不混淆：减少"优化导致行为变化"的风险，包体仍远小于 25MB
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 关于页要显示版本号，需要生成 BuildConfig（AGP 8 起默认不生成）
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    // 说明：数据库层测试用的是 Robolectric，它需要正常的安卓测试环境，
    // 在本项目的受限开发环境里跑不起来，所以放在 tools/db-tests/ 里（见那里的 README）。
}

/**
 * 把单元测试的运行期 classpath 写成一个文本文件。
 *
 * 用途：某些受限环境里 Gradle 派生的测试进程无法通过管道通信（报
 * "Could not write standard input to Gradle Test Executor"），此时可以取出 classpath，
 * 再用 `java -cp <classpath> org.junit.runner.JUnitCore ...` 直接跑测试。
 *
 * 写成文件而不是打印到控制台，是为了避免中文路径经过控制台编码后变成乱码。
 */
tasks.register("writeTestRuntimeClasspath") {
    val target = layout.buildDirectory.file("test-classpath.txt")
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeText(configurations.getByName("debugUnitTestRuntimeClasspath").asPath, Charsets.UTF_8)
        println("classpath 已写出 (" + file.length() + " 字节)")
    }
}
