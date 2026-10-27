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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cyan = Color(0xFF0078A8)
val Purple = Color(0xFF6554C0)
val Pink = Color(0xFFC2185B)
val Bg = Color(0xFFF4F7FA)
val PanelBg = Color(0xFFFFFFFF)
val Dim = Color(0xFF586575)
val Ink = Color(0xFF1C2733)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cyan, secondary = Purple, background = Bg, surface = PanelBg,
            onPrimary = Color.White, onBackground = Ink, onSurface = Ink
        ),
        content = content
    )
}

@Composable
fun Mono(t: String, color: Color = Ink, size: Int = 13, bold: Boolean = false, modifier: Modifier = Modifier) {
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
            .background(PanelBg)
            .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.35f), Purple.copy(alpha = 0.35f))), shape)
            .padding(12.dp),
        content = content
    )
}

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.White),
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
            focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedContainerColor = PanelBg, unfocusedContainerColor = PanelBg,
            focusedBorderColor = Cyan, unfocusedBorderColor = Purple.copy(alpha = 0.55f),
            focusedLabelColor = Cyan, unfocusedLabelColor = Dim, cursorColor = Cyan
        )
    )
}

/** Standard module page: title bar with back arrow + scrollable body. */
@Composable
fun Screen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawerToggle()
            Text("‹", color = Cyan, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Mono(title.uppercase(), Cyan, 20, true, Modifier.weight(1f))
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
