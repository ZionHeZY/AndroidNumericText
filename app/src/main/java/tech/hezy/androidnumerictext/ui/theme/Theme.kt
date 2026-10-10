package tech.hezy.androidnumerictext.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import tech.hezy.androidnumerictext.R

@Composable
fun AndroidNumericTextTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.demo_background)
    val foreground = colorResource(R.color.demo_text)
    val secondary = colorResource(R.color.demo_secondary)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colorResource(R.color.demo_accent),
            onPrimary = colorResource(R.color.demo_on_accent),
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            onSurfaceVariant = secondary,
            outline = secondary,
            outlineVariant = colorResource(R.color.demo_outline)
        ),
        typography = Typography,
        content = content
    )
}
