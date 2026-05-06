package com.ermofit.app.data.repository

import com.ermofit.app.data.model.WorkoutProgressSession
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WorkoutProgressRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {

    fun observeSessions(): Flow<List<WorkoutProgressSession>> {
        return authRepository.observeUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    var registration: ListenerRegistration? = null
                    registration = sessionsCollection(userId)
                        .orderBy(FIELD_FINISHED_AT, Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                if (error.isFatalFirestoreError()) {
                                    registration?.remove()
                                }
                                close(error)
                                return@addSnapshotListener
                            }
                            val sessions = snapshot?.documents
                                ?.mapNotNull { document -> document.toWorkoutProgressSession() }
                                .orEmpty()
                            trySend(sessions)
                        }
                    awaitClose { registration?.remove() }
                }
            }
        }
    }

    suspend fun saveCompletedWorkout(
        programId: String,
        programTitle: String,
        source: String,
        completedExercises: Int,
        totalSeconds: Int,
        finishedAt: Long = System.currentTimeMillis()
    ) {
        val userId = authRepository.currentUserId() ?: return
        val document = sessionsCollection(userId).document()
        document.set(
            mapOf(
                FIELD_ID to document.id,
                FIELD_PROGRAM_ID to programId,
                FIELD_PROGRAM_TITLE to programTitle.trim(),
                FIELD_SOURCE to source,
                FIELD_COMPLETED_EXERCISES to completedExercises.coerceAtLeast(0),
                FIELD_TOTAL_SECONDS to totalSeconds.coerceAtLeast(0),
                FIELD_FINISHED_AT to finishedAt
            )
        ).await()
    }

    private fun sessionsCollection(userId: String) = firestore
        .collection(COLLECTION_USERS)
        .document(userId)
        .collection(COLLECTION_WORKOUT_SESSIONS)

    private fun DocumentSnapshot.toWorkoutProgressSession(): WorkoutProgressSession? {
        val finishedAt = getLong(FIELD_FINISHED_AT) ?: return null
        return WorkoutProgressSession(
            id = getString(FIELD_ID).orEmpty().ifBlank { id },
            programId = getString(FIELD_PROGRAM_ID).orEmpty(),
            programTitle = getString(FIELD_PROGRAM_TITLE).orEmpty(),
            source = getString(FIELD_SOURCE).orEmpty(),
            completedExercises = (getLong(FIELD_COMPLETED_EXERCISES) ?: 0L).toInt(),
            totalSeconds = (getLong(FIELD_TOTAL_SECONDS) ?: 0L).toInt(),
            finishedAt = finishedAt
        )
    }

    private fun FirebaseFirestoreException.isFatalFirestoreError(): Boolean {
        return code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ||
            code == FirebaseFirestoreException.Code.UNAUTHENTICATED
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_WORKOUT_SESSIONS = "workoutSessions"
        const val FIELD_ID = "id"
        const val FIELD_PROGRAM_ID = "programId"
        const val FIELD_PROGRAM_TITLE = "programTitle"
        const val FIELD_SOURCE = "source"
        const val FIELD_COMPLETED_EXERCISES = "completedExercises"
        const val FIELD_TOTAL_SECONDS = "totalSeconds"
        const val FIELD_FINISHED_AT = "finishedAt"
    }
}
