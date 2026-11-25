package com.lvupme.app.ui.navigation

import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lvupme.app.AppContainer
import com.lvupme.app.ui.exam.ExamScreen
import com.lvupme.app.ui.home.HomeScreen
import com.lvupme.app.ui.onboarding.LevelTestScreen
import com.lvupme.app.ui.onboarding.OnboardingChatScreen
import com.lvupme.app.ui.onboarding.OnboardingProposal
import com.lvupme.app.ui.settings.SettingsScreen
import com.lvupme.app.ui.stats.StatsScreen
import com.lvupme.app.ui.study.StudyScreen
import com.lvupme.app.ui.theme.LvUpBackground
import com.lvupme.app.ui.theme.game
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ONBOARDING_CHAT = "onboarding_chat"
    const val LEVEL_TEST = "level_test/{proposalJson}"
    const val STUDY = "study/{subjectId}?cardId={cardId}&unitId={unitId}"
    const val EXAM = "exam/{subjectId}?unitId={unitId}"

    private val json = Json { ignoreUnknownKeys = true }

    fun levelTest(proposal: OnboardingProposal): String {
        val rawBytes = json.encodeToString(proposal).toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(rawBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "level_test/$encoded"
    }

    fun parseProposal(raw: String?): OnboardingProposal? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val decoded = String(
                Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            json.decodeFromString<OnboardingProposal>(decoded)
        }.getOrNull()
    }

    fun study(subjectId: Long, cardId: Long = -1L, unitId: Long = -1L) =
        "study/$subjectId?cardId=$cardId&unitId=$unitId"

    fun exam(subjectId: Long, unitId: Long = -1L) =
        "exam/$subjectId?unitId=$unitId"
}

@Composable
fun LvUpNavGraph(
    container: AppContainer,
    deepLinkSubjectId: Long?,
    deepLinkCardId: Long?,
    onDeepLinkConsumed: () -> Unit
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.STATS, Routes.SETTINGS)

    LaunchedEffect(deepLinkSubjectId, deepLinkCardId) {
        if (deepLinkSubjectId != null && deepLinkSubjectId > 0L) {
            navController.navigate(Routes.study(deepLinkSubjectId, deepLinkCardId ?: -1L)) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    val g = MaterialTheme.game
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = g.sky,
        selectedTextColor = g.sky,
        indicatorColor = g.skySoft,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LvUpBackground()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        HorizontalDivider(thickness = 1.dp, color = g.cardBorder.copy(alpha = 0.7f))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            tonalElevation = 0.dp
                        ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = navItemColors,
                            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
                            label = { Text("홈", fontWeight = FontWeight.Bold) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.STATS,
                            onClick = {
                                navController.navigate(Routes.STATS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = navItemColors,
                            icon = { Icon(Icons.Default.BarChart, contentDescription = "통계") },
                            label = { Text("통계", fontWeight = FontWeight.Bold) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SETTINGS,
                            onClick = {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = navItemColors,
                            icon = { Icon(Icons.Default.Settings, contentDescription = "설정") },
                            label = { Text("설정", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onAddSubject = { navController.navigate(Routes.ONBOARDING_CHAT) },
                    onOpenStudy = { id, unitId ->
                        navController.navigate(Routes.study(id, unitId = unitId ?: -1L))
                    },
                    onOpenExam = { id, unitId ->
                        navController.navigate(Routes.exam(id, unitId = unitId ?: -1L))
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    container = container,
                    onPracticeWeakCard = { subjectId, cardId ->
                        navController.navigate(Routes.study(subjectId, cardId))
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(container = container)
            }
            composable(Routes.ONBOARDING_CHAT) {
                OnboardingChatScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onProceedToLevelTest = { proposal ->
                        navController.navigate(Routes.levelTest(proposal))
                    }
                )
            }
            composable(
                route = Routes.LEVEL_TEST,
                arguments = listOf(navArgument("proposalJson") { type = NavType.StringType })
            ) { entry ->
                val proposal = Routes.parseProposal(entry.arguments?.getString("proposalJson"))
                if (proposal == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    LevelTestScreen(
                        container = container,
                        proposal = proposal,
                        onBack = { navController.popBackStack() },
                        onFinished = { newSubjectId ->
                            navController.navigate(Routes.study(newSubjectId)) {
                                popUpTo(Routes.HOME) { inclusive = false }
                            }
                        }
                    )
                }
            }
            composable(
                route = Routes.STUDY,
                arguments = listOf(
                    navArgument("subjectId") { type = NavType.LongType },
                    navArgument("cardId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("unitId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val subjectId = entry.arguments?.getLong("subjectId") ?: return@composable
                val cardId = entry.arguments?.getLong("cardId")?.takeIf { it > 0L }
                val unitId = entry.arguments?.getLong("unitId")?.takeIf { it > 0L }
                StudyScreen(
                    container = container,
                    subjectId = subjectId,
                    initialPinnedCardId = cardId,
                    initialFocusUnitId = unitId,
                    onBack = { navController.popBackStack() },
                    onOpenExam = { id, targetUnitId ->
                        navController.navigate(Routes.exam(id, unitId = targetUnitId ?: -1L))
                    }
                )
            }
            composable(
                route = Routes.EXAM,
                arguments = listOf(
                    navArgument("subjectId") { type = NavType.LongType },
                    navArgument("unitId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val subjectId = entry.arguments?.getLong("subjectId") ?: return@composable
                val unitId = entry.arguments?.getLong("unitId")?.takeIf { it > 0L }
                ExamScreen(
                    container = container,
                    subjectId = subjectId,
                    initialUnitId = unitId,
                    onBack = { navController.popBackStack() },
                    onGoStudy = { id, targetUnitId ->
                        navController.navigate(Routes.study(id, unitId = targetUnitId ?: -1L)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    }
                )
            }
        }
    }
    }
}
