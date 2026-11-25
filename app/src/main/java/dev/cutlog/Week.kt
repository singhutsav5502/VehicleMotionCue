@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.cutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

private const val KCAL_PER_KG = 7700f

@Composable
fun WeekScreen(store: Store) {
    val v = store.version
    val p = store.prefs
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = this week (Mon–Sun), 1 = last 7 days
    var offset by rememberSaveable { mutableIntStateOf(0) } // weeks back, calendar mode only

    val t = today()
    val monday = LocalDate.ofEpochDay(t).with(DayOfWeek.MONDAY).toEpochDay() - offset * 7L
    val from = if (mode == 0) monday else t - 6
    val to = from + 6
    val totals = remember(v, from) { store.db.dailyTotals(from, to) }
    val workouts = remember(v, from) { store.db.workouts(from, to) }
    val days = (from..to).toList()
    val labels = days.map { LocalDate.ofEpochDay(it).dayOfWeek.name.take(2).lowercase().replaceFirstChar { c -> c.uppercase() } }
    val values = days.map { totals[it]?.kcal }

    val budget = p.kcalTarget * 7
    val consumed = totals.values.sumOf { it.kcal }
    val logged = totals.size
    val avg = if (logged > 0) consumed / logged else 0
    val avgP = if (logged > 0) totals.values.sumOf { it.protein.toDouble() } / logged else 0.0
    val proteinDays = totals.values.count { it.protein >= p.proteinTarget * 0.95f }
    val deficit = totals.values.sumOf { p.maintenance - it.kcal }
    val estLoss = deficit / KCAL_PER_KG

    // Rest-of-week allowance (calendar mode, current week only)
    val daysLeft = if (mode == 0 && offset == 0) (to - t + 1).toInt() else 0
    val consumedBeforeToday = totals.filterKeys { it < t }.values.sumOf { it.kcal }
    val perDayLeft = if (daysLeft > 0) (budget - consumedBeforeToday) / daysLeft else 0

    // Last 4 calendar weeks
    val weeks = remember(v, t) {
        val thisMon = LocalDate.ofEpochDay(t).with(DayOfWeek.MONDAY).toEpochDay()
        (3 downTo 0).map { k ->
            val s = thisMon - 7L * k
            val tot = store.db.dailyTotals(s, s + 6)
            Triple(s, tot.values.sumOf { it.kcal }, tot.size)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Week", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item { ChipRow(listOf(0, 1), mode, { if (it == 0) "Mon–Sun" else "Last 7 days" }) { mode = it; offset = 0 } }
        if (mode == 0) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                androidx.compose.material3.TextButton(onClick = { offset++ }) { Text("‹ Prev") }
                Text(
                    "${LocalDate.ofEpochDay(from).dayOfMonth} ${LocalDate.ofEpochDay(from).month.name.take(3)} – ${LocalDate.ofEpochDay(to).dayOfMonth} ${LocalDate.ofEpochDay(to).month.name.take(3)}",
                    Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
                )
                androidx.compose.material3.TextButton(onClick = { if (offset > 0) offset-- }, enabled = offset > 0) { Text("Next ›") }
            }
        }
        item {
            Panel {
                BarChart(values, labels, p.kcalTarget)
                Text("Dashed line = ${p.kcalTarget} kcal daily target", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Panel {
                Text("Daily vs weekly", fontWeight = FontWeight.SemiBold)
                MacroBar("Week so far", consumed.toFloat(), budget, "kcal")
                KeyValue("Weekly budget", "${fmtKcal(budget)} kcal")
                KeyValue("Remaining this week", "${fmtKcal(budget - consumed)} kcal")
                KeyValue("Avg per logged day", "${fmtKcal(avg)} kcal ($logged/7 logged)")
                if (daysLeft > 0) KeyValue(
                    "To land on budget",
                    "${fmtKcal(perDayLeft)} kcal/day × $daysLeft day" + if (daysLeft > 1) "s" else "",
                )
            }
        }
        item {
            Panel {
                Text("Protein", fontWeight = FontWeight.SemiBold)
                KeyValue("Avg per logged day", "${avgP.roundToInt()} g / ${p.proteinTarget} g")
                KeyValue("Days on target", "$proteinDays / $logged")
            }
        }
        item {
            Panel {
                Text("Estimated fat loss", fontWeight = FontWeight.SemiBold)
                KeyValue("Deficit vs ${fmtKcal(p.maintenance)} maintenance", "${fmtKcal(deficit)} kcal")
                KeyValue("≈ fat lost (logged days)", "${fmt1(estLoss)} kg")
                if (logged > 0) KeyValue("Pace at this average", "${fmt1((p.maintenance - avg) * 7 / KCAL_PER_KG)} kg/week")
                Text("Maintenance is an estimate. Your weigh-in trend is the real number; adjust maintenance in Settings after 3–4 weeks.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Panel {
                Text("Training", fontWeight = FontWeight.SemiBold)
                KeyValue("Sessions", "${workouts.size}")
                KeyValue("Minutes", "${workouts.sumOf { it.minutes }}")
                KeyValue("Est. burn (not added to budget)", "${fmtKcal(workouts.sumOf { it.kcal })} kcal")
            }
        }
        item {
            Panel {
                Text("Last 4 weeks", fontWeight = FontWeight.SemiBold)
                weeks.forEach { (s, kcal, n) ->
                    val d = LocalDate.ofEpochDay(s)
                    KeyValue(
                        "Week of ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}",
                        if (n == 0) "—" else "${fmtKcal(kcal)} · avg ${fmtKcal(kcal / n)}/day",
                    )
                }
            }
        }
    }
}
