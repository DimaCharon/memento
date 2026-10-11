import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** Извлекает versionName из gradle.properties-подобного файла, чтобы номер версии жил в одном месте. */
fun readVersion(): Pair<Int, String> {
    val f = rootProject.file("version.properties")
    if (!f.exists()) return 1 to "1.0.0"
    val p = Properties().apply { f.inputStream().use { load(it) } }
    return (p.getProperty("versionCode")?.toIntOrNull() ?: 1) to (p.getProperty("versionName") ?: "1.0.0")
}

val (vCode, vName) = readVersion()

/** Ключ подписи: сначала из переменных окружения (CI/локально), затем — из файла в репозитории. */
val keystoreFile = rootProject.file("keystore/memento.jks")
val hasEnvKey = System.getenv("MEMENTO_STORE_PASSWORD") != null && System.getenv("MEMENTO_KEY_PASSWORD") != null
val hasRepoKey = keystoreFile.exists()

android {
    namespace = "ru.memento"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.memento"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName
        resourceConfigurations += listOf("ru", "en")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("memento") {
            storeFile = keystoreFile
            storePassword = System.getenv("MEMENTO_STORE_PASSWORD") ?: "memento-pass"
            keyAlias = System.getenv("MEMENTO_KEY_ALIAS") ?: "memento"
            keyPassword = System.getenv("MEMENTO_KEY_PASSWORD") ?: "memento-pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasEnvKey || hasRepoKey) signingConfigs.getByName("memento") else signingConfigs.getByName("debug")
        }
        /** Тот же код, но без R8/сжатия ресурсов — гарантированный запасной вариант для слабых/OEM-прошивок. */
        create("safe") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            signingConfig = if (hasEnvKey || hasRepoKey) signingConfigs.getByName("memento") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.maxHeapSize = "2g" }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val core = "androidx.core:core-ktx:1.13.1"
    val appcompat = "androidx.appcompat:appcompat:1.7.0"
    val material = "com.google.android.material:material:1.12.0"
    val constraint = "androidx.constraintlayout:constraintlayout:2.1.4"
    val recycler = "androidx.recyclerview:recyclerview:1.3.2"
    val fragment = "androidx.fragment:fragment-ktx:1.7.1"
    val lifecycle = "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
    val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"

    implementation(core)
    implementation(appcompat)
    implementation(material)
    implementation(constraint)
    implementation(recycler)
    implementation(fragment)
    implementation(lifecycle)
    implementation(coroutines)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

/** Удобные задачи: собрать все варианты и сложить APK в ./apk */
tasks.register<Copy>("collectApks") {
    val dir = rootProject.layout.projectDirectory.dir("apk")
    dependsOn("assembleRelease", "assembleSafe")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(dir)
}
