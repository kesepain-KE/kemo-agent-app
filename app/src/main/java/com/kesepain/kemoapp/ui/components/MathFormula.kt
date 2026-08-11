package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A small, dependency-free TeX subset renderer. The web client uses KaTeX;
 * this renderer intentionally covers the common subset emitted by the agent
 * while keeping formula layout inside Compose (no WebView per message).
 */
internal sealed interface MathNode {
    data class Sequence(val children: List<MathNode>) : MathNode
    data class Text(val value: String, val style: MathTextStyle = MathTextStyle.Normal) : MathNode
    data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode
    data class Root(val radicand: MathNode, val index: MathNode? = null) : MathNode
    data class Script(val base: MathNode, val superscript: MathNode? = null, val subscript: MathNode? = null) : MathNode
    data class Matrix(val rows: List<List<MathNode>>, val left: String = "", val right: String = "") : MathNode
    data class Accent(val base: MathNode, val mark: String) : MathNode
    data class Styled(val base: MathNode, val style: MathTextStyle) : MathNode
}

internal enum class MathTextStyle { Normal, Italic, Roman, Bold, Operator }

internal fun parseMathExpression(source: String): MathNode = MathExpressionCache.get(source)

internal fun MathNode.toPlainText(): String = when (this) {
    is MathNode.Sequence -> children.joinToString("") { it.toPlainText() }
    is MathNode.Text -> value
    is MathNode.Fraction -> "${numerator.toPlainText()}⁄${denominator.toPlainText()}"
    is MathNode.Root -> "√${radicand.toPlainText()}"
    is MathNode.Script -> {
        val base = base.toPlainText()
        val sup = superscript?.toPlainText()?.let(::toSuperscript).orEmpty()
        val sub = subscript?.toPlainText()?.let(::toSubscript).orEmpty()
        buildString {
            append(base)
            if (sup.isNotBlank()) append(sup)
            if (sub.isNotBlank()) append(sub)
        }
    }
    is MathNode.Matrix -> {
        val rowsText = rows.joinToString("; ") { row -> row.joinToString(", ") { it.toPlainText() } }
        "${left.ifBlank { "[" }}$rowsText${right.ifBlank { "]" }}"
    }
    is MathNode.Accent -> "${base.toPlainText()}${mark}"
    is MathNode.Styled -> base.toPlainText()
}

