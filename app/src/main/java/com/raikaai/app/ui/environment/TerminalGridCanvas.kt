package com.raikaai.app.ui.environment

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val DefaultFg = Color(0xFFE6E6E6)
private val DefaultBg = Color.Black
private val CursorColor = Color(0xFF98C379)

/**
 * Draws a [VtGrid] cell by cell. Every run starts at its own column, so a
 * glyph from a fallback font (box drawing, symbols) can never push the rest
 * of the row out of line. Block elements and the common box-drawing lines
 * are drawn as shapes so logos and borders join seamlessly.
 *
 * [version] must change whenever the grid changes; it is read while drawing
 * so the canvas redraws without recomposing the screen.
 */
@Composable
fun TerminalGridCanvas(
    grid: VtGrid,
    version: () -> Int,
    style: TextStyle,
    cellWidth: Float,
    cellHeight: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 2048)
    Canvas(modifier) {
        version()
        val rows = grid.rows
        val cols = grid.cols
        val cp = grid.cp
        val fg = grid.fg
        val bg = grid.bg
        val fl = grid.fl
        val baseStyle = style.copy(letterSpacing = 0.sp)
        // Programs paint their own background (opencode's theme is not pure
        // black): the margin around the grid takes the top-left cell's color
        // so there is no black frame, and the grid is centered in the area.
        if (cp.isNotEmpty()) {
            val edge = cellBg(bg[0], fg[0], fl[0])
            if (edge != DefaultBg) drawRect(edge)
        }
        val originX = ((size.width - cols * cellWidth) / 2f).coerceAtLeast(0f)
        val originY = ((size.height - rows * cellHeight) / 2f).coerceAtLeast(0f)
        drawContext.transform.translate(originX, originY)

        for (r in 0 until rows) {
            val y = r * cellHeight
            // Backgrounds first, merged into runs.
            var c = 0
            while (c < cols) {
                val i = grid.cellIndex(r, c)
                val b = cellBg(bg[i], fg[i], fl[i])
                var end = c + 1
                while (end < cols) {
                    val j = grid.cellIndex(r, end)
                    if (cellBg(bg[j], fg[j], fl[j]) != b) break
                    end++
                }
                if (b != DefaultBg) {
                    drawRect(b, Offset(c * cellWidth, y), Size((end - c) * cellWidth + 0.5f, cellHeight + 0.5f))
                }
                c = end
            }
            // Glyphs.
            c = 0
            while (c < cols) {
                val i = grid.cellIndex(r, c)
                val code = cp[i]
                val flags = fl[i]
                if (code == 0 || flags and VtGrid.HIDDEN != 0 || (code == ' '.code && flags and (VtGrid.UNDERLINE or VtGrid.STRIKE) == 0)) {
                    c++
                    continue
                }
                val color = cellFg(fg[i], bg[i], flags)
                val x = c * cellWidth
                if (drawShape(code, x, y, cellWidth, cellHeight, color)) {
                    c++
                    continue
                }
                if (code in 0x20..0x7E) {
                    // ASCII run with one style: monospace widths match the grid.
                    val sb = StringBuilder()
                    var end = c
                    while (end < cols) {
                        val j = grid.cellIndex(r, end)
                        val cj = cp[j]
                        if (cj !in 0x20..0x7E || fl[j] != flags || fg[j] != fg[i] || bg[j] != bg[i]) break
                        sb.append(cj.toChar())
                        end++
                    }
                    drawGlyphs(measurer, sb.toString().trimEnd(), x, y, baseStyle, color, flags)
                    c = end
                } else {
                    drawGlyphs(measurer, String(Character.toChars(code)), x, y, baseStyle, color, flags)
                    c++
                }
            }
        }

        if (grid.cursorVisible) {
            val x = grid.cursorCol * cellWidth
            val y = grid.cursorRow * cellHeight
            drawRect(CursorColor.copy(alpha = 0.55f), Offset(x, y), Size(cellWidth, cellHeight))
        }
        drawContext.transform.translate(-originX, -originY)
    }
}

private fun DrawScope.drawGlyphs(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    base: TextStyle,
    color: Color,
    flags: Int,
) {
    if (text.isEmpty()) return
    val deco =
        when {
            flags and VtGrid.UNDERLINE != 0 && flags and VtGrid.STRIKE != 0 ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            flags and VtGrid.UNDERLINE != 0 -> TextDecoration.Underline
            flags and VtGrid.STRIKE != 0 -> TextDecoration.LineThrough
            else -> null
        }
    // The measurer's cache ignores color (it does not change layout), so a
    // cached layout keeps the color it was first measured with. Programs
    // that fade text (opencode on resize) then stayed at the faded color.
    // Measure without color and pass the color when drawing.
    val layout =
        measurer.measure(
            text = text,
            style =
                base.copy(
                    color = Color.Unspecified,
                    fontWeight = if (flags and VtGrid.BOLD != 0) FontWeight.Bold else null,
                    fontStyle = if (flags and VtGrid.ITALIC != 0) FontStyle.Italic else null,
                    textDecoration = deco,
                ),
            softWrap = false,
        )
    drawText(layout, color = color, topLeft = Offset(x, y))
}

