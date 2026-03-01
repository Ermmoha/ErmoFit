package com.ermofit.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.ermofit.app.R
import com.ermofit.app.data.local.model.MediaType
import com.ermofit.app.ui.i18n.appStrings

@Composable
@Suppress("UNUSED_PARAMETER")
fun ExerciseMedia(
    mediaType: MediaType,
    mediaUrl: String,
    fallbackImageUrl: String? = null,
    stableKey: String = mediaUrl,
    modifier: Modifier = Modifier
) {
    val preferredUrl = mediaUrl.takeIf { it.isNotBlank() } ?: fallbackImageUrl.orEmpty()
    val imageModifier = modifier
        .fillMaxWidth()
        .height(220.dp)

    ExerciseImage(
        mediaUrl = preferredUrl,
        stableKey = stableKey,
        modifier = imageModifier
    )
}

@Composable
private fun ExerciseImage(
    mediaUrl: String,
    stableKey: String,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val resolvedUrl = mediaUrl.trim()
    val candidates = remember(resolvedUrl) { imageUrlCandidates(resolvedUrl) }
    var candidateIndex by remember(resolvedUrl) { mutableStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex).orEmpty()

    if (activeUrl.isBlank()) {
        FallbackExerciseImage(seed = stableKey, modifier = modifier)
        return
    }

    SubcomposeAsyncImage(
        model = activeUrl,
        contentDescription = strings.exerciseMediaContentDescription,
        onError = {
            if (candidateIndex < candidates.lastIndex) {
                candidateIndex += 1
            }
        },
        loading = {
            FallbackExerciseImage(
                seed = stableKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        },
        error = {
            FallbackExerciseImage(
                seed = stableKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        },
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun FallbackExerciseImage(
    seed: String,
    modifier: Modifier = Modifier
) {
    val imageId = fallbackExerciseImage(seed)
    Image(
        painter = painterResource(id = imageId),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

private fun fallbackExerciseImage(seed: String): Int {
    val images = listOf(
        R.drawable.ex_media_01,
        R.drawable.ex_media_02,
        R.drawable.ex_media_03,
        R.drawable.ex_media_04,
        R.drawable.ex_media_05,
        R.drawable.ex_media_06,
        R.drawable.ex_media_07,
        R.drawable.ex_media_08,
        R.drawable.ex_media_09,
        R.drawable.ex_media_10,
        R.drawable.ex_media_11,
        R.drawable.ex_media_12,
        R.drawable.ex_media_13,
        R.drawable.ex_media_14,
        R.drawable.ex_media_15,
        R.drawable.ex_media_16,
        R.drawable.ex_media_17,
        R.drawable.ex_media_18,
        R.drawable.ex_media_19,
        R.drawable.ex_media_20
    )
    val index = seed.hashCode().toLong().let { if (it < 0) -it else it }.toInt() % images.size
    return images[index]
}
