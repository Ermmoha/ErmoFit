package com.ermofit.app.data.repository

import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.FavoriteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: NewPlanDao
) {
    suspend fun toggleFavoriteProgram(programId: String) {
        toggle(programId, TYPE_FAVORITE_PROGRAM)
    }

    fun observeFavoriteProgramIds(): Flow<List<String>> =
        dao.observeFavoritesByType(TYPE_FAVORITE_PROGRAM)
            .map { list -> list.map(FavoriteEntity::id) }

    suspend fun toggleFavoriteExercise(exerciseId: String) {
        toggle(exerciseId, TYPE_FAVORITE_EXERCISE)
    }

    fun observeFavoriteExerciseIds(): Flow<List<String>> =
        dao.observeFavoritesByType(TYPE_FAVORITE_EXERCISE)
            .map { list -> list.map(FavoriteEntity::id) }

    suspend fun toggleDownloadedProgram(programId: String) {
        toggle(programId, TYPE_DOWNLOADED_PROGRAM)
    }

    fun observeDownloadedProgramIds(): Flow<List<String>> =
        dao.observeFavoritesByType(TYPE_DOWNLOADED_PROGRAM)
            .map { list -> list.map(FavoriteEntity::id) }

    suspend fun toggleDownloadedExercise(exerciseId: String) {
        toggle(exerciseId, TYPE_DOWNLOADED_EXERCISE)
    }

    fun observeDownloadedExerciseIds(): Flow<List<String>> =
        dao.observeFavoritesByType(TYPE_DOWNLOADED_EXERCISE)
            .map { list -> list.map(FavoriteEntity::id) }

    private suspend fun toggle(id: String, type: String) {
        if (dao.isFavorite(id = id, type = type)) {
            dao.removeFavorite(id = id, type = type)
        } else {
            dao.upsertFavorite(FavoriteEntity(id = id, type = type))
        }
    }

    private companion object {
        const val TYPE_FAVORITE_PROGRAM = "favorite_program"
        const val TYPE_FAVORITE_EXERCISE = "favorite_exercise"
        const val TYPE_DOWNLOADED_PROGRAM = "downloaded_program"
        const val TYPE_DOWNLOADED_EXERCISE = "downloaded_exercise"
    }
}
