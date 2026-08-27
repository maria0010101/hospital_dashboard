package com.example.hospital_dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 極簡 Markdown 區塊模型（標題/條列/表格/段落/分隔線/程式碼）。 */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val index: Int, val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val text: String) : MdBlock()
    data object Divider : MdBlock()
}

/** 簡易 Markdown → 區塊清單（支援標題/條列/表格/粗體/行內程式碼/分隔線/程式碼塊）。 */
fun parseMarkdown(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        when {
            line.startsWith("```") -> {
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    sb.appendLine(lines[i]); i++
                }
                i++ // 跳過結束 ``` 
                blocks.add(MdBlock.Code(sb.toString().trimEnd()))
            }
            line.startsWith("|") -> {
                val tableLines = mutableListOf(line)
                i++
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    tableLines.add(lines[i]); i++
                }
                // 過濾分隔列 |---|---|
                val data = tableLines.filter { l ->
                    !Regex("^\\|?[\\s\\|:\\-]+\\|?$").matches(l.trim())
                }
                val parsed = data.map { l ->
                    l.trim().trim('|').split("|").map { it.trim() }
                }
                if (parsed.isNotEmpty()) {
                    val header = parsed.first()
                    val rows = parsed.drop(1).filter { it.isNotEmpty() && it.any { c -> c.isNotBlank() } }
                    blocks.add(MdBlock.Table(header, rows))
                }
            }
            line.startsWith("###") -> { blocks.add(MdBlock.Heading(3, line.removePrefix("###").trim())); i++ }
            line.startsWith("##") -> { blocks.add(MdBlock.Heading(2, line.removePrefix("##").trim())); i++ }
            line.startsWith("#") -> { blocks.add(MdBlock.Heading(1, line.removePrefix("#").trim())); i++ }
            line.trim().matches(Regex("^[-*_]{3,}$")) -> { blocks.add(MdBlock.Divider); i++ }
            line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(line.trim().removePrefix("- ").removePrefix("* "))); i++
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(line.trim()) -> {
                val idx = Regex("^(\\d+)[.)]\\s+").find(line.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 1
                blocks.add(MdBlock.Numbered(idx, line.trim().replaceFirst(Regex("^\\d+[.)]\\s+"), ""))); i++
            }
            line.isBlank() -> { i++ }
            else -> {
                val sb = StringBuilder(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() &&
                    !lines[i].trimStart().startsWith("|") &&
                    !lines[i].trimStart().startsWith("- ") &&
                    !lines[i].trimStart().startsWith("* ") &&
                    !Regex("^\\d+[.)]\\s+").containsMatchIn(lines[i].trim()) &&
                    !lines[i].trimStart().startsWith("#")) {
                    sb.append("\n").append(lines[i]); i++
                }
                blocks.add(MdBlock.Paragraph(sb.toString()))
            }
        }
    }
    return blocks
}

/** 行內格式：**粗體**、`行內程式碼` → AnnotatedString。 */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var pos = 0
    val tokens = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)").findAll(text).toList()
    tokens.forEach { m ->
        append(text.substring(pos, m.range.first))
        val tok = m.value
        if (tok.startsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(tok.removePrefix("**").removeSuffix("**"))
            }
        } else {
            withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0xFFEEEEEE),
                color = Color(0xFFC0392B)
            )) {
                append(tok.removePrefix("`").removeSuffix("`"))
            }
        }
        pos = m.range.last + 1
    }
    append(text.substring(pos))
}

/** Markdown 區塊渲染（AI 分析結果用）。 */
@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier) {
    val blocks = parseMarkdown(md)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(b.text),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (b.level <= 2) 4.dp else 0.dp)
                )
                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(inlineMarkdown(b.text), style = MaterialTheme.typography.bodyMedium)
                }
                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text("${b.index}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(inlineMarkdown(b.text), style = MaterialTheme.typography.bodyMedium)
                }
                is MdBlock.Table -> MdTable(b)
                is MdBlock.Paragraph -> Text(inlineMarkdown(b.text), style = MaterialTheme.typography.bodyMedium)
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF4F4F4)).padding(8.dp)
                ) {
                    Text(b.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                MdBlock.Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDDDDDD)))
            }
        }
    }
}

/** Markdown 表格：表頭粗體，各列以等寬間隔呈現。 */
@Composable
private fun MdTable(t: MdBlock.Table) {
    val colWidths = (0 until (t.header.size)).map { ci ->
        (listOf(t.header.getOrNull(ci) ?: "") + t.rows.map { it.getOrNull(ci) ?: "" })
            .maxOf { it.length }.coerceIn(3, 14)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF8F8F8)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            t.header.forEachIndexed { ci, h ->
                Text(h, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.weight(colWidths[ci].toFloat()))
            }
        }
        t.rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                t.header.indices.forEach { ci ->
                    Text(row.getOrNull(ci) ?: "", fontSize = 12.sp,
                        modifier = Modifier.weight(colWidths[ci].toFloat()))
                }
            }
        }
    }
}

/** Markdown → 簡易 HTML（供 PDF 匯出）。 */
fun markdownToHtml(md: String): String {
    val sb = StringBuilder()
    sb.append("<html><head><meta charset=\"utf-8\"><style>")
    sb.append("body{font-family:sans-serif;font-size:14px;line-height:1.6;padding:16px;color:#222}")
    sb.append("h1{font-size:20px}h2{font-size:17px}h3{font-size:15px}")
    sb.append("table{border-collapse:collapse;width:100%;margin:6px 0}")
    sb.append("th,td{border:1px solid #ccc;padding:4px 8px;font-size:13px;text-align:left}")
    sb.append("th{background:#f0f0f0}code{background:#eee;padding:1px 4px;border-radius:3px}")
    sb.append("li{margin:2px 0}</style></head><body>")
    parseMarkdown(md).forEach { b ->
        when (b) {
            is MdBlock.Heading -> sb.append("<h${b.level}>${htmlEsc(b.text)}</h${b.level}>")
            is MdBlock.Bullet -> sb.append("<li>${htmlInline(b.text)}</li>")
            is MdBlock.Numbered -> sb.append("<li>${htmlInline(b.text)}</li>")
            is MdBlock.Table -> {
                sb.append("<table><tr>")
                b.header.forEach { sb.append("<th>${htmlInline(it)}</th>") }
                sb.append("</tr>")
                b.rows.forEach { r ->
                    sb.append("<tr>")
                    b.header.indices.forEach { ci -> sb.append("<td>${htmlInline(r.getOrNull(ci) ?: "")}</td>") }
                    sb.append("</tr>")
                }
                sb.append("</table>")
            }
            is MdBlock.Paragraph -> sb.append("<p>${htmlInline(b.text)}</p>")
            is MdBlock.Code -> sb.append("<pre><code>${htmlEsc(b.text)}</code></pre>")
            MdBlock.Divider -> sb.append("<hr>")
        }
    }
    sb.append("</body></html>")
    return sb.toString()
}

private fun htmlInline(s: String): String {
    var t = htmlEsc(s)
    t = t.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
    t = t.replace(Regex("`([^`]+)`"), "<code>$1</code>")
    return t
}

private fun htmlEsc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
