package com.simplexray.an.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val BRACKET_PAIRS = mapOf('{' to '}', '[' to ']')
private val OPEN_BRACKETS = BRACKET_PAIRS.keys
private val CLOSE_BRACKETS = BRACKET_PAIRS.values
private val ALL_BRACKETS = OPEN_BRACKETS + CLOSE_BRACKETS

@Composable
fun bracketMatcherTransformation(textFieldValue: TextFieldValue): VisualTransformation {
    val highlightStyle =
        SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.33f))

    return remember(textFieldValue.text, textFieldValue.selection) {
        VisualTransformation { originalText ->
            val text = originalText.text
            if (text.isEmpty()) {
                return@VisualTransformation TransformedText(originalText, OffsetMapping.Identity)
            }
            findMatchingBrackets(text, textFieldValue.selection.start)?.let { (start, end) ->
                val annotatedString = buildAnnotatedString {
                    append(originalText)
                    addStyle(highlightStyle, start, start + 1)
                    addStyle(highlightStyle, end, end + 1)
                }
                return@VisualTransformation TransformedText(annotatedString, OffsetMapping.Identity)
            }
            TransformedText(originalText, OffsetMapping.Identity)
        }
    }
}

private fun findMatchingBrackets(text: String, cursor: Int): Pair<Int, Int>? {
    val charIndex = (cursor - 1).takeIf { it >= 0 && text[it] in ALL_BRACKETS }
        ?: cursor.takeIf { it < text.length && text[it] in ALL_BRACKETS }
        ?: return null

    return when (val char = text[charIndex]) {
        in OPEN_BRACKETS -> findClosingBracket(text, charIndex, char)?.let { charIndex to it }
        in CLOSE_BRACKETS -> findOpeningBracket(text, charIndex, char)?.let { it to charIndex }
        else -> null
    }
}

private fun findClosingBracket(text: String, startIndex: Int, openChar: Char): Int? {
    if (isInsideString(text, startIndex)) return null

    val closeChar = BRACKET_PAIRS[openChar] ?: return null
    var balance = 1
    var inString = false
    var i = startIndex + 1

    while (i < text.length) {
        when {
            text[i] == '\\' -> {
                i++
            }

            text[i] == '"' -> inString = !inString
            !inString && text[i] == openChar -> balance++
            !inString && text[i] == closeChar -> {
                balance--
                if (balance == 0) return i
            }
        }
        i++
    }
    return null
}

private fun findOpeningBracket(text: String, startIndex: Int, closeChar: Char): Int? {
    if (isInsideString(text, startIndex)) return null

    val openChar = BRACKET_PAIRS.entries.find { it.value == closeChar }?.key ?: return null
    var balance = 1
    var i = startIndex - 1

    while (i >= 0) {
        if (!isInsideString(text, i)) {
            when (text[i]) {
                openChar -> {
                    balance--
                    if (balance == 0) return i
                }

                closeChar -> balance++
            }
        }
        i--
    }
    return null
}

private fun isInsideString(text: String, index: Int): Boolean {
    var inString = false
    var i = 0
    while (i < index) {
        when {
            text[i] == '\\' -> i++
            text[i] == '"' -> inString = !inString
        }
        i++
    }
    return inString
}