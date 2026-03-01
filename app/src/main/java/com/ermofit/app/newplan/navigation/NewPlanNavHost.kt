package com.ermofit.app.newplan.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ermofit.app.newplan.ui.bootstrap.BootstrapViewModel
import com.ermofit.app.newplan.ui.bootstrap.SplashScreen
import com.ermofit.app.newplan.ui.catalog.CatalogScreen
import com.ermofit.app.newplan.ui.catalog.CatalogViewModel
import com.ermofit.app.newplan.ui.exercisedetails.ExerciseDetailsScreen
import com.ermofit.app.newplan.ui.exercisedetails.ExerciseDetailsViewModel
import com.ermofit.app.newplan.ui.home.HomeScreen
import com.ermofit.app.newplan.ui.home.HomeViewModel
import com.ermofit.app.newplan.ui.onboarding.OnboardingScreen
import com.ermofit.app.newplan.ui.onboarding.OnboardingViewModel
import com.ermofit.app.newplan.ui.profile.ProfileScreen
import com.ermofit.app.newplan.ui.profile.ProfileViewModel
import com.ermofit.app.newplan.ui.search.SearchScreen
import com.ermofit.app.newplan.ui.search.SearchViewModel
import com.ermofit.app.newplan.ui.stats.StatsScreen
import com.ermofit.app.newplan.ui.stats.StatsViewModel
import com.ermofit.app.newplan.ui.workoutdetails.WorkoutDetailsScreen
import com.ermofit.app.newplan.ui.workoutdetails.WorkoutDetailsViewModel
import com.ermofit.app.newplan.ui.workoutplayer.WorkoutPlayerScreen
import com.ermofit.app.newplan.ui.workoutplayer.WorkoutPlayerViewModel

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPlanNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBars = route !in setOf(NewPlanRoutes.Splash, NewPlanRoutes.Onboarding)
    val rootTabs = setOf(
        NewPlanRoutes.Home,
        NewPlanRoutes.Catalog,
        NewPlanRoutes.Stats,
        NewPlanRoutes.Profile
    )
    val isRootTab = route in rootTabs

    Scaffold(
        topBar = {
            if (showBars) {
                CenterAlignedTopAppBar(
                    title = { Text(titleForRoute(route)) },
                    navigationIcon = {
                        if (!isRootTab) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    actions = {
                        if (route in setOf(NewPlanRoutes.Home, NewPlanRoutes.Catalog)) {
                            IconButton(
                                onClick = {
                                    navController.navigate(NewPlanRoutes.Search) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Поиск")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                )
            }
        },
        bottomBar = {
            if (isRootTab) {
                NavigationBar {
                    val items = bottomItems()
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { navController.navigateToRoot(item.route) },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NewPlanRoutes.Splash,
            modifier = Modifier.padding(padding)
        ) {
            composable(NewPlanRoutes.Splash) {
                val viewModel: BootstrapViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.targetRoute) {
                    val target = state.targetRoute ?: return@LaunchedEffect
                    navController.navigate(target) {
                        popUpTo(NewPlanRoutes.Splash) { inclusive = true }
                    }
                }
                SplashScreen(
                    uiState = state,
                    onRetry = viewModel::bootstrap
                )
            }

            composable(NewPlanRoutes.Onboarding) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.completed) {
                    if (!state.completed) return@LaunchedEffect
                    navController.navigate(NewPlanRoutes.Home) {
                        popUpTo(NewPlanRoutes.Onboarding) { inclusive = true }
                    }
                }
                OnboardingScreen(
                    uiState = state,
                    onGoalSelect = viewModel::selectGoal,
                    onLevelSelect = viewModel::selectLevel,
                    onDurationSelect = viewModel::selectDuration,
                    onEquipmentToggle = viewModel::toggleEquipment,
                    onRestrictionToggle = viewModel::toggleRestriction,
                    onRestSelect = viewModel::selectRest,
                    onNotificationsToggle = viewModel::setNotifications,
                    onSave = viewModel::save
                )
            }

            composable(NewPlanRoutes.Home) {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = state,
                    onOpenTraining = { id -> navController.navigate(NewPlanRoutes.workoutDetails(id)) },
                    onStartTraining = { id -> navController.navigate(NewPlanRoutes.workoutPlayer(id)) },
                    onRegenerate = viewModel::regenerate
                )
            }

            composable(NewPlanRoutes.Catalog) {
                val viewModel: CatalogViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CatalogScreen(
                    uiState = state,
                    onTabChange = viewModel::setTab,
                    onGoalChange = viewModel::setGoal,
                    onLevelChange = viewModel::setLevel,
                    onDurationChange = viewModel::setDuration,
                    onEquipmentToggle = viewModel::toggleEquipment,
                    onRestrictionToggle = viewModel::toggleRestriction,
                    onQueryChange = viewModel::setQuery,
                    onTrainingClick = { id -> navController.navigate(NewPlanRoutes.workoutDetails(id)) },
                    onExerciseClick = { id -> navController.navigate(NewPlanRoutes.exerciseDetails(id)) }
                )
            }

            composable(NewPlanRoutes.Stats) {
                val viewModel: StatsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                StatsScreen(uiState = state)
            }

            composable(NewPlanRoutes.Profile) {
                val viewModel: ProfileViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    uiState = state,
                    onGoalSelect = viewModel::selectGoal,
                    onLevelSelect = viewModel::selectLevel,
                    onDurationSelect = viewModel::selectDuration,
                    onEquipmentToggle = viewModel::toggleEquipment,
                    onRestrictionToggle = viewModel::toggleRestriction,
                    onRestSelect = viewModel::selectRest,
                    onNotificationsToggle = viewModel::setNotifications,
                    onLanguageSelect = viewModel::setContentLanguage,
                    onSave = viewModel::saveSettings,
                    onClearProgress = viewModel::clearProgress
                )
            }

            composable(NewPlanRoutes.Search) {
                val viewModel: SearchViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SearchScreen(
                    uiState = state,
                    onQueryChange = viewModel::setQuery,
                    onSubmitQuery = viewModel::submitQuery,
                    onHistoryClick = viewModel::useHistoryQuery,
                    onClearHistory = viewModel::clearHistory,
                    onTrainingClick = { id -> navController.navigate(NewPlanRoutes.workoutDetails(id)) },
                    onExerciseClick = { id -> navController.navigate(NewPlanRoutes.exerciseDetails(id)) }
                )
            }

            composable(
                route = NewPlanRoutes.WorkoutDetails,
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
            ) {
                val viewModel: WorkoutDetailsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WorkoutDetailsScreen(
                    uiState = state,
                    onExerciseClick = { id -> navController.navigate(NewPlanRoutes.exerciseDetails(id)) },
                    onStartWorkout = { id -> navController.navigate(NewPlanRoutes.workoutPlayer(id)) },
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }

            composable(
                route = NewPlanRoutes.ExerciseDetails,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) {
                val viewModel: ExerciseDetailsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ExerciseDetailsScreen(
                    uiState = state,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }

            composable(
                route = NewPlanRoutes.WorkoutPlayer,
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType })
            ) {
                val viewModel: WorkoutPlayerViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WorkoutPlayerScreen(
                    uiState = state,
                    onStartPause = viewModel::toggleStartPause,
                    onNext = viewModel::next,
                    onPrev = viewModel::prev,
                    onFinish = viewModel::finish,
                    onCompleteReps = viewModel::completeRepsSet,
                    onSaveResult = viewModel::saveResult
                )
            }
        }
    }
}

private fun titleForRoute(route: String): String {
    return when {
        route == NewPlanRoutes.Home -> "Главная"
        route == NewPlanRoutes.Catalog -> "Каталог"
        route == NewPlanRoutes.Stats -> "Статистика"
        route == NewPlanRoutes.Profile -> "Профиль"
        route == NewPlanRoutes.Search -> "Поиск"
        route.startsWith("np_workout_details") -> "Тренировка"
        route.startsWith("np_exercise_details") -> "Упражнение"
        route.startsWith("np_workout_player") -> "Плеер"
        else -> "ErmoFit Home"
    }
}

private fun bottomItems(): List<BottomItem> {
    return listOf(
        BottomItem(
            route = NewPlanRoutes.Home,
            label = "Главная",
            icon = { Icon(Icons.Default.Home, contentDescription = null) }
        ),
        BottomItem(
            route = NewPlanRoutes.Catalog,
            label = "Каталог",
            icon = { Icon(Icons.Default.GridView, contentDescription = null) }
        ),
        BottomItem(
            route = NewPlanRoutes.Stats,
            label = "Статистика",
            icon = { Icon(Icons.Default.BarChart, contentDescription = null) }
        ),
        BottomItem(
            route = NewPlanRoutes.Profile,
            label = "Профиль",
            icon = { Icon(Icons.Default.Person, contentDescription = null) }
        )
    )
}

private fun NavHostController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(NewPlanRoutes.Home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
