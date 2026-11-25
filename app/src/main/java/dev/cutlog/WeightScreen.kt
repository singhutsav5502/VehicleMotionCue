package dev.cutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Trailing 7-day moving average over weigh-ins (by calendar day, not by entry count). */
private fun movingAvg(ws: List<Weight>): List<Pair<Long, Float>> = ws.map { w ->
    val window = ws.filter { it.day in (w.day - 6)..w.day }
    w.day to window.map { it.kg }.average().toFloat()
}

/** Least-squares slope in kg/day over the last [days] days of entries. */
private fun slopePerDay(ws: List<Weight>, days: Int = 21): Float? {
    if (ws.isEmpty()) return null
    val last = ws.last().day
    val pts = ws.filter { it.day > last - days }
    if (pts.size < 3 || pts.last().day - pts.first().day < 5) return null
    val mx = pts.map { it.day.toDouble() }.average()
    val my = pts.map { it.kg.toDouble() }.average()
    val num = pts.sumOf { (it.day - mx) * (it.kg - my) }
    val den = pts.sumOf { (it.day - mx) * (it.day - mx) }
    return if (den == 0.0) null else (num / den).toFloat()
}

@Composable
fun WeightScreen(store: Store) {
    val v = store.version
    val p = store.prefs
    val ws = remember(v) { store.db.weights() }
    val avg = remember(ws) { movingAvg(ws) }
    val slope = remember(ws) { slopePerDay(ws) }

    val todays = ws.lastOrNull { it.day == today() }
    var kg by rememberSaveable { mutableStateOf(todays?.kg?.toString() ?: "") }
    var bf by rememberSaveable { mutableStateOf(todays?.bodyFat?.toString() ?: "") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Weight", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            Panel {
                Text("Today's weigh-in (morning, before eating)", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("kg", kg, { kg = it }, Modifier.weight(1f))
                    NumField("Body fat % (opt)", bf, { bf = it }, Modifier.weight(1f))
                    Button(
                        enabled = kg.toFloatOrNull() != null,
                        onClick = {
                            store.db.setWeight(Weight(day = today(), kg = kg.toFloat(), bodyFat = bf.toFloatOrNull()))
                            store.changed()
                        },
                    ) { Text("Save") }
                }
            }
        }
        if (ws.isNotEmpty()) {
            item {
                Panel {
                    WeightChart(ws.map { it.day to it.kg }, avg, p.goalWeight)
                    Text("Dots = weigh-ins · line = 7-day average · dashed = goal ${fmt1(p.goalWeight)} kg", color = Muted, fontSize = 12.sp)
                }
            }
            item {
                val first = ws.first(); val cur = avg.last().second
                val daysToGoal = p.goalDate - today()
                Panel {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("7-day avg", "${fmt1(cur)} kg", Modifier.weight(1f))
                        Stat("Change", "${if (cur - first.kg > 0) "+" else ""}${fmt1(cur - first.kg)} kg", Modifier.weight(1f))
                        Stat("To goal", "${fmt1(cur - p.goalWeight)} kg", Modifier.weight(1f))
                    }
                    if (slope != null) {
                        val perWeek = slope * 7
                        val perMonth = slope * 30
                        KeyValue("Trend (last 3 weeks)", "${fmt1(perWeek)} kg/week · ${fmt1(perMonth)} kg/month")
                        if (daysToGoal > 0) {
                            KeyValue("Projected on ${LocalDate.ofEpochDay(p.goalDate)}", "${fmt1(cur + slope * daysToGoal)} kg")
                        }
                        if (slope < 0) {
                            val days = ((cur - p.goalWeight) / -slope).toLong()
                            if (days > 0) KeyValue("Goal reached around", LocalDate.ofEpochDay(today() + days).toString())
                        }
                        if (perWeek < -1.3f && ws.last().day - first.day > 28) {
                            Text("Losing faster than ~1.3 kg/week after the first month. Consider adding 100–200 kcal/day.", color = Warn, fontSize = 13.sp)
                        }
                    } else {
                        Text("Log at least 3 weigh-ins across a week to see your trend.", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            item { SectionTitle("History") }
            items(ws.reversed(), key = { it.id }) { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmt1(w.kg)} kg" + (w.bodyFat?.let { " · ${fmt1(it)}% BF" } ?: ""), fontSize = 15.sp)
                        Text(dayLabel(w.day), color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { store.db.deleteWeight(w.id); store.changed() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = Muted)
                    }
                }
            }
        }
    }
}
