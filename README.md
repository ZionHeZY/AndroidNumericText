# AndroidNumericText

A small Kotlin integer-animation component for Android Views and Jetpack Compose, inspired by SwiftUI's numeric text transition.

This began as an older Android UI experiment. The 1.1.0 refresh turns both implementations into a reusable library, replaces the demo UI, and corrects the original README: **this is an animated integer display, not a numeric input or formatting SDK**.

## Demo

<table><tr><th>Jetpack Compose</th><th>Android Views</th></tr><tr><td><img src="demo-compose.gif" width="320" alt="Compose integer animation through carries, zero and negative numbers"></td><td><img src="demo-views.gif" width="320" alt="View digit animation through carries, zero and negative numbers"></td></tr></table>

Recorded from the actual demo app, cropped and palette-compressed at 320 px / 12 fps. Both GIFs together are approximately **149 KiB**, down from approximately **1,042 KiB** for the original recordings. No autoplay video or external image service is required.

## Install

Add JitPack once in your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add one library dependency in your app module:

```kotlin
implementation("com.github.ZionHeZY:AndroidNumericText:v1.1.0")
```

The library supports Android **API 21+**; the demo requires API 26+. Both APIs are in the same AAR. Compose dependencies are included transitively, so this is not a Compose-free View-only artifact. To use the composable, enable Compose and configure a Kotlin-compatible Compose compiler in your host project.

## Jetpack Compose

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tech.hezy.numerictext.AnimatedNumber

AnimatedNumber(
    number = count,
    style = TextStyle(
        fontSize = 48.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium
    ),
    color = Color(0xFFB84636),
    animationDurationMillis = 240
)
```

`count` is an `Int` owned by your app. Use `rememberSaveable` or your existing state holder when it must survive recreation. `modifier` controls layout, padding and semantics. Full `TextStyle` customization is supported; the explicit `color` parameter overrides `style.color`. Supply a theme-appropriate color rather than relying on a Material theme default.

## Android Views

Declare `xmlns:app="http://schemas.android.com/apk/res-auto"` on your layout root:

```xml
<tech.hezy.numerictext.AnimatedNumberView
    android:id="@+id/number"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="8dp"
    app:textSize="48sp"
    app:textColor="#B84636"
    app:animationDuration="240" />
```

```kotlin
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import tech.hezy.numerictext.AnimatedNumberView

val number = findViewById<AnimatedNumberView>(R.id.number)
number.typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
number.textColor = Color.argb(128, 20, 70, 160)
number.animationDurationMillis = 240
number.setNumber(count)
```

Custom font resources can be loaded by your host app and assigned as a `Typeface`. `textSizePixels` is available for pixel-based sizing. Read the current target using `number.number`. Call View APIs on the main thread. Persist the integer in your own state holder and restore it with `setNumber`; the View is not the model.

## Behavior and customization

| Capability | Android View | Compose |
|---|---|---|
| Animation | Changed digit slots roll; unchanged digits stay still | The complete integer rolls and fades |
| Typography | `Typeface`, `setTextSize`, `textSizePixels` | Full `TextStyle` |
| Color | ARGB, including transparency | `Color`, including transparency |
| Placement | Standard View layout and padding | Standard `Modifier` |
| Timing | `animationDurationMillis` / XML `animationDuration` | `animationDurationMillis` |

Both use a 200 ms default duration. Set duration to **0** for immediate updates. Negative durations and invalid View font sizes are rejected. Increasing values move upward; decreasing values move downward. The initial number appears immediately. Rapid changes converge to the latest target; the View settles the preceding target before starting the next transition rather than queueing updates.

The View reserves enough width during digit-count changes and shrinks after the transition, without inserting leading zeroes. It cancels animation when hidden or detached. System animation settings are respected; disabling animations does not leave stale digits. Accessibility exposes the latest complete integer, not separate animated fragments.

**Scope:** signed `Int` values, including `Int.MIN_VALUE` and `Int.MAX_VALUE`, rendered as ASCII integer text. No decimals, currency, grouping separators, editable input, or minimum/maximum input validation. Apply domain validation in your own state layer. Very large text or integers need sufficient parent width, or a horizontally scrollable container as demonstrated by the app.

## Build and verify

Use JDK 17 and Android SDK 35. The wrapper uses Gradle 8.9; the project uses AGP 8.7.0, Kotlin 1.9.24 and Compose compiler 1.5.14.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :numeric-text:lintRelease \
  :numeric-text:publishReleasePublicationToVerificationRepository
./scripts/verify-consumer.sh
```

The consumer script builds a separate app from published Maven metadata, not a project dependency. It exercises customized Compose and View usage without adding a separate Compose BOM. To verify the public coordinate:

```sh
./scripts/verify-consumer.sh https://jitpack.io v1.1.0
```

With an emulator attached, install both debug APKs and run the behavioral smoke checks:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  tech.hezy.androidnumerictext.test/tech.hezy.androidnumerictext.NumericSmoke
```

Require a `PASS:` result; Android instrumentation can return a zero process exit code even on failure. Checks cover signed integer rendering, rollover measurement, rapid updates, custom typography and alpha, disabled animation, visibility/detachment, recreation and demo cancellation. CI also runs an independent consumer build and emulator checks on API 26 and 35.

## 1.1.0 changes

- Publish both View and Compose APIs from `numeric-text`; remove the app-only composable.
- Replace per-digit animators with one reusable View animator and cached drawing buffers/metrics.
- Fix `wrap_content`, signed values, animation duration, accessibility and lifecycle handling.
- Expose typography, color and animation customization with an independently consumed artifact.
- Refresh both working demos, shrink README recordings, and add reproducible publishing and behavioral verification.

## License

[MIT](LICENSE). Issues and pull requests are welcome at [ZionHeZY/AndroidNumericText](https://github.com/ZionHeZY/AndroidNumericText).
