package com.ermofit.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.local.AppDatabase
import com.ermofit.app.data.local.entity.CategoryEntity
import com.ermofit.app.data.local.entity.ExerciseEntity
import com.ermofit.app.data.local.entity.ExerciseTextEntity
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramExerciseEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity
import com.ermofit.app.data.local.model.MediaType
import com.ermofit.app.data.model.AppLanguage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.first

@Singleton
class ExternalWorkoutImportRepository @Inject constructor(
    private val database: AppDatabase,
    private val preferencesManager: UserPreferencesManager,
    @ApplicationContext private val context: Context
) {
    private val dao = database.fitnessDao()
    private val gson = Gson()

    suspend fun importFromSourcesIfNeeded() {
        val appLanguage = resolveCatalogLanguage(preferencesManager.observeLanguage().first())
        val langCode = if (appLanguage == AppLanguage.RU) "ru" else "en"
        val requiredVersion = requiredImportVersion(langCode)
        val savedVersion = preferencesManager.externalImportVersionFlow.first()
        val hasPrograms = dao.getProgramsCount() > 0

        if (savedVersion == requiredVersion && hasPrograms) return

        val bundle = runCatching {
            buildImportBundle(preferredLangCode = langCode)
        }.getOrElse { throwable ->
            if (hasPrograms) return
            throw throwable
        }

        if (bundle.exercises.isEmpty() || bundle.programs.isEmpty()) {
            if (hasPrograms) return
            error(
                "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u0444\u043e\u0440\u043c\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u043a\u0430\u0442\u0430\u043b\u043e\u0433 \u0442\u0440\u0435\u043d\u0438\u0440\u043e\u0432\u043e\u043a \u0438\u0437 \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0445 \u0444\u0430\u0439\u043b\u043e\u0432."
            )
        }

        database.withTransaction {
            dao.clearProgramExercises()
            dao.clearProgramTexts()
            dao.clearPrograms()
            dao.clearExerciseTexts()
            dao.clearExercises()
            dao.clearCategories()

            dao.insertCategories(bundle.categories)
            dao.insertExercises(bundle.exercises)
            dao.insertExerciseTexts(bundle.exerciseTexts)
            dao.insertPrograms(bundle.programs)
            dao.insertProgramTexts(bundle.programTexts)
            dao.insertProgramExercises(bundle.programExercises)
        }

        preferencesManager.setExternalImportVersion(requiredVersion)
    }

    private fun buildImportBundle(preferredLangCode: String): ImportBundle {
        val ruRows = readExercises(RU_ASSET)
        val enRows = readExercises(EN_ASSET)
        require(ruRows.isNotEmpty() || enRows.isNotEmpty()) {
            "bd_ru.json / bd_en.json are empty or unavailable."
        }

        val ruById = ruRows.associateBy { it.id.trim() }
        val enById = enRows.associateBy { it.id.trim() }
        val allIds = (ruById.keys + enById.keys)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val now = System.currentTimeMillis()
        val exercises = mutableListOf<ExerciseEntity>()
        val exerciseTexts = mutableListOf<ExerciseTextEntity>()

        val categoryTitleRu = linkedMapOf<String, String>()
        val categoryTitleEn = linkedMapOf<String, String>()
        val categoryImageById = linkedMapOf<String, String>()
        val exerciseLevelRank = linkedMapOf<String, Int>()


        allIds.forEach { sourceId ->
            val ru = ruById[sourceId]
            val en = enById[sourceId]
            val selected = if (preferredLangCode == "ru") ru ?: en else en ?: ru
            if (selected == null) return@forEach

            val normalizedId = "file_${sourceId.trim()}"
            val canonicalCategory = canonicalCategory(
                enCategory = en?.category.orEmpty(),
                ruCategory = ru?.category.orEmpty(),
                fallback = selected.category.orEmpty()
            )
            val categoryId = "cat_$canonicalCategory"
            val ruCategoryTitle = resolveCategoryTitleRu(canonicalCategory, ru?.category)
            val enCategoryTitle = resolveCategoryTitleEn(canonicalCategory, en?.category)
            categoryTitleRu[categoryId] = ruCategoryTitle
            categoryTitleEn[categoryId] = enCategoryTitle

            val titleRu = ru?.name?.trim().orEmpty().ifBlank {
                en?.name?.trim().orEmpty().ifBlank { sourceId }
            }
            val titleEn = en?.name?.trim().orEmpty().ifBlank {
                ru?.name?.trim().orEmpty().ifBlank { sourceId }
            }
            val descriptionRu = buildDescription(
                row = ru,
                fallback = "\u0423\u043f\u0440\u0430\u0436\u043d\u0435\u043d\u0438\u0435 \u0434\u043b\u044f \u0434\u043e\u043c\u0430\u0448\u043d\u0435\u0439 \u0442\u0440\u0435\u043d\u0438\u0440\u043e\u0432\u043a\u0438."
            )
            val descriptionEn = buildDescription(
                row = en,
                fallback = "Exercise for home training."
            )

            val selectedTitle = if (preferredLangCode == "ru") titleRu else titleEn
            val selectedDescription = if (preferredLangCode == "ru") descriptionRu else descriptionEn

            val muscleGroup = firstNotBlank(
                selected.primaryMuscles.orEmpty(),
                ru?.primaryMuscles.orEmpty(),
                en?.primaryMuscles.orEmpty()
            ).ifBlank {
                if (preferredLangCode == "ru") "\u0412\u0441\u0435 \u0442\u0435\u043b\u043e" else "Full body"
            }

            val equipment = selected.equipment?.trim().orEmpty().ifBlank {
                if (preferredLangCode == "ru") {
                    ru?.equipment?.trim().orEmpty().ifBlank { "\u0421\u043e\u0431\u0441\u0442\u0432\u0435\u043d\u043d\u044b\u0439 \u0432\u0435\u0441" }
                } else {
                    en?.equipment?.trim().orEmpty().ifBlank { "Bodyweight" }
                }
            }

            val imageUrl = resolveImageUrl(
                selected.images.orEmpty().firstOrNull()
                    ?: ru?.images.orEmpty().firstOrNull()
                    ?: en?.images.orEmpty().firstOrNull()
            )
            val tags = buildTags(
                selected = selected,
                ru = ru,
                en = en,
                canonicalCategory = canonicalCategory
            )

            exercises += ExerciseEntity(
                id = normalizedId,
                categoryId = categoryId,
                title = selectedTitle.take(120),
                description = selectedDescription.take(1800),
                muscleGroup = muscleGroup.take(80),
                equipment = equipment.take(80),
                tags = tags,
                mediaType = MediaType.IMAGE,
                mediaUrl = imageUrl.orEmpty(),
                fallbackImageUrl = imageUrl,
                languageCode = preferredLangCode,
                updatedAt = now
            )

            exerciseTexts += ExerciseTextEntity(
                exerciseId = normalizedId,
                langCode = "ru",
                name = titleRu.take(120),
                description = descriptionRu.take(1800),
                source = TEXT_SOURCE_FILE,
                updatedAt = now
            )
            exerciseTexts += ExerciseTextEntity(
                exerciseId = normalizedId,
                langCode = "en",
                name = titleEn.take(120),
                description = descriptionEn.take(1800),
                source = TEXT_SOURCE_FILE,
                updatedAt = now
            )

            exerciseLevelRank[normalizedId] = levelRank(
                selected.level.orEmpty(),
                ru?.level.orEmpty(),
                en?.level.orEmpty()
            )

            if (!imageUrl.isNullOrBlank() && categoryImageById[categoryId].isNullOrBlank()) {
                categoryImageById[categoryId] = imageUrl
            }
        }

        val categories = (categoryTitleRu.keys + categoryTitleEn.keys)
            .distinct()
            .sorted()
            .map { categoryId ->
                CategoryEntity(
                    id = categoryId,
                    title = if (preferredLangCode == "ru") {
                        categoryTitleRu[categoryId].orEmpty().ifBlank {
                            categoryTitleEn[categoryId].orEmpty().ifBlank {
                                "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f"
                            }
                        }
                    } else {
                        categoryTitleEn[categoryId].orEmpty().ifBlank {
                            categoryTitleRu[categoryId].orEmpty().ifBlank { "Category" }
                        }
                    },
                    imageUrl = categoryImageById[categoryId]
                )
            }

        val programs = mutableListOf<ProgramEntity>()
        val programTexts = mutableListOf<ProgramTextEntity>()
        val links = mutableListOf<ProgramExerciseEntity>()

        categories.forEach { category ->
            val pool = exercises.filter { it.categoryId == category.id }
            if (pool.size < MIN_EXERCISES_PER_PROGRAM) return@forEach

            val programCount = (pool.size / 10).coerceIn(MIN_PROGRAMS_PER_CATEGORY, MAX_PROGRAMS_PER_CATEGORY)
            for (index in 0 until programCount) {
                val rank = index % 3
                val levelEn = when (rank) {
                    0 -> "Beginner"
                    1 -> "Intermediate"
                    else -> "Advanced"
                }
                val candidatePool = pool.filter { exerciseLevelRank[it.id] ?: 0 <= rank }
                    .ifEmpty { pool }
                if (candidatePool.size < MIN_EXERCISES_PER_PROGRAM) continue

                val exerciseCount = (6 + (index % 3)).coerceAtMost(candidatePool.size)
                val selected = pickDeterministic(
                    source = candidatePool,
                    seed = "${category.id}_$index",
                    count = exerciseCount
                )
                if (selected.size < MIN_EXERCISES_PER_PROGRAM) continue

                val programId = "file_prog_${category.id}_${(index + 1).toString().padStart(2, '0')}"
                val backgroundImage = selected.firstOrNull { it.mediaUrl.isNotBlank() }?.mediaUrl
                    ?: selected.firstNotNullOfOrNull { it.fallbackImageUrl?.takeIf(String::isNotBlank) }
                    ?: category.imageUrl.orEmpty()

                val durationMinutes = estimateProgramDuration(
                    levelRank = rank,
                    exerciseCount = selected.size,
                    variant = index
                )

                val focusTags = selected
                    .flatMap { it.tags }
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "strength" && it != "cardio" && it != "stretching" }
                    .distinct()
                    .take(3)

                val ruTitle = buildProgramTitle(
                    categoryTitle = categoryTitleRu[category.id].orEmpty().ifBlank { "Тренировка" },
                    langCode = "ru",
                    level = levelEn,
                    variant = index,
                    durationMinutes = durationMinutes
                )
                val enTitle = buildProgramTitle(
                    categoryTitle = categoryTitleEn[category.id].orEmpty().ifBlank { "Workout" },
                    langCode = "en",
                    level = levelEn,
                    variant = index,
                    durationMinutes = durationMinutes
                )
                val ruDescription = buildProgramDescription(
                    langCode = "ru",
                    categoryTitle = categoryTitleRu[category.id].orEmpty().ifBlank { "Тренировка" },
                    level = levelEn,
                    exerciseCount = selected.size,
                    durationMinutes = durationMinutes,
                    focusTags = focusTags
                )
                val enDescription = buildProgramDescription(
                    langCode = "en",
                    categoryTitle = categoryTitleEn[category.id].orEmpty().ifBlank { "Workout" },
                    level = levelEn,
                    exerciseCount = selected.size,
                    durationMinutes = durationMinutes,
                    focusTags = focusTags
                )

                programs += ProgramEntity(
                    id = programId,
                    title = if (preferredLangCode == "ru") ruTitle else enTitle,
                    description = if (preferredLangCode == "ru") ruDescription else enDescription,
                    level = levelEn,
                    durationMinutes = durationMinutes,
                    categoryId = category.id,
                    backgroundImageUrl = backgroundImage
                )

                programTexts += ProgramTextEntity(
                    programId = programId,
                    langCode = "ru",
                    title = ruTitle,
                    description = ruDescription,
                    source = TEXT_SOURCE_FILE,
                    updatedAt = now
                )
                programTexts += ProgramTextEntity(
                    programId = programId,
                    langCode = "en",
                    title = enTitle,
                    description = enDescription,
                    source = TEXT_SOURCE_FILE,
                    updatedAt = now
                )

                selected.forEachIndexed { order, exercise ->
                    val isTimed = order % 2 == 0
                    links += ProgramExerciseEntity(
                        programId = programId,
                        exerciseId = exercise.id,
                        orderIndex = order + 1,
                        defaultDurationSec = if (isTimed) 35 + rank * 5 else 0,
                        defaultReps = if (isTimed) 0 else 10 + rank * 2
                    )
                }
            }
        }

        return ImportBundle(
            categories = categories,
            exercises = exercises,
            exerciseTexts = exerciseTexts,
            programs = programs,
            programTexts = programTexts,
            programExercises = links
        )
    }

    private fun readExercises(assetName: String): List<BdExercise> {
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BdExercise>>() {}.type
        val rows = gson.fromJson<List<BdExercise>>(raw, type).orEmpty()
        if (assetName != RU_ASSET) return rows
        return rows.map(::decodeRuRowIfNeeded)
    }

    private fun readSeedProgramBackgrounds(assetName: String): List<String> {
        val raw = runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val payload = gson.fromJson(raw, ErmoSeedPayload::class.java) ?: return emptyList()
        return payload.programs.orEmpty()
            .mapNotNull { it.backgroundImageUrl?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun buildDescription(row: BdExercise?, fallback: String): String {
        if (row == null) return fallback
        val fromInstructions = row.instructions.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (fromInstructions.isNotBlank()) return fromInstructions

        val parts = listOfNotNull(
            row.force?.takeIf { it.isNotBlank() }?.trim(),
            row.mechanic?.takeIf { it.isNotBlank() }?.trim(),
            row.category?.takeIf { it.isNotBlank() }?.trim()
        )
        return if (parts.isNotEmpty()) parts.joinToString(" - ") else fallback
    }

    private fun buildTags(
        selected: BdExercise,
        ru: BdExercise?,
        en: BdExercise?,
        canonicalCategory: String
    ): List<String> {
        val raw = mutableListOf<String>()
        raw += canonicalCategory
        listOf(selected.force, selected.mechanic, selected.category, selected.equipment).forEach { value ->
            if (!value.isNullOrBlank()) raw += value
        }
        raw += selected.primaryMuscles.orEmpty()
        raw += selected.secondaryMuscles.orEmpty()

        if (raw.isEmpty()) {
            raw += ru?.category.orEmpty()
            raw += en?.category.orEmpty()
        }

        return raw
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map(::normalizeTag)
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
            .ifEmpty { listOf(canonicalCategory) }
    }

    private fun buildProgramTitle(
        categoryTitle: String,
        langCode: String,
        level: String,
        variant: Int,
        durationMinutes: Int
    ): String {
        if (langCode == "ru") {
            val prefix = when (level.lowercase(Locale.ROOT)) {
                "beginner" -> "\u041b\u0435\u0433\u043a\u0438\u0439 \u0441\u0442\u0430\u0440\u0442"
                "intermediate" -> "\u0420\u0430\u0431\u043e\u0447\u0438\u0439 \u0442\u0435\u043c\u043f"
                else -> "\u0418\u043d\u0442\u0435\u043d\u0441\u0438\u0432"
            }
            val suffix = when (variant % 4) {
                0 -> "\u0431\u0430\u0437\u0430"
                1 -> "\u043f\u0440\u043e\u0433\u0440\u0435\u0441\u0441"
                2 -> "\u0432\u044b\u043d\u043e\u0441\u043b\u0438\u0432\u043e\u0441\u0442\u044c"
                else -> "\u043a\u043e\u043d\u0442\u0440\u043e\u043b\u044c"
            }
            return "$prefix: $categoryTitle - $suffix ($durationMinutes \u043c\u0438\u043d)"
        }

        val prefix = when (level.lowercase(Locale.ROOT)) {
            "beginner" -> "Easy Start"
            "intermediate" -> "Steady Pace"
            else -> "Power Session"
        }
        val suffix = when (variant % 4) {
            0 -> "base"
            1 -> "progress"
            2 -> "endurance"
            else -> "control"
        }
        return "$prefix: $categoryTitle • $suffix (${durationMinutes}m)"
    }

    private fun buildProgramDescription(
        langCode: String,
        categoryTitle: String,
        level: String,
        exerciseCount: Int,
        durationMinutes: Int,
        focusTags: List<String>
    ): String {
        val levelLabelRu = when (level.lowercase(Locale.ROOT)) {
            "beginner" -> "\u043d\u043e\u0432\u0438\u0447\u043e\u043a"
            "intermediate" -> "\u0441\u0440\u0435\u0434\u043d\u0438\u0439"
            else -> "\u043f\u0440\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044b\u0439"
        }
        val focus = focusTags
            .map { it.replace("-", " ") }
            .take(3)
            .joinToString(", ")
            .ifBlank {
                if (langCode == "ru") {
                    "\u043e\u0431\u0449\u0430\u044f \u0444\u0438\u0437\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043a\u0430"
                } else {
                    "general fitness"
                }
            }

        return if (langCode == "ru") {
            "\u041f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u0430 \"$categoryTitle\", \u0443\u0440\u043e\u0432\u0435\u043d\u044c $levelLabelRu: $exerciseCount \u0443\u043f\u0440\u0430\u0436\u043d\u0435\u043d\u0438\u0439, \u043f\u0440\u0438\u043c\u0435\u0440\u043d\u043e $durationMinutes \u043c\u0438\u043d\u0443\u0442. \u0410\u043a\u0446\u0435\u043d\u0442: $focus. \u041d\u0430\u0433\u0440\u0443\u0437\u043a\u0430 \u0440\u0430\u0437\u0431\u0438\u0442\u0430 \u043f\u043e \u043a\u0440\u0443\u0433\u0430\u043c \u0441 \u043a\u043e\u043d\u0442\u0440\u043e\u043b\u0435\u043c \u0442\u0435\u0445\u043d\u0438\u043a\u0438."
        } else {
            "Structured $categoryTitle plan for $level level: $exerciseCount exercises, around $durationMinutes minutes. Focus: $focus. The workload is split into rounds with controlled technique and pacing."
        }
    }

    private fun estimateProgramDuration(
        levelRank: Int,
        exerciseCount: Int,
        variant: Int
    ): Int {
        val perExercise = when (levelRank) {
            0 -> 3
            1 -> 4
            else -> 5
        }
        return (exerciseCount * perExercise + 5 + (variant % 3)).coerceIn(15, 60)
    }

    private fun pickDeterministic(
        source: List<ExerciseEntity>,
        seed: String,
        count: Int
    ): List<ExerciseEntity> {
        if (source.isEmpty() || count <= 0) return emptyList()
        val sorted = source.sortedBy { it.id }
        val start = positiveIndex(seed.hashCode(), sorted.size)
        val rotated = sorted.drop(start) + sorted.take(start)
        return rotated.take(count)
    }

    private fun levelRank(primary: String, ru: String, en: String): Int {
        val raw = listOf(primary, ru, en)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return when {
            raw.contains("expert") ||
                raw.contains("advanced") ||
                raw.contains("\u044d\u043a\u0441\u043f\u0435\u0440\u0442") ||
                raw.contains("\u043f\u0440\u043e\u0434\u0432\u0438\u043d") -> 2
            raw.contains("intermediate") ||
                raw.contains("\u043f\u0440\u043e\u043c\u0435\u0436") ||
                raw.contains("\u0441\u0440\u0435\u0434\u043d") -> 1
            else -> 0
        }
    }

    private fun canonicalCategory(
        enCategory: String,
        ruCategory: String,
        fallback: String
    ): String {
        val raw = listOf(enCategory, ruCategory, fallback)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        return when {
            raw.contains("stretch") || raw.contains("\u0440\u0430\u0441\u0442\u044f\u0436") -> "stretching"
            raw.contains("plyo") || raw.contains("\u043f\u043b\u0438\u043e\u043c") -> "plyometrics"
            raw.contains("powerlifting") || raw.contains("\u043f\u0430\u0443\u044d\u0440\u043b\u0438\u0444") -> "powerlifting"
            raw.contains("olympic") || raw.contains("\u043e\u043b\u0438\u043c\u043f") -> "olympic_weightlifting"
            raw.contains("strongman") || raw.contains("\u0441\u0442\u0440\u043e\u043d\u0433") -> "strongman"
            raw.contains("cardio") || raw.contains("\u043a\u0430\u0440\u0434\u0438\u043e") -> "cardio"
            else -> "strength"
        }
    }

    private fun resolveCategoryTitleRu(canonical: String, original: String?): String {
        val fallback = when (canonical) {
            "stretching" -> "\u0420\u0430\u0441\u0442\u044f\u0436\u043a\u0430"
            "plyometrics" -> "\u041f\u043b\u0438\u043e\u043c\u0435\u0442\u0440\u0438\u043a\u0430"
            "powerlifting" -> "\u041f\u0430\u0443\u044d\u0440\u043b\u0438\u0444\u0442\u0438\u043d\u0433"
            "olympic_weightlifting" -> "\u041e\u043b\u0438\u043c\u043f\u0438\u0439\u0441\u043a\u0430\u044f \u0442\u044f\u0436\u0435\u043b\u0430\u044f \u0430\u0442\u043b\u0435\u0442\u0438\u043a\u0430"
            "strongman" -> "\u0421\u0442\u0440\u043e\u043d\u0433\u043c\u0435\u043d"
            "cardio" -> "\u041a\u0430\u0440\u0434\u0438\u043e"
            else -> "\u0421\u0438\u043b\u0430"
        }
        return sanitizeCategoryTitle(original).ifBlank { fallback }
    }

    private fun decodeRuRowIfNeeded(row: BdExercise): BdExercise {
        return row.copy(
            name = decodeMojibakeIfNeeded(row.name),
            force = row.force?.let(::decodeMojibakeIfNeeded),
            level = row.level?.let(::decodeMojibakeIfNeeded),
            mechanic = row.mechanic?.let(::decodeMojibakeIfNeeded),
            equipment = row.equipment?.let(::decodeMojibakeIfNeeded),
            primaryMuscles = row.primaryMuscles?.map(::decodeMojibakeIfNeeded),
            secondaryMuscles = row.secondaryMuscles?.map(::decodeMojibakeIfNeeded),
            instructions = row.instructions?.map(::decodeMojibakeIfNeeded),
            category = row.category?.let(::decodeMojibakeIfNeeded)
        )
    }

    private fun decodeMojibakeIfNeeded(value: String): String {
        if (!looksLikeMojibake(value)) return value
        val cp1251 = Charset.forName("windows-1251")
        val decoded = runCatching {
            String(value.toByteArray(cp1251), Charsets.UTF_8)
        }.getOrDefault(value)
        return if (scoreRussianReadability(decoded) > scoreRussianReadability(value)) {
            decoded
        } else {
            value
        }
    }

    private fun looksLikeMojibake(value: String): Boolean {
        if (value.isBlank()) return false
        val cyrSupplement = value.count { it in '\u0400'..'\u045F' && it != '\u0401' && it != '\u0451' }
        return cyrSupplement >= 2 && (value.contains('Р') || value.contains('С'))
    }

    private fun scoreRussianReadability(value: String): Int {
        val basicCyr = value.count { it in '\u0410'..'\u044F' || it == '\u0401' || it == '\u0451' }
        val weirdCyr = value.count { it in '\u0400'..'\u045F' && it !in ('\u0410'..'\u044F') && it != '\u0401' && it != '\u0451' }
        return basicCyr * 4 - weirdCyr * 8
    }

    private fun resolveCategoryTitleEn(canonical: String, original: String?): String {
        val fallback = when (canonical) {
            "stretching" -> "Stretching"
            "plyometrics" -> "Plyometrics"
            "powerlifting" -> "Powerlifting"
            "olympic_weightlifting" -> "Olympic Weightlifting"
            "strongman" -> "Strongman"
            "cardio" -> "Cardio"
            else -> "Strength"
        }
        return sanitizeCategoryTitle(original).ifBlank { fallback }
    }

    private fun sanitizeCategoryTitle(original: String?): String {
        return original.orEmpty()
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .trim()
    }

    private fun resolveImageUrl(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return null
        val normalized = raw.removePrefix("/")

        val withImagesFolder = if (normalized.count { it == '/' } == 1) {
            val segments = normalized.split('/')
            "$FREE_EXERCISE_DB_BASE/${encodePathSegment(segments[0])}/images/${encodePathSegment(segments[1])}"
        } else {
            null
        }

        return withImagesFolder ?: "$FREE_EXERCISE_DB_BASE/${encodePath(normalized)}"
    }

    private fun encodePath(path: String): String {
        return path
            .split('/')
            .joinToString("/") { encodePathSegment(it) }
    }

    private fun encodePathSegment(segment: String): String {
        return Uri.encode(segment)
    }

    private fun normalizeTag(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-')
            .take(40)
    }

    private fun positiveIndex(value: Int, bound: Int): Int {
        val positive = value.absoluteValue
        return if (bound == 0) 0 else positive % bound
    }

    private fun firstNotBlank(vararg sources: List<String>): String {
        sources.forEach { source ->
            source.firstOrNull { it.isNotBlank() }?.let { return it.trim() }
        }
        return ""
    }

    private fun resolveCatalogLanguage(appLanguage: AppLanguage): AppLanguage {
        return when (appLanguage) {
            AppLanguage.RU -> AppLanguage.RU
            AppLanguage.EN -> AppLanguage.EN
            AppLanguage.SYSTEM -> {
                val locale = Locale.getDefault().language.lowercase(Locale.ROOT)
                if (locale.startsWith("ru")) AppLanguage.RU else AppLanguage.EN
            }
        }
    }

    private fun requiredImportVersion(langCode: String): Int {
        val suffix = if (langCode == "ru") 1 else 2
        return BASE_IMPORT_VERSION * 10 + suffix
    }

    private data class BdExercise(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("force") val force: String? = null,
        @SerializedName("level") val level: String? = null,
        @SerializedName("mechanic") val mechanic: String? = null,
        @SerializedName("equipment") val equipment: String? = null,
        @SerializedName("primaryMuscles") val primaryMuscles: List<String>? = emptyList(),
        @SerializedName("secondaryMuscles") val secondaryMuscles: List<String>? = emptyList(),
        @SerializedName("instructions") val instructions: List<String>? = emptyList(),
        @SerializedName("category") val category: String? = null,
        @SerializedName("images") val images: List<String>? = emptyList()
    )

    private data class ErmoSeedPayload(
        @SerializedName("programs") val programs: List<ErmoSeedProgram>? = emptyList()
    )

    private data class ErmoSeedProgram(
        @SerializedName("backgroundImageUrl") val backgroundImageUrl: String? = null
    )

    private data class ImportBundle(
        val categories: List<CategoryEntity>,
        val exercises: List<ExerciseEntity>,
        val exerciseTexts: List<ExerciseTextEntity>,
        val programs: List<ProgramEntity>,
        val programTexts: List<ProgramTextEntity>,
        val programExercises: List<ProgramExerciseEntity>
    )

    private companion object {
        const val RU_ASSET = "bd_ru.json"
        const val EN_ASSET = "bd_en.json"
        const val ERMOFIT_SEED_ASSET = "ermofit_seed.json"
        const val BASE_IMPORT_VERSION = 35
        const val MIN_EXERCISES_PER_PROGRAM = 4
        const val MIN_PROGRAMS_PER_CATEGORY = 4
        const val MAX_PROGRAMS_PER_CATEGORY = 18
        const val FREE_EXERCISE_DB_BASE =
            "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises"
        const val TEXT_SOURCE_FILE = "file_bd"
    }
}
