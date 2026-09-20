package workbench.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

val AnsiBlack = Color(0xFF3B4252)
val AnsiRed = Color(0xFFFF6B6B)
val AnsiGreen = Color(0xFF51CF66)
val AnsiYellow = Color(0xFFFCC419)
val AnsiBlue = Color(0xFF4DABF7)
val AnsiMagenta = Color(0xFFCC5DE8)
val AnsiCyan = Color(0xFF20C997)
val AnsiWhite = Color(0xFFECEFF4)

val AnsiBrightBlack = Color(0xFF4C566A)
val AnsiBrightRed = Color(0xFFFF8787)
val AnsiBrightGreen = Color(0xFF69DB7C)
val AnsiBrightYellow = Color(0xFFFFD43B)
val AnsiBrightBlue = Color(0xFF74C0FC)
val AnsiBrightMagenta = Color(0xFFDA77F2)
val AnsiBrightCyan = Color(0xFF38D9A9)
val AnsiBrightWhite = Color(0xFFFFFFFF)

/**
 * Parses raw shell terminal output containing ANSI escape sequences into a Compose AnnotatedString.
 */
fun parseAnsi(text: String, defaultColor: Color = Color(0xFFD4F5E2)): AnnotatedString {
    // Regex matching CSI sequences: ESC [ parameters command
    val regex = Regex("\u001B\\[([0-9;]*)([a-zA-Z])")

    return buildAnnotatedString {
        var currentColor: Color = defaultColor
        var isBold = false
        var lastIdx = 0

        val matches = regex.findAll(text)
        for (m in matches) {
            val start = m.range.first
            if (start > lastIdx) {
                val plain = text.substring(lastIdx, start).replace("\r", "")
                if (plain.isNotEmpty()) {
                    append(
                        AnnotatedString(
                            text = plain,
                            spanStyle = SpanStyle(
                                color = currentColor,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    )
                }
            }
            lastIdx = m.range.last + 1

            val command = m.groupValues[2]
            if (command == "m") {
                // Color and style codes
                val paramStr = m.groupValues[1]
                val codes = if (paramStr.isEmpty()) listOf(0) else paramStr.split(";").mapNotNull { it.toIntOrNull() }
                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = defaultColor
                            isBold = false
                        }
                        1 -> isBold = true
                        22 -> isBold = false
                        30 -> currentColor = AnsiBlack
                        31 -> currentColor = AnsiRed
                        32 -> currentColor = AnsiGreen
                        33 -> currentColor = AnsiYellow
                        34 -> currentColor = AnsiBlue
                        35 -> currentColor = AnsiMagenta
                        36 -> currentColor = AnsiCyan
                        37 -> currentColor = AnsiWhite
                        39 -> currentColor = defaultColor
                        90 -> currentColor = AnsiBrightBlack
                        91 -> currentColor = AnsiBrightRed
                        92 -> currentColor = AnsiBrightGreen
                        93 -> currentColor = AnsiBrightYellow
                        94 -> currentColor = AnsiBrightBlue
                        95 -> currentColor = AnsiBrightMagenta
                        96 -> currentColor = AnsiBrightCyan
                        97 -> currentColor = AnsiBrightWhite
                    }
                }
            }
        }

        if (lastIdx < text.length) {
            val remaining = text.substring(lastIdx).replace("\r", "")
            if (remaining.isNotEmpty()) {
                append(
                    AnnotatedString(
                        text = remaining,
                        spanStyle = SpanStyle(
                            color = currentColor,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                )
            }
        }
    }
}
