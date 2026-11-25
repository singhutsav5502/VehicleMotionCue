@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.cutlog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

private fun defaultMeal(): String {
    val h = LocalTime.now().hour
    return when {
        h < 11 -> "Breakfast"
        h < 16 -> "Lunch"
        h < 19 -> "Snack"
        else -> "Dinner"
    }
}

@Composable
fun TodayScreen(store: Store) {
    var day by rememberSaveable { mutableLongStateOf(today()) }
    var page by rememberSaveable { mutableStateOf("") } // "", "food", "workout"

    when (page) {
        "food" -> { AddFoodPage(store, day) { page = "" }; return }
        "workout" -> { AddWorkoutPage(store, day) { page = "" }; return }
    }

    val v = store.version
    val p = store.prefs
    val foods = remember(v, day) { store.db.foods(day, day) }
    val workouts = remember(v, day) { store.db.workouts(day, day) }
    val tot = foods.fold(Totals()) { a, f -> a + f }
    val left = p.kcalTarget - tot.kcal

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { day-- }) { Text("‹", fontSize = 24.sp) }
                Text(dayLabel(day), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (day < today()) {
                    TextButton(onClick = { day = today() }) { Text("Today") }
                    TextButton(onClick = { day++ }) { Text("›", fontSize = 24.sp) }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtKcal(kotlin.math.abs(left)),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (left >= 0) Accent else Over,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (left >= 0) "kcal left" else "kcal over",
                        color = Muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                MacroBar("Calories", tot.kcal.toFloat(), p.kcalTarget, "kcal")
                MacroBar("Protein", tot.protein, p.proteinTarget, "g")
                MacroBar("Carbs", tot.carbs, p.carbsTarget, "g")
                MacroBar("Fat", tot.fat, p.fatTarget, "g")
            }
        }
        item {
            val hints = buildList {
                p.rotationFor(day)?.let { (item, d) -> add("Dinner rotation: $item (day $d/${p.rotationDays})") }
                val pLeft = p.proteinTarget - tot.protein
                if (pLeft >= 20 && left >= 120) add("Protein ${pLeft.roundToInt()} g short. Whey in water adds 24 g for 120 kcal.")
                if (foods.any { it.name.contains("thali", true) } && pLeft >= 20) add("Veg lunch today: add the extra whey scoop.")
                if (workouts.isNotEmpty()) add("Workout calories are already in your plan. Don't eat them back.")
            }
            if (hints.isNotEmpty()) Panel {
                hints.forEach { Text("• $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { page = "food" }, Modifier.weight(1f)) { Text("+ Food") }
                OutlinedButton(onClick = { page = "workout" }, Modifier.weight(1f)) { Text("+ Workout") }
            }
        }
        MEALS.forEach { meal ->
            val list = foods.filter { it.meal == meal }
            if (list.isNotEmpty()) {
                item(key = "h_$meal") {
                    val mt = list.fold(Totals()) { a, f -> a + f }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle(meal)
                        Text("${mt.kcal} kcal · ${mt.protein.roundToInt()} g P", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                items(list, key = { "f_${it.id}" }) { f -> FoodRow(f) { store.db.deleteFood(f.id); store.changed() } }
            }
        }
        if (workouts.isNotEmpty()) {
            item { SectionTitle("Workouts") }
            items(workouts, key = { "w_${it.id}" }) { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontSize = 15.sp)
                        Text("${w.minutes} min · ~${w.kcal} kcal burned", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { store.db.deleteWorkout(w.id); store.changed() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = Muted)
                    }
                }
            }
        }
        if (foods.isEmpty() && workouts.isEmpty()) {
            item { Text("Nothing logged yet.", color = Muted, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun FoodRow(f: Food, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 15.sp)
            Text(
                "${f.kcal} kcal · P ${f.protein.roundToInt()} · C ${f.carbs.roundToInt()} · F ${f.fat.roundToInt()}",
                color = Muted, fontSize = 12.sp,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Muted) }
    }
}

@Composable
private fun PageHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
    }
}

