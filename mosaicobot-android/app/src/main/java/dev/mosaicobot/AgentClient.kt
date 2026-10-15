package dev.mosaicobot

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AgentClient(private val client: OkHttpClient = agentNetworkClient()) {
    private fun endpoint(config: AgentConfig): String {
        require(config.apiKey.isNotBlank()) { "请在设备页配置手机 Agent 的 Key" }
        require(config.model.isNotBlank()) { "请配置模型名称" }
        val base = config.baseUrl.trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) { "模型接口必须使用不含凭据的 HTTPS 地址" }
        return base.toString().trimEnd('/') + "/chat/completions"
    }
    private suspend fun completion(config: AgentConfig, messages: JSONArray, maxTokens: Int): JSONObject {
        val body = JSONObject().put("model", config.model).put("messages", messages)
            .put("stream", false).put("max_tokens", maxTokens)
        // This provider includes reasoning in the output budget. Keep simple screen apps bounded.
        // Do not send a provider-specific option to arbitrary user-configured models.
        if (config.model == "step-5-preview:stepfun") body.put("reasoning_effort", "low")
        val request = Request.Builder().url(endpoint(config))
            .header("Authorization", "Bearer " + config.apiKey).header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return JSONObject(modelClient(client, config).fetch(request)).getJSONArray("choices").getJSONObject(0)
    }
    suspend fun checkConnection(config: AgentConfig) {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with exactly OK."))
        val choice = completion(config, messages, 64)
        require(choice.getJSONObject("message").optString("content").trim().isNotEmpty()) { "模型返回空内容，请检查模型配置" }
    }
    suspend fun generate(config: AgentConfig, contract: String, intent: String, previous: Work?, stage: (String) -> Unit): Work {
        endpoint(config)
        return withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
            generateValidated(config, contract, intent, previous, stage)
        } ?: throw IOException("Agent 自动续写与修复超时，未保存残缺应用；原需求和已有版本已保留，可重试")
    }

    private suspend fun generateValidated(config: AgentConfig, contract: String, intent: String, previous: Work?, stage: (String) -> Unit): Work {
        val base = JSONArray().put(JSONObject().put("role", "system").put("content", contract))
        if (previous != null) base.put(JSONObject().put("role", "user").put("content", "Existing app to revise:\n" + previous.lua))
        // Never silently drop the end of the user's requirements during a retry.
        base.put(JSONObject().put("role", "user").put("content", intent))
        var messages = base
        var draft = ""
        var continuing = false
        var continuations = 0
        var repairs = 0
        var nextStage = "手机 Agent 正在生成 Lua 应用"
        var lastError = "未收到完整应用包"
        fun parseGenerated(output: String): Work = ArtifactValidator.parse(output, intent, previous).also {
            GeneratedGamePolicy.validate(it.lua)
        }
        repeat(MAX_CALLS) { callIndex ->
            currentCoroutineContext().ensureActive()
            stage("$nextStage（请求 "+(callIndex + 1)+"/$MAX_CALLS）")
            currentCoroutineContext().ensureActive()
            val choice = completion(config, messages, 16384)
            currentCoroutineContext().ensureActive()
            val message = choice.getJSONObject("message")
            val finish = choice.optString("finish_reason")
            if (finish == "content_filter" || (!message.isNull("refusal") && message.optString("refusal").isNotBlank())) {
                throw UnsupportedOperationException("模型拒绝生成此应用；未继续重试或部署")
            }
            require(finish in setOf("", "stop", "length")) { "模型未返回应用内容（不支持的结束状态），未保存应用" }
            val output = if (message.isNull("content")) "" else message.optString("content")
            stage("校验应用包、Lua 语法与能力范围")
            currentCoroutineContext().ensureActive()
            // A restarted app must replace, never concatenate with, the old prefix.
            // Recognize raw headers even when the replacement itself is truncated.
            var replacement = continuing && ArtifactEnvelope.startsRawApp(output)
            if (continuing) {
                try { return parseGenerated(output) }
                catch (e: UnsupportedOperationException) { throw e }
                catch (_: ArtifactFormatException) { /* An actual suffix still needs its prefix. */ }
                catch (_: IllegalArgumentException) { replacement = true /* Complete envelope, bad Lua. */ }
                catch (_: org.json.JSONException) { replacement = true /* Complete JSON, missing fields. */ }
            }
            val candidate = if (continuing && !replacement) draft + output else output
            if (replacement) continuations = 0
            var incomplete = false
            var completeCandidate = false
            try {
                require(candidate.length <= MAX_DRAFT_CHARS) { "应用包超过组装大小限制" }
                // A provider may report length even after emitting a complete app.
                // Completeness and Lua validation, not finish_reason alone, decide.
                val work = ArtifactValidator.parse(candidate, intent, previous)
                // A complete app missing controls needs a full repair, never a suffix.
                completeCandidate = true
                GeneratedGamePolicy.validate(work.lua)
                return work
            } catch (e: UnsupportedOperationException) { throw e }
            catch (e: ArtifactFormatException) { lastError = e.message ?: "应用包格式错误"; incomplete = e.incomplete }
            catch (e: IllegalArgumentException) { lastError = e.message ?: "应用包校验失败" }
            catch (e: org.json.JSONException) { lastError = "应用包缺少合法的 title、summary 或 lua 字段" }

            if (callIndex == MAX_CALLS - 1) return@repeat
            val canContinue = !completeCandidate && (finish == "length" || incomplete) && output.isNotBlank() &&
                candidate != draft && candidate.length < MAX_DRAFT_CHARS && continuations < MAX_CONTINUATIONS
            val instruction: String
            if (canContinue) {
                draft = candidate
                continuing = true
                continuations++
                nextStage = "应用输出尚未完整，Agent 正在自动续写（$continuations/$MAX_CONTINUATIONS）"
                instruction = "The previous assistant message is the exact incomplete app prefix. " +
                    "Continue from its final character. Return ONLY the missing suffix, with no repeated prefix, " +
                    "no new app header and no explanation. Your text is appended directly; never guess overlaps. " +
                    (if (ArtifactEnvelope.startsRawApp(candidate))
                        "Preserve raw Lua quotes, backslashes and newlines. Complete all behavior and finish with a newline then -- MOSAICOBOT_END. Close any existing outer code fence. "
                    else "This legacy prefix is JSON: preserve JSON string escaping, including a trailing backslash or partial Unicode escape, and close the JSON and any outer fence. ") +
                    "Do not simplify or omit requirements."
            } else {
                if (repairs == MAX_REPAIRS) throw IllegalArgumentException("Agent 自动修复后仍未通过校验：" + lastError.take(160) + "；未保存残缺应用，原需求已保留")
                repairs++
                continuing = false
                continuations = 0
                draft = ""
                nextStage = "Agent 正在完整修复应用（$repairs/$MAX_REPAIRS）"
                instruction = "Validation failed: " + lastError.take(200) +
                    ". Return a corrected COMPLETE RAW LUA app, not JSON and not a suffix. " +
                    "Use -- MOSAICOBOT_APP_V1, then -- title: Chinese title, then -- summary: one-line Chinese summary, " +
                    "then unescaped Lua source, and finally -- MOSAICOBOT_END on its own line. " +
                    "The prior attempt was incomplete, " +
                    "invalid or made no progress. Preserve ALL original requirements and the existing app behavior " +
                    "except the requested edits. Use concise code and fewer comments, never drop features. " +
                    "Lua source must fit 65536 UTF-8 bytes. Preserve the interaction declaration and both touch/gamepad " +
                    "controls for games; do not reclassify a game as app or game-touch just to pass validation. " +
                    "Only an explicit user request for touch-only permits game-touch. " +
                    "No prose or placeholders. If unsupported, return the contract refusal."
            }
            // Keep original intent/source plus ONE current draft, not every growing prefix.
            messages = JSONArray(base.toString())
            if (candidate.isNotBlank()) messages.put(JSONObject().put("role", "assistant").put("content", candidate.take(MAX_DRAFT_CHARS)))
            messages.put(JSONObject().put("role", "user").put("content", instruction))
        }
        throw IllegalArgumentException("Agent 已达到 $MAX_CALLS 次生成/续写上限：" + lastError.take(160) + "；未保存残缺应用，原需求已保留，可重试")
    }

    private companion object {
        const val MAX_CALLS = 8
        const val MAX_CONTINUATIONS = 3
        const val MAX_REPAIRS = 2
        const val MAX_DRAFT_CHARS = 512 * 1024
        const val GENERATION_TIMEOUT_MS = 12 * 60 * 1000L
    }

}
