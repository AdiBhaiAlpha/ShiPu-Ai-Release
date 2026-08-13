package com.example.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Production-grade Markdown parser and renderer for Jetpack Compose.
 * Handles headings, bold/italic/strike, list items, code blocks with horizontal scroll and copy button,
 * blockquotes, tables, horizontal dividers, and links seamlessly.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    SelectionContainer {
        Column(modifier = modifier) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        HeadingBlock(block, textColor)
                    }
                    is MarkdownBlock.Code -> {
                        CodeBlockView(code = block.code, language = block.language, context = context)
                    }
                    is MarkdownBlock.ListBlock -> {
                        ListBlockView(block, textColor)
                    }
                    is MarkdownBlock.Quote -> {
                        QuoteBlockView(block, textColor)
                    }
                    is MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    }
                    is MarkdownBlock.Table -> {
                        TableBlockView(block, textColor)
                    }
                    is MarkdownBlock.Paragraph -> {
                        ParagraphBlock(block.text, textColor)
                    }
                }

                if (index < blocks.size - 1 && block !is MarkdownBlock.Divider) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Code(val code: String, val language: String) : MarkdownBlock()
    data class ListBlock(val items: List<ListItem>) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

data class ListItem(val text: String, val isOrdered: Boolean, val number: Int = 1, val indentLevel: Int = 0)

private fun parseMarkdown(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()

    var i = 0
    val total = lines.size

    while (i < total) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code block fence
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < total && !lines[i].trim().startsWith("```")) {
                codeBuilder.append(lines[i]).append("\n")
                i++
            }
            if (i < total && lines[i].trim().startsWith("```")) {
                i++ // consume closing ```
            }
            blocks.add(MarkdownBlock.Code(codeBuilder.toString().trimEnd(), language))
            continue
        }

        // 2. Horizontal Divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headings
        if (trimmed.startsWith("#")) {
            var level = 0
            while (level < trimmed.length && trimmed[level] == '#') {
                level++
            }
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                val headingText = trimmed.substring(level + 1).trim()
                blocks.add(MarkdownBlock.Heading(level, headingText))
                i++
                continue
            }
        }

        // 4. Blockquotes
        if (trimmed.startsWith(">")) {
            val quoteBuilder = StringBuilder()
            while (i < total && lines[i].trim().startsWith(">")) {
                val qLine = lines[i].trim().removePrefix(">").trim()
                quoteBuilder.append(qLine).append("\n")
                i++
            }
            blocks.add(MarkdownBlock.Quote(quoteBuilder.toString().trimEnd()))
            continue
        }

        // 5. Tables
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < total && lines[i + 1].trim().contains("---")) {
            val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            i += 2 // skip header and separator line
            val rows = mutableListOf<List<String>>()
            while (i < total && lines[i].trim().startsWith("|")) {
                val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                rows.add(rowCells)
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // 6. Lists
        val isUnordered = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
        val isOrdered = trimmed.matches(Regex("""^\d+\.\s.*"""))

        if (isUnordered || isOrdered) {
            val listItems = mutableListOf<ListItem>()
            while (i < total) {
                val curLine = lines[i]
                val curTrimmed = curLine.trim()
                val isCurUnordered = curTrimmed.startsWith("- ") || curTrimmed.startsWith("* ") || curTrimmed.startsWith("+ ")
                val isCurOrdered = curTrimmed.matches(Regex("""^\d+\.\s.*"""))

                if (!isCurUnordered && !isCurOrdered) break

                val indent = (curLine.length - curLine.trimStart().length) / 2
                if (isCurUnordered) {
                    val content = curTrimmed.substring(2).trim()
                    listItems.add(ListItem(content, false, indentLevel = indent))
                } else if (isCurOrdered) {
                    val parts = curTrimmed.split(Regex("""^\d+\.\s"""), 2)
                    val numMatch = Regex("""^(\d+)\.""").find(curTrimmed)
                    val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val content = if (parts.size > 1) parts[1].trim() else curTrimmed
                    listItems.add(ListItem(content, true, number = num, indentLevel = indent))
                }
                i++
            }
            blocks.add(MarkdownBlock.ListBlock(listItems))
            continue
        }

        // 7. Regular Paragraph
        if (trimmed.isNotEmpty()) {
            val paragraphBuilder = StringBuilder()
            while (i < total && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].trim().startsWith("#") &&
                !lines[i].trim().startsWith(">") &&
                !lines[i].trim().startsWith("|") &&
                !lines[i].trim().startsWith("- ") &&
                !lines[i].trim().startsWith("* ") &&
                !lines[i].trim().matches(Regex("""^\d+\.\s.*"""))
            ) {
                paragraphBuilder.append(lines[i]).append(" ")
                i++
            }
            blocks.add(MarkdownBlock.Paragraph(paragraphBuilder.toString().trim()))
            continue
        }

        i++
    }

    return blocks
}

