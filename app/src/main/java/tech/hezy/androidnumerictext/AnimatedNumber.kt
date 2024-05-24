package tech.hezy.androidnumerictext

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
