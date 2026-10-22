package com.muscleup.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.muscleup.app.data.Store

sealed interface Route {
    data object Main : Route
    data class ExerciseDetail(val id: String) : Route
    data class PlanDetail(val id: String) : Route
    data object Session : Route
    data object ExercisePicker : Route
    data class WorkoutDetail(val id: Long) : Route
    data class ExerciseProgress(val id: String) : Route
    data object ExerciseLibrary : Route
    data object KnowledgeList : Route
    data class ArticleR(val index: Int) : Route
    data object Supplements : Route
    data object Recipes : Route
    data object OneRmCalc : Route
    data object PlateCalc : Route
    data object Timer : Route
    data object BodyCalc : Route
    data object Sleep : Route
    data object Achievements : Route
    data object RemindersR : Route
    data object ProfileEdit : Route
    data object DataManage : Route
    data object Records : Route
    data object FloatTimerR : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Main)
    var tab by mutableIntStateOf(0)
    fun go(r: Route) {
        stack.add(r)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(r: Route) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(r)
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("首页", Icons.Filled.Home),
    TabItem("训练", Icons.Filled.FitnessCenter),
    TabItem("饮食", Icons.Filled.Restaurant),
    TabItem("身体", Icons.Filled.MonitorWeight),
    TabItem("我的", Icons.Filled.Person),
)

@Composable
fun App() {
    if (!Store.profile.onboarded) {
        Onboarding()
        return
    }
    val current = Nav.stack.last()
    BackHandler(enabled = Nav.stack.size > 1 || Nav.tab != 0) {
        if (Nav.stack.size > 1) Nav.back() else Nav.tab = 0
    }
    AnimatedContent(current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { r ->
        when (r) {
            Route.Main -> MainTabs()
            is Route.ExerciseDetail -> ExerciseDetailScreen(r.id)
            is Route.PlanDetail -> PlanDetailScreen(r.id)
            Route.Session -> SessionScreen()
            Route.ExercisePicker -> ExerciseLibraryScreen(pickMode = true)
            is Route.WorkoutDetail -> WorkoutDetailScreen(r.id)
            is Route.ExerciseProgress -> ExerciseProgressScreen(r.id)
            Route.ExerciseLibrary -> ExerciseLibraryScreen(pickMode = false)
            Route.KnowledgeList -> KnowledgeListScreen()
            is Route.ArticleR -> ArticleScreen(r.index)
            Route.Supplements -> SupplementsScreen()
            Route.Recipes -> RecipesScreen()
            Route.OneRmCalc -> OneRmScreen()
            Route.PlateCalc -> PlateScreen()
            Route.Timer -> TimerScreen()
            Route.BodyCalc -> BodyCalcScreen()
            Route.Sleep -> SleepScreen()
            Route.Achievements -> AchievementsScreen()
            Route.RemindersR -> RemindersScreen()
            Route.ProfileEdit -> ProfileEditScreen()
            Route.DataManage -> DataScreen()
            Route.Records -> RecordsScreen()
            Route.FloatTimerR -> FloatTimerScreen()
        }
    }
}

@Composable
private fun MainTabs() {
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = Nav.tab == i,
                        onClick = { Nav.tab = i },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (Nav.tab) {
            0 -> HomeTab(m)
            1 -> TrainTab(m)
            2 -> DietTab(m)
            3 -> BodyTab(m)
            else -> MeTab(m)
        }
    }
}
