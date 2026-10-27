package com.nexa.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

object AvatarMotion {
    val pose = mutableStateOf("idle")
    val offsetX = mutableFloatStateOf(0f)
    val offsetY = mutableFloatStateOf(0f)
}

@Composable
fun Avatar(speaking: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar-motion")
    val gait by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (poseForAnimation() == "run") 480 else 760, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "gait"
    )
    val idle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Restart),
        label = "idle"
    )
    val blinkCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800), RepeatMode.Restart),
        label = "blink"
    )
    val pose = AvatarMotion.pose.value
    val voicePulse = if (speaking) ((sin(gait * 1.7f) + 1f) / 2f) else 0f
    val mouth = max(Voice.mouthOpen.floatValue, voicePulse * 0.72f)
    val mood = Mood.now.value
    val accent = when (mood) {
        "angry" -> Color(0xFFFF5864)
        "sad" -> Color(0xFF779DFF)
        "happy", "laugh" -> Color(0xFF61F7C1)
        "shy", "caring" -> Color(0xFFFF79C6)
        "playful", "surprised" -> Color(0xFFB88CFF)
        else -> Cyan
    }

    Canvas(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { AvatarMotion.pose.value = "walk" },
                    onDragEnd = { AvatarMotion.pose.value = "idle" },
                    onDragCancel = { AvatarMotion.pose.value = "idle" },
                    onDrag = { _, amount ->
                        val width = size.width.coerceAtLeast(1)
                        val height = size.height.coerceAtLeast(1)
                        AvatarMotion.offsetX.floatValue =
                            (AvatarMotion.offsetX.floatValue + amount.x * 1000f / width).coerceIn(-240f, 240f)
                        AvatarMotion.offsetY.floatValue =
                            (AvatarMotion.offsetY.floatValue + amount.y * 1800f / height).coerceIn(-180f, 180f)
                    }
                )
            }
            .clickable {
                AvatarMotion.pose.value = if (AvatarMotion.pose.value == "wave") "idle" else "wave"
                Mood.now.value = "happy"
                Voice.say("Hey, Captain!", "happy")
            }
    ) {
        val unit = minOf(size.width / 1000f, size.height / 1800f)
        val left = (size.width - 1000f * unit) / 2f
        val top = (size.height - 1800f * unit) / 2f
        val bob = if (pose == "idle") sin(idle) * 7f else absSin(gait) * 9f
        val sway = when (pose) {
            "dance" -> sin(gait * 0.5f) * 32f
            "walk", "run" -> sin(gait) * 12f
            "sit" -> 0f
            else -> sin(idle * 0.5f) * 4f
        }
        val travel = when (pose) {
            "walk" -> sin(gait) * 72f
            "run" -> sin(gait) * 118f
            "dance" -> sin(gait * 0.5f) * 38f
            "sit" -> 0f
            else -> sin(idle * 0.5f) * 7f
        }

        translate(left, top) {
            scale(unit, pivot = Offset.Zero) {
                translate(
                    left = travel + AvatarMotion.offsetX.floatValue,
                    top = bob + AvatarMotion.offsetY.floatValue
                ) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(500f, 880f),
                            radius = 690f
                        ),
                        radius = 690f,
                        center = Offset(500f, 880f)
                    )
                    drawFloor(accent, gait, pose)
                    drawLegs(accent, gait, pose)
                    drawArms(accent, gait, pose, sway)
                    translate(left = sway, top = 0f) {
                        drawBody(accent, mood)
                        drawFace(accent, mood, speaking, mouth, blinkCycle, gait)
                    }
                }
            }
        }
    }
}

private fun absSin(value: Float): Float = kotlin.math.abs(sin(value))

private fun poseForAnimation(): String = AvatarMotion.pose.value

@Composable
fun AvatarMotionControls(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("IDLE", "WALK", "RUN", "SIT", "DANCE", "WAVE").forEach { label ->
            Link(
                label,
                if (AvatarMotion.pose.value == label.lowercase()) Cyan else Dim
            ) {
                AvatarMotion.pose.value = label.lowercase()
                if (label == "DANCE") Mood.now.value = "playful"
                if (label == "WAVE") Mood.now.value = "happy"
            }
        }
    }
}

private fun DrawScope.drawFloor(accent: Color, phase: Float, pose: String) {
    val centerY = if (pose == "sit") 1638f else 1690f
    val pulse = 0.5f + absSin(phase) * 0.3f
    drawOval(
        Brush.radialGradient(
            listOf(accent.copy(alpha = pulse * 0.3f), Color.Transparent),
            center = Offset(500f, centerY),
            radius = 300f
        ),
        topLeft = Offset(230f, centerY - 38f),
        size = Size(540f, 76f)
    )
    drawLine(accent.copy(alpha = pulse * 0.5f), Offset(300f, centerY), Offset(700f, centerY), 2f)
}

