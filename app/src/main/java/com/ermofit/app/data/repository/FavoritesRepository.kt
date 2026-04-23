package com.ermofit.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.FavoriteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: NewPlanDao,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    suspend fun toggleFavoriteProgram(programId: String) {
        toggleFavorite(
            id = programId,
            localType = TYPE_FAVORITE_PROGRAM,
            remoteCollection = COLLECTION_FAVORITE_PROGRAMS
        )
    }

    fun observeFavoriteProgramIds(): Flow<List<String>> =
        observeFavoriteIds(
            localType = TYPE_FAVORITE_PROGRAM,
            remoteCollection = COLLECTION_FAVORITE_PROGRAMS
        )

    suspend fun toggleFavoriteExercise(exerciseId: String) {
        toggleFavorite(
            id = exerciseId,
            localType = TYPE_FAVORITE_EXERCISE,
            remoteCollection = COLLECTION_FAVORITE_EXERCISES
        )
    }

    fun observeFavoriteExerciseIds(): Flow<List<String>> =
        observeFavoriteIds(
            localType = TYPE_FAVORITE_EXERCISE,
            remoteCollection = COLLECTION_FAVORITE_EXERCISES
        )

    suspend fun toggleDownloadedProgram(programId: String) {
        toggleLocal(programId, TYPE_DOWNLOADED_PROGRAM)
    }

    fun observeDownloadedProgramIds(): Flow<List<String>> =
        observeLocalFavorites(TYPE_DOWNLOADED_PROGRAM)

    suspend fun toggleDownloadedExercise(exerciseId: String) {
        toggleLocal(exerciseId, TYPE_DOWNLOADED_EXERCISE)
    }

    fun observeDownloadedExerciseIds(): Flow<List<String>> =
        observeLocalFavorites(TYPE_DOWNLOADED_EXERCISE)

    private fun observeFavoriteIds(
        localType: String,
        remoteCollection: String
    ): Flow<List<String>> {
        return authRepository.observeUserId().flatMapLatest { userId ->
            if (userId == null) {
                observeLocalFavorites(localType)
            } else {
                callbackFlow {
                    val registration = userFavoritesCollection(
                        userId = userId,
                        collectionName = remoteCollection
                    ).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        trySend(snapshot?.documents?.map { it.id }.orEmpty())
                    }
                    awaitClose { registration.remove() }
                }
            }
        }
    }

    private fun observeLocalFavorites(type: String): Flow<List<String>> =
        dao.observeFavoritesByType(type)
            .map { list -> list.map(FavoriteEntity::id) }

    private suspend fun toggleFavorite(
        id: String,
        localType: String,
        remoteCollection: String
    ) {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            toggleLocal(id, localType)
            return
        }

        val document = userFavoritesCollection(
            userId = userId,
            collectionName = remoteCollection
        ).document(id)

        if (document.get().await().exists()) {
            document.delete().await()
        } else {
            document.set(
                mapOf(
                    FIELD_ID to id,
                    FIELD_UPDATED_AT to System.currentTimeMillis()
                )
            ).await()
        }
    }

    private suspend fun toggleLocal(id: String, type: String) {
        if (dao.isFavorite(id = id, type = type)) {
            dao.removeFavorite(id = id, type = type)
        } else {
            dao.upsertFavorite(FavoriteEntity(id = id, type = type))
        }
    }

    private fun userFavoritesCollection(
        userId: String,
        collectionName: String
    ) = firestore
        .collection(COLLECTION_USERS)
        .document(userId)
        .collection(collectionName)

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_FAVORITE_PROGRAMS = "favoritesPrograms"
        const val COLLECTION_FAVORITE_EXERCISES = "favoritesExercises"
        const val FIELD_ID = "id"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val TYPE_FAVORITE_PROGRAM = "favorite_program"
        const val TYPE_FAVORITE_EXERCISE = "favorite_exercise"
        const val TYPE_DOWNLOADED_PROGRAM = "downloaded_program"
        const val TYPE_DOWNLOADED_EXERCISE = "downloaded_exercise"
    }
}
