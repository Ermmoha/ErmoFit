package com.ermofit.app.newplan.data.local.relation

import com.ermofit.app.newplan.data.local.entity.TrainingEntity

data class TrainingWithExercises(
    val training: TrainingEntity,
    val exercises: List<TrainingExerciseJoined>
)
