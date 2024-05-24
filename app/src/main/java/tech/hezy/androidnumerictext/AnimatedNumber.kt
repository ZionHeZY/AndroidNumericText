package tech.hezy.androidnumerictext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun AnimatedNumber(
    number: Int,
    modifier: Modifier = Modifier
) {
    var oldNumber by remember { mutableIntStateOf(number) }
    var newNumber by remember { mutableIntStateOf(number) }

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
                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                } else {
                    (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
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
