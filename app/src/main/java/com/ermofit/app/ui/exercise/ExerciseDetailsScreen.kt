package com.ermofit.app.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.ui.components.ExerciseMedia
import com.ermofit.app.ui.i18n.appStrings
import java.nio.charset.Charset

@Composable
fun ExerciseDetailsScreen(
    viewModel: ExerciseDetailsViewModel = hiltViewModel()
) {
    val strings = appStrings()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exercise = uiState.exercise
    if (exercise == null) {
        Text(text = strings.exerciseNotFound, modifier = Modifier.padding(16.dp))
        return
    }
    val resolvedText = uiState.resolvedText
    val localizedTitle = resolvedText?.title?.ifBlank { exercise.title } ?: exercise.title
    val localizedDescription = resolvedText?.description?.ifBlank { exercise.description } ?: exercise.description
    val muscleGroupText = normalizeMetaValue(exercise.muscleGroup)
    val equipmentText = normalizeMetaValue(exercise.equipment)
    val tagsText = exercise.tags.map(::normalizeMetaValue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExerciseMedia(
                    mediaType = exercise.mediaType,
                    mediaUrl = exercise.mediaUrl,
                    fallbackImageUrl = exercise.fallbackImageUrl,
                    stableKey = exercise.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                )
                Text(
                    text = localizedTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = localizedDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaRow(label = strings.muscleGroupLabel, value = muscleGroupText)
                MetaRow(label = strings.equipmentLabel, value = equipmentText)
                MetaRow(
                    label = strings.tagsLabel,
                    value = tagsText.filter { it.isNotBlank() }.joinToString().ifBlank { "-" }
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::toggleFavorite,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                if (uiState.isFavorite) {
                    strings.removeExerciseFromFavorites
                } else {
                    strings.addExerciseToFavorites
                }
            )
        }
    }
}

private fun normalizeMetaValue(raw: String): String {
    val cleaned = raw
        .replace("[", "")
        .replace("]", "")
        .replace("\"", "")
        .trim()
    if (!looksLikeMojibake(cleaned)) return cleaned
    val decoded = runCatching {
        String(cleaned.toByteArray(Charset.forName("windows-1251")), Charsets.UTF_8)
    }.getOrDefault(cleaned)
    return if (decoded.count { it in '\u0410'..'\u044F' || it == '\u0401' || it == '\u0451' } >
        cleaned.count { it in '\u0410'..'\u044F' || it == '\u0401' || it == '\u0451' }) {
        decoded
    } else {
        cleaned
    }
}

private fun looksLikeMojibake(value: String): Boolean {
    if (value.isBlank()) return false
    val cyrSupplement = value.count { it in '\u0400'..'\u045F' && it != '\u0401' && it != '\u0451' }
    return cyrSupplement >= 2 && (value.contains('Р') || value.contains('С'))
}

@Composable
private fun MetaRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
