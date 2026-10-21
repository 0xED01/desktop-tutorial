package com.muscleup.app.data

data class PlanItem(val exId: String, val sets: Int, val reps: String, val restSec: Int)
data class PlanDay(val name: String, val focus: String, val items: List<PlanItem>)
data class Plan(
    val id: String, val name: String, val level: String, val perWeek: String, val place: String,
    val desc: String, val days: List<PlanDay>,
)

object Plans {
    private fun i(id: String, sets: Int, reps: String, rest: Int = 90) = PlanItem(id, sets, reps, rest)

    val all = listOf(
        Plan(
            "beginner_fullbody", "新手全身增肌（A/B 交替）", "新手", "每周 3 练", "健身房",
            "瘦子入门最推荐。每次练全身，大重量复合动作为主，频率高、恢复好。一三五训练，A/B 两套交替进行。每周尝试在动作标准的前提下加重或加次数。",
            listOf(
                PlanDay("A 日", "深蹲 · 卧推 · 划船", listOf(
                    i("squat", 3, "6-8", 150), i("bench_press", 3, "6-8", 150), i("barbell_row", 3, "8-10", 120),
                    i("db_shoulder_press", 2, "10-12"), i("db_curl", 2, "10-12", 60), i("plank", 3, "45秒", 60),
                )),
                PlanDay("B 日", "硬拉 · 推举 · 引体", listOf(
                    i("rdl", 3, "8-10", 120), i("ohp", 3, "6-8", 150), i("lat_pulldown", 3, "8-12", 90),
                    i("incline_db_press", 3, "8-12"), i("tricep_pushdown", 2, "10-12", 60), i("calf_raise", 3, "15-20", 60),
                )),
            ),
        ),
        Plan(
            "upper_lower", "上下肢分化", "进阶", "每周 4 练", "健身房",
            "适合训练 3 个月以上。上肢/下肢各练两次，力量日和增肌日搭配，兼顾力量增长与肌肉围度。建议 周一上肢A 周二下肢A 周四上肢B 周五下肢B。",
            listOf(
                PlanDay("上肢 A（力量）", "胸 · 背 · 肩", listOf(
                    i("bench_press", 4, "5-6", 180), i("barbell_row", 4, "6-8", 150), i("ohp", 3, "6-8", 120),
                    i("pullup", 3, "力竭", 120), i("db_curl", 3, "10-12", 60), i("skull_crusher", 3, "10-12", 60),
                )),
                PlanDay("下肢 A（力量）", "深蹲 · 硬拉", listOf(
                    i("squat", 4, "5-6", 180), i("rdl", 3, "8-10", 150), i("leg_press", 3, "10-12", 120),
                    i("leg_curl", 3, "10-12", 60), i("calf_raise", 4, "12-15", 60), i("hanging_leg_raise", 3, "10-15", 60),
                )),
                PlanDay("上肢 B（增肌）", "上胸 · 背宽 · 手臂", listOf(
                    i("incline_db_press", 4, "8-12"), i("lat_pulldown", 4, "10-12"), i("db_row", 3, "10-12"),
                    i("lateral_raise", 4, "12-20", 60), i("face_pull", 3, "15", 60), i("hammer_curl", 3, "12", 60),
                    i("overhead_ext", 3, "12", 60),
                )),
                PlanDay("下肢 B（增肌）", "腿型 · 臀部", listOf(
                    i("deadlift", 3, "5", 180), i("bulgarian_split", 3, "8-10"), i("hip_thrust", 3, "10-12"),
                    i("leg_ext", 3, "12-15", 60), i("leg_curl", 3, "12-15", 60), i("cable_crunch", 3, "15", 60),
                )),
            ),
        ),
        Plan(
            "ppl", "推 · 拉 · 腿（PPL）", "熟练", "每周 6 练（或 3 练）", "健身房",
            "经典高容量分化。推（胸肩三头）、拉（背二头）、腿各练一次为一轮，一周两轮。容量高，需要充足饮食与睡眠支持，饮食跟不上时建议每周只练一轮。",
            listOf(
                PlanDay("推日", "胸 · 肩 · 三头", listOf(
                    i("bench_press", 4, "6-8", 150), i("incline_db_press", 3, "8-12"), i("db_shoulder_press", 3, "8-12"),
                    i("lateral_raise", 4, "12-20", 60), i("cable_cross", 3, "12-15", 60), i("tricep_pushdown", 3, "10-12", 60),
                    i("overhead_ext", 3, "10-12", 60),
                )),
                PlanDay("拉日", "背 · 后束 · 二头", listOf(
                    i("deadlift", 3, "5", 180), i("pullup", 4, "力竭", 120), i("barbell_row", 3, "8-10", 120),
                    i("seated_row", 3, "10-12"), i("face_pull", 3, "15", 60), i("bb_curl", 3, "8-12", 60),
                    i("hammer_curl", 3, "12", 60),
                )),
                PlanDay("腿日", "腿 · 臀 · 核心", listOf(
                    i("squat", 4, "6-8", 180), i("rdl", 3, "8-10", 150), i("leg_press", 3, "10-12", 120),
                    i("leg_curl", 3, "12", 60), i("calf_raise", 4, "15", 60), i("hanging_leg_raise", 3, "12", 60),
                )),
            ),
        ),
        Plan(
            "home_db", "居家哑铃增肌", "新手-进阶", "每周 3-4 练", "居家（一对可调哑铃+凳子）",
            "没时间去健身房也能增肌。一对可调节哑铃即可覆盖全身。上下肢交替，推荐 周一上肢 周二下肢 周四上肢 周五下肢。",
            listOf(
                PlanDay("上肢", "胸 · 背 · 肩 · 手臂", listOf(
                    i("db_bench", 4, "8-12"), i("db_row", 4, "10-12"), i("db_shoulder_press", 3, "10-12"),
                    i("db_fly", 3, "12-15", 60), i("lateral_raise", 3, "15-20", 45), i("db_curl", 3, "12", 60),
                    i("overhead_ext", 3, "12", 60),
                )),
                PlanDay("下肢", "腿 · 臀 · 核心", listOf(
                    i("goblet_squat", 4, "10-12"), i("rdl", 3, "10-12"), i("bulgarian_split", 3, "10"),
                    i("lunge", 3, "12"), i("glute_bridge", 3, "15", 60), i("calf_raise", 4, "20", 45),
                    i("dead_bug", 3, "10", 45),
                )),
            ),
        ),
        Plan(
            "bodyweight", "零器械徒手增肌", "新手", "每周 3 练", "居家（无器械）",
            "适合完全没有器械的情况。依靠慢速离心、暂停和渐进变式增加难度（如俯卧撑→垫脚俯卧撑→钻石俯卧撑）。徒手增肌上限有限，有条件建议购买哑铃或弹力带。",
            listOf(
                PlanDay("全身 A", "推 · 腿", listOf(
                    i("pushup", 4, "力竭-2", 90), i("pike_pushup", 3, "8-12"), i("bw_squat", 4, "20-25", 60),
                    i("bulgarian_split", 3, "12-15"), i("dips_tri", 3, "12-15", 60), i("plank", 3, "60秒", 60),
                )),
                PlanDay("全身 B", "拉 · 臀", listOf(
                    i("inverted_row", 4, "8-12"), i("diamond_pushup", 3, "8-12"), i("glute_bridge", 4, "15-20", 60),
                    i("lunge", 3, "12-15"), i("calf_raise", 4, "20-25", 45), i("crunch", 3, "15-20", 45),
                )),
            ),
        ),
        Plan(
            "arms_shoulders", "肩臂专项（补短板）", "进阶", "每周 1-2 练（附加）", "健身房",
            "很多瘦子最在意肩宽和臂围。在主计划之外，每周增加 1 次肩臂专项，可以与腿日分开安排。",
            listOf(
                PlanDay("肩臂日", "三角肌 · 二头 · 三头", listOf(
                    i("db_shoulder_press", 3, "8-10"), i("lateral_raise", 4, "15-20", 45), i("rear_delt_fly", 3, "15", 45),
                    i("close_bench", 3, "8-10", 120), i("incline_curl", 3, "10-12", 60), i("skull_crusher", 3, "10-12", 60),
                    i("hammer_curl", 3, "12", 60), i("shrug", 3, "12-15", 60),
                )),
            ),
        ),
    )

    fun byId(id: String): Plan = all.firstOrNull { it.id == id } ?: all.first()
}
