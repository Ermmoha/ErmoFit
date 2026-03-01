package com.ermofit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.ermofit.app.R
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.ui.i18n.appLanguage
import com.ermofit.app.ui.i18n.appStrings

@Composable
fun ProgramCard(
    program: ProgramEntity,
    onClick: () -> Unit,
    titleOverride: String? = null,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val isRu = appLanguage().raw == "ru"
    val title = titleOverride?.takeIf { it.isNotBlank() } ?: program.title
    val level = localizedLevel(program.level, isRu)
    val currentUrl = program.backgroundImageUrl.trim()
    val urlCandidates = remember(currentUrl) { imageUrlCandidates(currentUrl) }
    var candidateIndex by remember(currentUrl) { mutableStateOf(0) }
    val activeUrl = urlCandidates.getOrNull(candidateIndex).orEmpty()
    val accent = spotifyAccent(program.id)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(214.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (activeUrl.isBlank()) {
                FallbackProgramImage(
                    seed = program.id,
                    title = title
                )
            } else {
                SubcomposeAsyncImage(
                    model = activeUrl,
                    contentDescription = title,
                    onError = {
                        if (candidateIndex < urlCandidates.lastIndex) {
                            candidateIndex += 1
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(214.dp),
                    contentScale = ContentScale.Crop,
                    loading = {
                        FallbackProgramImage(
                            seed = program.id,
                            title = title
                        )
                    },
                    error = {
                        FallbackProgramImage(
                            seed = program.id,
                            title = title
                        )
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.06f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text(
                        text = level,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = accent.copy(alpha = 0.9f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${program.durationMinutes} ${strings.unitMinutesShort}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isRu) {
                        "\u0422\u0440\u0435\u043d\u0438\u0440\u043e\u0432\u043e\u0447\u043d\u0430\u044f \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u0430"
                    } else {
                        "ErmoFit Program"
                    },
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun localizedLevel(rawLevel: String, isRu: Boolean): String {
    if (!isRu) return rawLevel
    return when {
        rawLevel.equals("beginner", ignoreCase = true) ->
            "\u041d\u043e\u0432\u0438\u0447\u043e\u043a"
        rawLevel.equals("intermediate", ignoreCase = true) ->
            "\u0421\u0440\u0435\u0434\u043d\u0438\u0439"
        rawLevel.equals("advanced", ignoreCase = true) ->
            "\u041f\u0440\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044b\u0439"
        else -> rawLevel
    }
}

private fun spotifyAccent(seed: String): Color {
    val palette = listOf(
        Color(0xFF1ED760),
        Color(0xFF1DB954),
        Color(0xFF06D6A0),
        Color(0xFF2DE2E6),
        Color(0xFFF2C94C)
    )
    val index = seed.hashCode().toLong().let { if (it < 0) -it else it }.toInt() % palette.size
    return palette[index]
}

@Composable
private fun FallbackProgramImage(
    seed: String,
    title: String
) {
    Image(
        painter = painterResource(id = fallbackProgramImage(seed)),
        contentDescription = title,
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp),
        contentScale = ContentScale.Crop
    )
}

private fun fallbackProgramImage(seed: String): Int {
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
