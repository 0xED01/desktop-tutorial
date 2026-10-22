package com.muscleup.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.log10
import kotlin.math.roundToInt

fun today(): Long = LocalDate.now().toEpochDay()

data class Profile(
    val name: String = "健身新人",
    val male: Boolean = true,
    val age: Int = 22,
    val heightCm: Double = 175.0,
    val weightKg: Double = 58.0,
    val targetWeightKg: Double = 68.0,
    val activity: Double = 1.55,
    val surplus: Int = 400,
    val level: Int = 0, // 0 新手 1 进阶 2 熟练
    val onboarded: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("name", name); put("male", male); put("age", age); put("heightCm", heightCm)
        put("weightKg", weightKg); put("targetWeightKg", targetWeightKg); put("activity", activity)
        put("surplus", surplus); put("level", level); put("onboarded", onboarded)
    }

    companion object {
        fun from(o: JSONObject?): Profile {
            if (o == null) return Profile()
            val d = Profile()
            return Profile(
                o.optString("name", d.name), o.optBoolean("male", d.male), o.optInt("age", d.age),
                o.optDouble("heightCm", d.heightCm), o.optDouble("weightKg", d.weightKg),
                o.optDouble("targetWeightKg", d.targetWeightKg), o.optDouble("activity", d.activity),
                o.optInt("surplus", d.surplus), o.optInt("level", d.level), o.optBoolean("onboarded", false),
            )
        }
    }
}

/** 营养目标计算（Mifflin-St Jeor） */
data class Targets(val bmr: Int, val tdee: Int, val kcal: Int, val protein: Int, val fat: Int, val carbs: Int, val waterMl: Int)

fun Profile.targets(currentWeight: Double = weightKg): Targets {
    val bmr = 10 * currentWeight + 6.25 * heightCm - 5 * age + if (male) 5 else -161
    val tdee = bmr * activity
    val kcal = tdee + surplus
    val protein = 2.0 * currentWeight
    val fat = kcal * 0.25 / 9
    val carbs = (kcal - protein * 4 - fat * 9) / 4
    return Targets(
        bmr.roundToInt(), tdee.roundToInt(), kcal.roundToInt(), protein.roundToInt(),
        fat.roundToInt(), carbs.roundToInt(), (currentWeight * 40).roundToInt().coerceAtLeast(2000),
    )
}

fun bmi(weight: Double, heightCm: Double): Double = weight / ((heightCm / 100) * (heightCm / 100))

fun bmiLabel(b: Double) = when {
    b < 18.5 -> "偏瘦"
    b < 24 -> "正常"
    b < 28 -> "超重"
    else -> "肥胖"
}

/** 美国海军体脂公式（cm） */
fun navyBodyFat(male: Boolean, heightCm: Double, waist: Double, neck: Double, hip: Double): Double? {
    return try {
        val v = if (male) {
            if (waist <= neck) return null
            495 / (1.0324 - 0.19077 * log10(waist - neck) + 0.15456 * log10(heightCm)) - 450
        } else {
            if (waist + hip <= neck) return null
            495 / (1.29579 - 0.35004 * log10(waist + hip - neck) + 0.22100 * log10(heightCm)) - 450
        }
        if (v.isNaN() || v < 2 || v > 60) null else v
    } catch (e: Exception) {
        null
    }
}

