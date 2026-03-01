package com.ermofit.app.navigation

object RootRoutes {
    const val Welcome = "welcome"
    const val Login = "login"
    const val Register = "register"
    const val Main = "main"
}

object MainRoutes {
    const val Favorites = "favorites"
    const val Home = "home"
    const val Profile = "profile"
    const val Search = "search"
    const val ProgramDetails = "program/{programId}"
    const val ExerciseDetails = "exercise/{exerciseId}"
    const val WorkoutPlayer = "workout/{programId}"

    fun programDetails(programId: String) = "program/$programId"
    fun exerciseDetails(exerciseId: String) = "exercise/$exerciseId"
    fun workoutPlayer(programId: String) = "workout/$programId"
}
