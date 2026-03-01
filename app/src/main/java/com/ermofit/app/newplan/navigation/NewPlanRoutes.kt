package com.ermofit.app.newplan.navigation

object NewPlanRoutes {
    const val Splash = "np_splash"
    const val Onboarding = "np_onboarding"

    const val Home = "np_home"
    const val Catalog = "np_catalog"
    const val Stats = "np_stats"
    const val Profile = "np_profile"

    const val Search = "np_search"
    const val WorkoutDetails = "np_workout_details/{trainingId}"
    const val ExerciseDetails = "np_exercise_details/{exerciseId}"
    const val WorkoutPlayer = "np_workout_player/{trainingId}"

    fun workoutDetails(trainingId: String): String = "np_workout_details/$trainingId"
    fun exerciseDetails(exerciseId: String): String = "np_exercise_details/$exerciseId"
    fun workoutPlayer(trainingId: String): String = "np_workout_player/$trainingId"
}

