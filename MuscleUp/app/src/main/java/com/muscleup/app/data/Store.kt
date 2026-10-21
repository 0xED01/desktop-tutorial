package com.muscleup.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 全部数据保存在本地 JSON 文件中，状态用 Compose State 驱动 UI。 */
object Store {
    private lateinit var file: File

    var profile by mutableStateOf(Profile())
        private set
    var workouts by mutableStateOf(listOf<WorkoutLog>())
        private set
    var foods by mutableStateOf(listOf<FoodLog>())
        private set
    var weights by mutableStateOf(listOf<WeightLog>())
        private set
    var measures by mutableStateOf(listOf<Measure>())
        private set
    var sleeps by mutableStateOf(listOf<SleepLog>())
        private set
    var water by mutableStateOf(mapOf<Long, Int>())
        private set
    var customFoods by mutableStateOf(listOf<Food>())
        private set
    var activePlanId by mutableStateOf("beginner_fullbody")
        private set
    var planCursor by mutableStateOf(0)
        private set
    var reminders by mutableStateOf(defaultReminders())
        private set

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "muscleup.json")
        if (file.exists()) {
            runCatching { load(JSONObject(file.readText())) }
        }
    }

    private fun defaultReminders() = listOf(
        Reminder("workout", "该训练啦！今天的计划等着你 💪", 18, 30, false),
        Reminder("breakfast", "吃早餐！增肌第一餐别省", 7, 30, false),
        Reminder("snack", "加餐时间：来点坚果/牛奶/香蕉", 15, 30, false),
        Reminder("water", "喝水提醒：补充 300ml 水", 10, 0, false),
        Reminder("sleep", "准备睡觉，肌肉在睡眠中生长 😴", 22, 45, false),
    )

    private fun load(o: JSONObject) {
        profile = Profile.from(o.optJSONObject("profile"))
        workouts = o.optJSONArray("workouts").objs().map { WorkoutLog.from(it) }
        foods = o.optJSONArray("foods").objs().map { FoodLog.from(it) }
        weights = o.optJSONArray("weights").objs().map { WeightLog.from(it) }
        measures = o.optJSONArray("measures").objs().map { Measure.from(it) }
        sleeps = o.optJSONArray("sleeps").objs().map { SleepLog.from(it) }
        customFoods = o.optJSONArray("customFoods").objs().map { Food.from(it) }
        val w = o.optJSONObject("water")
        water = w?.keys()?.asSequence()?.associate { it.toLong() to w.getInt(it) } ?: emptyMap()
        activePlanId = o.optString("activePlan", activePlanId)
        planCursor = o.optInt("planCursor", 0)
        val saved = o.optJSONArray("reminders").objs().map { Reminder.from(it) }.associateBy { it.key }
        reminders = defaultReminders().map { saved[it.key] ?: it }
    }

    private fun save() {
        val o = JSONObject()
        o.put("profile", profile.toJson())
        o.put("workouts", JSONArray(workouts.map { it.toJson() }))
        o.put("foods", JSONArray(foods.map { it.toJson() }))
        o.put("weights", JSONArray(weights.map { it.toJson() }))
        o.put("measures", JSONArray(measures.map { it.toJson() }))
        o.put("sleeps", JSONArray(sleeps.map { it.toJson() }))
        o.put("customFoods", JSONArray(customFoods.map { it.toJson() }))
        o.put("water", JSONObject().apply { water.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("activePlan", activePlanId)
        o.put("planCursor", planCursor)
        o.put("reminders", JSONArray(reminders.map { it.toJson() }))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    fun exportJson(): String = file.takeIf { it.exists() }?.readText() ?: "{}"

    fun importJson(text: String): Boolean = runCatching {
        load(JSONObject(text)); save()
    }.isSuccess

    fun resetAll() {
        profile = Profile(); workouts = emptyList(); foods = emptyList(); weights = emptyList()
        measures = emptyList(); sleeps = emptyList(); water = emptyMap(); customFoods = emptyList()
        activePlanId = "beginner_fullbody"; planCursor = 0; reminders = defaultReminders()
        save()
    }

    fun updateProfile(p: Profile) {
        profile = p
        save()
    }

    // ---------- 训练 ----------
    fun addWorkout(w: WorkoutLog, advancePlan: Boolean) {
        workouts = (workouts + w).sortedByDescending { it.id }
        if (advancePlan) {
            val plan = Plans.byId(activePlanId)
            planCursor = (planCursor + 1) % plan.days.size
        }
        save()
    }

    fun deleteWorkout(id: Long) {
        workouts = workouts.filterNot { it.id == id }
        save()
    }

    fun setActivePlan(id: String) {
        activePlanId = id
        planCursor = 0
        save()
    }

    fun setCursor(i: Int) {
        planCursor = i
        save()
    }

    /** 该动作最近一次的记录 */
    fun lastEntry(exId: String): ExerciseEntry? =
        workouts.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { it.exerciseId == exId && it.sets.isNotEmpty() }

    fun bestOneRm(exId: String): Double =
        workouts.flatMap { it.entries }.filter { it.exerciseId == exId }.flatMap { it.sets }
            .maxOfOrNull { oneRm(it.weight, it.reps) } ?: 0.0

    // ---------- 饮食 ----------
    fun addFood(f: FoodLog) {
        foods = foods + f
        save()
    }

    fun deleteFood(id: Long) {
        foods = foods.filterNot { it.id == id }
        save()
    }

    fun addCustomFood(f: Food) {
        customFoods = customFoods.filterNot { it.name == f.name } + f
        save()
    }

    fun deleteCustomFood(name: String) {
        customFoods = customFoods.filterNot { it.name == name }
        save()
    }

    fun foodsOn(date: Long) = foods.filter { it.date == date }

    // ---------- 身体 ----------
    fun addWeight(date: Long, w: Double) {
        weights = (weights.filterNot { it.date == date } + WeightLog(date, w)).sortedBy { it.date }
        if (date >= (weights.maxOfOrNull { it.date } ?: 0)) profile = profile.copy(weightKg = w)
        save()
    }

    fun deleteWeight(date: Long) {
        weights = weights.filterNot { it.date == date }
        save()
    }

    fun addMeasure(m: Measure) {
        measures = (measures.filterNot { it.date == m.date } + m).sortedBy { it.date }
        save()
    }

    fun deleteMeasure(date: Long) {
        measures = measures.filterNot { it.date == date }
        save()
    }

    fun currentWeight(): Double = weights.lastOrNull()?.weight ?: profile.weightKg

    fun addWater(date: Long, ml: Int) {
        water = water + (date to ((water[date] ?: 0) + ml).coerceAtLeast(0))
        save()
    }

    fun addSleep(s: SleepLog) {
        sleeps = (sleeps.filterNot { it.date == s.date } + s).sortedBy { it.date }
        save()
    }

    fun deleteSleep(date: Long) {
        sleeps = sleeps.filterNot { it.date == date }
        save()
    }

    fun updateReminder(r: Reminder) {
        reminders = reminders.map { if (it.key == r.key) r else it }
        save()
    }

    /** 连续打卡周数：从本周（或上周）开始往前，每周至少训练 2 次 */
    fun weekStreak(): Int {
        val weeks = workouts.groupBy { (it.date + 3) / 7 }.mapValues { it.value.map { w -> w.date }.distinct().size }
        var w = (today() + 3) / 7
        if ((weeks[w] ?: 0) < 2) w--
        var n = 0
        while ((weeks[w] ?: 0) >= 2) { n++; w-- }
        return n
    }

    fun trainingDays(): Int = workouts.map { it.date }.distinct().size
}
