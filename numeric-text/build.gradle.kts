plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    `maven-publish`
}

group = "com.github.ZionHeZY.AndroidNumericText"
version = providers.gradleProperty("version").orElse("v1.1.0").get()

android {
    namespace = "tech.hezy.numerictext"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
}

publishing {
    repositories {
        maven {
            name = "verification"
            url = uri(rootProject.layout.buildDirectory.dir("repository"))
        }
    }
}

afterEvaluate {
    publishing.publications.register<MavenPublication>("release") {
        from(components["release"])
        artifactId = "numeric-text"
        pom {
            name.set("AndroidNumericText")
            description.set("Animated integer text for Android Views and Jetpack Compose.")
            url.set("https://github.com/ZionHeZY/AndroidNumericText")
            scm {
                url.set("https://github.com/ZionHeZY/AndroidNumericText")
                connection.set("scm:git:https://github.com/ZionHeZY/AndroidNumericText.git")
            }
        }
    }
}