package com.muscleup.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.muscleup.app.data.Exercises
import com.muscleup.app.data.Knowledge
import com.muscleup.app.data.Plans
import com.muscleup.app.data.Store
import com.muscleup.app.ui.Nav
import com.muscleup.app.ui.Route
import com.muscleup.app.ui.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 端到端走查：引导 → 训练 → 饮食 → 体重 → 打开每个页面，确保不崩溃且数据正确保存。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {
    init {
        com.muscleup.app.ui.LiveTicker.enabled = false
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.Before
    fun reset() {
        rule.runOnUiThread {
            Store.resetAll(); Session.discard()
            Nav.stack.clear(); Nav.stack.add(Route.Main); Nav.tab = 0
        }
        rule.waitForIdle()
    }

    private fun settle() {
        rule.waitForIdle()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shot(name: String) = settle()

    private fun go(r: Route, name: String) {
        rule.runOnUiThread { Nav.go(r) }
        shot(name)
        rule.runOnUiThread { Nav.back() }
        settle()
    }

    @Test
    fun fullFlow() {
        // 1. 引导
        rule.onNodeWithText("欢迎使用增肌助手").assertExists()
        shot("00_onboarding")
        rule.onNodeWithText("开始增肌之旅").performScrollTo().performClick()
        settle()
        assertTrue(Store.profile.onboarded)
        assertEquals(1, Store.weights.size)
        rule.onNodeWithText("今日营养").assertExists()
        shot("01_home")

        // 2. 训练：开始今日训练，完成第一组并保存
        rule.onNodeWithText("开始训练").performScrollTo().performClick()
        settle()
        shot("02_session")
        val boxes = rule.onAllNodes(isToggleable())
        boxes[0].performClick()
        settle()
        // 首组无历史重量，手动填写
        rule.runOnUiThread { Session.exercises[0].sets[0].weight = "40" }
        shot("03_session_rest")
        rule.onNodeWithText("完成训练").performClick()
        rule.onNodeWithText("保存").performClick()
        settle()
        assertEquals(1, Store.workouts.size)
        assertEquals(40.0, Store.workouts[0].entries[0].sets[0].weight, 0.01)
        assertEquals(1, Store.planCursor)
        shot("04_summary")
        rule.onNodeWithText("返回").performScrollTo().performClick()
        settle()

        // 3. 各个 Tab
        for ((i, n) in listOf("train", "diet", "body", "me").withIndex()) {
            rule.runOnUiThread { Nav.tab = i + 1 }
            shot("1${i}_tab_$n")
        }
        rule.runOnUiThread { Nav.tab = 1 }
        rule.onNodeWithText("动作库").performClick(); shot("15_train_library")
        rule.onNodeWithText("训练记录").performClick(); shot("16_train_history")

        // 4. 饮食：添加一个鸡蛋到早餐
        rule.runOnUiThread { Nav.tab = 2 }
        settle()
        rule.onAllNodesWithContentDescription("添加")[0].performClick()
        settle()
        shot("20_add_food")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("鸡蛋")
        settle()
        rule.onAllNodes(hasText("鸡蛋", substring = false) and !hasSetTextAction())[0].performClick()
        settle()
        shot("21_amount")
        rule.onNodeWithText("添加").performClick()
        settle()
        assertEquals(1, Store.foods.size)
        assertEquals("鸡蛋", Store.foods[0].name)
        shot("22_diet_after")

        // 5. 身体：记录体重
        rule.runOnUiThread { Nav.tab = 3 }
        rule.onNodeWithText("记体重").performClick()
        rule.onNodeWithText("+0.5").performClick()
        rule.onNodeWithText("保存").performClick()
        settle()
        assertEquals(58.5, Store.currentWeight(), 0.01)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("记围度"))
        rule.onNodeWithText("记围度").performClick()
        rule.onNodeWithText("保存").performClick()
        settle()
        assertEquals(1, Store.measures.size)
        shot("30_body")

        // 6. 打开每个子页面
        rule.runOnUiThread { Nav.tab = 0 }
        go(Route.PlanDetail(Plans.all[1].id), "40_plan")
        go(Route.ExerciseDetail("bench_press"), "41_exercise")
        go(Route.ExerciseProgress(Store.workouts[0].entries[0].exerciseId), "42_progress")
        go(Route.WorkoutDetail(Store.workouts[0].id), "43_workout_detail")
        go(Route.Records, "44_records")
        go(Route.ExerciseLibrary, "45_library")
        go(Route.KnowledgeList, "46_knowledge")
        Knowledge.articles.indices.forEach { go(Route.ArticleR(it), "47_article_$it") }
        go(Route.Supplements, "48_supplements")
        go(Route.Recipes, "49_recipes")
        go(Route.OneRmCalc, "50_onerm")
        go(Route.PlateCalc, "51_plate")
        go(Route.Timer, "52_timer")
        go(Route.BodyCalc, "53_bodycalc")
        go(Route.Sleep, "54_sleep")
        go(Route.Achievements, "55_achievements")
        go(Route.RemindersR, "56_reminders")
        go(Route.ProfileEdit, "57_profile")
        go(Route.DataManage, "58_data")

        // 7. 每个计划的每一天都能开始训练
        for (p in Plans.all) for (d in p.days.indices) {
            rule.runOnUiThread { Session.startFromPlan(p, d); Nav.go(Route.Session) }
            settle()
            assertTrue(Session.exercises.isNotEmpty())
            rule.runOnUiThread { Session.discard(); Nav.back() }
            settle()
        }
        // 自由训练 + 选择动作
        rule.runOnUiThread { Session.startEmpty(); Nav.go(Route.Session) }
        rule.onNodeWithText("添加动作", substring = true).performClick()
        settle()
        rule.onNodeWithText(Exercises.all[0].name).performClick()
        settle()
        assertEquals(1, Session.exercises.size)
        shot("60_free_session")

        // 8. 持久化：重新加载后数据仍在
        val ctx = rule.activity.applicationContext
        Store.init(ctx)
        assertEquals(1, Store.workouts.size)
        assertEquals(1, Store.foods.size)
        assertTrue(Store.exportJson().contains("鸡蛋"))
    }
}
