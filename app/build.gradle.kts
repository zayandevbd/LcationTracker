plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zayan.locationtracker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zayan.locationtracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    // Exclude duplicate META-INF files that sometimes conflict with Google Play Services
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.core)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.localbroadcastmanager)

    //  Material Design Components
    implementation(libs.material)

    // Lifecycle: ViewModel + LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.common.java8)

    // Room: Local Database
    implementation(libs.room.runtime)
    // annotationProcessor (not kapt) — this is a Java-only project
    annotationProcessor(libs.room.compiler)

    // Google Play Services: Location
    implementation(libs.play.services.location)

    //  Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
}