@Composable
fun AddFoodPage(store: Store, day: Long, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val v = store.version
    var meal by rememberSaveable { mutableStateOf(defaultMeal()) }
    var mult by rememberSaveable { mutableFloatStateOf(1f) }
    var query by rememberSaveable { mutableStateOf("") }
    var added by remember { mutableStateOf("") }

    // Claude meal parsing
    var describe by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var parseError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Custom entry
    var cName by rememberSaveable { mutableStateOf("") }
    var cKcal by rememberSaveable { mutableStateOf("") }
    var cP by rememberSaveable { mutableStateOf("") }
    var cC by rememberSaveable { mutableStateOf("") }
    var cF by rememberSaveable { mutableStateOf("") }
    var cSave by rememberSaveable { mutableStateOf(true) }

    val presets = remember(v) { store.prefs.customPresets + BUILTIN_PRESETS }
    val shown = presets.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    fun log(pr: Preset, m: Float = mult) {
        val name = if (m == 1f) pr.name else "${pr.name} ×${fmt1(m)}"
        store.db.addFood(
            Food(day = day, meal = meal, name = name, kcal = (pr.kcal * m).roundToInt(),
                protein = pr.protein * m, carbs = pr.carbs * m, fat = pr.fat * m),
        )
        store.changed()
        added = "Added $name to $meal"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("Add food · ${dayLabel(day)}", onClose) }
        item { ChipRow(MEALS, meal, { it }) { meal = it } }
        item { ChipRow(listOf(0.5f, 1f, 1.5f, 2f), mult, { "×" + fmt1(it) }) { mult = it } }
        if (added.isNotEmpty()) item { Text("✓ $added", color = Accent, fontSize = 13.sp) }

        item {
            Panel {
                Text("Describe it, Claude estimates", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = describe, onValueChange = { describe = it },
                    placeholder = { Text("e.g. canteen chicken curry, 1 cup rice, salad") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !parsing && describe.isNotBlank(),
                        onClick = {
                            if (store.prefs.apiKey.isBlank()) { parseError = "Add your Anthropic API key in Settings first."; return@Button }
                            parsing = true; parseError = ""; parsed = emptyList()
                            scope.launch {
                                try { parsed = Claude.parseMeal(store, describe) }
                                catch (e: Exception) { parseError = e.message ?: "Failed" }
                                finally { parsing = false }
                            }
                        },
                    ) { Text("Estimate") }
                    if (parsing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (parsed.size > 1) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { parsed.forEach { log(it, 1f) }; parsed = emptyList(); describe = "" }) { Text("Add all") }
                    }
                }
                if (parseError.isNotEmpty()) Text(parseError, color = Over, fontSize = 13.sp)
                parsed.forEach { pr ->
                    PresetRow(pr, onClick = { log(pr, 1f); parsed = parsed - pr })
                }
            }
        }

        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search presets") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(shown, key = { (if (it.custom) "c_" else "b_") + it.name }) { pr ->
            PresetRow(
                pr,
                onClick = { log(pr) },
                onDelete = if (pr.custom) ({
                    store.prefs.customPresets = store.prefs.customPresets.filterNot { it.name == pr.name }
                    store.changed()
                }) else null,
            )
        }

        item {
            Panel {
                Text("Custom entry", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(cName, { cName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("kcal", cKcal, { cKcal = it }, Modifier.weight(1f))
                    NumField("Protein", cP, { cP = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Carbs", cC, { cC = it }, Modifier.weight(1f))
                    NumField("Fat", cF, { cF = it }, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(cSave, { cSave = it })
                    Text("Save as preset", Modifier.weight(1f))
                    Button(
                        enabled = cName.isNotBlank() && cKcal.toFloatOrNull() != null,
                        onClick = {
                            val pr = Preset(
                                cName.trim(), cKcal.toFloat().roundToInt(), cP.toFloatOrNull() ?: 0f,
                                cC.toFloatOrNull() ?: 0f, cF.toFloatOrNull() ?: 0f, custom = true,
                            )
                            if (cSave) store.prefs.customPresets =
                                listOf(pr) + store.prefs.customPresets.filterNot { it.name == pr.name }
                            log(pr, 1f)
                            cName = ""; cKcal = ""; cP = ""; cC = ""; cF = ""
                        },
                    ) { Text("Add") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PresetRow(p: Preset, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name + if (p.custom) "  ★" else "", fontSize = 15.sp)
                Text(
                    "${p.kcal} kcal · P ${p.protein.roundToInt()} · C ${p.carbs.roundToInt()} · F ${p.fat.roundToInt()}",
                    color = Muted, fontSize = 12.sp,
                )
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete preset", tint = Muted) }
            Text("+", color = Accent, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
fun AddWorkoutPage(store: Store, day: Long, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var name by rememberSaveable { mutableStateOf("") }
    var mins by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var added by remember { mutableStateOf("") }

    fun log(w: WorkoutPreset) {
        store.db.addWorkout(Workout(day = day, name = w.name, minutes = w.minutes, kcal = w.kcal))
        store.changed(); added = "Added ${w.name}"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("Add workout · ${dayLabel(day)}", onClose) }
        item {
            Text(
                "Burn estimates are for ~99 kg. They're for tracking only and aren't added to your food budget.",
                color = Muted, fontSize = 13.sp,
            )
        }
        if (added.isNotEmpty()) item { Text("✓ $added", color = Accent, fontSize = 13.sp) }
        items(WORKOUT_PRESETS) { w ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { log(w) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontSize = 15.sp)
                        Text("${w.minutes} min · ~${w.kcal} kcal", color = Muted, fontSize = 12.sp)
                    }
                    Text("+", color = Accent, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
        item {
            Panel {
                Text("Custom", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Back day)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("Minutes", mins, { mins = it }, Modifier.weight(1f))
                    NumField("kcal (optional)", kcal, { kcal = it }, Modifier.weight(1f))
                }
                Button(
                    enabled = name.isNotBlank() && mins.toFloatOrNull() != null,
                    onClick = {
                        val m = mins.toFloat().roundToInt()
                        // Default estimate: ~6 kcal/min for machine weight training at this bodyweight.
                        val k = kcal.toFloatOrNull()?.roundToInt() ?: (m * 6)
                        log(WorkoutPreset(name.trim(), m, k))
                        name = ""; mins = ""; kcal = ""
                    },
                ) { Text("Add") }
            }
        }
    }
}
