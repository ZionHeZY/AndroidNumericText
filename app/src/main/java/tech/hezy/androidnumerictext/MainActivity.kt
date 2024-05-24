package tech.hezy.androidnumerictext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimatedNumberApp()
        }
    }
}

@Composable
fun AnimatedNumberApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedNumberDemo()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedNumber(
    number: Int,
    modifier: Modifier = Modifier
) {
    var oldNumber by remember { mutableStateOf(number) }
    var newNumber by remember { mutableStateOf(number) }

    LaunchedEffect(number) {
        oldNumber = newNumber
        newNumber = number
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = newNumber,
            transitionSpec = {
                if (newNumber > oldNumber) {
                    slideInVertically { it } + fadeIn() with slideOutVertically { -it } + fadeOut()
                } else {
                    slideInVertically { -it } + fadeIn() with slideOutVertically { it } + fadeOut()
                }.using(SizeTransform(clip = false))
            }, label = ""
        ) { targetNumber ->
            Text(
                text = "$targetNumber",
                fontSize = 36.sp
            )
        }
    }
}

@Composable
fun AnimatedNumberDemo() {
    var number by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedNumber(number = number, modifier = Modifier.height(45.dp))

        Spacer(modifier = Modifier.height(50.dp))

        Button(onClick = {
            coroutineScope.launch {
                number++
            }
        }) {
            Text(text = "Increase")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            coroutineScope.launch {
                number--
            }
        }) {
            Text(text = "Decrease")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AnimatedNumberApp()
}
