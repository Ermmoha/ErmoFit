package com.ermofit.app.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedRepository @Inject constructor(
    private val externalWorkoutImportRepository: ExternalWorkoutImportRepository
) {
    suspend fun ensureSeedLoaded() {
        externalWorkoutImportRepository.importFromSourcesIfNeeded()
    }
}

