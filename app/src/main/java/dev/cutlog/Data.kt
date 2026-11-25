package dev.cutlog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// ---------- Models ----------

data class Food(
    val id: Long = 0,
    val day: Long,
    val ts: Long = System.currentTimeMillis(),
    val meal: String,
    val name: String,
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
)

data class Workout(
    val id: Long = 0,
    val day: Long,
    val name: String,
    val minutes: Int,
    val kcal: Int,
)

data class Weight(
    val id: Long = 0,
    val day: Long,
    val kg: Float,
    val bodyFat: Float? = null,
)

data class Totals(val kcal: Int = 0, val protein: Float = 0f, val carbs: Float = 0f, val fat: Float = 0f) {
    operator fun plus(f: Food) = Totals(kcal + f.kcal, protein + f.protein, carbs + f.carbs, fat + f.fat)
}

data class Preset(
    val name: String,
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val custom: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("kcal", kcal).put("protein", protein.toDouble())
        .put("carbs", carbs.toDouble()).put("fat", fat.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = Preset(
            o.getString("name"), o.getInt("kcal"), o.getDouble("protein").toFloat(),
            o.getDouble("carbs").toFloat(), o.getDouble("fat").toFloat(), custom = true,
        )
    }
}

data class WorkoutPreset(val name: String, val minutes: Int, val kcal: Int)

val MEALS = listOf("Breakfast", "Lunch", "Snack", "Dinner")

/** Built from the plan: skim milk, air-fryer dinners, canteen lunches. Values are estimates. */
val BUILTIN_PRESETS = listOf(
    Preset("Whey + 300ml skim milk", 225, 34f, 18f, 2f),
    Preset("Whey in water", 120, 24f, 3f, 1.5f),
    Preset("Hung curd 150g + orange", 180, 14f, 20f, 5f),
    Preset("Low-fat paneer 250g", 475, 55f, 8f, 22f),
    Preset("Firm tofu 400g", 480, 50f, 10f, 28f),
    Preset("Soy chunks 100g (dry)", 345, 52f, 33f, 1f),
    Preset("Chicken thigh 200g (raw)", 250, 38f, 0f, 10f),
    Preset("Lunch: chicken + rice", 600, 35f, 60f, 20f),
    Preset("Lunch: chicken + veg", 450, 35f, 15f, 25f),
    Preset("Lunch: veg thali (trimmed)", 500, 17f, 65f, 17f),
    Preset("Air-fried veg (onion/capsicum)", 60, 3f, 10f, 1f),
    Preset("Potato / sweet potato 150g", 120, 3f, 27f, 0f),
    Preset("Oil 1 tsp", 40, 0f, 0f, 4.5f),
    Preset("Orange", 60, 1f, 14f, 0f),
    Preset("Diet Coke", 1, 0f, 0f, 0f),
)

val WORKOUT_PRESETS = listOf(
    WorkoutPreset("Upper body (machines)", 55, 330),
    WorkoutPreset("Lower body (machines)", 50, 380),
    WorkoutPreset("Cycling, low resistance", 12, 85),
    WorkoutPreset("Cycling, moderate", 15, 150),
    WorkoutPreset("Incline walk", 15, 120),
    WorkoutPreset("Walk 30 min", 30, 150),
)

fun today(): Long = LocalDate.now().toEpochDay()
fun dayLabel(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return when (day - today()) {
        0L -> "Today"
        -1L -> "Yesterday"
        else -> "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
    }
}