private fun DrawScope.drawLegs(accent: Color, phase: Float, pose: String) {
    val stride = when (pose) {
        "walk" -> sin(phase) * 68f
        "run" -> sin(phase) * 108f
        "dance" -> sin(phase) * 46f
        else -> 0f
    }
    val seated = pose == "sit"
    val hipY = 1085f
    val kneeY = if (seated) 1280f else 1370f
    val ankleY = if (seated) 1435f else 1610f
    val hipLeft = Offset(438f, hipY)
    val hipRight = Offset(562f, hipY)
    val kneeLeft = Offset(408f - stride, kneeY)
    val kneeRight = Offset(592f + stride, kneeY)
    val ankleLeft = Offset(390f - stride * 0.62f + if (seated) 90f else 0f, ankleY)
    val ankleRight = Offset(610f + stride * 0.62f + if (seated) -90f else 0f, ankleY)
    val bootY = if (seated) 1500f else 1690f
    val shell = Color(0xFF121829)
    val shade = Color(0xFF28314A)

    fun segment(start: Offset, end: Offset, thickness: Float) {
        drawLine(Color(0xFF080B14), start, end, thickness + 18f, StrokeCap.Round)
        drawLine(shell, start, end, thickness, StrokeCap.Round)
        drawLine(shade, start, end, thickness * 0.42f, StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.82f), start, end, 4f, StrokeCap.Round)
    }

    segment(hipLeft, kneeLeft, 70f)
    segment(hipRight, kneeRight, 70f)
    segment(kneeLeft, ankleLeft, 60f)
    segment(kneeRight, ankleRight, 60f)
    listOf(kneeLeft, kneeRight).forEach { knee ->
        drawCircle(Color(0xFF090D19), 49f, knee)
        drawCircle(shade, 36f, knee)
        drawCircle(accent.copy(alpha = 0.85f), 11f, knee)
        drawCircle(Color.White.copy(alpha = 0.65f), 4f, knee)
    }
    drawBoot(ankleLeft, -1f, bootY, shell, shade, accent)
    drawBoot(ankleRight, 1f, bootY, shell, shade, accent)
    drawLine(accent.copy(alpha = 0.8f), Offset(468f, 1120f), Offset(500f, 1185f), 8f, StrokeCap.Round)
    drawLine(accent.copy(alpha = 0.8f), Offset(532f, 1120f), Offset(500f, 1185f), 8f, StrokeCap.Round)
}

private fun DrawScope.drawBoot(
    ankle: Offset,
    direction: Float,
    bootY: Float,
    shell: Color,
    shade: Color,
    accent: Color
) {
    val toe = Offset(ankle.x + direction * 56f, bootY)
    drawLine(Color(0xFF080B14), ankle, toe, 82f, StrokeCap.Round)
    drawLine(shell, ankle, toe, 64f, StrokeCap.Round)
    drawLine(shade, ankle, toe, 30f, StrokeCap.Round)
    drawLine(accent, Offset(ankle.x - 12f, ankle.y - 20f), Offset(ankle.x - 12f, ankle.y + 35f), 5f, StrokeCap.Round)
    drawLine(accent, Offset(toe.x - 28f, toe.y + 40f), Offset(toe.x + 32f, toe.y + 40f), 8f, StrokeCap.Round)
}

private fun DrawScope.drawArms(accent: Color, phase: Float, pose: String, sway: Float) {
    val sleeve = Color(0xFF141A2A)
    val metal = Color(0xFF343E59)
    val shoulderY = 780f
    val waving = pose == "wave"
    val dance = pose == "dance"
    val leftElbow = Offset(278f - sway * 0.25f, if (dance) 1000f + sin(phase) * 35f else 1010f)
    val leftWrist = Offset(295f - sway * 0.35f, 1190f)
    val rightElbow = if (waving) Offset(765f, 610f) else Offset(722f + sway * 0.3f, 1000f)
    val rightWrist = if (waving) Offset(835f, 420f + sin(phase) * 12f) else Offset(700f + sway * 0.35f, 1190f)

    fun arm(start: Offset, elbow: Offset, wrist: Offset) {
        drawLine(Color(0xFF080B14), start, elbow, 110f, StrokeCap.Round)
        drawLine(sleeve, start, elbow, 86f, StrokeCap.Round)
        drawLine(metal, start, elbow, 39f, StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.85f), start, elbow, 5f, StrokeCap.Round)
        drawCircle(Color(0xFF080B14), 51f, elbow)
        drawCircle(sleeve, 39f, elbow)
        drawCircle(accent.copy(alpha = 0.9f), 12f, elbow)
        drawLine(Color(0xFF080B14), elbow, wrist, 74f, StrokeCap.Round)
        drawLine(sleeve, elbow, wrist, 54f, StrokeCap.Round)
        drawLine(metal, elbow, wrist, 22f, StrokeCap.Round)
        drawLine(accent, elbow, wrist, 5f, StrokeCap.Round)
        drawCircle(Color(0xFFE0BDB5), 30f, wrist)
        drawCircle(accent, 8f, wrist)
    }

    arm(Offset(330f, shoulderY), leftElbow, leftWrist)
    arm(Offset(670f, shoulderY), rightElbow, rightWrist)
}