@Composable
private fun HeadingBlock(heading: MarkdownBlock.Heading, textColor: Color) {
    val fontSize = when (heading.level) {
        1 -> 22.sp
        2 -> 19.sp
        3 -> 17.sp
        else -> 15.sp
    }
    val fontWeight = if (heading.level <= 2) FontWeight.Bold else FontWeight.SemiBold

    Text(
        text = parseFormattedText(heading.text, textColor),
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = textColor,
        lineHeight = (fontSize.value * 1.3).sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ParagraphBlock(text: String, textColor: Color) {
    Text(
        text = parseFormattedText(text, textColor),
        fontSize = 15.sp,
        color = textColor,
        lineHeight = 22.sp
    )
}

@Composable
private fun ListBlockView(listBlock: MarkdownBlock.ListBlock, textColor: Color) {
    Column {
        listBlock.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.indentLevel * 16).dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (item.isOrdered) "${item.number}. " else "• ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = parseFormattedText(item.text, textColor),
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuoteBlockView(quote: MarkdownBlock.Quote, textColor: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = parseFormattedText(quote.text, textColor),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = textColor.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TableBlockView(table: MarkdownBlock.Table, textColor: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                table.headers.forEach { header ->
                    Text(
                        text = header,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier
                            .width(110.dp)
                            .padding(8.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Data Rows
            table.rows.forEach { row ->
                Row {
                    table.headers.indices.forEach { colIdx ->
                        val cellText = row.getOrNull(colIdx) ?: ""
                        Text(
                            text = cellText,
                            fontSize = 12.sp,
                            color = textColor,
                            modifier = Modifier
                                .width(110.dp)
                                .padding(8.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    code: String,
    language: String,
    context: Context
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("code_block_container"),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0D1117),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column {
            // Language and Copy Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "Code" }.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8B949E)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF21262D), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code Snippet", code)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("copy_code_block_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) Color(0xFF3FB950) else Color(0xFFC9D1D9),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied!" else "Copy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (copied) Color(0xFF3FB950) else Color(0xFFC9D1D9)
                    )
                }
            }

            // Code Content Box with Horizontal Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE6EDF3),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

/**
 * Helper to build an AnnotatedString with formatting for inline bold (**), italic (*), strikethrough (~~),
 * inline code (`), and links ([Text](URL)).
 */
private fun parseFormattedText(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Bold (**text**)
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val endIdx = text.indexOf("**", cursor + 2)
                if (endIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(cursor + 2, endIdx))
                    }
                    cursor = endIdx + 2
                    continue
                }
            }

            // Italic (*text*)
            if (text[cursor] == '*' && (cursor + 1 >= length || text[cursor + 1] != '*')) {
                val endIdx = text.indexOf('*', cursor + 1)
                if (endIdx != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(cursor + 1, endIdx))
                    }
                    cursor = endIdx + 1
                    continue
                }
            }

            // Strikethrough (~~text~~)
            if (cursor + 1 < length && text[cursor] == '~' && text[cursor + 1] == '~') {
                val endIdx = text.indexOf("~~", cursor + 2)
                if (endIdx != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(cursor + 2, endIdx))
                    }
                    cursor = endIdx + 2
                    continue
                }
            }

            // Inline Code (`code`)
            if (text[cursor] == '`') {
                val endIdx = text.indexOf('`', cursor + 1)
                if (endIdx != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = textColor.copy(alpha = 0.12f)
                        )
                    ) {
                        append(" ${text.substring(cursor + 1, endIdx)} ")
                    }
                    cursor = endIdx + 1
                    continue
                }
            }

            // Links ([Text](URL))
            if (text[cursor] == '[') {
                val closeBracket = text.indexOf(']', cursor + 1)
                if (closeBracket != -1 && closeBracket + 1 < length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(cursor + 1, closeBracket)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF2F81F7),
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(linkText)
                        }
                        cursor = closeParen + 1
                        continue
                    }
                }
            }

            // Regular character
            append(text[cursor])
            cursor++
        }
    }
}