// ---------- SQLite (no Room: zero annotation processing, tiny APK) ----------

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "cutlog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE food(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER, ts INTEGER, meal TEXT, name TEXT, kcal INTEGER, protein REAL, carbs REAL, fat REAL)")
        db.execSQL("CREATE INDEX food_day ON food(day)")
        db.execSQL("CREATE TABLE workout(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER, name TEXT, minutes INTEGER, kcal INTEGER)")
        db.execSQL("CREATE INDEX workout_day ON workout(day)")
        db.execSQL("CREATE TABLE weight(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER UNIQUE, kg REAL, bf REAL)")
        // Seed with the InBody scan from 15 Nov 2026.
        db.execSQL("INSERT INTO weight(day, kg, bf) VALUES(${LocalDate.of(2026, 11, 15).toEpochDay()}, 98.8, 35.6)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addFood(f: Food) {
        writableDatabase.insert("food", null, ContentValues().apply {
            put("day", f.day); put("ts", f.ts); put("meal", f.meal); put("name", f.name)
            put("kcal", f.kcal); put("protein", f.protein); put("carbs", f.carbs); put("fat", f.fat)
        })
    }

    fun deleteFood(id: Long) { writableDatabase.delete("food", "id=?", arrayOf(id.toString())) }

    fun foods(from: Long, to: Long): List<Food> {
        val out = ArrayList<Food>()
        readableDatabase.rawQuery(
            "SELECT id,day,ts,meal,name,kcal,protein,carbs,fat FROM food WHERE day BETWEEN ? AND ? ORDER BY day, ts",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) out += Food(
                c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getString(4),
                c.getInt(5), c.getFloat(6), c.getFloat(7), c.getFloat(8),
            )
        }
        return out
    }

    fun dailyTotals(from: Long, to: Long): Map<Long, Totals> =
        foods(from, to).groupBy { it.day }.mapValues { (_, l) -> l.fold(Totals()) { a, f -> a + f } }

    fun addWorkout(w: Workout) {
        writableDatabase.insert("workout", null, ContentValues().apply {
            put("day", w.day); put("name", w.name); put("minutes", w.minutes); put("kcal", w.kcal)
        })
    }

    fun deleteWorkout(id: Long) { writableDatabase.delete("workout", "id=?", arrayOf(id.toString())) }

    fun workouts(from: Long, to: Long): List<Workout> {
        val out = ArrayList<Workout>()
        readableDatabase.rawQuery(
            "SELECT id,day,name,minutes,kcal FROM workout WHERE day BETWEEN ? AND ? ORDER BY day, id",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) out += Workout(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getInt(4))
        }
        return out
    }

    fun setWeight(w: Weight) {
        writableDatabase.insertWithOnConflict("weight", null, ContentValues().apply {
            put("day", w.day); put("kg", w.kg)
            if (w.bodyFat != null) put("bf", w.bodyFat) else putNull("bf")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWeight(id: Long) { writableDatabase.delete("weight", "id=?", arrayOf(id.toString())) }

    fun weights(): List<Weight> {
        val out = ArrayList<Weight>()
        readableDatabase.rawQuery("SELECT id,day,kg,bf FROM weight ORDER BY day", null).use { c ->
            while (c.moveToNext()) out += Weight(
                c.getLong(0), c.getLong(1), c.getFloat(2), if (c.isNull(3)) null else c.getFloat(3),
            )
        }
        return out
    }

    fun exportCsv(): String = buildString {
        appendLine("type,date,meal_or_name,name,kcal,protein,carbs,fat,minutes,kg,bodyfat")
        foods(0, Long.MAX_VALUE / 2).forEach {
            appendLine("food,${LocalDate.ofEpochDay(it.day)},${it.meal},\"${it.name.replace("\"", "'")}\",${it.kcal},${it.protein},${it.carbs},${it.fat},,,")
        }
        workouts(0, Long.MAX_VALUE / 2).forEach {
            appendLine("workout,${LocalDate.ofEpochDay(it.day)},,\"${it.name.replace("\"", "'")}\",${it.kcal},,,,${it.minutes},,")
        }
        weights().forEach {
            appendLine("weight,${LocalDate.ofEpochDay(it.day)},,,,,,,,${it.kg},${it.bodyFat ?: ""}")
        }
    }
}

// ---------- Settings ----------

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("cutlog", Context.MODE_PRIVATE)

    var kcalTarget: Int
        get() = p.getInt("kcal", 1450); set(v) = p.edit().putInt("kcal", v).apply()
    var proteinTarget: Int
        get() = p.getInt("protein", 140); set(v) = p.edit().putInt("protein", v).apply()
    var carbsTarget: Int
        get() = p.getInt("carbs", 120); set(v) = p.edit().putInt("carbs", v).apply()
    var fatTarget: Int
        get() = p.getInt("fat", 45); set(v) = p.edit().putInt("fat", v).apply()

    /** Estimated maintenance incl. steps + training; used for deficit/projection. */
    var maintenance: Int
        get() = p.getInt("maint", 2600); set(v) = p.edit().putInt("maint", v).apply()
    var goalWeight: Float
        get() = p.getFloat("goalW", 84f); set(v) = p.edit().putFloat("goalW", v).apply()
    var goalDate: Long
        get() = p.getLong("goalD", LocalDate.of(2027, 2, 27).toEpochDay()); set(v) = p.edit().putLong("goalD", v).apply()

    var apiKey: String
        get() = p.getString("apiKey", "") ?: ""; set(v) = p.edit().putString("apiKey", v).apply()
    var model: String
        get() = p.getString("model", "claude-haiku-5-5") ?: "claude-haiku-5-5"; set(v) = p.edit().putString("model", v).apply()

    /** Dinner protein rotation, each item used for [rotationDays] consecutive days. */
    var rotation: List<String>
        get() = (p.getString("rot", null) ?: "Low-fat paneer 250g|Firm tofu 400g|Soy chunks 100g (dry)").split("|").filter { it.isNotBlank() }
        set(v) = p.edit().putString("rot", v.joinToString("|")).apply()
    var rotationDays: Int
        get() = p.getInt("rotDays", 2); set(v) = p.edit().putInt("rotDays", v.coerceAtLeast(1)).apply()
    var rotationStart: Long
        get() = p.getLong("rotStart", LocalDate.of(2026, 11, 25).toEpochDay()); set(v) = p.edit().putLong("rotStart", v).apply()

    var customPresets: List<Preset>
        get() {
            val s = p.getString("presets", null) ?: return emptyList()
            return runCatching {
                val a = JSONArray(s); List(a.length()) { Preset.fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
        set(v) = p.edit().putString("presets", JSONArray().apply { v.forEach { put(it.toJson()) } }.toString()).apply()

    fun rotationFor(day: Long): Pair<String, Int>? {
        val r = rotation; if (r.isEmpty()) return null
        val n = rotationDays
        val idx = Math.floorMod(day - rotationStart, (r.size * n).toLong()).toInt()
        return r[idx / n] to (idx % n + 1)
    }
}

/** App-wide holder. `version` is bumped on every write so Compose re-reads the DB. */
class Store(ctx: Context) {
    val db = Db(ctx)
    val prefs = Prefs(ctx)
    var version by mutableIntStateOf(0)
        private set

    fun changed() { version++ }
}

object StoreHolder {
    @Volatile private var instance: Store? = null
    fun get(ctx: Context): Store = instance ?: synchronized(this) {
        instance ?: Store(ctx.applicationContext).also { instance = it }
    }
}
