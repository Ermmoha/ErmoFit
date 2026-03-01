package com.ermofit.app.newplan.domain.model

data class UserSettings(
    val goal: String = "strength",
    val level: String = "beginner",
    val durationMinutes: Int = 30,
    val equipmentOwned: List<String> = listOf("no_equipment"),
    val restrictions: List<String> = emptyList(),
    val restSec: Int = 30,
    val notificationsEnabled: Boolean = false
)

object TrainingGoals {
    const val STRENGTH = "strength"
    const val FATBURN = "fatburn"
    const val ENDURANCE = "endurance"
    const val MOBILITY = "mobility"

    val all = listOf(STRENGTH, FATBURN, ENDURANCE, MOBILITY)
}

object TrainingLevels {
    const val BEGINNER = "beginner"
    const val INTERMEDIATE = "intermediate"
    const val ADVANCED = "advanced"

    val all = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
}

object EquipmentTags {
    const val NO_EQUIPMENT = "no_equipment"
    const val MAT = "mat"
    const val BANDS = "bands"
    const val DUMBBELLS = "dumbbells"
    const val KETTLEBELL = "kettlebell"
    const val BARBELL = "barbell"
    const val CABLE = "cable"
    const val MACHINE = "machine"
    const val MEDICINE_BALL = "medicine_ball"
    const val EXERCISE_BALL = "exercise_ball"
    const val FOAM_ROLLER = "foam_roller"
    const val EZ_BAR = "ez_bar"
    const val OTHER = "other"
    const val PULLUP_BAR = "pullup_bar"
    const val JUMP_ROPE = "jump_rope"
    const val BENCH = "bench"

    val all = listOf(
        NO_EQUIPMENT,
        MAT,
        BANDS,
        DUMBBELLS,
        KETTLEBELL,
        BARBELL,
        CABLE,
        MACHINE,
        MEDICINE_BALL,
        EXERCISE_BALL,
        FOAM_ROLLER,
        EZ_BAR,
        OTHER,
        PULLUP_BAR,
        JUMP_ROPE,
        BENCH
    )
}

object RestrictionTags {
    const val NO_JUMPS = "no_jumps"
    const val KNEES = "knees"
    const val LOWER_BACK = "lower_back"
    const val QUIET = "quiet"

    val all = listOf(NO_JUMPS, KNEES, LOWER_BACK, QUIET)
}
