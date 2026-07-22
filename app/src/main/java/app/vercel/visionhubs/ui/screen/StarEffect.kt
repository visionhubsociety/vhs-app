package app.vercel.visionhubs.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class Star(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var alpha: Float,
    var twinkle: Float
)

@Composable
fun StarsEffectComponent() {
    val stars = remember { mutableStateListOf<Star>() }
    var timeMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTime ->
                timeMillis = frameTime
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (stars.isEmpty() && size.width > 0 && size.height > 0) {
            repeat(120) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * size.width,
                        y = Random.nextFloat() * size.height,
                        radius = Random.nextFloat() * 3.5f + 1.2f,
                        speed = Random.nextFloat() * 0.8f + 0.2f,
                        alpha = Random.nextFloat() * 0.6f + 0.3f,
                        twinkle = Random.nextFloat() * Math.PI.toFloat() * 2f
                    )
                )
            }
        }

        val currentSec = timeMillis / 1000f

        stars.forEach { star ->
            val sinAlpha = star.alpha * (0.7f + 0.3f * kotlin.math.sin(currentSec * 0.8f + star.twinkle))
            
            drawCircle(
                color = Color.White.copy(alpha = sinAlpha.coerceIn(0f, 1f)),
                radius = star.radius,
                center = Offset(star.x, star.y)
            )

            star.y += star.speed
            if (star.y > size.height + 2f) {
                star.y = -2f
                star.x = Random.nextFloat() * size.width
            }
        }
    }
}
