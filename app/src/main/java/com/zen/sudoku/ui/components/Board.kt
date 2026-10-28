package com.zen.sudoku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zen.sudoku.data.Game
import com.zen.sudoku.data.Settings
import com.zen.sudoku.engine.Sudoku
import kotlin.math.PI
import kotlin.math.sin

/** Cells that should be flagged red: wrong vs. the solution, or conflicting with a peer. */
fun errorCells(game: Game, autoCheck: Boolean): BooleanArray = BooleanArray(81) { i ->
    val v = game.values[i]
    v != 0 && game.givens[i] == 0 &&
        if (autoCheck) v != game.solution[i] else Sudoku.PEERS[i].any { game.values[it] == v }
}

@Composable
fun SudokuBoard(
    game: Game,
    selected: Int,
    settings: Settings,
    hidden: Boolean,
    celebrate: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    val errors = remember(game.values, settings.autoCheck) { errorCells(game, settings.autoCheck) }

    val wave = remember { Animatable(0f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(1400, easing = LinearEasing))
        } else {
            wave.snapTo(0f)
        }
    }

    val shape = RoundedCornerShape(14.dp)
    val thick = cs.onSurface.copy(alpha = 0.72f)
    val thin = cs.onSurface.copy(alpha = 0.14f)
    val selectedBg = cs.primary.copy(alpha = 0.30f).compositeOver(cs.surface)
    val sameBg = cs.primary.copy(alpha = 0.16f).compositeOver(cs.surface)
    val regionBg = cs.onSurface.copy(alpha = 0.05f).compositeOver(cs.surface)
    val errorBg = cs.error.copy(alpha = 0.14f).compositeOver(cs.surface)
    val noteHl = cs.primary.copy(alpha = 0.18f)

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val cellPx = with(density) { maxWidth.toPx() } / 9f
        val big = remember(cellPx, density) { digitLayouts(measurer, cellPx * 0.58f, FontWeight.Medium, density) }
        val user = remember(cellPx, density) { digitLayouts(measurer, cellPx * 0.58f, FontWeight.Normal, density) }
        val small = remember(cellPx, density) { digitLayouts(measurer, cellPx * 0.25f, FontWeight.Medium, density) }

        Canvas(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(cs.surface)
                .border(1.6.dp, thick, shape)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val c = (o.x / (size.width / 9f)).toInt().coerceIn(0, 8)
                        val r = (o.y / (size.height / 9f)).toInt().coerceIn(0, 8)
                        select(r * 9 + c)
                    }
                },
        ) {
            val cell = size.width / 9f
            val sel = selected
            val selVal = if (sel >= 0 && !hidden) game.values[sel] else 0

            // Cell backgrounds
            for (i in 0 until 81) {
                val r = i / 9
                val c = i % 9
                val bg: Color? = when {
                    hidden -> null
                    i == sel -> selectedBg
                    errors[i] -> errorBg
                    settings.highlightSame && selVal != 0 && game.values[i] == selVal -> sameBg
                    settings.highlightRegion && sel >= 0 &&
                        (r == sel / 9 || c == sel % 9 || Sudoku.box(i) == Sudoku.box(sel)) -> regionBg
                    else -> null
                }
                if (bg != null) drawRect(bg, Offset(c * cell, r * cell), Size(cell, cell))
            }

            // Victory wave sweeping from the top-left corner
            val w = wave.value
            if (w > 0f && w < 1f) {
                for (i in 0 until 81) {
                    val r = i / 9
                    val c = i % 9
                    val t = (w - (r + c) / 16f * 0.6f) / 0.4f
                    if (t in 0f..1f) {
                        val a = sin(PI * t).toFloat() * 0.45f
                        drawRect(cs.primary.copy(alpha = a), Offset(c * cell, r * cell), Size(cell, cell))
                    }
                }
            }

            // Grid lines
            val thinW = 1.dp.toPx()
            val thickW = 1.6.dp.toPx()
            for (k in 1..8) {
                val p = k * cell
                val (color, width) = if (k % 3 == 0) thick to thickW else thin to thinW
                drawLine(color, Offset(p, 0f), Offset(p, size.height), width)
                drawLine(color, Offset(0f, p), Offset(size.width, p), width)
            }

            if (hidden) return@Canvas

            // Digits and notes
            for (i in 0 until 81) {
                val r = i / 9
                val c = i % 9
                val v = game.values[i]
                if (v != 0) {
                    val given = game.givens[i] != 0
                    val layout = if (given) big[v] else user[v]
                    val color = when {
                        given -> cs.onSurface
                        errors[i] -> cs.error
                        else -> cs.primary
                    }
                    drawText(
                        layout, color,
                        Offset(
                            c * cell + (cell - layout.size.width) / 2f,
                            r * cell + (cell - layout.size.height) / 2f,
                        ),
                    )
                } else if (game.notes[i] != 0) {
                    val sub = cell / 3f
                    for (d in 1..9) {
                        if (game.notes[i] and (1 shl d) == 0) continue
                        val x = c * cell + (d - 1) % 3 * sub
                        val y = r * cell + (d - 1) / 3 * sub
                        val hl = settings.highlightSame && d == selVal
                        if (hl) drawCircle(noteHl, sub * 0.42f, Offset(x + sub / 2f, y + sub / 2f))
                        val layout = small[d]
                        drawText(
                            layout,
                            if (hl) cs.primary else cs.onSurfaceVariant,
                            Offset(x + (sub - layout.size.width) / 2f, y + (sub - layout.size.height) / 2f),
                        )
                    }
                }
            }
        }
    }
}

private fun digitLayouts(
    measurer: androidx.compose.ui.text.TextMeasurer,
    px: Float,
    weight: FontWeight,
    density: androidx.compose.ui.unit.Density,
): Array<TextLayoutResult> {
    val style = TextStyle(fontSize = with(density) { px.toSp() }, fontWeight = weight)
    return Array(10) { measurer.measure(it.toString(), style) }
}
