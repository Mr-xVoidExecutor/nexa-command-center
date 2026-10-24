package com.nexa.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cyan = Color(0xFF00F0FF)
val Purple = Color(0xFF9D4DFF)
val Pink = Color(0xFFFF2BD6)
val Bg = Color(0xFF05060F)
val PanelBg = Color(0xFF0C1124)
val Dim = Color(0xFF8FA3C7)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, secondary = Purple, background = Bg, surface = PanelBg,
            onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun Mono(t: String, color: Color = Color.White, size: Int = 13, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        t, modifier = modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun Link(text: String, color: Color = Cyan, onClick: () -> Unit) {
    Text(
        text, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape)
            .background(PanelBg.copy(alpha = 0.85f))
            .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.7f), Purple.copy(alpha = 0.7f))), shape)
            .padding(12.dp),
        content = content
    )
}

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.15f), contentColor = Cyan),
        border = BorderStroke(1.dp, Cyan),
        shape = RoundedCornerShape(10.dp)
    ) { Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
fun Field(
    v: String, onChange: (String) -> Unit, label: String,
    secret: Boolean = false, lines: Int = 1, modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = v, onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        maxLines = if (lines == 1) 1 else 8,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan, unfocusedBorderColor = Purple.copy(alpha = 0.6f),
            focusedLabelColor = Cyan, cursorColor = Cyan
        )
    )
}

/** Standard module page: title bar with back arrow + scrollable body. */
@Composable
fun Screen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Cyan, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Mono(title.uppercase(), Cyan, 20, true)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun MatrixRain(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "matrix").animateFloat(
        0f, 100000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "t"
    )
    val paint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 0, 240, 255)
            textSize = 34f
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val chars = remember { "01ABCDEFXYZ<>{}#@".toCharArray() }
    Canvas(modifier.fillMaxSize()) {
        val cols = (size.width / 34f).toInt()
        val rows = (size.height / 38f).toInt()
        drawIntoCanvas { c ->
            for (i in 0 until cols) {
                val head = ((t / 12f) * (0.6f + (i % 5) * 0.2f) + i * 13).toInt() % (rows + 12)
                for (k in 0 until 10) {
                    val r = head - k
                    if (r in 0 until rows) {
                        paint.alpha = (255 * (1f - k / 10f) * 0.5f).toInt()
                        val ch = chars[(i * 7 + r * 3 + (t / 40f).toInt()) % chars.size]
                        c.nativeCanvas.drawText(ch.toString(), i * 34f, r * 38f + 30f, paint)
                    }
                }
            }
        }
    }
}

/**
 * NEXA's living body: the character image breathes, floats, sways and drifts around,
 * pulses harder while she is speaking, and a holographic scan-line sweeps over her.
 */
@Composable
fun Avatar(speaking: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "avatar")
    val bob by inf.animateFloat(-18f, 18f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val drift by inf.animateFloat(-34f, 34f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val breathe by inf.animateFloat(1f, 1.025f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val sway by inf.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val talk by inf.animateFloat(1f, 1.05f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "talk")
    val glow by inf.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    val scan by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "scan")
    val s = breathe * (if (speaking) talk else 1f)
    val shape = RoundedCornerShape(24.dp)
    val mood = Mood.now.value
    val mc = when (mood) {
        "angry" -> Color(0xFFFF3B3B)
        "sad" -> Color(0xFF4D7CFF)
        "happy" -> Color(0xFF00FFA3)
        "shy", "caring" -> Pink
        "playful" -> Purple
        else -> Cyan
    }
    val ctx = LocalContext.current
    // Optional expression images: drop nexa_happy / nexa_angry / nexa_sad / nexa_shy / nexa_playful / nexa_caring into res/drawable-nodpi
    val resId = remember(mood) {
        val id = ctx.resources.getIdentifier("nexa_$mood", "drawable", ctx.packageName)
        if (id != 0) id else R.drawable.nexa_avatar
    }
    Box(
        modifier.clip(shape).border(
            2.dp,
            Brush.verticalGradient(listOf(mc.copy(alpha = if (speaking) 1f else glow), Pink.copy(alpha = glow))),
            shape
        )
    ) {
        Image(
            painterResource(resId), "NEXA",
            Modifier.fillMaxSize().graphicsLayer {
                translationY = bob; translationX = drift; rotationZ = sway
                scaleX = 1.12f * s; scaleY = 1.12f * s
            },
            alignment = Alignment.TopCenter, contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(mc.copy(alpha = 0.12f)))
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * scan
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Cyan.copy(alpha = 0.35f), Color.Transparent),
                    startY = y - 60f, endY = y + 60f
                ),
                topLeft = Offset(0f, y - 60f), size = Size(size.width, 120f)
            )
        }
    }
}
