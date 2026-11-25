package dev.cutlog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(store: Store) {
    val p = store.prefs
    val ctx = LocalContext.current
    var kcal by remember { mutableStateOf(p.kcalTarget.toString()) }
    var protein by remember { mutableStateOf(p.proteinTarget.toString()) }
    var carbs by remember { mutableStateOf(p.carbsTarget.toString()) }
    var fat by remember { mutableStateOf(p.fatTarget.toString()) }
    var maint by remember { mutableStateOf(p.maintenance.toString()) }
    var goalW by remember { mutableStateOf(p.goalWeight.toString()) }
    var goalD by remember { mutableStateOf(LocalDate.ofEpochDay(p.goalDate).toString()) }
    var rot by remember { mutableStateOf(p.rotation.joinToString("\n")) }
    var rotDays by remember { mutableStateOf(p.rotationDays.toString()) }
    var key by remember { mutableStateOf(p.apiKey) }
    var model by remember { mutableStateOf(p.model) }
    var msg by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (msg.isNotEmpty()) item { Text("✓ $msg", color = Accent, fontSize = 13.sp) }

        item {
            Panel {
                Text("Daily targets", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("kcal", kcal, { kcal = it }, Modifier.weight(1f))
                    NumField("Protein g", protein, { protein = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
                    NumField("Fat g", fat, { fat = it }, Modifier.weight(1f))
                }
                val k = kcal.toFloatOrNull() ?: 0f
                val macroK = (protein.toFloatOrNull() ?: 0f) * 4 + (carbs.toFloatOrNull() ?: 0f) * 4 + (fat.toFloatOrNull() ?: 0f) * 9
                Text("Macros add up to ${macroK.roundToInt()} kcal (target ${k.roundToInt()}). Weekly budget: ${fmtKcal((k * 7).roundToInt())} kcal.", color = Muted, fontSize = 12.sp)
                Text("Goal & projection", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Maintenance kcal", maint, { maint = it }, Modifier.weight(1f))
                    NumField("Goal kg", goalW, { goalW = it }, Modifier.weight(1f))
                }
                OutlinedTextField(goalD, { goalD = it }, label = { Text("Goal date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    kcal.toFloatOrNull()?.let { p.kcalTarget = it.roundToInt() }
                    protein.toFloatOrNull()?.let { p.proteinTarget = it.roundToInt() }
                    carbs.toFloatOrNull()?.let { p.carbsTarget = it.roundToInt() }
                    fat.toFloatOrNull()?.let { p.fatTarget = it.roundToInt() }
                    maint.toFloatOrNull()?.let { p.maintenance = it.roundToInt() }
                    goalW.toFloatOrNull()?.let { p.goalWeight = it }
                    runCatching { LocalDate.parse(goalD.trim()) }.getOrNull()?.let { p.goalDate = it.toEpochDay() }
                    store.changed(); msg = "Targets saved"
                }) { Text("Save targets") }
            }
        }

        item {
            Panel {
                Text("Dinner protein rotation", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(rot, { rot = it }, label = { Text("One item per line") }, modifier = Modifier.fillMaxWidth())
                NumField("Days per item", rotDays, { rotDays = it }, Modifier.fillMaxWidth())
                p.rotationFor(today())?.let { (item, d) -> Text("Today: $item (day $d)", color = Muted, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        p.rotation = rot.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        rotDays.toFloatOrNull()?.let { p.rotationDays = it.roundToInt() }
                        store.changed(); msg = "Rotation saved"
                    }) { Text("Save") }
                    OutlinedButton(onClick = {
                        p.rotationStart = today(); store.changed(); msg = "Rotation restarts today with the first item"
                    }) { Text("Start from today") }
                }
            }
        }

        item {
            Panel {
                Text("Claude", fontWeight = FontWeight.SemiBold)
                Text(
                    "In-app chat and meal estimates use your Anthropic API key (console.anthropic.com, billed per use). The key stays on this phone and is only sent to api.anthropic.com. “Share to app” works without a key.",
                    color = Muted, fontSize = 12.sp,
                )
                OutlinedTextField(
                    key, { key = it.trim() }, label = { Text("API key (sk-ant-…)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(model, { model = it.trim() }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("claude-haiku-5-5 is cheapest; claude-sonnet-5-5 gives better advice.", color = Muted, fontSize = 12.sp)
                Button(onClick = { p.apiKey = key; p.model = model.ifBlank { "claude-haiku-5-5" }; store.changed(); msg = "Claude settings saved" }) { Text("Save") }
            }
        }

        item {
            Panel {
                Text("Data", fontWeight = FontWeight.SemiBold)
                Text("Everything is stored only on this phone. Export a CSV backup now and then.", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "CutLog export ${LocalDate.now()}")
                        putExtra(Intent.EXTRA_TEXT, store.db.exportCsv())
                    }
                    ctx.startActivity(Intent.createChooser(send, "Export CSV"))
                }) { Text("Export CSV") }
            }
        }

        item {
            Text(
                "Battery: no background services, no sync, no location. The network is used only when you send something to Claude. The true-black theme saves power on AMOLED screens.",
                color = Muted, fontSize = 12.sp,
            )
        }
    }
}