private fun DrawScope.drawBody(accent: Color, mood: String) {
    val dark = Color(0xFF111827)
    val metal = Color(0xFF29334B)
    val armor = Path().apply {
        moveTo(360f, 700f)
        cubicTo(453f, 667f, 547f, 667f, 640f, 700f)
        lineTo(700f, 920f)
        cubicTo(687f, 1000f, 653f, 1067f, 600f, 1120f)
        cubicTo(533f, 1147f, 467f, 1147f, 400f, 1120f)
        cubicTo(347f, 1067f, 307f, 1000f, 300f, 920f)
        close()
    }
    drawPath(armor, Color(0xFF080B14))
    drawPath(
        armor,
        Brush.linearGradient(listOf(Color(0xFF46516D), dark, Color(0xFF202941)), Offset(290f, 700f), Offset(720f, 1120f))
    )
    drawPath(armor, Color(0xFF66728F).copy(alpha = 0.75f), style = Stroke(width = 9f))
    val chest = Path().apply {
        moveTo(375f, 765f)
        cubicTo(458f, 745f, 542f, 745f, 625f, 765f)
        lineTo(585f, 920f)
        cubicTo(528f, 957f, 472f, 957f, 415f, 920f)
        close()
    }
    drawPath(chest, Brush.linearGradient(listOf(Color(0xFF26334C), Color(0xFF0D1321)), Offset(370f, 750f), Offset(630f, 950f)))
    drawPath(chest, metal.copy(alpha = 0.8f), style = Stroke(width = 5f))
    drawLine(accent, Offset(405f, 810f), Offset(468f, 835f), 8f, StrokeCap.Round)
    drawLine(accent, Offset(595f, 810f), Offset(532f, 835f), 8f, StrokeCap.Round)
    drawLine(accent.copy(alpha = 0.9f), Offset(500f, 855f), Offset(500f, 1030f), 9f, StrokeCap.Round)
    drawCircle(accent, 15f, Offset(500f, 880f))
    drawCircle(Color.White.copy(alpha = 0.9f), 5f, Offset(500f, 880f))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF171D2C), Color(0xFF56617A), Color(0xFF171D2C))),
        Offset(386f, 1040f), Size(228f, 63f), androidx.compose.ui.geometry.CornerRadius(23f)
    )
    drawLine(accent, Offset(410f, 1072f), Offset(590f, 1072f), 5f, StrokeCap.Round)
    drawLine(accent.copy(alpha = if (mood == "angry") 1f else 0.4f), Offset(438f, 1100f), Offset(562f, 1100f), 4f, StrokeCap.Round)
    drawRoundRect(dark, Offset(458f, 588f), Size(84f, 151f), androidx.compose.ui.geometry.CornerRadius(28f))
    drawRoundRect(metal, Offset(468f, 610f), Size(64f, 88f), androidx.compose.ui.geometry.CornerRadius(22f))
}