@Composable
internal fun MathFormulaBlock(
    source: String,
    onCopied: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(source) { mutableStateOf(false) }
    val parsed = remember(source) { runCatching { parseMathExpression(source.trim()) }.getOrNull() }
    val readable = parsed?.toPlainText().orEmpty().ifBlank { source.trim() }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = readable },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(source.trim()))
                    copied = true
                    onCopied()
                },
                modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
            ) {
                Icon(
                    if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy formula",
                    modifier = Modifier.size(17.dp),
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormulaViewport(
                node = parsed,
                fallback = source.trim(),
                display = true,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 48.dp, top = 18.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun FormulaViewport(
    node: MathNode?,
    fallback: String,
    display: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node == null) {
                SelectionContainer {
                    Text(
                        fallback,
                        style = mathTextStyle(display, MathTextStyle.Normal),
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                }
            } else {
                MathNodeView(node, display = display)
            }
        }
    }
}

@Composable
private fun MathNodeView(node: MathNode, display: Boolean, scale: Float = 1f) {
    when (node) {
        is MathNode.Sequence -> Row(
            horizontalArrangement = Arrangement.spacedBy((if (display) 1.5f else 1f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEachIndexed { index, child -> key(index) { MathNodeView(child, display, scale) } }
        }
        is MathNode.Text -> Text(
            text = node.value,
            style = mathTextStyle(display, node.style, scale),
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        is MathNode.Fraction -> Column(
            modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            MathNodeView(node.numerator, display = false, scale = scale * .82f)
            HorizontalDivider(Modifier.fillMaxWidth(), thickness = (1f * scale).coerceAtLeast(.7f).dp)
            MathNodeView(node.denominator, display = false, scale = scale * .82f)
        }
        is MathNode.Root -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (node.index != null) {
                MathNodeView(node.index, display = false, scale = scale * .52f)
                Spacer(Modifier.width((-2).dp))
            }
            Text("√", style = mathTextStyle(display, MathTextStyle.Normal, scale * 1.35f), softWrap = false)
            val mathColor = MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier.padding(top = 2.dp).drawBehind {
                    drawLine(
                        color = mathColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = (1.2f * scale).coerceAtLeast(1f),
                    )
                },
            ) { MathNodeView(node.radicand, display = false, scale = scale) }
        }
        is MathNode.Script -> ScriptView(node, display, scale)
        is MathNode.Matrix -> MatrixView(node, display, scale)
        is MathNode.Accent -> Box(contentAlignment = Alignment.Center) {
            MathNodeView(node.base, display, scale)
            Text(
                node.mark,
                modifier = Modifier.align(Alignment.TopCenter),
                style = mathTextStyle(display, MathTextStyle.Normal, scale * .72f),
                softWrap = false,
            )
        }
        is MathNode.Styled -> MathNodeView(node.base.withTextStyle(node.style), display, scale)
    }
}

@Composable
private fun ScriptView(node: MathNode.Script, display: Boolean, scale: Float) {
    val baseIsLargeOperator = node.base is MathNode.Text && node.base.style == MathTextStyle.Operator
    if (display && baseIsLargeOperator) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-2).dp)) {
            node.superscript?.let { MathNodeView(it, display = false, scale = scale * .58f) }
            MathNodeView(node.base, display = true, scale = scale * 1.12f)
            node.subscript?.let { MathNodeView(it, display = false, scale = scale * .58f) }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MathNodeView(node.base, display, scale)
        val scriptOffset = when {
            node.superscript != null && node.subscript == null -> (-6f * scale).dp
            node.subscript != null && node.superscript == null -> (6f * scale).dp
            else -> 0.dp
        }
        Column(
            modifier = Modifier.padding(start = 1.dp).offset(y = scriptOffset),
            verticalArrangement = Arrangement.spacedBy((-3).dp),
        ) {
            node.superscript?.let {
                MathNodeView(it, display = false, scale = scale * .58f)
            }
            node.subscript?.let {
                MathNodeView(it, display = false, scale = scale * .58f)
            }
        }
    }
}

private fun MathNode.withTextStyle(style: MathTextStyle): MathNode = when (this) {
    is MathNode.Text -> copy(style = style)
    is MathNode.Sequence -> copy(children = children.map { it.withTextStyle(style) })
    is MathNode.Fraction -> copy(numerator = numerator.withTextStyle(style), denominator = denominator.withTextStyle(style))
    is MathNode.Root -> copy(radicand = radicand.withTextStyle(style), index = index?.withTextStyle(style))
    is MathNode.Script -> copy(
        base = base.withTextStyle(style),
        superscript = superscript?.withTextStyle(style),
        subscript = subscript?.withTextStyle(style),
    )
    is MathNode.Matrix -> copy(rows = rows.map { row -> row.map { it.withTextStyle(style) } })
    is MathNode.Accent -> copy(base = base.withTextStyle(style))
    is MathNode.Styled -> base.withTextStyle(this.style)
}

@Composable
private fun MatrixView(node: MathNode.Matrix, display: Boolean, scale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (node.left.isNotBlank()) MatrixDelimiter(node.left, node.rows.size, display, scale)
        Column(verticalArrangement = Arrangement.spacedBy((4f * scale).dp)) {
            node.rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy((14f * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { columnIndex, cell ->
                        key("$rowIndex:$columnIndex") { MathNodeView(cell, display = false, scale = scale * .86f) }
                    }
                }
            }
        }
        if (node.right.isNotBlank()) MatrixDelimiter(node.right, node.rows.size, display, scale)
    }
}

