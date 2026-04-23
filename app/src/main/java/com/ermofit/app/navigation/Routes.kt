package com.ermofit.app.navigation

object RootRoutes {
    const val Welcome = "welcome"
    const val Login = "login"
    const val Register = "register"
    const val Main = "main"
}

object MainRoutes {
    const val WorkoutSourceStock = "stock"
    const val WorkoutSourceCustom = "custom"
    const val Favorites = "favorites"
    const val Home = "home"
    const val Exercises = "exercises"
    const val Profile = "profile"
    const val Search = "search"
    const val CreateCustomProgram = "custom-programs/create"
    const val EditCustomProgram = "custom-programs/{programId}/edit"
    const val CustomProgramDetails = "custom-programs/{programId}"
    const val ProgramDetails = "program/{programId}"
    const val ExerciseDetails = "exercise/{exerciseId}"
    const val WorkoutPlayer = "workout/{programId}?source={source}"

    fun customProgramDetails(programId: String) = "custom-programs/$programId"
    fun editCustomProgram(programId: String) = "custom-programs/$programId/edit"
    fun programDetails(programId: String) = "program/$programId"
    fun exerciseDetails(exerciseId: String) = "exercise/$exerciseId"
    fun workoutPlayer(
        programId: String,
        source: String = WorkoutSourceStock
    ) = "workout/$programId?source=$source"
}
