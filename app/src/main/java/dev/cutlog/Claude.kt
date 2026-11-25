package dev.cutlog

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.roundToInt

data class ChatMsg(val role: String, val text: String)

/** Minimal Anthropic Messages API client. Network only runs when you press send. */
object Claude {
    suspend fun send(
        apiKey: String,
        model: String,
        system: String,
        messages: List<ChatMsg>,
        maxTokens: Int = 1024,
    ): String = withContext(Dispatchers.IO) {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", apiKey)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            val msgs = JSONArray()
            messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", msgs)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrDefault(text.take(300))
                throw IOException("HTTP $code: $msg")
            }
            val content = JSONObject(text).getJSONArray("content")
            buildString {
                for (i in 0 until content.length()) {
                    val o = content.getJSONObject(i)
                    if (o.optString("type") == "text") append(o.getString("text"))
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /** Asks Claude to turn a free-text meal description into loggable items. */
    suspend fun parseMeal(store: Store, description: String): List<Preset> {
        val system = """
            You estimate nutrition for an Indian user's meals (canteen food, home air-fryer cooking).
            Reply with ONLY a JSON object, no prose, in this exact shape:
            {"items":[{"name":"short name with portion","kcal":0,"protein":0,"carbs":0,"fat":0}]}
            Use realistic Indian portion sizes and include cooking oil where it would be used.
            Numbers are per item as eaten. Protein/carbs/fat in grams.
        """.trimIndent()
        val reply = send(
            store.prefs.apiKey, store.prefs.model, system,
            listOf(ChatMsg("user", description)), maxTokens = 600,
        )
        val start = reply.indexOf('{'); val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Couldn't read Claude's estimate:\n$reply")
        val items = JSONObject(reply.substring(start, end + 1)).getJSONArray("items")
        return List(items.length()) {
            val o = items.getJSONObject(it)
            Preset(
                o.optString("name", "Item"),
                o.optDouble("kcal", 0.0).roundToInt(),
                o.optDouble("protein", 0.0).toFloat(),
                o.optDouble("carbs", 0.0).toFloat(),
                o.optDouble("fat", 0.0).toFloat(),
            )
        }
    }

    fun systemPrompt(store: Store): String = """
        You are the user's fat-loss coach inside their CutLog app. Be concise (mobile screen), practical, and honest.
        Profile: male, 22, 177 cm. InBody 15 Nov 2026: 98.8 kg, 35.6% body fat, SMM 35.9 kg, visceral fat level 16.
        Diet constraints: low egg and meat (intolerance). Protein mostly from whey, skim milk, hung curd, paneer, tofu, soy chunks; chicken at canteen lunches.
        Little time: dinner is a ~30 min air-fryer meal. Lifts 3-6x/week on machines, aims for 8-10k steps.
        Plan: if lunch was chicken, normal dinner; if veg thali, add a whey scoop. Don't eat back workout calories.

        Live data from the app:
        ${summary(store)}
    """.trimIndent()

    /** Plain-text summary used for the system prompt and for sharing to the Claude app. */
    fun summary(store: Store, days: Int = 7): String {
        val p = store.prefs
        val t = today()
        val from = t - (days - 1)
        val totals = store.db.dailyTotals(from, t)
        val workouts = store.db.workouts(from, t).groupBy { it.day }
        val weights = store.db.weights()
        return buildString {
            appendLine("Targets: ${p.kcalTarget} kcal/day (${p.kcalTarget * 7}/week), protein ${p.proteinTarget} g, carbs ${p.carbsTarget} g, fat ${p.fatTarget} g. Est. maintenance ${p.maintenance} kcal.")
            appendLine("Goal: ${p.goalWeight} kg by ${LocalDate.ofEpochDay(p.goalDate)}.")
            p.rotationFor(t)?.let { (item, d) -> appendLine("Today's dinner rotation: $item (day $d of ${p.rotationDays}).") }
            appendLine("Last $days days (kcal / protein g / workouts):")
            var sum = 0; var logged = 0
            for (d in from..t) {
                val tot = totals[d]
                val w = workouts[d].orEmpty()
                val ws = if (w.isEmpty()) "" else " | " + w.joinToString { "${it.name} ${it.minutes}min" }
                if (tot != null) { sum += tot.kcal; logged++ }
                appendLine("- ${LocalDate.ofEpochDay(d)}: " + (tot?.let { "${it.kcal} kcal, ${it.protein.roundToInt()} g P" } ?: "not logged") + ws)
            }
            if (logged > 0) appendLine("Avg on logged days: ${sum / logged} kcal.")
            val todayFoods = store.db.foods(t, t)
            if (todayFoods.isNotEmpty()) {
                appendLine("Today's entries:")
                todayFoods.forEach { appendLine("- ${it.meal}: ${it.name} (${it.kcal} kcal, ${it.protein.roundToInt()} g P)") }
            }
            if (weights.isNotEmpty()) {
                appendLine("Weigh-ins (latest 10): " + weights.takeLast(10).joinToString { "${LocalDate.ofEpochDay(it.day)} ${it.kg}kg" + (it.bodyFat?.let { b -> " ${b}%" } ?: "") })
            }
        }.trim()
    }

    /** Opens the Claude app with the summary if installed, otherwise the system share sheet. */
    fun shareToClaude(ctx: Context, store: Store) {
        val text = "Here's my CutLog data. Review my week and tell me what to adjust.\n\n" + summary(store)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val direct = Intent(send).setPackage("com.anthropic.claude")
        val intent = if (direct.resolveActivity(ctx.packageManager) != null) direct
        else Intent.createChooser(send, "Share with Claude")
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
