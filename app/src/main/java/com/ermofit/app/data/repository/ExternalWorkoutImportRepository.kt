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

            if (!imageUrl.isNullOrBlank() && categoryImageById[categoryId].isNullOrBlank()) {
                categoryImageById[categoryId] = imageUrl
            }
        }

        val exerciseCategories = (categoryTitleRu.keys + categoryTitleEn.keys)
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
        val categories = (exerciseCategories + programCategories(preferredLangCode))
            .distinctBy { it.id }

        val programSeed = buildCuratedProgramSeed(
            preferredLangCode = preferredLangCode,
            now = now,
            categories = categories,
            exercises = exercises
        )

        return ImportBundle(
            categories = categories,
            exercises = exercises,
            exerciseTexts = exerciseTexts,
            programs = programSeed.programs,
            programTexts = programSeed.programTexts,
            programExercises = programSeed.programExercises
        )
    }

    private fun buildCuratedProgramSeed(
        preferredLangCode: String,
        now: Long,
        categories: List<CategoryEntity>,
        exercises: List<ExerciseEntity>
    ): ProgramSeed {
        val exerciseById = exercises.associateBy { it.id }
        val categoryIds = categories.map { it.id }.toSet()
        val fallbackCategoryId = categories.firstOrNull()?.id ?: CAT_HOME
        val categoryImageById = categories.associate { it.id to it.imageUrl.orEmpty() }

        val programs = mutableListOf<ProgramEntity>()
        val programTexts = mutableListOf<ProgramTextEntity>()
        val links = mutableListOf<ProgramExerciseEntity>()

        curatedProgramTemplates().forEach { template ->
            val selected = template.exercises.mapNotNull { ref ->
                exerciseById[fileExerciseId(ref.exerciseId)]?.let { exercise -> ref to exercise }
            }
            if (selected.size < MIN_CURATED_EXERCISES_PER_PROGRAM) return@forEach

            val safeCategoryId = template.categoryId.takeIf { it in categoryIds } ?: fallbackCategoryId
            val backgroundImage = selected.firstOrNull { (_, exercise) -> exercise.mediaUrl.isNotBlank() }
                ?.second
                ?.mediaUrl
                ?: selected.firstNotNullOfOrNull { (_, exercise) ->
                    exercise.fallbackImageUrl?.takeIf(String::isNotBlank)
                }
                ?: categoryImageById[safeCategoryId].orEmpty()

            programs += ProgramEntity(
                id = template.id,
                title = if (preferredLangCode == "ru") template.titleRu else template.titleEn,
                description = if (preferredLangCode == "ru") template.descriptionRu else template.descriptionEn,
                level = template.level,
                durationMinutes = template.durationMinutes,
                categoryId = safeCategoryId,
                backgroundImageUrl = backgroundImage
            )

            programTexts += ProgramTextEntity(
                programId = template.id,
                langCode = "ru",
                title = template.titleRu,
                description = template.descriptionRu,
                source = TEXT_SOURCE_FILE,
                updatedAt = now
            )
            programTexts += ProgramTextEntity(
                programId = template.id,
                langCode = "en",
                title = template.titleEn,
                description = template.descriptionEn,
                source = TEXT_SOURCE_FILE,
                updatedAt = now
            )

            selected.forEachIndexed { index, item ->
                val (ref, exercise) = item
                links += ProgramExerciseEntity(
                    programId = template.id,
                    exerciseId = exercise.id,
                    orderIndex = index + 1,
                    defaultDurationSec = ref.durationSec,
                    defaultReps = ref.reps
                )
            }
        }

        return ProgramSeed(
            programs = programs,
            programTexts = programTexts,
            programExercises = links
        )
    }

    private fun programCategories(languageCode: String): List<CategoryEntity> {
        val isRu = languageCode == "ru"
        return listOf(
            CategoryEntity(
                id = CAT_HOME,
                title = if (isRu) "Дом" else "Home",
                imageUrl = null
            ),
            CategoryEntity(
                id = CAT_GYM,
                title = if (isRu) "Зал" else "Gym",
                imageUrl = null
            ),
            CategoryEntity(
                id = CAT_OUTDOOR,
                title = if (isRu) "Улица" else "Outdoor",
                imageUrl = null
            )
        )
    }

    private fun curatedProgramTemplates(): List<CuratedProgramTemplate> {
        return listOf(
            CuratedProgramTemplate(
                id = "prog_30_day_home_start",
                categoryId = CAT_HOME,
                level = "Beginner",
                durationMinutes = 30,
                titleRu = "30 дней: челлендж дома",
                titleEn = "30-Day Home Challenge",
                descriptionRu = "Простая домашняя тренировка для первого месяца. Повторяйте 3 раза в неделю, каждую неделю добавляя один круг или 5-10 секунд в планке.",
                descriptionEn = "A simple home workout for the first month. Repeat it 3 times per week and add one round or 5-10 plank seconds each week.",
                exercises = listOf(
                    ref("Cat_Stretch", seconds = 35),
                    ref("Bodyweight_Squat", reps = 12),
                    ref("Incline_Push-Up", reps = 10),
                    ref("Bodyweight_Walking_Lunge", reps = 10),
                    ref("Butt_Lift_Bridge", reps = 12),
                    ref("Plank", seconds = 30),
                    ref("Childs_Pose", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_home_weight_loss",
                categoryId = CAT_HOME,
                level = "Beginner",
                durationMinutes = 30,
                titleRu = "Похудение дома",
                titleEn = "Weight Loss at Home",
                descriptionRu = "Понятная тренировка без оборудования: шаги, приседания, кор и умеренный пульс. Двигайтесь активно, но без гонки за максимумом.",
                descriptionEn = "A clear no-equipment workout: steps, squats, core, and moderate heart-rate work. Move actively without chasing maximum effort.",
                exercises = listOf(
                    ref("Trail_Running_Walking", seconds = 180),
                    ref("Step-up_with_Knee_Raise", reps = 12),
                    ref("Bodyweight_Squat", reps = 12),
                    ref("Mountain_Climbers", seconds = 25),
                    ref("Plank", seconds = 25),
                    ref("Childs_Pose", seconds = 35)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_belly_waist",
                categoryId = CAT_HOME,
                level = "Beginner",
                durationMinutes = 25,
                titleRu = "Живот и талия",
                titleEn = "Belly and Waist",
                descriptionRu = "Кор плюс короткое кардио: укрепляем пресс, улучшаем осанку и повышаем общий расход энергии. Работайте спокойно, без рывков шеей и поясницей.",
                descriptionEn = "Core plus short cardio: strengthen the abs, improve posture, and raise total energy use. Move calmly without yanking the neck or lower back.",
                exercises = listOf(
                    ref("Dead_Bug", reps = 12),
                    ref("Plank", seconds = 30),
                    ref("Side_Bridge", seconds = 25),
                    ref("Cross-Body_Crunch", reps = 12),
                    ref("Mountain_Climbers", seconds = 30),
                    ref("Childs_Pose", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_home_legs_glutes",
                categoryId = CAT_HOME,
                level = "Intermediate",
                durationMinutes = 35,
                titleRu = "Ноги и ягодицы дома",
                titleEn = "Home Legs and Glutes",
                descriptionRu = "Домашний акцент на ноги и ягодицы: приседания, выпады, мосты и шаги. Держите колени под контролем и не проваливайте поясницу.",
                descriptionEn = "A home legs-and-glutes focus: squats, lunges, bridges, and step-ups. Keep knees controlled and avoid collapsing through the lower back.",
                exercises = listOf(
                    ref("Bodyweight_Squat", reps = 15),
                    ref("Bodyweight_Walking_Lunge", reps = 12),
                    ref("Single_Leg_Glute_Bridge", reps = 10),
                    ref("Step-up_with_Knee_Raise", reps = 12),
                    ref("Plank", seconds = 35),
                    ref("90_90_Hamstring", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_no_jump_home_cardio",
                categoryId = CAT_HOME,
                level = "Beginner",
                durationMinutes = 25,
                titleRu = "Кардио дома без прыжков",
                titleEn = "No-Jump Home Cardio",
                descriptionRu = "Для квартиры и спокойного старта: без прыжков, с ровным темпом и простыми движениями. Подходит, когда не хочется шуметь.",
                descriptionEn = "Apartment-friendly and easy to start: no jumps, steady pace, and simple movements. Good when you need a quiet session.",
                exercises = listOf(
                    ref("Trail_Running_Walking", seconds = 180),
                    ref("Step-up_with_Knee_Raise", reps = 12),
                    ref("Bodyweight_Squat", reps = 12),
                    ref("Butt_Lift_Bridge", reps = 14),
                    ref("Plank", seconds = 30),
                    ref("Childs_Pose", seconds = 35)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_first_gym_day",
                categoryId = CAT_GYM,
                level = "Beginner",
                durationMinutes = 40,
                titleRu = "Первый день в зале",
                titleEn = "First Day at the Gym",
                descriptionRu = "Без сложных схем: дорожка, тренажеры, спина, грудь, ноги и кор. Выбирайте легкий вес, чтобы оставалось 2-3 повтора в запасе.",
                descriptionEn = "No complicated plan: treadmill, machines, back, chest, legs, and core. Pick light loads with 2-3 reps left in reserve.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 240),
                    ref("Leg_Press", reps = 12),
                    ref("Machine_Bench_Press", reps = 10),
                    ref("Wide-Grip_Lat_Pulldown", reps = 10),
                    ref("Seated_Leg_Curl", reps = 12),
                    ref("Plank", seconds = 35),
                    ref("Calf_Stretch_Hands_Against_Wall", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_dumbbells_full_body",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 45,
                titleRu = "Гантели: все тело",
                titleEn = "Dumbbells: Full Body",
                descriptionRu = "Тренировка в зале без очереди к тренажерам: ноги, грудь, спина, плечи и кор. Отдыхайте 60-90 секунд между подходами.",
                descriptionEn = "A gym workout without waiting for machines: legs, chest, back, shoulders, and core. Rest 60-90 seconds between sets.",
                exercises = listOf(
                    ref("90_90_Hamstring", seconds = 35),
                    ref("Dumbbell_Squat", reps = 10),
                    ref("Dumbbell_Bench_Press", reps = 10),
                    ref("Bent_Over_Two-Dumbbell_Row", reps = 10),
                    ref("Dumbbell_Lunges", reps = 10),
                    ref("Dumbbell_Shoulder_Press", reps = 8),
                    ref("Plank", seconds = 45)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_barbell_strength",
                categoryId = CAT_GYM,
                level = "Advanced",
                durationMinutes = 55,
                titleRu = "Сила со штангой",
                titleEn = "Barbell Strength",
                descriptionRu = "Для уверенных пользователей зала: присед, жим, тяга, вертикальный жим и подтягивания. Отдыхайте 2-3 минуты и не жертвуйте техникой.",
                descriptionEn = "For confident gym users: squat, bench, deadlift, overhead press, and pull-ups. Rest 2-3 minutes and do not trade technique for load.",
                exercises = listOf(
                    ref("Barbell_Squat", reps = 6),
                    ref("Barbell_Bench_Press_-_Medium_Grip", reps = 6),
                    ref("Bent_Over_Barbell_Row", reps = 8),
                    ref("Barbell_Deadlift", reps = 5),
                    ref("Barbell_Shoulder_Press", reps = 6),
                    ref("Pullups", reps = 8),
                    ref("Plank", seconds = 60)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_back_posture_gym",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 35,
                titleRu = "Спина и осанка",
                titleEn = "Back and Posture",
                descriptionRu = "Тренировка для спины, плеч и корпуса после сидячего дня. Держите движение плавным, без рывков и запрокидывания головы.",
                descriptionEn = "Back, shoulders, and core after a long sitting day. Keep the movement smooth, with no jerking or head throwing.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Wide-Grip_Lat_Pulldown", reps = 10),
                    ref("Face_Pull", reps = 12),
                    ref("Bent_Over_Two-Dumbbell_Row", reps = 10),
                    ref("Round_The_World_Shoulder_Stretch", seconds = 40),
                    ref("Plank", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_chest_triceps",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 40,
                titleRu = "Зал: грудь и трицепс",
                titleEn = "Gym: Chest and Triceps",
                descriptionRu = "Понятная тренировка верха тела: жим, работа в кроссовере и трицепс. Берите вес, с которым последние повторы тяжелые, но техника не разваливается.",
                descriptionEn = "A clear upper-body workout: pressing, cable work, and triceps. Choose a load where the last reps are hard but technique stays clean.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Machine_Bench_Press", reps = 10),
                    ref("Dumbbell_Bench_Press", reps = 10),
                    ref("Cable_Chest_Press", reps = 12),
                    ref("Cable_Rope_Overhead_Triceps_Extension", reps = 12),
                    ref("Plank", seconds = 35)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_back_biceps",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 40,
                titleRu = "Зал: спина и бицепс",
                titleEn = "Gym: Back and Biceps",
                descriptionRu = "Тяги для спины плюс простая работа на бицепс. Сначала тяните лопатками, потом руками, не раскачивайте корпус.",
                descriptionEn = "Back pulls plus simple biceps work. Start the pull with your shoulder blades, then your arms, and avoid swinging.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Wide-Grip_Lat_Pulldown", reps = 10),
                    ref("Bent_Over_Two-Dumbbell_Row", reps = 10),
                    ref("Face_Pull", reps = 12),
                    ref("Cable_Hammer_Curls_-_Rope_Attachment", reps = 12),
                    ref("Side_Bridge", seconds = 30)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_legs_glutes",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 45,
                titleRu = "Зал: ноги и ягодицы",
                titleEn = "Gym: Legs and Glutes",
                descriptionRu = "Тренировка ног без лишней сложности: жим ногами, выпады, задняя поверхность, ягодицы и икры. Двигайтесь в полном контролируемом диапазоне.",
                descriptionEn = "A simple lower-body gym workout: leg press, lunges, hamstrings, glutes, and calves. Move through a full controlled range.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Leg_Press", reps = 12),
                    ref("Dumbbell_Lunges", reps = 10),
                    ref("Seated_Leg_Curl", reps = 12),
                    ref("Barbell_Hip_Thrust", reps = 10),
                    ref("Standing_Calf_Raises", reps = 15),
                    ref("90_90_Hamstring", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_shoulders_arms",
                categoryId = CAT_GYM,
                level = "Intermediate",
                durationMinutes = 40,
                titleRu = "Зал: плечи и руки",
                titleEn = "Gym: Shoulders and Arms",
                descriptionRu = "Акцент на плечи, бицепс и трицепс. Не задирайте плечи к ушам и не раскачивайте корпус ради большего веса.",
                descriptionEn = "Shoulders, biceps, and triceps focus. Do not shrug your shoulders up or swing the body just to lift more weight.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Dumbbell_Shoulder_Press", reps = 10),
                    ref("Cable_Seated_Lateral_Raise", reps = 12),
                    ref("Face_Pull", reps = 12),
                    ref("High_Cable_Curls", reps = 12),
                    ref("Cable_Rope_Overhead_Triceps_Extension", reps = 12),
                    ref("Plank", seconds = 35)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_gym_abs_core",
                categoryId = CAT_GYM,
                level = "Beginner",
                durationMinutes = 30,
                titleRu = "Зал: пресс",
                titleEn = "Gym: Abs",
                descriptionRu = "Пресс и стабилизация корпуса на тренажерах и коврике. Работайте медленно: качество движения важнее количества повторов.",
                descriptionEn = "Abs and trunk stability with machines and mat work. Move slowly: quality beats rep count.",
                exercises = listOf(
                    ref("Walking_Treadmill", seconds = 180),
                    ref("Ab_Crunch_Machine", reps = 12),
                    ref("Cable_Crunch", reps = 12),
                    ref("Plank", seconds = 35),
                    ref("Side_Bridge", seconds = 25),
                    ref("Dead_Bug", reps = 12)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_outdoor_walk_tone",
                categoryId = CAT_OUTDOOR,
                level = "Beginner",
                durationMinutes = 30,
                titleRu = "Улица: ходьба и тонус",
                titleEn = "Outdoor: Walk and Tone",
                descriptionRu = "Прогулка плюс простые упражнения с собственным весом. Хороший вариант для начала, когда дома тренироваться скучно.",
                descriptionEn = "A walk plus simple bodyweight work. A good starter option when home workouts feel boring.",
                exercises = listOf(
                    ref("Trail_Running_Walking", seconds = 300),
                    ref("Step-up_with_Knee_Raise", reps = 12),
                    ref("Bodyweight_Walking_Lunge", reps = 10),
                    ref("Plank", seconds = 30),
                    ref("90_90_Hamstring", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_outdoor_run_start",
                categoryId = CAT_OUTDOOR,
                level = "Beginner",
                durationMinutes = 30,
                titleRu = "Беговой старт",
                titleEn = "Running Start",
                descriptionRu = "Легкий бег или быстрая ходьба с короткой силовой частью. Не бегите через боль: темп должен оставаться разговорным.",
                descriptionEn = "Easy jogging or brisk walking with a short strength block. Do not run through pain; keep the pace conversational.",
                exercises = listOf(
                    ref("Trail_Running_Walking", seconds = 360),
                    ref("Bodyweight_Squat", reps = 12),
                    ref("Step-up_with_Knee_Raise", reps = 10),
                    ref("Side_Bridge", seconds = 25),
                    ref("Calf_Stretch_Hands_Against_Wall", seconds = 40)
                )
            ),
            CuratedProgramTemplate(
                id = "prog_outdoor_interval_challenge",
                categoryId = CAT_OUTDOOR,
                level = "Intermediate",
                durationMinutes = 35,
                titleRu = "Интервальный челлендж",
                titleEn = "Interval Challenge",
                descriptionRu = "Динамичная уличная тренировка: короткие интервалы, прыжки, корпус и восстановление. Подходит, если колени спокойно переносят прыжковые движения.",
                descriptionEn = "A dynamic outdoor workout: short intervals, jumps, core, and recovery. Use it if your knees tolerate jumping well.",
                exercises = listOf(
                    ref("Fast_Skipping", seconds = 40),
                    ref("Mountain_Climbers", seconds = 40),
                    ref("Freehand_Jump_Squat", reps = 12),
                    ref("Pushups", reps = 10),
                    ref("Lateral_Bound", seconds = 30),
                    ref("Plank", seconds = 45),
                    ref("Childs_Pose", seconds = 40)
                )
            )
        )
    }
    private fun ref(exerciseId: String, seconds: Int = 0, reps: Int = 0): CuratedExerciseRef {
        return CuratedExerciseRef(
            exerciseId = exerciseId,
            durationSec = seconds,
            reps = reps
        )
    }

    private fun fileExerciseId(sourceId: String): String = "file_$sourceId"

    private fun readExercises(assetName: String): List<BdExercise> {
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<BdExercise>>() {}.type
        val rows = gson.fromJson<List<BdExercise>>(raw, type).orEmpty()
        if (assetName != RU_ASSET) return rows
        return rows.map(::decodeRuRowIfNeeded)
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

    private data class CuratedProgramTemplate(
        val id: String,
        val categoryId: String,
        val level: String,
        val durationMinutes: Int,
        val titleRu: String,
        val titleEn: String,
        val descriptionRu: String,
        val descriptionEn: String,
        val exercises: List<CuratedExerciseRef>
    )

    private data class CuratedExerciseRef(
        val exerciseId: String,
        val durationSec: Int,
        val reps: Int
    )

    private data class ProgramSeed(
        val programs: List<ProgramEntity>,
        val programTexts: List<ProgramTextEntity>,
        val programExercises: List<ProgramExerciseEntity>
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
        const val BASE_IMPORT_VERSION = 38
        const val MIN_CURATED_EXERCISES_PER_PROGRAM = 4
        const val CAT_HOME = "cat_program_home"
        const val CAT_GYM = "cat_program_gym"
        const val CAT_OUTDOOR = "cat_program_outdoor"
        const val FREE_EXERCISE_DB_BASE =
            "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises"
        const val TEXT_SOURCE_FILE = "file_bd"
    }
}
