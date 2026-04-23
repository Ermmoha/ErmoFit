package com.ermofit.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.navigation.MainRoutes
import com.ermofit.app.ui.customprogram.CustomProgramDetailsViewModel
import com.ermofit.app.ui.i18n.AppStrings
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.exercise.ExerciseDetailsScreen
import com.ermofit.app.ui.favorites.FavoritesScreen
import com.ermofit.app.ui.exercises.ExercisesScreen
import com.ermofit.app.ui.home.HomeScreen
import com.ermofit.app.ui.i18n.appStrings
import com.ermofit.app.ui.profile.ProfileScreen
import com.ermofit.app.ui.program.ProgramDetailsScreen
import com.ermofit.app.ui.customprogram.CustomProgramBuilderScreen
import com.ermofit.app.ui.customprogram.CustomProgramDetailsScreen
import com.ermofit.app.ui.search.SearchScreen
import com.ermofit.app.ui.workout.WorkoutPlayerScreen

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    onLogoutToWelcome: () -> Unit
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val navController = rememberNavController()
    var homeSortSignal by rememberSaveable { mutableIntStateOf(0) }
    var exercisesSortSignal by rememberSaveable { mutableIntStateOf(0) }
    var showDeleteCustomProgramDialog by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val isWorkoutRoute = route.startsWith("workout/")
    val customProgramId = backStackEntry?.arguments?.getString("programId")
    val customProgramDetailsViewModel = if (route == MainRoutes.CustomProgramDetails && backStackEntry != null) {
        hiltViewModel<CustomProgramDetailsViewModel>(backStackEntry!!)
    } else {
        null
    }
    val customProgramUiState = if (customProgramDetailsViewModel != null) {
        customProgramDetailsViewModel.uiState.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val isRootTab = route in setOf(
        MainRoutes.Favorites,
        MainRoutes.Home,
        MainRoutes.Exercises,
        MainRoutes.Profile
    )

    Scaffold(
        topBar = {
            if (!isWorkoutRoute) {
                CenterAlignedTopAppBar(
                    title = { Text(topBarTitle(route, strings)) },
                    navigationIcon = {
                        if (!isRootTab) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.navBackAction)
                            }
                        }
                    },
                    actions = {
                        if (route == MainRoutes.Home || route == MainRoutes.Exercises) {
                            IconButton(
                                onClick = {
                                    if (route == MainRoutes.Home) {
                                        homeSortSignal += 1
                                    } else {
                                        exercisesSortSignal += 1
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = if (isRu) "\u0424\u0438\u043b\u044c\u0442\u0440\u044b \u0438 \u0441\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430" else "Filters and sorting"
                                )
                            }
                            IconButton(
                                onClick = {
                                    navController.navigate(MainRoutes.Search) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = strings.navSearchAction)
                            }
                        } else if (isRootTab) {
                            IconButton(
                                onClick = {
                                    navController.navigate(MainRoutes.Search) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = strings.navSearchAction)
                            }
                        } else if (route == MainRoutes.CustomProgramDetails && customProgramId != null) {
                            IconButton(
                                onClick = {
                                    navController.navigate(MainRoutes.editCustomProgram(customProgramId)) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = strings.profileEdit)
                            }
                            IconButton(
                                onClick = { showDeleteCustomProgramDialog = true },
                                enabled = customProgramUiState?.isDeleting != true
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = strings.removeAction)
                            }
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (isRootTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = route == MainRoutes.Favorites,
                        onClick = {
                            navController.navigate(MainRoutes.Favorites) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = strings.tabFavorites) },
                        label = {
                            Text(
                                text = strings.tabFavorites,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = route == MainRoutes.Home,
                        onClick = {
                            navController.navigate(MainRoutes.Home) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = strings.tabHome) },
                        label = {
                            Text(
                                text = strings.tabHome,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = route == MainRoutes.Exercises,
                        onClick = {
                            navController.navigate(MainRoutes.Exercises) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = strings.exercisesLabel) },
                        label = {
                            Text(
                                text = strings.exercisesLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = route == MainRoutes.Profile,
                        onClick = {
                            navController.navigate(MainRoutes.Profile) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Person, contentDescription = strings.tabProfile) },
                        label = {
                            Text(
                                text = strings.tabProfile,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainRoutes.Home,
            modifier = Modifier.padding(padding)
        ) {
            composable(MainRoutes.Home) {
                HomeScreen(
                    sortDialogSignal = homeSortSignal,
                    onProgramClick = { navController.navigate(MainRoutes.programDetails(it)) },
                    onCreateProgramClick = {
                        navController.navigate(MainRoutes.CreateCustomProgram) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(MainRoutes.Favorites) {
                FavoritesScreen(
                    onCustomProgramClick = { navController.navigate(MainRoutes.customProgramDetails(it)) },
                    onProgramClick = { navController.navigate(MainRoutes.programDetails(it)) },
                    onExerciseClick = { navController.navigate(MainRoutes.exerciseDetails(it)) }
                )
            }
            composable(MainRoutes.Profile) {
                ProfileScreen(onLoggedOut = onLogoutToWelcome)
            }
            composable(MainRoutes.Exercises) {
                ExercisesScreen(
                    sortDialogSignal = exercisesSortSignal,
                    onExerciseClick = { navController.navigate(MainRoutes.exerciseDetails(it)) }
                )
            }
            composable(MainRoutes.Search) {
                SearchScreen(
                    onProgramClick = { navController.navigate(MainRoutes.programDetails(it)) },
                    onExerciseClick = { navController.navigate(MainRoutes.exerciseDetails(it)) }
                )
            }
            composable(MainRoutes.CreateCustomProgram) {
                CustomProgramBuilderScreen(
                    onProgramSaved = { programId ->
                        navController.navigate(MainRoutes.customProgramDetails(programId)) {
                            popUpTo(MainRoutes.CreateCustomProgram) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = MainRoutes.EditCustomProgram,
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) {
                CustomProgramBuilderScreen(
                    onProgramSaved = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = MainRoutes.CustomProgramDetails,
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) {
                CustomProgramDetailsScreen(
                    onExerciseClick = { navController.navigate(MainRoutes.exerciseDetails(it)) },
                    onStartWorkout = {
                        navController.navigate(
                            MainRoutes.workoutPlayer(
                                programId = it,
                                source = MainRoutes.WorkoutSourceCustom
                            )
                        )
                    },
                    onProgramDeleted = { navController.popBackStack() }
                )
            }
            composable(
                route = MainRoutes.ProgramDetails,
                arguments = listOf(navArgument("programId") { type = NavType.StringType })
            ) {
                ProgramDetailsScreen(
                    onExerciseClick = { navController.navigate(MainRoutes.exerciseDetails(it)) },
                    onStartWorkout = { navController.navigate(MainRoutes.workoutPlayer(it)) }
                )
            }
            composable(
                route = MainRoutes.ExerciseDetails,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) {
                ExerciseDetailsScreen()
            }
            composable(
                route = MainRoutes.WorkoutPlayer,
                arguments = listOf(
                    navArgument("programId") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = MainRoutes.WorkoutSourceStock
                    }
                )
            ) {
                WorkoutPlayerScreen(
                    onBack = { navController.popBackStack() },
                    onFinish = { navController.popBackStack() }
                )
            }
        }
    }

    if (showDeleteCustomProgramDialog && customProgramDetailsViewModel != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomProgramDialog = false },
            title = {
                Text(if (isRu) "Удалить программу?" else "Delete program?")
            },
            text = {
                Text(
                    if (isRu) {
                        "Программа будет удалена из вашего профиля без возможности восстановления."
                    } else {
                        "This program will be removed from your profile and cannot be restored."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCustomProgramDialog = false
                        customProgramDetailsViewModel.deleteProgram(
                            fallbackErrorMessage = if (isRu) {
                                "Не удалось удалить программу."
                            } else {
                                "Failed to delete the program."
                            }
                        )
                    }
                ) {
                    Text(strings.removeAction)
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showDeleteCustomProgramDialog = false }
                ) {
                    Text(strings.profileCancel)
                }
            }
        )
    }
}

private fun topBarTitle(route: String, strings: AppStrings): String {
    return when {
        route == MainRoutes.Home -> strings.topHome
        route == MainRoutes.Favorites -> strings.topFavorites
        route == MainRoutes.Profile -> strings.topProfile
        route == MainRoutes.Exercises -> strings.exercisesLabel
        route == MainRoutes.Search -> strings.topSearch
        route == MainRoutes.CreateCustomProgram -> strings.topCreateProgram
        route == MainRoutes.EditCustomProgram -> strings.topCreateProgram
        route.startsWith("custom-programs/") -> strings.topProgramDetails
        route.startsWith("program/") -> strings.topProgramDetails
        route.startsWith("exercise/") -> strings.topExerciseDetails
        route.startsWith("workout/") -> strings.topWorkout
        else -> strings.appName
    }
}


