package com.raikaai.app.ui.environment

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * A small VT/ANSI screen for one command's output.
 *
 * The sandbox has no PTY, so programs write raw bytes: carriage returns for
 * progress bars, cursor moves (fastfetch puts its info next to the logo with
 * cursor-up/right), erase sequences, and SGR colors. Showing that text line
 * by line scrambled the layout and leaked no colors. This screen interprets
 * the common subset and exposes styled rows.
 *
 * Rows are relative to the start of the command's output (row 0). Newline
 * acts as CR+LF because piped output has no tty line discipline.
 */
class AnsiScreen(
    cols: Int,
    private val maxRows: Int = 1500,
) {
    var cols: Int = cols.coerceIn(20, 400)
        private set

    private class Row(width: Int) {
        var chars = CharArray(width) { ' ' }
        var attrs = IntArray(width) { DEFAULT_ATTR }
        var len = 0

        fun ensure(width: Int) {
            if (chars.size >= width) return
            val oldSize = chars.size
            chars = chars.copyOf(width).also { for (i in oldSize until width) it[i] = ' ' }
            attrs = attrs.copyOf(width).also { for (i in oldSize until width) it[i] = DEFAULT_ATTR }
        }
    }

    private val rows = ArrayList<Row>()
    private var r = 0
    private var c = 0
    private var savedR = 0
    private var savedC = 0
    private var attr = DEFAULT_ATTR

    /** Lowest row changed since [takeDirtyFrom]; Int.MAX_VALUE when clean. */
    private var dirtyFrom = Int.MAX_VALUE

    /** Rows dropped from the top since the last sync (scrollback cap). */
    var droppedSinceSync = 0
        private set

    /** Set when the program cleared the screen (e.g. `clear`). */
    var clearRequested = false

    // Parser state
    private var state = State.NORMAL
    private val params = StringBuilder()
    private val strBuf = StringBuilder()

    private enum class State { NORMAL, ESC, CSI, OSC, OSC_ESC, CHARSET, STRING, STRING_ESC }

    /**
     * PTY mode: replies to terminal queries (cursor position, device
     * attributes, colors). Null in pipe mode, where nothing reads them.
     */
    var respond: ((String) -> Unit)? = null

    /** PTY mode: hand the alternate screen (CSI ?1049h) to a [VtGrid]. */
    var altScreenEnabled = false

    /** Set when a program entered the alternate screen; [feed] stops right after it. */
    var altRequested = false

    /** Screen height reported to cursor-position queries. */
    var screenRows = 24

    val rowCount: Int get() = rows.size

    /** Cursor position (row index in this screen, column). */
    val cursorRow: Int get() = r
    val cursorCol: Int get() = c

    /**
     * Feeds output text. [isErr] tints default-colored text as an error.
     * Returns how many chars were consumed: fewer than [text] only when the
     * program switched to the alternate screen ([altRequested]).
     */
    fun feed(text: String, isErr: Boolean = false): Int {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            i++
            when (state) {
                State.NORMAL -> normal(ch, isErr)
                State.ESC -> escape(ch)
                State.CSI -> csi(ch)
                State.OSC -> when (ch) {
                    '\u0007' -> { osc(strBuf.toString()); state = State.NORMAL }
                    '\u001B' -> state = State.OSC_ESC
                    else -> if (strBuf.length < 512) strBuf.append(ch)
                }
                State.OSC_ESC -> { osc(strBuf.toString()); state = State.NORMAL }
                State.CHARSET -> state = State.NORMAL
                // DCS / APC / PM / SOS strings (capability queries, kitty
                // graphics): skip the payload instead of printing it.
                State.STRING -> when (ch) {
                    '\u0007' -> { dcs(strBuf.toString()); state = State.NORMAL }
                    '\u001B' -> state = State.STRING_ESC
                    else -> if (strBuf.length < 4096) strBuf.append(ch)
                }
                State.STRING_ESC -> { dcs(strBuf.toString()); state = State.NORMAL }
            }
            if (altRequested) return i
        }
        return text.length
    }

    /** Bracketed paste requested by the program (CSI ?2004h), e.g. an interactive shell. */
    var bracketedPaste = false

    /** Exit code from the wrapper's "command finished" marker (OSC 9998), PTY mode. */
    var doneCode: Int? = null

    private fun osc(s: String) {
        if (s.startsWith("9998;")) {
            doneCode = s.removePrefix("9998;").trim().toIntOrNull() ?: 0
            return
        }
        val reply = respond ?: return
        when (s) {
            "10;?" -> reply("\u001B]10;rgb:e6e6/e6e6/e6e6\u001B\\")
            "11;?" -> reply("\u001B]11;rgb:0000/0000/0000\u001B\\")
        }
    }

    private fun dcs(s: String) {
        if (s.startsWith("P+q")) respond?.invoke("\u001BP0+r\u001B\\")
    }

    private fun normal(ch: Char, isErr: Boolean) {
        when (ch) {
            '\u001B' -> state = State.ESC
            '\n' -> { r++; c = 0 }
            '\r' -> c = 0
            '\b' -> if (c > 0) c--
            '\t' -> c = minOf(cols - 1, (c / 8 + 1) * 8)
            '\u0007', '\u0000', '\u000E', '\u000F' -> Unit
            else -> {
                if (ch < ' ') return
                if (c >= cols) { r++; c = 0 }
                val row = row(r)
                row.ensure(cols)
                row.chars[c] = ch
                row.attrs[c] = if (isErr && fgOf(attr) == FG_DEFAULT) withFg(attr, FG_ERR) else attr
                if (c + 1 > row.len) row.len = c + 1
                c++
                markDirty(r)
            }
        }
    }

    private fun escape(ch: Char) {
        state = State.NORMAL
        when (ch) {
            '[' -> { params.setLength(0); state = State.CSI }
            ']' -> { strBuf.setLength(0); state = State.OSC }
            'P', '_', '^', 'X' -> { strBuf.setLength(0); strBuf.append(ch); state = State.STRING }
            '(', ')', '*', '+' -> state = State.CHARSET
            '7' -> { savedR = r; savedC = c }
            '8' -> { r = savedR; c = savedC }
            'c' -> clearAll()
            'M' -> if (r > 0) r-- // reverse index
            'D' -> r++
            'E' -> { r++; c = 0 }
            else -> Unit
        }
    }

    private fun csi(ch: Char) {
        if (ch in '0'..'9' || ch == ';' || ch == '?' || ch == ':' || ch == '>' || ch == '=' || ch == '<' ||
            ch == ' ' || ch == '$' || ch == '!' || ch == '"'
        ) {
            if (params.length < 64) params.append(ch)
            return
        }
        state = State.NORMAL
        val raw = params.toString()
        val private = raw.startsWith("?")
        val nums = raw.trimStart('?', '>', '=', '<').filter { it.isDigit() || it == ';' || it == ':' }
            .split(';', ':').map { it.trim().toIntOrNull() }
        fun n(i: Int, def: Int = 1): Int = (nums.getOrNull(i) ?: def).let { if (it == 0 && def == 1) 1 else it }
        val reply = respond
        if (raw.contains('$')) {
            if (ch == 'p' && reply != null) {
                val mode = nums.getOrNull(0) ?: 0
                reply(if (private) "\u001B[?$mode;0\$y" else "\u001B[$mode;0\$y")
            }
            return
        }
        if (raw.startsWith(">")) {
            if (ch == 'c') reply?.invoke("\u001B[>1;10;0c")
            if (ch == 'q') reply?.invoke("\u001BP>|RaikaAI terminal\u001B\\")
            return
        }
        if (raw.startsWith("<") || raw.startsWith("=") || raw.contains('!') || raw.contains('"')) return
        if (private) {
            // Alternate screen goes to a VtGrid in PTY mode; other private
            // modes (cursor, bracketed paste, mouse) need no state here.
            if ((ch == 'h' || ch == 'l') && nums.any { it == 2004 }) bracketedPaste = ch == 'h'
            if (ch == 'h' && altScreenEnabled && nums.any { it == 1049 || it == 1047 || it == 47 }) altRequested = true
            if (ch == 'n' && nums.getOrNull(0) == 6) reply?.invoke("\u001B[?${minOf(r, screenRows - 1) + 1};${c + 1}R")
            return
        }
        when (ch) {
            'c' -> if ((nums.getOrNull(0) ?: 0) == 0) reply?.invoke("\u001B[?62;22c")
            'n' -> when (nums.getOrNull(0)) {
                5 -> reply?.invoke("\u001B[0n")
                6 -> reply?.invoke("\u001B[${minOf(r, screenRows - 1) + 1};${c + 1}R")
            }
            't' -> if (nums.getOrNull(0) == 18) reply?.invoke("\u001B[8;$screenRows;${cols}t")
        }
        when (ch) {
            'm' -> sgr(nums)
            'A' -> r = maxOf(0, r - n(0))
            'B', 'e' -> r += n(0)
            'C', 'a' -> c = minOf(cols - 1, c + n(0))
            'D' -> c = maxOf(0, c - n(0))
            'E' -> { r += n(0); c = 0 }
            'F' -> { r = maxOf(0, r - n(0)); c = 0 }
            'G', '`' -> c = (n(0) - 1).coerceIn(0, cols - 1)
            'd' -> r = maxOf(0, n(0) - 1)
            'H', 'f' -> { r = maxOf(0, n(0) - 1); c = (n(1) - 1).coerceIn(0, cols - 1) }
            'K' -> eraseLine(nums.getOrNull(0) ?: 0)
            'J' -> eraseScreen(nums.getOrNull(0) ?: 0)
            'X' -> { val row = row(r); row.ensure(cols); for (i in c until minOf(cols, c + n(0))) { row.chars[i] = ' '; row.attrs[i] = DEFAULT_ATTR }; markDirty(r) }
            'P' -> deleteChars(n(0))
            's' -> { savedR = r; savedC = c }
            'u' -> { r = savedR; c = savedC }
            else -> Unit
        }
    }

    private fun eraseLine(mode: Int) {
        val row = row(r)
        row.ensure(cols)
        val (from, to) = when (mode) {
            1 -> 0 to minOf(c + 1, cols)
            2 -> 0 to cols
            else -> c to cols
        }
        for (i in from until to) { row.chars[i] = ' '; row.attrs[i] = DEFAULT_ATTR }
        if (mode != 1 && row.len > c) row.len = if (mode == 2) 0 else c
        markDirty(r)
    }

    private fun eraseScreen(mode: Int) {
        when (mode) {
            2, 3 -> clearAll()
            1 -> { for (i in 0 until r) clearRow(i); eraseLine(1) }
            // Cursor at the origin + erase-below is how `clear` clears the screen.
            else -> if (r == 0 && c == 0) clearAll() else { eraseLine(0); for (i in r + 1 until rows.size) clearRow(i) }
        }
    }

    private fun deleteChars(count: Int) {
        val row = row(r)
        row.ensure(cols)
        for (i in c until cols) {
            val src = i + count
            row.chars[i] = if (src < cols) row.chars[src] else ' '
            row.attrs[i] = if (src < cols) row.attrs[src] else DEFAULT_ATTR
        }
        row.len = maxOf(c, row.len - count)
        markDirty(r)
    }

    private fun clearRow(i: Int) {
        val row = rows.getOrNull(i) ?: return
        row.chars.fill(' ')
        row.attrs.fill(DEFAULT_ATTR)
        row.len = 0
        markDirty(i)
    }

    private fun clearAll() {
        rows.clear()
        r = 0
        c = 0
        clearRequested = true
        dirtyFrom = 0
    }

    private fun row(i: Int): Row {
        while (rows.size <= i) {
            rows.add(Row(cols))
            markDirty(rows.size - 1)
        }
        if (rows.size > maxRows) {
            val drop = rows.size - maxRows
            repeat(drop) { rows.removeAt(0) }
            droppedSinceSync += drop
            r = maxOf(0, r - drop)
            savedR = maxOf(0, savedR - drop)
            dirtyFrom = maxOf(0, dirtyFrom - drop)
            return rows[minOf(i - drop, rows.size - 1).coerceAtLeast(0)]
        }
        return rows[i]
    }

    private fun markDirty(i: Int) {
        if (i < dirtyFrom) dirtyFrom = i
    }

    /** Returns the first row changed since the previous call and resets tracking. */
    fun takeDirtyFrom(): Int {
        val d = dirtyFrom
        dirtyFrom = Int.MAX_VALUE
        droppedSinceSync = 0
        return d
    }

    // ── SGR ──

    private fun sgr(nums: List<Int?>) {
        if (nums.isEmpty() || (nums.size == 1 && nums[0] == null)) { attr = DEFAULT_ATTR; return }
        var i = 0
        while (i < nums.size) {
            when (val p = nums[i] ?: 0) {
                0 -> attr = DEFAULT_ATTR
                1 -> attr = attr or BOLD
                2 -> attr = attr or DIM
                3 -> attr = attr or ITALIC
                4 -> attr = attr or UNDERLINE
                7 -> attr = attr or INVERSE
                9 -> attr = attr or STRIKE
                22 -> attr = attr and (BOLD or DIM).inv()
                23 -> attr = attr and ITALIC.inv()
                24 -> attr = attr and UNDERLINE.inv()
                27 -> attr = attr and INVERSE.inv()
                29 -> attr = attr and STRIKE.inv()
                in 30..37 -> attr = withFg(attr, p - 30)
                39 -> attr = withFg(attr, FG_DEFAULT)
                in 40..47 -> attr = withBg(attr, p - 40)
                49 -> attr = withBg(attr, BG_DEFAULT)
                in 90..97 -> attr = withFg(attr, p - 90 + 8)
                in 100..107 -> attr = withBg(attr, p - 100 + 8)
                38, 48 -> {
                    val mode = nums.getOrNull(i + 1)
                    val color =
                        when (mode) {
                            5 -> nums.getOrNull(i + 2)?.coerceIn(0, 255).also { i += 2 }
                            2 -> {
                                val rr = nums.getOrNull(i + 2) ?: 0
                                val gg = nums.getOrNull(i + 3) ?: 0
                                val bb = nums.getOrNull(i + 4) ?: 0
                                i += 4
                                rgbTo256(rr, gg, bb)
                            }
                            else -> null
                        }
                    if (color != null) attr = if (p == 38) withFg(attr, color) else withBg(attr, color)
                }
                else -> Unit
            }
            i++
        }
    }

    // ── Rendering ──

    /** Row [i] as styled text (trailing blanks trimmed). */
    fun annotatedRow(
        i: Int,
        defaultFg: Color,
        errFg: Color,
    ): AnnotatedString {
        val row = rows.getOrNull(i) ?: return AnnotatedString("")
        var end = row.len
        while (end > 0 && row.chars[end - 1] == ' ' && bgOf(row.attrs[end - 1]) == BG_DEFAULT) end--
        return buildAnnotatedString {
            var start = 0
            while (start < end) {
                val a = row.attrs[start]
                var stop = start + 1
                while (stop < end && row.attrs[stop] == a) stop++
                val style = spanFor(a, defaultFg, errFg)
                if (style == null) {
                    append(String(row.chars, start, stop - start))
                } else {
                    pushStyle(style)
                    append(String(row.chars, start, stop - start))
                    pop()
                }
                start = stop
            }
        }
    }

    /** Plain text of row [i] (for tests and copy). */
    fun plainRow(i: Int): String {
        val row = rows.getOrNull(i) ?: return ""
        return String(row.chars, 0, row.len).trimEnd()
    }

    private fun spanFor(
        a: Int,
        defaultFg: Color,
        errFg: Color,
    ): SpanStyle? {
        if (a == DEFAULT_ATTR) return null
        var fg = colorOf(fgOf(a), defaultFg, errFg, isBg = false)
        var bg = colorOf(bgOf(a), Color.Unspecified, errFg, isBg = true)
        if (a and INVERSE != 0) {
            val f = if (fg == Color.Unspecified) defaultFg else fg
            fg = if (bg == Color.Unspecified) Color.Black else bg
            bg = f
        }
        if (a and DIM != 0 && fg != Color.Unspecified) fg = fg.copy(alpha = 0.6f)
        val deco =
            when {
                a and UNDERLINE != 0 && a and STRIKE != 0 -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                a and UNDERLINE != 0 -> TextDecoration.Underline
                a and STRIKE != 0 -> TextDecoration.LineThrough
                else -> null
            }
        return SpanStyle(
            color = fg,
            background = bg,
            fontWeight = if (a and BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (a and ITALIC != 0) FontStyle.Italic else null,
            textDecoration = deco,
        )
    }

    companion object {
        private const val FG_DEFAULT = 256
        private const val FG_ERR = 257
        private const val BG_DEFAULT = 256
        private const val BOLD = 1 shl 18
        private const val DIM = 1 shl 19
        private const val ITALIC = 1 shl 20
        private const val UNDERLINE = 1 shl 21
        private const val INVERSE = 1 shl 22
        private const val STRIKE = 1 shl 23
        private const val DEFAULT_ATTR = FG_DEFAULT or (BG_DEFAULT shl 9)

        private fun fgOf(a: Int) = a and 0x1FF

        private fun bgOf(a: Int) = (a shr 9) and 0x1FF

        private fun withFg(a: Int, fg: Int) = (a and 0x1FF.inv()) or fg

        private fun withBg(a: Int, bg: Int) = (a and (0x1FF shl 9).inv()) or (bg shl 9)

        /** xterm's 16 base colors, tuned for a black background. */
        private val BASE16 =
            longArrayOf(
                0xFF2E3436, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFDCDFE4,
                0xFF5C6370, 0xFFFF7A85, 0xFFB5E890, 0xFFFFD68A, 0xFF7CC4FF, 0xFFDDA0F0, 0xFF7FD6E0, 0xFFFFFFFF,
            )

        fun palette(index: Int): Color {
            if (index < 16) return Color(BASE16[index])
            if (index < 232) {
                val i = index - 16
                val steps = intArrayOf(0, 95, 135, 175, 215, 255)
                return Color(steps[i / 36], steps[(i / 6) % 6], steps[i % 6])
            }
            val v = 8 + (index - 232) * 10
            return Color(v, v, v)
        }

        private fun colorOf(
            v: Int,
            def: Color,
            errFg: Color,
            isBg: Boolean,
        ): Color =
            when {
                v == 256 -> def
                v == 257 && !isBg -> errFg
                v in 0..255 -> palette(v)
                else -> def
            }

        private fun rgbTo256(r: Int, g: Int, b: Int): Int {
            fun q(x: Int) = when { x < 48 -> 0; x < 115 -> 1; else -> (x - 35) / 40 }.coerceIn(0, 5)
            return 16 + 36 * q(r) + 6 * q(g) + q(b)
        }
    }
}