private fun colorOf(v: Int, def: Color): Color =
    when {
        v == VtGrid.DEFAULT_COLOR -> def
        v and VtGrid.TRUECOLOR != 0 -> Color(0xFF000000.toInt() or (v and 0xFFFFFF))
        else -> AnsiScreen.palette(v.coerceIn(0, 255))
    }

private fun cellBg(bg: Int, fg: Int, flags: Int): Color =
    if (flags and VtGrid.INVERSE != 0) colorOf(fg, DefaultFg) else colorOf(bg, DefaultBg)

private fun cellFg(fg: Int, bg: Int, flags: Int): Color {
    val c = if (flags and VtGrid.INVERSE != 0) colorOf(bg, DefaultBg) else colorOf(fg, DefaultFg)
    return if (flags and VtGrid.DIM != 0) c.copy(alpha = 0.6f) else c
}

/** Draws block elements and simple box-drawing lines; false = use the font. */
private fun DrawScope.drawShape(
    code: Int,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    color: Color,
): Boolean {
    fun rect(fx: Float, fy: Float, fw: Float, fh: Float, alpha: Float = 1f) =
        drawRect(color.copy(alpha = color.alpha * alpha), Offset(x + fx * w, y + fy * h), Size(fw * w + 0.5f, fh * h + 0.5f))
    when (code) {
        0x2588 -> rect(0f, 0f, 1f, 1f)
        0x2580 -> rect(0f, 0f, 1f, 0.5f)
        0x2584 -> rect(0f, 0.5f, 1f, 0.5f)
        0x258C -> rect(0f, 0f, 0.5f, 1f)
        0x2590 -> rect(0.5f, 0f, 0.5f, 1f)
        in 0x2581..0x2587 -> { val f = (code - 0x2580) / 8f; rect(0f, 1f - f, 1f, f) }
        in 0x2589..0x258F -> { val f = (0x2590 - code) / 8f; rect(0f, 0f, f, 1f) }
        0x2591 -> rect(0f, 0f, 1f, 1f, 0.25f)
        0x2592 -> rect(0f, 0f, 1f, 1f, 0.5f)
        0x2593 -> rect(0f, 0f, 1f, 1f, 0.75f)
        0x2594 -> rect(0f, 0f, 1f, 0.125f)
        0x2595 -> rect(0.875f, 0f, 0.125f, 1f)
        in 0x2596..0x259F -> {
            // Quadrants: bit 1 upper-left, 2 upper-right, 4 lower-left, 8 lower-right.
            val mask = intArrayOf(4, 8, 1, 13, 9, 7, 11, 2, 6, 14)[code - 0x2596]
            if (mask and 1 != 0) rect(0f, 0f, 0.5f, 0.5f)
            if (mask and 2 != 0) rect(0.5f, 0f, 0.5f, 0.5f)
            if (mask and 4 != 0) rect(0f, 0.5f, 0.5f, 0.5f)
            if (mask and 8 != 0) rect(0.5f, 0.5f, 0.5f, 0.5f)
        }
        else -> {
            val arms = BOX_ARMS[code] ?: return false
            val heavy = arms and HEAVY != 0
            val t = maxOf(1f, w * (if (heavy) 0.22f else 0.11f))
            val cx = x + w / 2f
            val cy = y + h / 2f
            if (arms and L != 0) drawRect(color, Offset(x, cy - t / 2), Size(w / 2 + t / 2, t))
            if (arms and R != 0) drawRect(color, Offset(cx - t / 2, cy - t / 2), Size(w / 2 + t / 2 + 0.5f, t))
            if (arms and U != 0) drawRect(color, Offset(cx - t / 2, y), Size(t, h / 2 + t / 2))
            if (arms and D != 0) drawRect(color, Offset(cx - t / 2, cy - t / 2), Size(t, h / 2 + t / 2 + 0.5f))
        }
    }
    return true
}

private const val L = 1
private const val R = 2
private const val U = 4
private const val D = 8
private const val HEAVY = 16

/** Arms of the box-drawing lines that are drawn as shapes (light and heavy). */
private val BOX_ARMS: Map<Int, Int> =
    mapOf(
        0x2500 to (L or R), 0x2501 to (L or R or HEAVY),
        0x2502 to (U or D), 0x2503 to (U or D or HEAVY),
        0x250C to (R or D), 0x250F to (R or D or HEAVY),
        0x2510 to (L or D), 0x2513 to (L or D or HEAVY),
        0x2514 to (U or R), 0x2517 to (U or R or HEAVY),
        0x2518 to (U or L), 0x251B to (U or L or HEAVY),
        0x251C to (U or D or R), 0x2523 to (U or D or R or HEAVY),
        0x2524 to (U or D or L), 0x252B to (U or D or L or HEAVY),
        0x252C to (L or R or D), 0x2533 to (L or R or D or HEAVY),
        0x2534 to (L or R or U), 0x253B to (L or R or U or HEAVY),
        0x253C to (L or R or U or D), 0x254B to (L or R or U or D or HEAVY),
        0x256D to (R or D), 0x256E to (L or D), 0x256F to (U or L), 0x2570 to (U or R),
        0x2574 to L, 0x2575 to U, 0x2576 to R, 0x2577 to D,
        0x2578 to (L or HEAVY), 0x2579 to (U or HEAVY), 0x257A to (R or HEAVY), 0x257B to (D or HEAVY),
    )
