package com.autocam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autocam.app.R
import com.autocam.engine.CompositionGuide
import com.autocam.engine.OverlayPrimitive

/**
 * Draws overlay[] only. No hit testing. Coordinates: origin top-left, already
 * compensated for ViewfinderFrame.rotationDeg.
 */
@Composable
fun GuideOverlay(
    guide: CompositionGuide?,
    modifier: Modifier = Modifier,
) {
    if (guide == null) return
    val hint = guide.overlay.firstOrNull { it.type == "hint_text" }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("guide_overlay")) {
            val w = size.width
            val h = size.height
            val ordered = guide.overlay.sortedBy { zIndex(it.type) }
            for (prim in ordered) {
                drawPrimitive(prim, w, h)
            }
        }
        if (hint != null) {
            Text(
                text = overlayHint(hint.key),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
        }
    }
}

@Composable
private fun overlayHint(key: String?): String {
    return when (key) {
        "guide.move_subject_to_reticle" -> stringResource(R.string.guide_move_subject_to_reticle)
        else -> key.orEmpty()
    }
}

private fun zIndex(type: String): Int {
    return when (type) {
        "grid_thirds" -> 0
        "subject_box" -> 1
        "pan_arrow" -> 2
        "target_reticle" -> 3
        "horizon_line" -> 4
        "hint_text" -> 5
        else -> 6
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrimitive(
    prim: OverlayPrimitive,
    w: Float,
    h: Float,
) {
    val white = Color.White.copy(alpha = (prim.opacity ?: 0.9).toFloat())
    when (prim.type) {
        "grid_thirds" -> {
            val alpha = (prim.opacity ?: 0.35).toFloat()
            val color = Color.White.copy(alpha = alpha)
            drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 2f)
            drawLine(color, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 2f)
            drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 2f)
            drawLine(color, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 2f)
        }
        "subject_box" -> {
            val box = prim.box ?: return
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(box.x0.toFloat() * w, box.y0.toFloat() * h),
                size = Size(
                    (box.x1 - box.x0).toFloat() * w,
                    (box.y1 - box.y0).toFloat() * h,
                ),
                style = Stroke(width = 3f),
            )
        }
        "target_reticle" -> {
            val nx = prim.nx ?: return
            val ny = prim.ny ?: return
            val c = Offset(nx.toFloat() * w, ny.toFloat() * h)
            drawCircle(Color.Cyan, radius = 18f, center = c, style = Stroke(width = 3f))
            drawLine(Color.Cyan, Offset(c.x - 28f, c.y), Offset(c.x + 28f, c.y), 3f)
            drawLine(Color.Cyan, Offset(c.x, c.y - 28f), Offset(c.x, c.y + 28f), 3f)
        }
        "pan_arrow" -> {
            val dx = prim.dx ?: return
            val dy = prim.dy ?: return
            val startX = (0.5 - dx / 2.0).toFloat() * w
            val startY = (0.5 - dy / 2.0).toFloat() * h
            val endX = startX + dx.toFloat() * w
            val endY = startY + dy.toFloat() * h
            drawLine(Color(0xFFFF9800), Offset(startX, startY), Offset(endX, endY), 6f)
        }
        "horizon_line" -> {
            val angle = (prim.angleDeg ?: 0.0).toFloat()
            val alpha = (prim.opacity ?: 0.2).toFloat()
            rotate(angle, Offset(w / 2f, h / 2f)) {
                drawLine(
                    Color.White.copy(alpha = alpha),
                    Offset(0f, h / 2f),
                    Offset(w, h / 2f),
                    3f,
                )
            }
        }
        else -> Unit
    }
}
