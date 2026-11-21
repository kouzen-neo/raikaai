package com.raikaai.app.ui.environment

/**
 * Fixed-size VT screen for full-screen programs (opencode, vim, htop) in PTY
 * mode. [AnsiScreen] keeps the scrolling line stream; when a program switches
 * to the alternate screen (CSI ?1049h) its output is fed here until it
 * switches back.
 *
 * Covers what TUIs commonly use: absolute cursor addressing, scroll regions,
 * insert/delete lines and characters, deferred wrap at the last column,
 * background-color erase, 16/256/truecolor SGR, DEC line-drawing charset,
 * wide characters, and replies to terminal queries (cursor position, device
 * attributes, window size, default colors) through [respond].
 */
class VtGrid(
    rows: Int,
    cols: Int,
    private val respond: (String) -> Unit,
) {
    var rows = rows.coerceIn(2, 300)
        private set
    var cols = cols.coerceIn(10, 400)
        private set

    /** Cell size in pixels, for window-size reports (CSI 14t / 16t). */
    var cellWidthPx = 8
    var cellHeightPx = 16

    // Cells: code point (0 = right half of a wide char), colors, flags.
    var cp = IntArray(this.rows * this.cols) { ' '.code }
        private set
    var fg = IntArray(this.rows * this.cols) { DEFAULT_COLOR }
        private set
    var bg = IntArray(this.rows * this.cols) { DEFAULT_COLOR }
        private set
    var fl = IntArray(this.rows * this.cols)
        private set

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    /** Mouse reporting the program asked for: 0 off, 1000 clicks, 1002 drags, 1003 all motion. */
    var mouseTracking = 0
        private set

    /** Bracketed paste (CSI ?2004h): pasted text is wrapped in ESC[200~ … ESC[201~. */
    var bracketedPaste = false
        private set

    /** SGR mouse encoding (CSI ?1006h), used by modern TUIs. */
    var mouseSgr = false
        private set

    /** Receives text a program copies with OSC 52 (opencode's selection). */
    var onClipboard: ((String) -> Unit)? = null

    /** Set when the program left the alternate screen (CSI ?1049l). */
    var exitRequested = false
        private set

    private var curFg = DEFAULT_COLOR
    private var curBg = DEFAULT_COLOR
    private var curFl = 0
    private var wrapPending = false
    private var autoWrap = true
    private var originMode = false
    private var top = 0
    private var bottom = this.rows - 1
    private var lastCp = ' '.code
    private var g0Dec = false
    private var g1Dec = false
    private var shiftOut = false

    private var saved = IntArray(8)

    private var state = S.NORMAL
    private val params = StringBuilder()
    private val strBuf = StringBuilder()
    private var charsetTarget = '('
    private var highSurrogate: Char? = null

    private enum class S { NORMAL, ESC, CSI, OSC, STRING, STRING_ESC, OSC_ESC, CHARSET }

    private fun idx(r: Int, c: Int) = r * cols + c

    fun cellIndex(r: Int, c: Int) = idx(r, c)

    /**
     * Feeds output; returns how many chars were consumed. Stops right after
     * the sequence that leaves the alternate screen ([exitRequested]).
     */
    fun feed(text: String): Int {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            i++
            when (state) {
                S.NORMAL -> normal(ch)
                S.ESC -> escape(ch)
                S.CSI -> csi(ch)
                S.OSC -> when (ch) {
                    '\u0007' -> { osc(strBuf.toString()); state = S.NORMAL }
                    '\u001B' -> state = S.OSC_ESC
                    else -> if (strBuf.length < 1024) strBuf.append(ch)
                }
                S.OSC_ESC -> if (ch == '\\') {
                    osc(strBuf.toString())
                    state = S.NORMAL
                } else {
                    state = S.ESC
                    escape(ch)
                }
                S.STRING -> when (ch) {
                    '\u0007' -> { dcs(strBuf.toString()); state = S.NORMAL }
                    '\u001B' -> state = S.STRING_ESC
                    else -> if (strBuf.length < 4096) strBuf.append(ch)
                }
                S.STRING_ESC -> { dcs(strBuf.toString()); state = S.NORMAL }
                S.CHARSET -> {
                    val dec = ch == '0'
                    if (charsetTarget == '(') g0Dec = dec else if (charsetTarget == ')') g1Dec = dec
                    state = S.NORMAL
                }
            }
            if (exitRequested) return i
        }
        return text.length
    }

    // ── Printing ──

    private fun normal(ch: Char) {
        when (ch) {
            '\u001B' -> state = S.ESC
            '\r' -> { cursorCol = 0; wrapPending = false }
            '\n', '\u000B', '\u000C' -> { lineFeed(); wrapPending = false }
            '\b' -> { if (cursorCol > 0) cursorCol--; wrapPending = false }
            '\t' -> { cursorCol = minOf(cols - 1, (cursorCol / 8 + 1) * 8); wrapPending = false }
            '\u000E' -> shiftOut = true
            '\u000F' -> shiftOut = false
            '\u0007', '\u0000', '\u007F' -> Unit
            else -> {
                if (ch < ' ') return
                if (Character.isHighSurrogate(ch)) { highSurrogate = ch; return }
                val code =
                    if (Character.isLowSurrogate(ch) && highSurrogate != null) {
                        Character.toCodePoint(highSurrogate!!, ch).also { highSurrogate = null }
                    } else {
                        ch.code
                    }
                print(code)
            }
        }
    }

    private fun print(code0: Int) {
        var code = code0
        if ((if (shiftOut) g1Dec else g0Dec) && code in 0x5F..0x7E) code = DEC_GRAPHICS[code - 0x5F].code
        val w = charWidth(code)
        if (w == 0) return // combining marks and joiners: keep the base cell
        if (wrapPending && autoWrap) {
            cursorCol = 0
            lineFeed()
        }
        wrapPending = false
        if (w == 2 && cursorCol == cols - 1) {
            if (!autoWrap) return
            put(cursorRow, cursorCol, ' '.code)
            cursorCol = 0
            lineFeed()
        }
        put(cursorRow, cursorCol, code)
        if (w == 2) put(cursorRow, cursorCol + 1, 0)
        lastCp = code
        val next = cursorCol + w
        if (next >= cols) {
            cursorCol = cols - 1
            wrapPending = true
        } else {
            cursorCol = next
        }
    }

    private fun put(r: Int, c: Int, code: Int) {
        if (r !in 0 until rows || c !in 0 until cols) return
        val i = idx(r, c)
        cp[i] = code
        fg[i] = curFg
        bg[i] = curBg
        fl[i] = curFl
    }

    private fun blank(i: Int) {
        cp[i] = ' '.code
        fg[i] = DEFAULT_COLOR
        bg[i] = curBg // background-color erase, like xterm
        fl[i] = 0
    }

    private fun lineFeed() {
        if (cursorRow == bottom) scrollUp(1) else if (cursorRow < rows - 1) cursorRow++
    }

    private fun scrollUp(n: Int) {
        val count = n.coerceIn(1, bottom - top + 1)
        for (r in top..bottom) {
            val src = r + count
            if (src <= bottom) copyRow(src, r) else clearRow(r)
        }
    }

    private fun scrollDown(n: Int) {
        val count = n.coerceIn(1, bottom - top + 1)
        for (r in bottom downTo top) {
            val src = r - count
            if (src >= top) copyRow(src, r) else clearRow(r)
        }
    }

    private fun copyRow(from: Int, to: Int) {
        System.arraycopy(cp, idx(from, 0), cp, idx(to, 0), cols)
        System.arraycopy(fg, idx(from, 0), fg, idx(to, 0), cols)
        System.arraycopy(bg, idx(from, 0), bg, idx(to, 0), cols)
        System.arraycopy(fl, idx(from, 0), fl, idx(to, 0), cols)
    }

    private fun clearRow(r: Int) {
        for (c in 0 until cols) blank(idx(r, c))
    }

    // ── Escapes ──

    private fun escape(ch: Char) {
        state = S.NORMAL
        when (ch) {
            '[' -> { params.setLength(0); state = S.CSI }
            ']' -> { strBuf.setLength(0); state = S.OSC }
            'P', '_', '^', 'X' -> { strBuf.setLength(0); strBuf.append(ch); state = S.STRING }
            '(', ')', '*', '+' -> { charsetTarget = ch; state = S.CHARSET }
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> { lineFeed(); wrapPending = false }
            'E' -> { cursorCol = 0; lineFeed(); wrapPending = false }
            'M' -> { if (cursorRow == top) scrollDown(1) else if (cursorRow > 0) cursorRow--; wrapPending = false }
            'c' -> reset()
            else -> Unit // '=', '>' keypad modes, etc.
        }
    }

    private fun csi(ch: Char) {
        if (ch in '0'..'9' || ch == ';' || ch == ':' || ch == '?' || ch == '>' || ch == '<' || ch == '=' ||
            ch == ' ' || ch == '$' || ch == '"' || ch == '!' || ch == '\''
        ) {
            if (params.length < 96) params.append(ch)
            return
        }
        if (ch < '@') { state = S.NORMAL; return } // malformed
        state = S.NORMAL
        val raw = params.toString()
        val prefix = raw.firstOrNull()?.takeIf { it == '?' || it == '>' || it == '<' || it == '=' }
        val intermediates = raw.filter { it == ' ' || it == '$' || it == '"' || it == '!' || it == '\'' }
        val body = raw.trimStart('?', '>', '<', '=').filter { it.isDigit() || it == ';' || it == ':' }
        val nums = if (body.isEmpty()) emptyList() else body.split(';').map { it.substringBefore(':').toIntOrNull() }
        val sub = body.split(';') // keeps colon sub-parameters for SGR
        fun n(i: Int, def: Int = 1): Int = (nums.getOrNull(i) ?: def).let { if (it == 0) def else it }

        if (intermediates.isNotEmpty()) {
            when {
                // DECRQM: report every mode as "not recognized".
                intermediates == "$" && ch == 'p' -> {
                    val mode = nums.getOrNull(0) ?: 0
                    respond(if (prefix == '?') "\u001B[?$mode;0\$y" else "\u001B[$mode;0\$y")
                }
                intermediates == "!" && ch == 'p' -> softReset()
            }
            return
        }
        when (prefix) {
            '?' -> {
                when (ch) {
                    'h', 'l' -> nums.forEach { setPrivateMode(it ?: 0, ch == 'h') }
                    'n' -> if (nums.getOrNull(0) == 6) respond("\u001B[?${cursorRow + 1};${cursorCol + 1}R")
                    else -> Unit // ?u kitty keyboard query: unanswered = unsupported
                }
                return
            }
            '>' -> {
                when (ch) {
                    'c' -> respond("\u001B[>1;10;0c")
                    'q' -> respond("\u001BP>|RaikaAI terminal\u001B\\")
                    else -> Unit
                }
                return
            }
            '<', '=' -> return
        }
        when (ch) {
            'm' -> sgr(sub)
            '@' -> insertChars(n(0))
            'A' -> moveTo(cursorRow - n(0), cursorCol, clampToRegion = true)
            'B', 'e' -> moveTo(cursorRow + n(0), cursorCol, clampToRegion = true)
            'C', 'a' -> moveTo(cursorRow, cursorCol + n(0))
            'D' -> moveTo(cursorRow, cursorCol - n(0))
            'E' -> moveTo(cursorRow + n(0), 0, clampToRegion = true)
            'F' -> moveTo(cursorRow - n(0), 0, clampToRegion = true)
            'G', '`' -> moveTo(cursorRow, n(0) - 1)
            'd' -> moveTo((if (originMode) top else 0) + n(0) - 1, cursorCol)
            'H', 'f' -> moveTo((if (originMode) top else 0) + n(0) - 1, n(1) - 1)
            'J' -> eraseDisplay(nums.getOrNull(0) ?: 0)
            'K' -> eraseLine(nums.getOrNull(0) ?: 0)
            'L' -> if (cursorRow in top..bottom) { val t = top; top = cursorRow; scrollDown(n(0)); top = t; cursorCol = 0 }
            'M' -> if (cursorRow in top..bottom) { val t = top; top = cursorRow; scrollUp(n(0)); top = t; cursorCol = 0 }
            'P' -> deleteChars(n(0))
            'S' -> scrollUp(n(0))
            'T' -> scrollDown(n(0))
            'X' -> for (c in cursorCol until minOf(cols, cursorCol + n(0))) blank(idx(cursorRow, c))
            'b' -> repeat(n(0).coerceAtMost(cols * rows)) { print(lastCp) }
            'r' -> {
                val t = (nums.getOrNull(0) ?: 1).coerceAtLeast(1) - 1
                val b = (nums.getOrNull(1)?.takeIf { it > 0 } ?: rows) - 1
                if (t < b && b < rows) { top = t; bottom = b } else { top = 0; bottom = rows - 1 }
                moveTo(if (originMode) top else 0, 0)
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'c' -> if ((nums.getOrNull(0) ?: 0) == 0) respond("\u001B[?62;22c")
            'n' -> when (nums.getOrNull(0)) {
                5 -> respond("\u001B[0n")
                6 -> respond("\u001B[${cursorRow - (if (originMode) top else 0) + 1};${cursorCol + 1}R")
            }
            't' -> when (nums.getOrNull(0)) {
                14 -> respond("\u001B[4;${rows * cellHeightPx};${cols * cellWidthPx}t")
                16 -> respond("\u001B[6;$cellHeightPx;${cellWidthPx}t")
                18 -> respond("\u001B[8;$rows;${cols}t")
            }
            'h', 'l' -> Unit // ANSI modes (insert mode) are rare in TUIs
            else -> Unit
        }
        wrapPending = false
    }

    private fun setPrivateMode(mode: Int, on: Boolean) {
        when (mode) {
            25 -> cursorVisible = on
            7 -> autoWrap = on
            6 -> { originMode = on; moveTo(if (on) top else 0, 0) }
            47, 1047, 1049 -> if (!on) exitRequested = true
            1000, 1002, 1003 -> mouseTracking = if (on) mode else if (mouseTracking == mode) 0 else mouseTracking
            1006 -> mouseSgr = on
            2004 -> bracketedPaste = on
            else -> Unit // mouse, bracketed paste, focus, sync output: no state needed
        }
    }

    private fun moveTo(r: Int, c: Int, clampToRegion: Boolean = false) {
        val (lo, hi) = if (clampToRegion && cursorRow in top..bottom) top to bottom else 0 to rows - 1
        cursorRow = r.coerceIn(lo, hi)
        cursorCol = c.coerceIn(0, cols - 1)
        wrapPending = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in cursorRow + 1 until rows) clearRow(r) }
            1 -> { for (r in 0 until cursorRow) clearRow(r); eraseLine(1) }
            else -> for (r in 0 until rows) clearRow(r)
        }
    }

    private fun eraseLine(mode: Int) {
        val (from, to) = when (mode) {
            1 -> 0 to cursorCol + 1
            2 -> 0 to cols
            else -> cursorCol to cols
        }
        for (c in from until minOf(to, cols)) blank(idx(cursorRow, c))
    }

    private fun insertChars(n: Int) {
        val count = n.coerceAtMost(cols - cursorCol)
        for (c in cols - 1 downTo cursorCol + count) {
            val s = idx(cursorRow, c - count)
            val d = idx(cursorRow, c)
            cp[d] = cp[s]; fg[d] = fg[s]; bg[d] = bg[s]; fl[d] = fl[s]
        }
        for (c in cursorCol until cursorCol + count) blank(idx(cursorRow, c))
    }

    private fun deleteChars(n: Int) {
        val count = n.coerceAtMost(cols - cursorCol)
        for (c in cursorCol until cols) {
            val d = idx(cursorRow, c)
            if (c + count < cols) {
                val s = idx(cursorRow, c + count)
                cp[d] = cp[s]; fg[d] = fg[s]; bg[d] = bg[s]; fl[d] = fl[s]
            } else {
                blank(d)
            }
        }
    }

    private fun saveCursor() {
        saved = intArrayOf(cursorRow, cursorCol, curFg, curBg, curFl, if (g0Dec) 1 else 0, if (originMode) 1 else 0, 0)
    }

    private fun restoreCursor() {
        cursorRow = saved[0].coerceIn(0, rows - 1)
        cursorCol = saved[1].coerceIn(0, cols - 1)
        curFg = saved[2]; curBg = saved[3]; curFl = saved[4]
        g0Dec = saved[5] == 1
        originMode = saved[6] == 1
        wrapPending = false
    }

    private fun softReset() {
        curFg = DEFAULT_COLOR; curBg = DEFAULT_COLOR; curFl = 0
        top = 0; bottom = rows - 1
        originMode = false; autoWrap = true; cursorVisible = true
        g0Dec = false; g1Dec = false; shiftOut = false
    }

    private fun reset() {
        softReset()
        for (i in cp.indices) blank(i)
        cursorRow = 0; cursorCol = 0; wrapPending = false
    }

    // ── OSC / DCS ──

    private fun osc(s: String) {
        val code = s.substringBefore(';')
        val arg = s.substringAfter(';', "")
        when (code) {
            "52" -> {
                // OSC 52 ; targets ; base64 — "?" is a read request, which we decline.
                val data = arg.substringAfter(';', "")
                if (data.isNotEmpty() && data != "?") {
                    runCatching { String(java.util.Base64.getDecoder().decode(data.trim()), Charsets.UTF_8) }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { onClipboard?.invoke(it) }
                }
            }
            "10" -> if (arg == "?") respond("\u001B]10;rgb:e6e6/e6e6/e6e6\u001B\\")
            "11" -> if (arg == "?") respond("\u001B]11;rgb:0000/0000/0000\u001B\\")
            "12" -> if (arg == "?") respond("\u001B]12;rgb:9898/c3c3/7979\u001B\\")
            "4" -> {
                // Palette queries: "4;N;?" (possibly several pairs).
                val parts = arg.split(';')
                var k = 0
                while (k + 1 < parts.size) {
                    val index = parts[k].toIntOrNull()
                    if (index != null && parts[k + 1] == "?" && index in 0..255) {
                        val c = AnsiScreen.palette(index)
                        fun h(v: Float) = "%02x".format((v * 255).toInt()).repeat(2)
                        respond("\u001B]4;$index;rgb:${h(c.red)}/${h(c.green)}/${h(c.blue)}\u001B\\")
                    }
                    k += 2
                }
            }
        }
    }

    private fun dcs(s: String) {
        // XTGETTCAP (DCS + q hex ST): answer "not available" for each name.
        if (s.startsWith("P+q")) respond("\u001BP0+r\u001B\\")
    }

    // ── SGR ──

    private fun sgr(parts: List<String>) {
        if (parts.isEmpty() || (parts.size == 1 && parts[0].isEmpty())) { curFg = DEFAULT_COLOR; curBg = DEFAULT_COLOR; curFl = 0; return }
        var i = 0
        fun num(k: Int) = parts.getOrNull(k)?.substringBefore(':')?.toIntOrNull()
        while (i < parts.size) {
            val part = parts[i]
            val p = part.substringBefore(':').toIntOrNull() ?: 0
            when (p) {
                0 -> { curFg = DEFAULT_COLOR; curBg = DEFAULT_COLOR; curFl = 0 }
                1 -> curFl = curFl or BOLD
                2 -> curFl = curFl or DIM
                3 -> curFl = curFl or ITALIC
                4 -> curFl = if (part.contains(":0")) curFl and UNDERLINE.inv() else curFl or UNDERLINE
                5, 6 -> Unit
                7 -> curFl = curFl or INVERSE
                8 -> curFl = curFl or HIDDEN
                9 -> curFl = curFl or STRIKE
                21 -> curFl = curFl or UNDERLINE
                22 -> curFl = curFl and (BOLD or DIM).inv()
                23 -> curFl = curFl and ITALIC.inv()
                24 -> curFl = curFl and UNDERLINE.inv()
                27 -> curFl = curFl and INVERSE.inv()
                28 -> curFl = curFl and HIDDEN.inv()
                29 -> curFl = curFl and STRIKE.inv()
                in 30..37 -> curFg = p - 30
                39 -> curFg = DEFAULT_COLOR
                in 40..47 -> curBg = p - 40
                49 -> curBg = DEFAULT_COLOR
                in 90..97 -> curFg = p - 90 + 8
                in 100..107 -> curBg = p - 100 + 8
                38, 48, 58 -> {
                    var color: Int? = null
                    if (part.contains(':')) {
                        // Colon form: 38:5:N or 38:2:[cs]:R:G:B
                        val s = part.split(':').map { it.toIntOrNull() }
                        when (s.getOrNull(1)) {
                            5 -> color = s.getOrNull(2)?.coerceIn(0, 255)
                            2 -> {
                                val rgb = if (s.size >= 6) s.subList(3, 6) else s.drop(2)
                                color = TRUECOLOR or ((rgb.getOrNull(0) ?: 0).coerceIn(0, 255) shl 16) or
                                    ((rgb.getOrNull(1) ?: 0).coerceIn(0, 255) shl 8) or (rgb.getOrNull(2) ?: 0).coerceIn(0, 255)
                            }
                        }
                    } else {
                        when (num(i + 1)) {
                            5 -> { color = num(i + 2)?.coerceIn(0, 255); i += 2 }
                            2 -> {
                                color = TRUECOLOR or ((num(i + 2) ?: 0).coerceIn(0, 255) shl 16) or
                                    ((num(i + 3) ?: 0).coerceIn(0, 255) shl 8) or (num(i + 4) ?: 0).coerceIn(0, 255)
                                i += 4
                            }
                        }
                    }
                    if (color != null) {
                        if (p == 38) curFg = color else if (p == 48) curBg = color
                    }
                }
                59 -> Unit
                else -> Unit
            }
            i++
        }
    }

    /**
     * Encodes a mouse event at cell ([col], [row]) for the program, or null
     * when it did not ask for that kind of event. [button]: 0 left,
     * 64/65 wheel up/down. [motion] is a drag with the button held.
     */
    fun mouseEvent(
        button: Int,
        col: Int,
        row: Int,
        press: Boolean,
        motion: Boolean = false,
    ): String? {
        if (mouseTracking == 0) return null
        if (motion && mouseTracking == 1000) return null
        val c = col.coerceIn(0, cols - 1) + 1
        val r = row.coerceIn(0, rows - 1) + 1
        val b = button + if (motion) 32 else 0
        if (mouseSgr) return "\u001B[<$b;$c;$r" + if (press) "M" else "m"
        // Legacy X10 encoding: release is button 3; wheel has no release.
        val code = if (press) b else 3
        if (c > 223 || r > 223) return null
        return "\u001B[M" + (32 + code).toChar() + (32 + c).toChar() + (32 + r).toChar()
    }

    /** Resizes, keeping the top-left content; the program redraws on SIGWINCH. */
    fun resize(newRows: Int, newCols: Int) {
        val r2 = newRows.coerceIn(2, 300)
        val c2 = newCols.coerceIn(10, 400)
        if (r2 == rows && c2 == cols) return
        val ncp = IntArray(r2 * c2) { ' '.code }
        val nfg = IntArray(r2 * c2) { DEFAULT_COLOR }
        val nbg = IntArray(r2 * c2) { DEFAULT_COLOR }
        val nfl = IntArray(r2 * c2)
        for (r in 0 until minOf(rows, r2)) {
            for (c in 0 until minOf(cols, c2)) {
                val s = idx(r, c)
                val d = r * c2 + c
                ncp[d] = cp[s]; nfg[d] = fg[s]; nbg[d] = bg[s]; nfl[d] = fl[s]
            }
        }
        cp = ncp; fg = nfg; bg = nbg; fl = nfl
        rows = r2; cols = c2
        top = 0; bottom = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        wrapPending = false
    }

    /** Plain text of row [r] (tests, copy). */
    fun rowText(r: Int): String =
        buildString {
            for (c in 0 until cols) {
                val code = cp[idx(r, c)]
                if (code != 0) appendCodePoint(code)
            }
        }.trimEnd()

    companion object {
        const val DEFAULT_COLOR = -1
        const val TRUECOLOR = 1 shl 24
        const val BOLD = 1
        const val DIM = 2
        const val ITALIC = 4
        const val UNDERLINE = 8
        const val INVERSE = 32
        const val HIDDEN = 64
        const val STRIKE = 128

        /** DEC special graphics for 0x5F..0x7E (line drawing in ncurses apps). */
        private const val DEC_GRAPHICS = " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·"

        /** Terminal cell width: 0 for combining marks, 2 for wide CJK and emoji. */
        fun charWidth(cp: Int): Int =
            when {
                cp in 0x0300..0x036F || cp in 0x200B..0x200F || cp in 0xFE00..0xFE0F || cp == 0x20E3 || cp in 0x1F3FB..0x1F3FF -> 0
                cp in 0x1100..0x115F || cp in 0x2E80..0x303E || cp in 0x3041..0x33FF || cp in 0x3400..0x4DBF ||
                    cp in 0x4E00..0x9FFF || cp in 0xA000..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF ||
                    cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6 ||
                    cp in 0x1F300..0x1F64F || cp in 0x1F900..0x1F9FF || cp in 0x1F680..0x1F6FF || cp in 0x20000..0x3FFFD -> 2
                else -> 1
            }
    }
}
