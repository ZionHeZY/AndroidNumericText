package tech.hezy.androidnumerictext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

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
