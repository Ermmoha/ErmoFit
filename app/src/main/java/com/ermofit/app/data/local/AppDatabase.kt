package com.ermofit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ermofit.app.data.local.dao.FitnessDao
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.CategoryTextEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTagEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.entity.FavoriteEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramExerciseEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity

@Database(
    entities = [
        CategoryEntity::class,
        CategoryTextEntity::class,
        ExerciseEntity::class,
        ExerciseTagEntity::class,
        ExerciseTextEntity::class,
        ProgramEntity::class,
        ProgramTextEntity::class,
        ProgramExerciseEntity::class,
        FavoriteEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fitnessDao(): FitnessDao
}
