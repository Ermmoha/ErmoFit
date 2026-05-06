package com.ermofit.app.ui.customprogram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ermofit.app.data.local.relation.ProgramExerciseWithDetails
import com.ermofit.app.data.model.CustomTrainingProgram
import com.ermofit.app.data.repository.CustomProgramsRepository
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.model.ResolvedExerciseText
import com.ermofit.app.domain.usecase.ExerciseTextResolver
import com.ermofit.app.ui.components.ExerciseProgramCard
import com.ermofit.app.ui.components.ProgramCard
import com.ermofit.app.ui.components.ShimmerPlaceholder
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomProgramDetailsUiState(
    val program: CustomTrainingProgram? = null,
    val exercises: List<ProgramExerciseWithDetails> = emptyList(),
    val exerciseTexts: Map<String, ResolvedExerciseText> = emptyMap(),
    val error: String? = null,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomProgramDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customProgramsRepository: CustomProgramsRepository,
    private val localDataRepository: LocalDataRepository,
    exerciseTextResolver: ExerciseTextResolver
) : ViewModel() {

    private val programId: String = checkNotNull(savedStateHandle["programId"])
    private val programFlow = customProgramsRepository.observeProgram(programId)
    private val settingsFlow = combine(
        exerciseTextResolver.observePreferredLangCode(),
        exerciseTextResolver.observeOnlyWithTranslation()
    ) { preferredLang, onlyTranslated ->
        DetailsSettings(
            preferredLang = preferredLang,
            onlyTranslated = onlyTranslated
        )
    }
    private val exercisesFlow = combine(programFlow, settingsFlow) { program, settings ->
        program to settings
    }.flatMapLatest { (program, settings) ->
        val exerciseIds = program?.exercises.orEmpty()
            .map { it.exerciseId }
            .distinct()
        if (program == null || exerciseIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            localDataRepository.observeExercisesByIds(
                ids = exerciseIds,
                langCode = settings.preferredLang,
                onlyTranslated = settings.onlyTranslated
            ).map { exercises ->
                val exerciseById = exercises.associateBy { it.id }
                program.exercises
                    .sortedBy { it.orderIndex }
                    .mapNotNull { item ->
                        val exercise = exerciseById[item.exerciseId] ?: return@mapNotNull null
                        ProgramExerciseWithDetails(
                            programId = program.id,
                            exerciseId = item.exerciseId,
                            orderIndex = item.orderIndex,
                            defaultDurationSec = item.durationSec,
                            defaultReps = item.reps,
                            title = exercise.title,
                            description = exercise.description,
                            muscleGroup = exercise.muscleGroup,
                            equipment = exercise.equipment,
                            tags = exercise.tags,
                            mediaType = exercise.mediaType,
                            mediaUrl = exercise.mediaUrl,
                            fallbackImageUrl = exercise.fallbackImageUrl
                        )
                    }
            }
        }
    }
    private val exerciseTextsFlow = exerciseTextResolver.observeTextsForProgramExercises(exercisesFlow)
    private val _error = MutableStateFlow<String?>(null)
    private val _isDeleting = MutableStateFlow(false)
    private val _isDeleted = MutableStateFlow(false)

    private val baseUiState = combine(
        programFlow,
        exercisesFlow,
        exerciseTextsFlow
    ) { program, exercises, exerciseTexts ->
        CustomProgramDetailsUiState(
            program = program,
            exercises = exercises,
            exerciseTexts = exerciseTexts,
            isLoading = false
        )
    }

    val uiState: StateFlow<CustomProgramDetailsUiState> = combine(
        baseUiState,
        _error,
        _isDeleting,
        _isDeleted
    ) { state, error, isDeleting, isDeleted ->
        state.copy(
            error = error,
            isDeleting = isDeleting,
            isDeleted = isDeleted
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CustomProgramDetailsUiState(isLoading = true)
    )

    fun deleteProgram(fallbackErrorMessage: String) {
        viewModelScope.launch {
            _isDeleting.value = true
            _error.value = null
            runCatching {
                customProgramsRepository.deleteProgram(programId)
            }.onSuccess {
                _isDeleted.value = true
            }.onFailure { throwable ->
                _error.value = throwable.message ?: fallbackErrorMessage
            }
            _isDeleting.value = false
        }
    }

    fun clearError() {
        _error.update { null }
    }

    fun consumeDeletion() {
        _isDeleted.update { false }
    }

    private data class DetailsSettings(
        val preferredLang: String,
        val onlyTranslated: Boolean
    )
}

@Composable
fun CustomProgramDetailsScreen(
    onExerciseClick: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onProgramDeleted: () -> Unit,
    viewModel: CustomProgramDetailsViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            viewModel.consumeDeletion()
            onProgramDeleted()
        }
    }

    if (uiState.isLoading) {
        CustomProgramDetailsShimmer()
        return
    }

    val program = uiState.program
    if (program == null) {
        Text(
            text = strings.programNotFound,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProgramCard(
                    program = program.toPreviewProgram(),
                    titleOverride = program.title,
                    onClick = {}
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = program.title,
                        fontWeight = FontWeight.Bold
                    )
                    if (program.description.isNotBlank()) {
                        Text(text = program.description)
                    }
                    Text(
                        text = "${strings.levelLabel}: ${localizedLevel(program.level, isRu)}"
                    )
                    Text(
                        text = "${strings.durationLabel}: ${program.estimatedDurationMinutes} ${strings.unitMinutesShort}"
                    )
                    Button(
                        onClick = { onStartWorkout(program.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.startWorkout)
                    }
                    Text(
                        text = strings.exercisesTitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            items(
                items = uiState.exercises,
                key = { item -> "${item.programId}_${item.orderIndex}_${item.exerciseId}" }
            ) { item ->
                val resolvedText = uiState.exerciseTexts[item.exerciseId]
                ExerciseProgramCard(
                    item = item,
                    titleOverride = resolvedText?.title,
                    descriptionOverride = resolvedText?.description,
                    onClick = { onExerciseClick(item.exerciseId) }
                )
            }
        }
    }
}

@Composable
private fun CustomProgramDetailsShimmer() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
                ShimmerBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }
        items(3) {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun ShimmerBlock(modifier: Modifier) {
    ShimmerPlaceholder(modifier = modifier)
}

private fun localizedLevel(rawLevel: String, isRu: Boolean): String {
    if (!isRu) return rawLevel
    return when {
        rawLevel.equals("beginner", ignoreCase = true) -> "Новичок"
        rawLevel.equals("intermediate", ignoreCase = true) -> "Средний"
        rawLevel.equals("advanced", ignoreCase = true) -> "Продвинутый"
        else -> rawLevel
    }
}