private fun DrawScope.drawFace(
    accent: Color,
    mood: String,
    speaking: Boolean,
    mouth: Float,
    blinkCycle: Float,
    phase: Float
) {
    val skin = Color(0xFFF0CFC8)
    val skinShade = Color(0xFFBB8F91)
    val hair = Color(0xFF171323)
    val face = Path().apply {
        moveTo(355f, 278f)
        cubicTo(365f, 170f, 413f, 115f, 500f, 112f)
        cubicTo(590f, 116f, 643f, 227f, 646f, 280f)
        lineTo(623f, 437f)
        cubicTo(611f, 522f, 570f, 588f, 500f, 597f)
        cubicTo(429f, 576f, 388f, 480f, 376f, 437f)
        close()
    }
    drawOval(hair, Offset(332f, 72f), Size(336f, 410f))
    drawPath(face, Brush.linearGradient(listOf(Color(0xFFFFE6DB), skin, skinShade), Offset(355f, 160f), Offset(645f, 555f)))
    drawPath(face, skinShade.copy(alpha = 0.55f), style = Stroke(width = 7f))

    val fringe = Path().apply {
        moveTo(350f, 300f)
        cubicTo(331f, 177f, 427f, 103f, 480f, 78f)
        cubicTo(582f, 78f, 642f, 127f, 662f, 226f)
        cubicTo(636f, 206f, 614f, 188f, 596f, 174f)
        cubicTo(577f, 218f, 552f, 247f, 521f, 260f)
        cubicTo(490f, 221f, 463f, 221f, 438f, 259f)
        cubicTo(410f, 284f, 381f, 297f, 350f, 300f)
        close()
    }
    drawPath(fringe, Brush.linearGradient(listOf(Color(0xFF393047), hair, Color(0xFF0C1120)), Offset(340f, 90f), Offset(640f, 300f)))
    drawLine(accent.copy(alpha = 0.85f), Offset(379f, 186f), Offset(349f, 376f), 16f, StrokeCap.Round)
    drawLine(Color(0xFF8C5DDA), Offset(620f, 198f), Offset(594f, 286f), 8f, StrokeCap.Round)

    drawLine(Color(0xFF443449), Offset(394f, 328f), Offset(471f, 316f), 13f, StrokeCap.Round)
    drawLine(Color(0xFF443449), Offset(529f, 316f), Offset(606f, 328f), 13f, StrokeCap.Round)
    val blink = if (blinkCycle > 0.955f) ((blinkCycle - 0.955f) / 0.045f).coerceIn(0f, 1f) else 0f
    drawEye(Offset(430f, 357f), accent, blink)
    drawEye(Offset(570f, 357f), accent, blink)
    drawLine(skinShade.copy(alpha = 0.7f), Offset(500f, 360f), Offset(486f, 421f), 7f, StrokeCap.Round)
    drawCircle(Color(0xFFE5AAA5), 7f, Offset(483f, 426f))

    drawOval(Color(0xFFDE9C9C).copy(alpha = 0.26f), Offset(386f, 404f), Size(60f, 26f))
    drawOval(Color(0xFFDE9C9C).copy(alpha = 0.26f), Offset(554f, 404f), Size(60f, 26f))
    drawLine(accent.copy(alpha = 0.85f), Offset(363f, 352f), Offset(338f, 375f), 8f, StrokeCap.Round)

    val mouthY = 476f
    val openHeight = if (speaking) 8f + mouth * 31f else 4f
    drawOval(Color(0xFF682F3C), Offset(469f, mouthY - openHeight / 2f), Size(62f, openHeight.coerceAtLeast(4f)))
    if (speaking && openHeight > 18f) {
        drawOval(Color(0xFFFFE9DF), Offset(481f, mouthY - openHeight * 0.36f), Size(38f, 6f))
    }
    val smile = Path().apply {
        val curve = when (mood) {
            "sad" -> -12f
            "angry" -> -7f
            "happy", "playful", "caring", "laugh" -> 15f
            else -> 3f + absSin(phase) * 3f
        }
        moveTo(470f, mouthY)
        cubicTo(490f, mouthY + curve * 0.67f, 510f, mouthY + curve * 0.67f, 530f, mouthY)
    }
    if (!speaking || openHeight < 16f) {
        drawPath(smile, skinShade, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEye(center: Offset, accent: Color, blink: Float) {
    val eyeSize = 66f
    if (blink > 0.15f) {
        drawLine(
            Color(0xFF8E6568),
            Offset(center.x - 27f, center.y),
            Offset(center.x + 27f, center.y),
            7f + blink * 10f,
            StrokeCap.Round
        )
        return
    }
    drawOval(Color(0xFFFFF8F1), Offset(center.x - eyeSize / 2f, center.y - 18f), Size(eyeSize, 38f))
    drawCircle(Color(0xFF3B3168), 18f, Offset(center.x, center.y + 1f))
    drawCircle(accent, 12f, Offset(center.x, center.y + 1f))
    drawCircle(Color(0xFF161324), 7f, Offset(center.x, center.y + 1f))
    drawCircle(Color.White.copy(alpha = 0.95f), 4f, Offset(center.x + 5f, center.y - 5f))
    drawLine(Color(0xFF292137), Offset(center.x - 32f, center.y - 18f), Offset(center.x + 32f, center.y - 18f), 6f, StrokeCap.Round)
}
