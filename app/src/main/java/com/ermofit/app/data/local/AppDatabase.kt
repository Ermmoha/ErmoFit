package com.ermofit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ermofit.app.data.local.dao.FitnessDao
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramExerciseEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.FavoriteEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        CategoryEntity::class,
        ExerciseEntity::class,
        ExerciseTextEntity::class,
        ProgramEntity::class,
        ProgramTextEntity::class,
        ProgramExerciseEntity::class,
        com.ermofit.app.newplan.data.local.entity.ExerciseEntity::class,
        TrainingEntity::class,
        TrainingExerciseEntity::class,
        WorkoutSessionEntity::class,
        FavoriteEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fitnessDao(): FitnessDao
    abstract fun newPlanDao(): NewPlanDao
}
