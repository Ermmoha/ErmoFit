package com.ermofit.app.data.repository

import com.ermofit.app.data.model.CustomTrainingExercise
import com.ermofit.app.data.model.CustomTrainingProgram
import com.ermofit.app.data.model.CustomTrainingProgramDraft
import com.ermofit.app.data.model.estimateCustomTrainingDurationMinutes
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CustomProgramsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {

    fun observePrograms(): Flow<List<CustomTrainingProgram>> {
        return authRepository.observeUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    var registration: ListenerRegistration? = null
                    registration = programsCollection(userId)
                        .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                if (error.isFatalFirestoreError()) {
                                    registration?.remove()
                                }
                                close(error)
                                return@addSnapshotListener
                            }
                            val programs = snapshot?.documents
                                ?.mapNotNull { document -> document.toCustomTrainingProgram() }
                                .orEmpty()
                            trySend(programs)
                        }
                    awaitClose { registration.remove() }
                }
            }
        }
    }

    fun observeProgram(programId: String): Flow<CustomTrainingProgram?> {
        return authRepository.observeUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                callbackFlow {
                    var registration: ListenerRegistration? = null
                    registration = programDocument(userId, programId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                if (error.isFatalFirestoreError()) {
                                    registration?.remove()
                                }
                                close(error)
                                return@addSnapshotListener
                            }
                            trySend(snapshot?.takeIf { it.exists() }?.toCustomTrainingProgram())
                        }
                    awaitClose { registration.remove() }
                }
            }
        }
    }

    suspend fun createProgram(draft: CustomTrainingProgramDraft): String {
        val userId = authRepository.currentUserId() ?: error("User is not authenticated")
        val now = System.currentTimeMillis()
        val document = programsCollection(userId).document()
        val program = buildProgram(
            id = document.id,
            userId = userId,
            draft = draft,
            createdAt = now,
            updatedAt = now
        )
        document.set(program.toDocument()).await()
        return document.id
    }

    suspend fun updateProgram(
        programId: String,
        draft: CustomTrainingProgramDraft
    ): String {
        val userId = authRepository.currentUserId() ?: error("User is not authenticated")
        val document = programDocument(userId, programId)
        val existingProgram = document.get().await().toCustomTrainingProgram()
        val now = System.currentTimeMillis()
        val program = buildProgram(
            id = programId,
            userId = userId,
            draft = draft,
            createdAt = existingProgram?.createdAt ?: now,
            updatedAt = now
        )
        document.set(program.toDocument()).await()
        return programId
    }

    suspend fun deleteProgram(programId: String) {
        val userId = authRepository.currentUserId() ?: error("User is not authenticated")
        programDocument(userId, programId).delete().await()
    }

    private fun programsCollection(userId: String) = firestore
        .collection(COLLECTION_USERS)
        .document(userId)
        .collection(COLLECTION_TRAINING_PROGRAMS)

    private fun programDocument(userId: String, programId: String) =
        programsCollection(userId).document(programId)

    private fun buildProgram(
        id: String,
        userId: String,
        draft: CustomTrainingProgramDraft,
        createdAt: Long,
        updatedAt: Long
    ): CustomTrainingProgram {
        val normalizedExercises = draft.exercises
            .sortedBy(CustomTrainingExercise::orderIndex)
            .mapIndexed { index, exercise ->
                exercise.copy(
                    orderIndex = index,
                    sets = exercise.sets.coerceAtLeast(1),
                    reps = exercise.reps.coerceAtLeast(0),
                    durationSec = exercise.durationSec.coerceAtLeast(0),
                    restSec = exercise.restSec.coerceAtLeast(0),
                    notes = exercise.notes.trim()
                )
            }
        val estimatedDurationMinutes = estimateCustomTrainingDurationMinutes(
            exercises = normalizedExercises,
            interExerciseRestSec = draft.interExerciseRestSec
        )
        return CustomTrainingProgram(
            id = id,
            userId = userId,
            title = draft.title.trim(),
            description = draft.description.trim(),
            level = draft.level.trim().ifBlank { "beginner" },
            interExerciseRestSec = draft.interExerciseRestSec.coerceAtLeast(0),
            coverImageUrl = draft.coverImageUrl.trim(),
            estimatedDurationMinutes = estimatedDurationMinutes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            exercises = normalizedExercises
        )
    }

    private fun CustomTrainingProgram.toDocument(): Map<String, Any> {
        return mapOf(
            FIELD_ID to id,
            FIELD_USER_ID to userId,
            FIELD_TITLE to title,
            FIELD_DESCRIPTION to description,
            FIELD_LEVEL to level,
            FIELD_INTER_EXERCISE_REST_SEC to interExerciseRestSec,
            FIELD_COVER_IMAGE_URL to coverImageUrl,
            FIELD_ESTIMATED_DURATION_MINUTES to estimatedDurationMinutes,
            FIELD_CREATED_AT to createdAt,
            FIELD_UPDATED_AT to updatedAt,
            FIELD_EXERCISES to exercises.sortedBy(CustomTrainingExercise::orderIndex).map { exercise ->
                mapOf(
                    FIELD_EXERCISE_ID to exercise.exerciseId,
                    FIELD_ORDER_INDEX to exercise.orderIndex,
                    FIELD_SETS to exercise.sets,
                    FIELD_REPS to exercise.reps,
                    FIELD_DURATION_SEC to exercise.durationSec,
                    FIELD_REST_SEC to exercise.restSec,
                    FIELD_NOTES to exercise.notes
                )
            }
        )
    }

    private fun DocumentSnapshot.toCustomTrainingProgram(): CustomTrainingProgram? {
        val title = getString(FIELD_TITLE)?.trim().orEmpty()
        if (title.isBlank()) return null
        val exercises = (get(FIELD_EXERCISES) as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val exerciseId = map[FIELD_EXERCISE_ID].asString().trim()
                if (exerciseId.isBlank()) return@mapNotNull null
                CustomTrainingExercise(
                    exerciseId = exerciseId,
                    orderIndex = map[FIELD_ORDER_INDEX].asInt(),
                    sets = map[FIELD_SETS].asInt(defaultValue = 1).coerceAtLeast(1),
                    reps = map[FIELD_REPS].asInt().coerceAtLeast(0),
                    durationSec = map[FIELD_DURATION_SEC].asInt().coerceAtLeast(0),
                    restSec = map[FIELD_REST_SEC].asInt().coerceAtLeast(0),
                    notes = map[FIELD_NOTES].asString().trim()
                )
            }
            .sortedBy(CustomTrainingExercise::orderIndex)

        return CustomTrainingProgram(
            id = getString(FIELD_ID).orEmpty().ifBlank { id },
            userId = getString(FIELD_USER_ID).orEmpty(),
            title = title,
            description = getString(FIELD_DESCRIPTION).orEmpty(),
            level = getString(FIELD_LEVEL).orEmpty().ifBlank { "beginner" },
            interExerciseRestSec = getLong(FIELD_INTER_EXERCISE_REST_SEC)?.toInt() ?: 15,
            coverImageUrl = getString(FIELD_COVER_IMAGE_URL).orEmpty(),
            estimatedDurationMinutes = getLong(FIELD_ESTIMATED_DURATION_MINUTES)?.toInt()
                ?: estimateCustomTrainingDurationMinutes(exercises, getLong(FIELD_INTER_EXERCISE_REST_SEC)?.toInt() ?: 15),
            createdAt = getLong(FIELD_CREATED_AT) ?: 0L,
            updatedAt = getLong(FIELD_UPDATED_AT) ?: 0L,
            exercises = exercises
        )
    }

    private fun Any?.asInt(defaultValue: Int = 0): Int {
        return when (this) {
            is Number -> toInt()
            is String -> toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun Any?.asString(): String {
        return when (this) {
            null -> ""
            is String -> this
            else -> toString()
        }
    }

    private fun FirebaseFirestoreException.isFatalFirestoreError(): Boolean {
        return code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ||
            code == FirebaseFirestoreException.Code.UNAUTHENTICATED
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_TRAINING_PROGRAMS = "trainingPrograms"
        const val FIELD_ID = "id"
        const val FIELD_USER_ID = "userId"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_LEVEL = "level"
        const val FIELD_INTER_EXERCISE_REST_SEC = "interExerciseRestSec"
        const val FIELD_COVER_IMAGE_URL = "coverImageUrl"
        const val FIELD_ESTIMATED_DURATION_MINUTES = "estimatedDurationMinutes"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_EXERCISES = "exercises"
        const val FIELD_EXERCISE_ID = "exerciseId"
        const val FIELD_ORDER_INDEX = "orderIndex"
        const val FIELD_SETS = "sets"
        const val FIELD_REPS = "reps"
        const val FIELD_DURATION_SEC = "durationSec"
        const val FIELD_REST_SEC = "restSec"
        const val FIELD_NOTES = "notes"
    }
}
