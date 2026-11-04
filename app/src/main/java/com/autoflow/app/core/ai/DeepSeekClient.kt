package com.autoflow.app.core.ai

import com.autoflow.app.core.ActionSpec
import com.autoflow.app.core.ConstraintSpec
import com.autoflow.app.core.ItemSpec
import com.autoflow.app.core.Macro
import com.autoflow.app.core.Registry
import com.autoflow.app.core.TriggerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 调用 DeepSeek 生成单个宏，或生成一组「模板包」。
 */
class DeepSeekClient(private val settings: AiSettings) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 通过 OpenAI 兼容的 GET /models 拉取当前账号可用模型。 */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = settings.apiKey
            require(key.isNotBlank()) { "先填入 API Key" }
            val body = httpGet("/models", withAuth = true)
            Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }
                .filter { it.isNotBlank() }
        }
    }

    /** 生成单个宏。 */
    suspend fun generateMacro(userPrompt: String): Result<Macro> = withContext(Dispatchers.IO) {
        runCatching {
            val content = chat(systemPromptSingle(), userPrompt)
            buildMacro(parseSingle(content))
        }
    }

    /** 生成模板包（3-5 个成套的宏）。 */
    suspend fun generateTemplates(userPrompt: String): Result<List<Macro>> = withContext(Dispatchers.IO) {
        runCatching {
            val content = chat(systemPromptTemplates(), userPrompt)
            val cleaned = extractJson(content)
            val pack = json.decodeFromString<AiTemplatePackDto>(cleaned)
            val built = pack.templates.mapNotNull { dto ->
                runCatching { buildMacro(dto) }.getOrNull()
            }
            require(built.isNotEmpty()) { "AI 没有给出可用的模板" }
            built
        }
    }

    // ---------------- HTTP ----------------

    private fun chat(system: String, user: String): String {
        val key = settings.apiKey
        require(key.isNotBlank()) { "还没有配置 DeepSeek API Key" }
        require(settings.model.isNotBlank()) { "还没有选择模型，请先到设置里拉取并选择" }

        val requestBody = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.3)
            put("response_format", buildJsonObject { put("type", "json_object") })
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }.toString()

        val text = httpPost("/chat/completions", requestBody)
        return Json.parseToJsonElement(text)
            .jsonObject["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!.jsonObject["content"]!!
            .jsonPrimitive.content
    }

    private fun httpPost(path: String, body: String): String {
        val url = URL(settings.baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer " + settings.apiKey)
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        }.getOrDefault("")
        conn.disconnect()
        if (code !in 200..299) error("DeepSeek 返回 $code：${text.take(300)}")
        return text
    }

    private fun httpGet(path: String, withAuth: Boolean): String {
        val url = URL(settings.baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            if (withAuth) setRequestProperty("Authorization", "Bearer " + settings.apiKey)
        }
        val code = conn.responseCode
        val text = runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        }.getOrDefault("")
        conn.disconnect()
        if (code !in 200..299) error("拉取模型失败（$code）：${text.take(200)}")
        return text
    }

    // ---------------- 解析 ----------------

    private fun extractJson(raw: String): String {
        val noFence = raw.replace("`", "").trim()
        val start = noFence.indexOf('{')
        val end = noFence.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 没有返回合法 JSON" }
        return noFence.substring(start, end + 1)
    }

    private fun parseSingle(content: String): AiMacroDto =
        json.decodeFromString(AiMacroDto.serializer(), extractJson(content))

    private fun buildMacro(dto: AiMacroDto): Macro {
        val triggers = dto.triggers
            .filter { Registry.trigger(it.type) != null }
            .map { TriggerSpec(it.type, normalizeParams(it.params, Registry.trigger(it.type))) }
            .ifEmpty { listOf(TriggerSpec("manual", emptyMap())) }

        val actions = dto.actions
            .filter { Registry.action(it.type) != null }
            .map { ActionSpec(it.type, normalizeParams(it.params, Registry.action(it.type))) }

        val constraints = dto.constraints
            .filter { Registry.constraint(it.type) != null }
            .map { ConstraintSpec(it.type, normalizeParams(it.params, Registry.constraint(it.type))) }

        require(actions.isNotEmpty()) { "缺少可用动作" }

        return Macro(
            name = dto.name.ifBlank { "AI 宏" },
            description = dto.description,
            triggers = triggers,
            actions = actions,
            constraints = constraints,
            accent = (0..5).random(),
        )
    }

    private fun normalizeParams(raw: Map<String, JsonElement>, spec: ItemSpec?): Map<String, String> {
        if (spec == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        spec.params.forEach { p ->
            val el = raw[p.key]
            val value = when (el) {
                null -> p.default
                is JsonPrimitive -> el.content
                else -> el.toString()
            }
            result[p.key] = if (value.isBlank() && p.default.isNotBlank()) p.default else value
        }
        return result
    }

    // ---------------- 提示词 ----------------

    private fun capabilityBlock(): String = buildString {
        appendLine("可用触发器（type: 参数）：")
        Registry.triggers.forEach { spec ->
            append("- ${spec.type}: ")
            append(spec.params.joinToString(", ") { it.key }.ifBlank { "无参数" })
            appendLine()
        }
        appendLine()
        appendLine("可用动作（按执行顺序）：")
        Registry.actions.forEach { spec ->
            append("- ${spec.type}: ")
            append(spec.params.joinToString(", ") { it.key }.ifBlank { "无参数" })
            appendLine()
        }
        appendLine()
        appendLine("可用条件（全部满足才执行）：")
        Registry.constraints.forEach { spec ->
            append("- ${spec.type}: ")
            append(spec.params.joinToString(", ") { it.key }.ifBlank { "无参数" })
            appendLine()
        }
    }

    private fun systemPromptSingle(): String = buildString {
        appendLine("你是 AutoFlow（Android 自动化工具）的宏生成器。")
        appendLine("唯一输出必须是一个 JSON 对象，不能有解释文字或 markdown。")
        appendLine(capabilityBlock())
        appendLine("输出结构：")
        appendLine("{\"name\":\"名称\",\"description\":\"说明\",\"triggers\":[{\"type\":\"manual\",\"params\":{}}],\"constraints\":[],\"actions\":[{\"type\":\"toast\",\"params\":{\"text\":\"你好\"}}]}")
        appendLine("规则：只能用上面的 type；params 值必须是字符串；每个宏至少一个触发器；if 与 endif、loop 与 endloop 必须成对。")
    }

    private fun systemPromptTemplates(): String = buildString {
        appendLine("你是 AutoFlow 的自动化模板设计师。")
        appendLine("根据用户的主题，设计 3-5 个互相配合、开箱即用的宏，组成一套模板包。")
        appendLine("唯一输出必须是一个 JSON 对象，不能有解释文字或 markdown。")
        appendLine(capabilityBlock())
        appendLine("输出结构：")
        appendLine("{\"templates\":[{\"name\":\"宏名\",\"description\":\"用途\",\"triggers\":[{\"type\":\"interval\",\"params\":{\"minutes\":\"30\"}}],\"constraints\":[],\"actions\":[{\"type\":\"notification\",\"params\":{\"title\":\"标题\",\"text\":\"内容\"}}]}]}")
        appendLine("规则：只能用上面的 type；params 值必须是字符串；每个宏至少一个触发器；控制流必须成对；模板之间要有互补性，覆盖不同场景。")
    }
}

@Serializable
data class AiItemDto(
    val type: String = "",
    val params: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class AiMacroDto(
    val name: String = "AI 宏",
    val description: String = "",
    val triggers: List<AiItemDto> = emptyList(),
    val constraints: List<AiItemDto> = emptyList(),
    val actions: List<AiItemDto> = emptyList(),
)

@Serializable
data class AiTemplatePackDto(
    val templates: List<AiMacroDto> = emptyList(),
)