@Composable
private fun MatrixDelimiter(delimiter: String, rowCount: Int, display: Boolean, scale: Float) {
    val pieces = delimiterPieces(delimiter, rowCount)
    Column(verticalArrangement = Arrangement.spacedBy((-3).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        pieces.forEach { piece ->
            Text(piece, style = mathTextStyle(display, MathTextStyle.Normal, scale * 1.16f), softWrap = false)
        }
    }
}

@Composable
private fun mathTextStyle(display: Boolean, style: MathTextStyle, scale: Float = 1f) = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = FontFamily.Serif,
    fontStyle = if (style == MathTextStyle.Italic) FontStyle.Italic else FontStyle.Normal,
    fontWeight = if (style == MathTextStyle.Bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = ((if (display) 22f else 16f) * scale).coerceIn(9f, 28f).sp,
    lineHeight = ((if (display) 28f else 21f) * scale).coerceIn(11f, 34f).sp,
    color = MaterialTheme.colorScheme.onSurface,
)

private fun delimiterPieces(delimiter: String, rowCount: Int): List<String> {
    if (rowCount <= 1) return listOf(delimiter)
    val pieces = when (delimiter) {
        "(" -> listOf("⎛", "⎜", "⎝")
        ")" -> listOf("⎞", "⎟", "⎠")
        "[" -> listOf("⎡", "⎢", "⎣")
        "]" -> listOf("⎤", "⎥", "⎦")
        "{" -> listOf("⎧", "⎨", "⎩")
        "}" -> listOf("⎫", "⎬", "⎭")
        "|" -> listOf("⎪", "⎪", "⎪")
        "‖" -> listOf("‖", "‖", "‖")
        else -> listOf(delimiter)
    }
    if (rowCount == 2) return listOf(pieces.first(), pieces.last())
    return buildList {
        add(pieces.first())
        repeat((rowCount - 2).coerceAtLeast(1)) { add(pieces[1]) }
        add(pieces.last())
    }
}

private class LatexMathParser(private val source: String) {
    private var index = 0

    fun parse(): MathNode {
        val result = parseSequence()
        return normalize(result)
    }

    private fun parseSequence(stopAtBrace: Boolean = false): MathNode {
        val nodes = mutableListOf<MathNode>()
        while (index < source.length) {
            if (stopAtBrace && source[index] == '}') break
            if (source.startsWith("\\end{", index)) break
            if (source.startsWith("\\\\", index)) break
            if (source[index] == '&') {
                index++
                nodes += MathNode.Text(" ")
                continue
            }
            var atom = parseAtom()
            var superscript: MathNode? = null
            var subscript: MathNode? = null
            while (true) {
                skipWhitespace()
                if (index >= source.length || (source[index] != '^' && source[index] != '_')) break
                val operator = source[index++]
                val argument = parseArgument()
                if (operator == '^') superscript = argument else subscript = argument
            }
            if (superscript != null || subscript != null) atom = MathNode.Script(atom, superscript, subscript)
            nodes += atom
        }
        if (stopAtBrace && index < source.length && source[index] == '}') index++
        return normalize(MathNode.Sequence(nodes))
    }

    private fun parseAtom(): MathNode {
        if (index >= source.length) return MathNode.Text("")
        return when (source[index]) {
            '{' -> {
                index++
                parseSequence(stopAtBrace = true)
            }
            '\\' -> parseCommand()
            else -> {
                val character = source[index++]
                MathNode.Text(character.toString(), rawStyle(character))
            }
        }
    }

    private fun parseArgument(): MathNode {
        skipWhitespace()
        if (index >= source.length) return MathNode.Text("")
        return if (source[index] == '{') {
            index++
            parseSequence(stopAtBrace = true)
        } else parseAtom()
    }

    private fun parseCommand(): MathNode {
        index++
        if (index >= source.length) return MathNode.Text("\\")
        if (source[index] == '\\') {
            index += 1
            return MathNode.Text(" ")
        }
        if (!source[index].isLetter()) {
            val escaped = source[index++]
            return MathNode.Text(symbolForEscaped(escaped), rawStyle(escaped))
        }
        val start = index
        while (index < source.length && source[index].isLetter()) index++
        val command = source.substring(start, index)
        return when (command) {
            "frac", "dfrac", "tfrac" -> MathNode.Fraction(parseArgument(), parseArgument())
            "sqrt" -> {
                val indexNode = if (peek() == '[') parseOptionalBracket() else null
                MathNode.Root(parseArgument(), indexNode)
            }
            "begin" -> parseEnvironment(readGroupText())
            "text", "textrm", "mathrm", "operatorname" -> MathNode.Text(readGroupText(), MathTextStyle.Roman)
            "mathbf", "boldsymbol" -> MathNode.Styled(parseArgument(), MathTextStyle.Bold)
            "mathit" -> MathNode.Styled(parseArgument(), MathTextStyle.Italic)
            "left" -> MathNode.Text(readDelimiter(), MathTextStyle.Normal)
            "right" -> MathNode.Text(readDelimiter(), MathTextStyle.Normal)
            "overline" -> MathNode.Accent(parseArgument(), "¯")
            "underline" -> MathNode.Accent(parseArgument(), "_")
            "hat" -> MathNode.Accent(parseArgument(), "ˆ")
            "vec" -> MathNode.Accent(parseArgument(), "→")
            "quad" -> MathNode.Text("  ")
            "qquad" -> MathNode.Text("    ")
            "enspace", ",", ";", ":", "!" -> MathNode.Text(" ")
            else -> {
                val mapped = SYMBOLS[command]
                if (mapped != null) MathNode.Text(mapped.first, mapped.second)
                else MathNode.Text("\\$command", MathTextStyle.Roman)
            }
        }
    }

    private fun parseEnvironment(name: String): MathNode {
        val endToken = "\\end{$name}"
        val end = source.indexOf(endToken, index)
        if (end < 0) return MathNode.Text("\\begin{$name}", MathTextStyle.Roman)
        val body = source.substring(index, end)
        index = end + endToken.length
        val normalizedBody = if (name == "array") removeArrayColumnSpec(body) else body
        val rows = splitMatrixRows(normalizedBody).map { row ->
            splitMatrixCells(row).map { cell -> LatexMathParser(cell.trim()).parse() }
        }.filter { it.isNotEmpty() }
        val (left, right) = when (name) {
            "pmatrix" -> "(" to ")"
            "bmatrix" -> "[" to "]"
            "Bmatrix" -> "{" to "}"
            "vmatrix" -> "|" to "|"
            "Vmatrix" -> "‖" to "‖"
            "cases" -> "{" to ""
            else -> "" to ""
        }
        return MathNode.Matrix(rows.ifEmpty { listOf(listOf(MathNode.Text(""))) }, left, right)
    }

    private fun readGroupText(): String {
        skipWhitespace()
        if (peek() != '{') return ""
        index++
        val start = index
        var depth = 1
        while (index < source.length && depth > 0) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth > 0) index++
        }
        val result = source.substring(start, index)
        if (peek() == '}') index++
        return result
    }

    private fun parseOptionalBracket(): MathNode {
        index++
        val start = index
        var depth = 1
        while (index < source.length && depth > 0) {
            when (source[index]) {
                '[' -> depth++
                ']' -> depth--
            }
            if (depth > 0) index++
        }
        val result = LatexMathParser(source.substring(start, index)).parse()
        if (peek() == ']') index++
        return result
    }

    private fun readDelimiter(): String {
        skipWhitespace()
        if (peek() != '\\') return if (index < source.length) source[index++].toString() else ""
        val commandNode = parseCommand()
        return commandNode.toPlainText()
    }

    private fun removeArrayColumnSpec(value: String): String {
        val trimmed = value.trimStart()
        if (!trimmed.startsWith('{')) return value
        val close = trimmed.indexOf('}')
        return if (close >= 0) trimmed.substring(close + 1) else value
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun rawStyle(value: Char): MathTextStyle = when {
        value.isLetter() -> MathTextStyle.Italic
        value in "+-=<>|/()[],:;" -> MathTextStyle.Normal
        else -> MathTextStyle.Normal
    }

    private fun normalize(node: MathNode): MathNode = when (node) {
        is MathNode.Sequence -> {
            val merged = mutableListOf<MathNode>()
            node.children.forEach { child ->
                val normalized = normalize(child)
                if (normalized is MathNode.Sequence) {
                    normalized.children.forEach { nested -> appendMerged(merged, nested) }
                } else appendMerged(merged, normalized)
            }
            MathNode.Sequence(merged)
        }
        is MathNode.Script -> node.copy(base = normalize(node.base), superscript = node.superscript?.let(::normalize), subscript = node.subscript?.let(::normalize))
        is MathNode.Fraction -> node.copy(numerator = normalize(node.numerator), denominator = normalize(node.denominator))
        is MathNode.Root -> node.copy(radicand = normalize(node.radicand), index = node.index?.let(::normalize))
        is MathNode.Matrix -> node.copy(rows = node.rows.map { row -> row.map(::normalize) })
        is MathNode.Accent -> node.copy(base = normalize(node.base))
        is MathNode.Styled -> node.copy(base = normalize(node.base))
        is MathNode.Text -> node
    }

    private fun appendMerged(target: MutableList<MathNode>, node: MathNode) {
        if (node is MathNode.Text && target.lastOrNull() is MathNode.Text) {
            val previous = target.last() as MathNode.Text
            if (previous.style == node.style) {
                target[target.lastIndex] = previous.copy(value = previous.value + node.value)
                return
            }
        }
        target += node
    }

    companion object {
        private val SYMBOLS = mapOf(
            "alpha" to ("α" to MathTextStyle.Italic), "beta" to ("β" to MathTextStyle.Italic),
            "gamma" to ("γ" to MathTextStyle.Italic), "delta" to ("δ" to MathTextStyle.Italic),
            "epsilon" to ("ϵ" to MathTextStyle.Italic), "varepsilon" to ("ε" to MathTextStyle.Italic),
            "zeta" to ("ζ" to MathTextStyle.Italic), "eta" to ("η" to MathTextStyle.Italic),
            "theta" to ("θ" to MathTextStyle.Italic), "vartheta" to ("ϑ" to MathTextStyle.Italic),
            "iota" to ("ι" to MathTextStyle.Italic), "kappa" to ("κ" to MathTextStyle.Italic),
            "lambda" to ("λ" to MathTextStyle.Italic), "mu" to ("μ" to MathTextStyle.Italic),
            "nu" to ("ν" to MathTextStyle.Italic), "xi" to ("ξ" to MathTextStyle.Italic),
            "pi" to ("π" to MathTextStyle.Italic), "varpi" to ("ϖ" to MathTextStyle.Italic),
            "rho" to ("ρ" to MathTextStyle.Italic), "sigma" to ("σ" to MathTextStyle.Italic),
            "tau" to ("τ" to MathTextStyle.Italic), "upsilon" to ("υ" to MathTextStyle.Italic),
            "phi" to ("ϕ" to MathTextStyle.Italic), "varphi" to ("φ" to MathTextStyle.Italic),
            "chi" to ("χ" to MathTextStyle.Italic), "psi" to ("ψ" to MathTextStyle.Italic),
            "omega" to ("ω" to MathTextStyle.Italic), "Gamma" to ("Γ" to MathTextStyle.Normal),
            "Delta" to ("Δ" to MathTextStyle.Normal), "Theta" to ("Θ" to MathTextStyle.Normal),
            "Lambda" to ("Λ" to MathTextStyle.Normal), "Xi" to ("Ξ" to MathTextStyle.Normal),
            "Pi" to ("Π" to MathTextStyle.Normal), "Sigma" to ("Σ" to MathTextStyle.Normal),
            "Upsilon" to ("Υ" to MathTextStyle.Normal), "Phi" to ("Φ" to MathTextStyle.Normal),
            "Psi" to ("Ψ" to MathTextStyle.Normal), "Omega" to ("Ω" to MathTextStyle.Normal),
            "infty" to ("∞" to MathTextStyle.Normal), "cdot" to ("·" to MathTextStyle.Normal),
            "times" to ("×" to MathTextStyle.Normal), "div" to ("÷" to MathTextStyle.Normal),
            "pm" to ("±" to MathTextStyle.Normal), "mp" to ("∓" to MathTextStyle.Normal),
            "le" to ("≤" to MathTextStyle.Normal), "leq" to ("≤" to MathTextStyle.Normal),
            "ge" to ("≥" to MathTextStyle.Normal), "geq" to ("≥" to MathTextStyle.Normal),
            "neq" to ("≠" to MathTextStyle.Normal), "ne" to ("≠" to MathTextStyle.Normal),
            "approx" to ("≈" to MathTextStyle.Normal), "sim" to ("∼" to MathTextStyle.Normal),
            "equiv" to ("≡" to MathTextStyle.Normal), "propto" to ("∝" to MathTextStyle.Normal),
            "to" to ("→" to MathTextStyle.Normal), "rightarrow" to ("→" to MathTextStyle.Normal),
            "leftarrow" to ("←" to MathTextStyle.Normal), "Rightarrow" to ("⇒" to MathTextStyle.Normal),
            "Leftrightarrow" to ("⇔" to MathTextStyle.Normal), "partial" to ("∂" to MathTextStyle.Italic),
            "nabla" to ("∇" to MathTextStyle.Normal), "forall" to ("∀" to MathTextStyle.Normal),
            "exists" to ("∃" to MathTextStyle.Normal), "in" to ("∈" to MathTextStyle.Normal),
            "notin" to ("∉" to MathTextStyle.Normal), "subset" to ("⊂" to MathTextStyle.Normal),
            "subseteq" to ("⊆" to MathTextStyle.Normal), "supset" to ("⊃" to MathTextStyle.Normal),
            "supseteq" to ("⊇" to MathTextStyle.Normal), "cup" to ("∪" to MathTextStyle.Normal),
            "cap" to ("∩" to MathTextStyle.Normal), "wedge" to ("∧" to MathTextStyle.Normal),
            "vee" to ("∨" to MathTextStyle.Normal), "neg" to ("¬" to MathTextStyle.Normal),
            "ldots" to ("…" to MathTextStyle.Normal), "cdots" to ("⋯" to MathTextStyle.Normal),
            "dots" to ("…" to MathTextStyle.Normal), "colon" to (":" to MathTextStyle.Normal),
            "angle" to ("∠" to MathTextStyle.Normal), "langle" to ("⟨" to MathTextStyle.Normal),
            "rangle" to ("⟩" to MathTextStyle.Normal), "lbrace" to ("{" to MathTextStyle.Normal),
            "rbrace" to ("}" to MathTextStyle.Normal), "sum" to ("∑" to MathTextStyle.Operator),
            "prod" to ("∏" to MathTextStyle.Operator), "int" to ("∫" to MathTextStyle.Operator),
            "iint" to ("∬" to MathTextStyle.Operator), "iiint" to ("∭" to MathTextStyle.Operator),
            "oint" to ("∮" to MathTextStyle.Operator), "lim" to ("lim" to MathTextStyle.Operator),
            "max" to ("max" to MathTextStyle.Operator), "min" to ("min" to MathTextStyle.Operator),
            "sup" to ("sup" to MathTextStyle.Operator), "inf" to ("inf" to MathTextStyle.Operator),
            "det" to ("det" to MathTextStyle.Operator), "gcd" to ("gcd" to MathTextStyle.Operator),
            "mod" to ("mod" to MathTextStyle.Operator),
            "log" to ("log" to MathTextStyle.Operator), "ln" to ("ln" to MathTextStyle.Operator),
            "sin" to ("sin" to MathTextStyle.Operator), "cos" to ("cos" to MathTextStyle.Operator),
            "tan" to ("tan" to MathTextStyle.Operator), "exp" to ("exp" to MathTextStyle.Operator),
        )

        private fun symbolForEscaped(value: Char): String = when (value) {
            '{' -> "{"; '}' -> "}"; '[' -> "["; ']' -> "]"; '_' -> "_"; '%' -> "%"; '$' -> "$"; '&' -> "&"; '#' -> "#";
            ',', ';', ':', '!', ' ' -> " "; else -> value.toString()
        }
    }
}

