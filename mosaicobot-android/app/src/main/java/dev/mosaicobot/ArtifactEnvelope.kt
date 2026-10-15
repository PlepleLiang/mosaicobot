package dev.mosaicobot

import org.json.JSONObject
import org.json.JSONTokener

/** Format errors contain no model text; unfinished envelopes can be continued even after "stop". */
class ArtifactFormatException(message: String, val incomplete: Boolean = false) : IllegalArgumentException(message)

/** Model transport only. Never fixes/executes Lua or guesses missing source characters. */
object ArtifactEnvelope {
    const val START = "-- MOSAICOBOT_APP_V1"
    const val END = "-- MOSAICOBOT_END"
    private val openingFence = Regex("""\A```(?:lua|json|text)?[ \t]*\r?\n""", RegexOption.IGNORE_CASE)
    private val closingFence = Regex("""(?m)^```[ \t]*\z""")
    private val endLine = Regex("""(?m)^-- MOSAICOBOT_END[ \t]*\r?$""")
    private val startLine = Regex("""(?m)^-- MOSAICOBOT_APP_V1[ \t]*\r?$""")

    private fun cleanStart(output: String) = output.trim().removePrefix("\uFEFF").trimStart()

    fun startsRawApp(output: String): Boolean {
        val clean = cleanStart(output).replaceFirst(openingFence, "")
        return clean.lineSequence().first().trimEnd() == START
    }

    fun decode(output: String): JSONObject {
        var clean = cleanStart(output)
        val opening = openingFence.find(clean)
        if (opening != null) {
            val closing = closingFence.find(clean)
                ?: throw ArtifactFormatException("应用包代码块尚未闭合", incomplete = true)
            if (closing.range.first <= opening.range.last) throw ArtifactFormatException("应用包代码块为空")
            clean = clean.substring(opening.range.last + 1, closing.range.first).trimEnd()
        }
        if (clean.isBlank()) throw ArtifactFormatException("模型未返回应用内容")
        if (startsRawApp(clean)) return decodeRaw(clean)
        val tokens = JSONTokener(clean)
        val value = try { tokens.nextValue() }
        catch (_: Exception) { throw ArtifactFormatException("旧 JSON 包装损坏（可能存在未转义引号、换行或截断）；请改为原始 Lua 应用包") }
        if (value !is JSONObject) throw ArtifactFormatException("无法识别应用包格式；需要带开始与结束标记的原始 Lua 应用包")
        if (tokens.nextClean() != '\u0000') throw ArtifactFormatException("应用包后存在额外内容或第二个应用包；需要唯一完整应用")
        return value
    }

    private fun decodeRaw(clean: String): JSONObject {
        if (startLine.findAll(clean).count() != 1) throw ArtifactFormatException("回复包含多个应用开始标记，请只返回一个完整应用")
        val lines = clean.split('\n', limit = 4)
        if (lines.size < 4) throw ArtifactFormatException("应用包标题、说明或源码尚未完整返回", incomplete = true)
        val title = lines[1].removeSuffix("\r")
        val summary = lines[2].removeSuffix("\r")
        if (!title.startsWith("-- title:") || !summary.startsWith("-- summary:")) {
            throw ArtifactFormatException("应用包头须依次为 -- title: 标题 和 -- summary: 单行说明")
        }
        val body = lines[3]
        val ends = endLine.findAll(body).toList()
        if (ends.isEmpty()) throw ArtifactFormatException("应用源码缺少 MOSAICOBOT_END 结束标记", incomplete = true)
        if (ends.size != 1 || ends.single().range.last != body.lastIndex) {
            throw ArtifactFormatException("应用结束标记重复或其后存在额外内容；需要唯一完整应用")
        }
        // Remove only the transport's newline before END; preserve all Lua escapes and source text.
        val lua = body.substring(0, ends.single().range.first).removeSuffix("\n").removeSuffix("\r")
        return JSONObject().put("title", title.removePrefix("-- title:").trim())
            .put("summary", summary.removePrefix("-- summary:").trim()).put("lua", lua)
    }
}
