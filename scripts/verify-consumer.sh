#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPOSITORY="${1:-$ROOT/build/repository}"
VERSION="${2:-v1.1.0}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/src/main/java/tech/hezy/numericconsumer"
printf 'android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n' > "$WORK/gradle.properties"
cat > "$WORK/settings.gradle.kts" <<'GRADLE'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(providers.gradleProperty("numericRepository").get())
            content { includeGroup("com.github.ZionHeZY") }
        }
    }
}
rootProject.name = "numeric-consumer"
GRADLE
cat > "$WORK/build.gradle.kts" <<'GRADLE'
plugins {
    id("com.android.application") version "8.7.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}
android {
    namespace = "tech.hezy.numericconsumer"
    compileSdk = 35
    defaultConfig {
        applicationId = "tech.hezy.numericconsumer"
        minSdk = 21
        targetSdk = 35
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
dependencies {
    // Component addition: no project dependency, local AAR or manual Compose BOM.
    implementation("com.github.ZionHeZY:AndroidNumericText:${providers.gradleProperty("numericVersion").get()}")
    implementation("androidx.activity:activity-compose:1.8.2")
}
GRADLE
cat > "$WORK/src/main/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="Numeric consumer">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
XML
cat > "$WORK/src/main/java/tech/hezy/numericconsumer/MainActivity.kt" <<'KOTLIN'
package tech.hezy.numericconsumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import tech.hezy.numerictext.AnimatedNumber
import tech.hezy.numerictext.AnimatedNumberView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ComposeView(this).apply {
            setContent {
                AnimatedNumber(
                    number = -10,
                    style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Color.Blue),
                    color = Color(0xFFB84636),
                    animationDurationMillis = 0
                )
            }
        })
        content.addView(AnimatedNumberView(this).apply {
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 48f)
            textColor = android.graphics.Color.argb(128, 20, 70, 160)
            animationDurationMillis = 0
            setNumber(42)
            setNumber(99)
        })
        setContentView(content)
    }
}
KOTLIN
"$ROOT/gradlew" --project-dir "$WORK" -PnumericRepository="$REPOSITORY" -PnumericVersion="$VERSION" assembleDebug
mkdir -p "$ROOT/build/consumer"
cp "$WORK/build/outputs/apk/debug/numeric-consumer-debug.apk" "$ROOT/build/consumer/consumer-debug.apk"
printf 'PASS: independent app consumes View and Compose from %s\n' "$REPOSITORY"