private fun splitMatrixRows(value: String): List<String> = splitTopLevel(value, "\\\\")

private fun splitMatrixCells(value: String): List<String> = splitTopLevel(value, "&")

private fun splitTopLevel(value: String, delimiter: String): List<String> {
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    var cursor = 0
    while (cursor < value.length) {
        when (value[cursor]) {
            '{', '[' -> depth++
            '}', ']' -> depth = (depth - 1).coerceAtLeast(0)
        }
        if (depth == 0 && value.startsWith(delimiter, cursor)) {
            result += value.substring(start, cursor)
            cursor += delimiter.length
            start = cursor
        } else cursor++
    }
    result += value.substring(start)
    return result
}

private fun toSuperscript(value: String): String = value.map { SUPERSCRIPT[it] ?: it }.joinToString("")
private fun toSubscript(value: String): String = value.map { SUBSCRIPT[it] ?: it }.joinToString("")

private val SUPERSCRIPT = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'i' to 'ⁱ', 'n' to 'ⁿ',
)
private val SUBSCRIPT = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ', 'y' to 'ᵧ',
)

private object MathExpressionCache {
    private const val MAX_ENTRIES = 160
    private val values = object : LinkedHashMap<String, MathNode>(MAX_ENTRIES, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MathNode>?): Boolean = size > MAX_ENTRIES
    }

    fun get(source: String): MathNode = synchronized(values) {
        values[source] ?: LatexMathParser(source).parse().also { values[source] = it }
    }
}
