package tech.hezy.androidnumerictext

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.hezy.androidnumerictext.ui.theme.AndroidNumericTextTheme
import tech.hezy.numerictext.AnimatedNumber

class MainActivity : ComponentActivity() {
    private var number by mutableIntStateOf(0)
    private var running by mutableStateOf(false)
    private val replay = DemoReplay({ number = it }, { running = it })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = getColor(R.color.demo_background)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(background, background),
            navigationBarStyle = SystemBarStyle.light(background, background)
        )
        number = savedInstanceState?.getInt(DEMO_NUMBER_KEY)
            ?: intent.getIntExtra(DEMO_NUMBER_KEY, 0)
        setContent {
            AndroidNumericTextTheme {
                NumericDemo(
                    number = number,
                    running = running,
                    onIncrease = { changeNumber((number.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) },
                    onDecrease = { changeNumber((number.toLong() - 1).coerceAtLeast(Int.MIN_VALUE.toLong()).toInt()) },
                    onReset = { changeNumber(0) },
                    onReplay = { if (running) replay.cancel() else replay.start() },
                    onSwitchMode = {
                        replay.cancel()
                        startActivity(Intent(this, SampleActivity::class.java).putExtra(DEMO_NUMBER_KEY, number))
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        changeNumber(intent.getIntExtra(DEMO_NUMBER_KEY, number))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(DEMO_NUMBER_KEY, number)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        replay.cancel()
        super.onStop()
    }

    private fun changeNumber(value: Int) {
        replay.cancel()
        number = value
    }
}

@Composable
private fun NumericDemo(
    number: Int,
    running: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onReplay: () -> Unit,
    onSwitchMode: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val increaseLabel = stringResource(R.string.increase_description)
    val decreaseLabel = stringResource(R.string.decrease_description)
    val modeLabel = stringResource(R.string.open_views_description)
    val shape = RoundedCornerShape(12.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    stringResource(R.string.demo_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.demo_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.compose_mode),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onSwitchMode,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = modeLabel },
                        shape = shape
                    ) {
                        Text(stringResource(R.string.open_views))
                    }
                }
                Spacer(Modifier.height(32.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 184.dp)
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedNumber(
                        number = number,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 88.sp),
                        color = colors.onSurface,
                        animationDurationMillis = DEMO_ANIMATION_MILLIS
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDecrease,
                        enabled = number != Int.MIN_VALUE,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = decreaseLabel },
                        shape = shape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Text(stringResource(R.string.decrease), fontSize = 20.sp)
                    }
                    OutlinedButton(
                        onClick = onIncrease,
                        enabled = number != Int.MAX_VALUE,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = increaseLabel },
                        shape = shape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Text(stringResource(R.string.increase), fontSize = 20.sp)
                    }
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                    shape = shape
                ) {
                    Text(stringResource(R.string.reset), color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onReplay,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = shape
                ) {
                    Text(stringResource(if (running) R.string.stop_demo else R.string.run_demo))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.replay_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
