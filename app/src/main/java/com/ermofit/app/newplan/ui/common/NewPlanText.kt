package com.ermofit.app.newplan.ui.common

import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.RestrictionTags
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels

fun goalLabel(goal: String): String {
    return when (goal) {
        TrainingGoals.STRENGTH -> "Сила"
        TrainingGoals.FATBURN -> "Похудение"
        TrainingGoals.ENDURANCE -> "Выносливость"
        TrainingGoals.MOBILITY -> "Подвижность"
        else -> goal
    }
}

fun levelLabel(level: String): String {
    return when (level) {
        TrainingLevels.BEGINNER -> "Новичок"
        TrainingLevels.INTERMEDIATE -> "Средний"
        TrainingLevels.ADVANCED -> "Продвинутый"
        else -> level
    }
}

fun equipmentLabel(tag: String): String {
    return when (tag) {
        EquipmentTags.NO_EQUIPMENT -> "Без оборудования"
        EquipmentTags.MAT -> "Коврик"
        EquipmentTags.BANDS -> "Резинки"
        EquipmentTags.DUMBBELLS -> "Гантели"
        EquipmentTags.KETTLEBELL -> "Гиря"
        EquipmentTags.BARBELL -> "Штанга"
        EquipmentTags.CABLE -> "Кабельный тренажер"
        EquipmentTags.MACHINE -> "Тренажер"
        EquipmentTags.MEDICINE_BALL -> "Медбол"
        EquipmentTags.EXERCISE_BALL -> "Фитбол"
        EquipmentTags.FOAM_ROLLER -> "Роллер"
        EquipmentTags.EZ_BAR -> "EZ-гриф"
        EquipmentTags.OTHER -> "Прочее"
        EquipmentTags.PULLUP_BAR -> "Турник"
        EquipmentTags.JUMP_ROPE -> "Скакалка"
        EquipmentTags.BENCH -> "Скамья"
        else -> tag
    }
}

fun restrictionLabel(tag: String): String {
    return when (tag) {
        RestrictionTags.NO_JUMPS -> "Без прыжков"
        RestrictionTags.KNEES -> "Беречь колени"
        RestrictionTags.LOWER_BACK -> "Беречь поясницу"
        RestrictionTags.QUIET -> "Тихий режим"
        else -> tag
    }
}

fun durationLabel(minutes: Int): String = "$minutes мин"

fun exerciseTypeLabel(type: String): String {
    return when (type) {
        "time" -> "Время"
        "reps" -> "Повторы"
        else -> type
    }
}

fun muscleLabel(tag: String): String {
    return when (tag) {
        "legs" -> "Ноги"
        "upper" -> "Верх тела"
        "core" -> "Кор"
        "mobility" -> "Подвижность"
        "stretch" -> "Растяжка"
        "cardio" -> "Кардио"
        else -> tag
    }
}

val motivationPhrases = listOf(
    "Каждая тренировка приближает к цели.",
    "Сегодня важен не максимум, а регулярность.",
    "Короткая тренировка лучше пропуска.",
    "Контроль техники важнее скорости.",
    "Сделай первый подход и ритм появится."
)