/** Epley 公式估算 1RM */
fun oneRm(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

data class SetRec(val weight: Double, val reps: Int) {
    fun toJson() = JSONObject().put("w", weight).put("r", reps)
    companion object { fun from(o: JSONObject) = SetRec(o.optDouble("w", 0.0), o.optInt("r", 0)) }
}

data class ExerciseEntry(val exerciseId: String, val sets: List<SetRec>) {
    fun toJson() = JSONObject().put("ex", exerciseId).put("sets", JSONArray(sets.map { it.toJson() }))
    val volume get() = sets.sumOf { it.weight * it.reps }
    companion object {
        fun from(o: JSONObject) = ExerciseEntry(o.getString("ex"), o.optJSONArray("sets").objs().map { SetRec.from(it) })
    }
}

data class WorkoutLog(
    val id: Long,
    val date: Long,
    val title: String,
    val entries: List<ExerciseEntry>,
    val durationMin: Int,
    val note: String = "",
) {
    val volume get() = entries.sumOf { it.volume }
    val setCount get() = entries.sumOf { it.sets.size }
    fun toJson() = JSONObject().put("id", id).put("date", date).put("title", title)
        .put("entries", JSONArray(entries.map { it.toJson() })).put("dur", durationMin).put("note", note)
    companion object {
        fun from(o: JSONObject) = WorkoutLog(
            o.getLong("id"), o.getLong("date"), o.optString("title"),
            o.optJSONArray("entries").objs().map { ExerciseEntry.from(it) }, o.optInt("dur"), o.optString("note"),
        )
    }
}

val MEALS = listOf("早餐", "午餐", "晚餐", "加餐")

data class FoodLog(
    val id: Long, val date: Long, val meal: Int, val name: String, val grams: Double,
    val kcal: Double, val protein: Double, val carbs: Double, val fat: Double,
) {
    fun toJson() = JSONObject().put("id", id).put("date", date).put("meal", meal).put("name", name).put("g", grams)
        .put("k", kcal).put("p", protein).put("c", carbs).put("f", fat)
    companion object {
        fun from(o: JSONObject) = FoodLog(
            o.getLong("id"), o.getLong("date"), o.optInt("meal"), o.optString("name"), o.optDouble("g"),
            o.optDouble("k"), o.optDouble("p"), o.optDouble("c"), o.optDouble("f"),
        )
    }
}

data class WeightLog(val date: Long, val weight: Double) {
    fun toJson() = JSONObject().put("date", date).put("w", weight)
    companion object { fun from(o: JSONObject) = WeightLog(o.getLong("date"), o.optDouble("w")) }
}

data class Measure(
    val date: Long, val chest: Double, val arm: Double, val waist: Double, val thigh: Double, val shoulder: Double,
) {
    fun toJson() = JSONObject().put("date", date).put("chest", chest).put("arm", arm).put("waist", waist)
        .put("thigh", thigh).put("shoulder", shoulder)
    companion object {
        fun from(o: JSONObject) = Measure(
            o.getLong("date"), o.optDouble("chest"), o.optDouble("arm"), o.optDouble("waist"),
            o.optDouble("thigh"), o.optDouble("shoulder"),
        )
    }
}

data class SleepLog(val date: Long, val hours: Double, val quality: Int) {
    fun toJson() = JSONObject().put("date", date).put("h", hours).put("q", quality)
    companion object { fun from(o: JSONObject) = SleepLog(o.getLong("date"), o.optDouble("h"), o.optInt("q", 3)) }
}

/** 悬浮计时器记录的一段训练时长 */
data class TimerLog(val id: Long, val date: Long, val startMs: Long, val seconds: Int, val label: String) {
    fun toJson() = JSONObject().put("id", id).put("date", date).put("start", startMs).put("sec", seconds).put("label", label)
    companion object {
        fun from(o: JSONObject) = TimerLog(o.getLong("id"), o.getLong("date"), o.optLong("start"), o.optInt("sec"), o.optString("label"))
    }
}

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

data class Reminder(val key: String, val title: String, val hour: Int, val minute: Int, val enabled: Boolean) {
    fun toJson() = JSONObject().put("key", key).put("title", title).put("h", hour).put("m", minute).put("on", enabled)
    companion object {
        fun from(o: JSONObject) = Reminder(o.getString("key"), o.optString("title"), o.optInt("h"), o.optInt("m"), o.optBoolean("on"))
    }
}

fun JSONArray?.objs(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).map { getJSONObject(it) }
}

fun Double.fmt(d: Int = 1): String {
    val s = "%.${d}f".format(this)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
