package com.ermofit.app.newplan.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ermofit.app.data.local.AppDatabase
import com.ermofit.app.data.model.AppLanguage
import com.ermofit.app.newplan.data.datastore.NewPlanPreferencesStore
import com.ermofit.app.newplan.data.local.dao.NewPlanDao
import com.ermofit.app.newplan.data.local.entity.ExerciseEntity
import com.ermofit.app.newplan.data.local.entity.TrainingEntity
import com.ermofit.app.newplan.data.local.entity.TrainingExerciseEntity
import com.ermofit.app.newplan.domain.model.EquipmentTags
import com.ermofit.app.newplan.domain.model.TrainingGoals
import com.ermofit.app.newplan.domain.model.TrainingLevels
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedImportRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: NewPlanDao,
    private val preferencesStore: NewPlanPreferencesStore,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend fun ensureSeedLoaded() {
        val languageCode = resolveContentLanguageCode()
        val hasExercises = dao.getExercisesCount() > 0
        val hasTrainings = dao.getTrainingsCount() > 0
        val hasLinks = dao.getTrainingExercisesCount() > 0
        val seedVersion = preferencesStore.getSeedVersion()
        val seededLanguage = preferencesStore.getSeededLanguageCode()

        val needImport = !hasExercises || !hasTrainings || !hasLinks ||
            seedVersion != SEED_VERSION || seededLanguage != languageCode
        if (!needImport) return

        val sourceExercises = readExercises(languageCode)
        require(sourceExercises.isNotEmpty()) { "Файл $languageCode не содержит упражнений." }

        val exercises = sourceExercises
            .mapNotNull { it.toEntityOrNull() }
            .distinctBy { it.id }
        require(exercises.isNotEmpty()) { "Не удалось подготовить упражнения для импорта." }

        val trainingSeed = generateTrainings(
            exercises = exercises,
            languageCode = languageCode
        )
        require(trainingSeed.links.isNotEmpty()) { "Не удалось собрать связи training-exercise." }

        database.withTransaction {
            dao.clearTrainingExercises()
            dao.clearTrainings()
            dao.clearExercises()

            dao.insertExercises(exercises)
            dao.insertTrainings(trainingSeed.trainings)
            dao.insertTrainingExercises(trainingSeed.links)
        }

        preferencesStore.setSeedVersion(SEED_VERSION)
        preferencesStore.setSeededLanguageCode(languageCode)
    }

    private suspend fun resolveContentLanguageCode(): String {
        return when (preferencesStore.getContentLanguage()) {
            AppLanguage.EN -> "en"
            AppLanguage.RU -> "ru"
            AppLanguage.SYSTEM -> "ru"
        }
    }

    private fun readExercises(languageCode: String): List<SourceExercise> {
        val assetName = if (languageCode == "ru") RU_ASSET else EN_ASSET
        val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<SourceExercise>>() {}.type
        return gson.fromJson<List<SourceExercise>>(json, type).orEmpty()
    }

    private fun generateTrainings(
        exercises: List<ExerciseEntity>,
        languageCode: String
    ): GeneratedTrainings {
        val exerciseById = exercises.associateBy { it.id }
        val trainings = mutableListOf<TrainingEntity>()
        val links = mutableListOf<TrainingExerciseEntity>()

        curatedTrainingTemplates().forEach { template ->
            val selected = template.exercises.mapNotNull { ref ->
                exerciseById[ref.exerciseId]?.let { exercise -> ref to exercise }
            }
            if (selected.size < MIN_EXERCISES_PER_TRAINING) return@forEach

            val equipment = selected
                .flatMap { (_, exercise) -> exercise.equipmentTags }
                .filterNot { it == EquipmentTags.OTHER }
                .distinct()
                .ifEmpty { listOf(EquipmentTags.NO_EQUIPMENT) }

            trainings += TrainingEntity(
                id = template.id,
                title = if (languageCode == "ru") template.titleRu else template.titleEn,
                description = if (languageCode == "ru") template.descriptionRu else template.descriptionEn,
                goal = template.goal,
                level = template.level,
                durationMinutes = template.durationMinutes,
                equipmentRequired = equipment,
                isGenerated = false
            )

            selected.forEachIndexed { index, item ->
                val (ref, exercise) = item
                links += TrainingExerciseEntity(
                    trainingId = template.id,
                    exerciseId = exercise.id,
                    orderIndex = index + 1,
                    customDurationSec = ref.durationSec,
                    customReps = ref.reps
                )
            }
        }

        return GeneratedTrainings(
            trainings = trainings,
            links = links
        )
    }

    private fun curatedTrainingTemplates(): List<TrainingTemplate> {
        return listOf(
            TrainingTemplate(
                id = "seed_${TrainingGoals.STRENGTH}_${TrainingLevels.BEGINNER}",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.BEGINNER,
                durationMinutes = 30,
                titleRu = "30 дней: челлендж дома",
                titleEn = "30-Day Home Challenge",
                descriptionRu = "Простая домашняя тренировка для первого месяца. Повторяйте 3 раза в неделю, каждую неделю добавляя один круг или 5-10 секунд в планке.",
                descriptionEn = "A simple home workout for the first month. Repeat it 3 times per week and add one round or 5-10 plank seconds each week.",
                exercises = listOf(
                    trainingRef("Cat_Stretch", seconds = 35),
                    trainingRef("Bodyweight_Squat", reps = 12),
                    trainingRef("Incline_Push-Up", reps = 10),
                    trainingRef("Bodyweight_Walking_Lunge", reps = 10),
                    trainingRef("Butt_Lift_Bridge", reps = 12),
                    trainingRef("Plank", seconds = 30),
                    trainingRef("Childs_Pose", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.STRENGTH}_${TrainingLevels.INTERMEDIATE}",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 45,
                titleRu = "Гантели: все тело",
                titleEn = "Dumbbells: Full Body",
                descriptionRu = "Понятная тренировка с гантелями: ноги, грудь, спина, плечи и кор. Отдыхайте 60-90 секунд и оставляйте 1-2 повтора в запасе.",
                descriptionEn = "A clear dumbbell workout: legs, chest, back, shoulders, and core. Rest 60-90 seconds and keep 1-2 reps in reserve.",
                exercises = listOf(
                    trainingRef("90_90_Hamstring", seconds = 35),
                    trainingRef("Dumbbell_Squat", reps = 10),
                    trainingRef("Dumbbell_Bench_Press", reps = 10),
                    trainingRef("Bent_Over_Two-Dumbbell_Row", reps = 10),
                    trainingRef("Dumbbell_Lunges", reps = 10),
                    trainingRef("Dumbbell_Shoulder_Press", reps = 8),
                    trainingRef("Plank", seconds = 45)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.STRENGTH}_${TrainingLevels.ADVANCED}",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.ADVANCED,
                durationMinutes = 55,
                titleRu = "Сила со штангой",
                titleEn = "Barbell Strength",
                descriptionRu = "Для уверенных пользователей зала: присед, жим, тяга, вертикальный жим и подтягивания. Отдыхайте 2-3 минуты и не жертвуйте техникой.",
                descriptionEn = "For confident gym users: squat, bench, deadlift, overhead press, and pull-ups. Rest 2-3 minutes and do not trade technique for load.",
                exercises = listOf(
                    trainingRef("Barbell_Squat", reps = 6),
                    trainingRef("Barbell_Bench_Press_-_Medium_Grip", reps = 6),
                    trainingRef("Bent_Over_Barbell_Row", reps = 8),
                    trainingRef("Barbell_Deadlift", reps = 5),
                    trainingRef("Barbell_Shoulder_Press", reps = 6),
                    trainingRef("Pullups", reps = 8),
                    trainingRef("Plank", seconds = 60)
                )
            ),
            TrainingTemplate(
                id = "seed_gym_chest_triceps",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 40,
                titleRu = "Зал: грудь и трицепс",
                titleEn = "Gym: Chest and Triceps",
                descriptionRu = "Понятная тренировка верха тела: жим, работа в кроссовере и трицепс. Берите вес, с которым последние повторы тяжелые, но техника не разваливается.",
                descriptionEn = "A clear upper-body workout: pressing, cable work, and triceps. Choose a load where the last reps are hard but technique stays clean.",
                exercises = listOf(
                    trainingRef("Walking_Treadmill", seconds = 180),
                    trainingRef("Machine_Bench_Press", reps = 10),
                    trainingRef("Dumbbell_Bench_Press", reps = 10),
                    trainingRef("Cable_Chest_Press", reps = 12),
                    trainingRef("Cable_Rope_Overhead_Triceps_Extension", reps = 12),
                    trainingRef("Plank", seconds = 35)
                )
            ),
            TrainingTemplate(
                id = "seed_gym_back_biceps",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 40,
                titleRu = "Зал: спина и бицепс",
                titleEn = "Gym: Back and Biceps",
                descriptionRu = "Тяги для спины плюс простая работа на бицепс. Сначала тяните лопатками, потом руками, не раскачивайте корпус.",
                descriptionEn = "Back pulls plus simple biceps work. Start the pull with your shoulder blades, then your arms, and avoid swinging.",
                exercises = listOf(
                    trainingRef("Walking_Treadmill", seconds = 180),
                    trainingRef("Wide-Grip_Lat_Pulldown", reps = 10),
                    trainingRef("Bent_Over_Two-Dumbbell_Row", reps = 10),
                    trainingRef("Face_Pull", reps = 12),
                    trainingRef("Cable_Hammer_Curls_-_Rope_Attachment", reps = 12),
                    trainingRef("Side_Bridge", seconds = 30)
                )
            ),
            TrainingTemplate(
                id = "seed_gym_legs_glutes",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 45,
                titleRu = "Зал: ноги и ягодицы",
                titleEn = "Gym: Legs and Glutes",
                descriptionRu = "Тренировка ног без лишней сложности: жим ногами, выпады, задняя поверхность, ягодицы и икры. Двигайтесь в полном контролируемом диапазоне.",
                descriptionEn = "A simple lower-body gym workout: leg press, lunges, hamstrings, glutes, and calves. Move through a full controlled range.",
                exercises = listOf(
                    trainingRef("Walking_Treadmill", seconds = 180),
                    trainingRef("Leg_Press", reps = 12),
                    trainingRef("Dumbbell_Lunges", reps = 10),
                    trainingRef("Seated_Leg_Curl", reps = 12),
                    trainingRef("Barbell_Hip_Thrust", reps = 10),
                    trainingRef("Standing_Calf_Raises", reps = 15),
                    trainingRef("90_90_Hamstring", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_gym_shoulders_arms",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 40,
                titleRu = "Зал: плечи и руки",
                titleEn = "Gym: Shoulders and Arms",
                descriptionRu = "Акцент на плечи, бицепс и трицепс. Не задирайте плечи к ушам и не раскачивайте корпус ради большего веса.",
                descriptionEn = "Shoulders, biceps, and triceps focus. Do not shrug your shoulders up or swing the body just to lift more weight.",
                exercises = listOf(
                    trainingRef("Walking_Treadmill", seconds = 180),
                    trainingRef("Dumbbell_Shoulder_Press", reps = 10),
                    trainingRef("Cable_Seated_Lateral_Raise", reps = 12),
                    trainingRef("Face_Pull", reps = 12),
                    trainingRef("High_Cable_Curls", reps = 12),
                    trainingRef("Cable_Rope_Overhead_Triceps_Extension", reps = 12),
                    trainingRef("Plank", seconds = 35)
                )
            ),
            TrainingTemplate(
                id = "seed_gym_abs_core",
                goal = TrainingGoals.STRENGTH,
                level = TrainingLevels.BEGINNER,
                durationMinutes = 30,
                titleRu = "Зал: пресс",
                titleEn = "Gym: Abs",
                descriptionRu = "Пресс и стабилизация корпуса на тренажерах и коврике. Работайте медленно: качество движения важнее количества повторов.",
                descriptionEn = "Abs and trunk stability with machines and mat work. Move slowly: quality beats rep count.",
                exercises = listOf(
                    trainingRef("Walking_Treadmill", seconds = 180),
                    trainingRef("Ab_Crunch_Machine", reps = 12),
                    trainingRef("Cable_Crunch", reps = 12),
                    trainingRef("Plank", seconds = 35),
                    trainingRef("Side_Bridge", seconds = 25),
                    trainingRef("Dead_Bug", reps = 12)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.FATBURN}_${TrainingLevels.BEGINNER}",
                goal = TrainingGoals.FATBURN,
                level = TrainingLevels.BEGINNER,
                durationMinutes = 30,
                titleRu = "Похудение дома",
                titleEn = "Weight Loss at Home",
                descriptionRu = "Без оборудования: шаги, приседания, кор и умеренный пульс. Двигайтесь активно, но без гонки за максимумом.",
                descriptionEn = "No equipment: steps, squats, core, and moderate heart-rate work. Move actively without chasing maximum effort.",
                exercises = listOf(
                    trainingRef("Trail_Running_Walking", seconds = 180),
                    trainingRef("Step-up_with_Knee_Raise", reps = 12),
                    trainingRef("Bodyweight_Squat", reps = 12),
                    trainingRef("Mountain_Climbers", seconds = 25),
                    trainingRef("Plank", seconds = 25),
                    trainingRef("Childs_Pose", seconds = 35)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.FATBURN}_${TrainingLevels.INTERMEDIATE}",
                goal = TrainingGoals.FATBURN,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 25,
                titleRu = "Живот и талия",
                titleEn = "Belly and Waist",
                descriptionRu = "Кор плюс короткое кардио: укрепляем пресс, улучшаем осанку и повышаем общий расход энергии. Работайте без рывков шеей и поясницей.",
                descriptionEn = "Core plus short cardio: strengthen the abs, improve posture, and raise total energy use. Move without yanking the neck or lower back.",
                exercises = listOf(
                    trainingRef("Dead_Bug", reps = 12),
                    trainingRef("Plank", seconds = 30),
                    trainingRef("Side_Bridge", seconds = 25),
                    trainingRef("Cross-Body_Crunch", reps = 12),
                    trainingRef("Mountain_Climbers", seconds = 30),
                    trainingRef("Childs_Pose", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.FATBURN}_${TrainingLevels.ADVANCED}",
                goal = TrainingGoals.FATBURN,
                level = TrainingLevels.ADVANCED,
                durationMinutes = 35,
                titleRu = "Интервальный челлендж",
                titleEn = "Interval Challenge",
                descriptionRu = "Динамичная тренировка: короткие интервалы, прыжки, корпус и восстановление. Подходит, если колени спокойно переносят прыжковые движения.",
                descriptionEn = "A dynamic workout: short intervals, jumps, core, and recovery. Use it if your knees tolerate jumping well.",
                exercises = listOf(
                    trainingRef("Fast_Skipping", seconds = 40),
                    trainingRef("Mountain_Climbers", seconds = 40),
                    trainingRef("Freehand_Jump_Squat", reps = 12),
                    trainingRef("Pushups", reps = 10),
                    trainingRef("Lateral_Bound", seconds = 30),
                    trainingRef("Plank", seconds = 45),
                    trainingRef("Childs_Pose", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.ENDURANCE}_${TrainingLevels.BEGINNER}",
                goal = TrainingGoals.ENDURANCE,
                level = TrainingLevels.BEGINNER,
                durationMinutes = 30,
                titleRu = "Улица: ходьба и тонус",
                titleEn = "Outdoor: Walk and Tone",
                descriptionRu = "Прогулка плюс простые упражнения с собственным весом. Хороший вариант для начала, когда дома тренироваться скучно.",
                descriptionEn = "A walk plus simple bodyweight work. A good starter option when home workouts feel boring.",
                exercises = listOf(
                    trainingRef("Trail_Running_Walking", seconds = 300),
                    trainingRef("Step-up_with_Knee_Raise", reps = 12),
                    trainingRef("Bodyweight_Walking_Lunge", reps = 10),
                    trainingRef("Plank", seconds = 30),
                    trainingRef("90_90_Hamstring", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.ENDURANCE}_${TrainingLevels.INTERMEDIATE}",
                goal = TrainingGoals.ENDURANCE,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 30,
                titleRu = "Беговой старт",
                titleEn = "Running Start",
                descriptionRu = "Легкий бег или быстрая ходьба с короткой силовой частью. Не бегите через боль: темп должен оставаться разговорным.",
                descriptionEn = "Easy jogging or brisk walking with a short strength block. Do not run through pain; keep the pace conversational.",
                exercises = listOf(
                    trainingRef("Trail_Running_Walking", seconds = 360),
                    trainingRef("Bodyweight_Squat", reps = 12),
                    trainingRef("Step-up_with_Knee_Raise", reps = 10),
                    trainingRef("Side_Bridge", seconds = 25),
                    trainingRef("Calf_Stretch_Hands_Against_Wall", seconds = 40)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.ENDURANCE}_${TrainingLevels.ADVANCED}",
                goal = TrainingGoals.ENDURANCE,
                level = TrainingLevels.ADVANCED,
                durationMinutes = 50,
                titleRu = "Кардио-выносливость",
                titleEn = "Cardio Endurance",
                descriptionRu = "Длиннее и ровнее: кардио-блоки плюс простая поддержка ног и корпуса. Главная цель - устойчивый пульс без провалов техники.",
                descriptionEn = "Longer and steadier: cardio blocks plus simple leg and core support. The goal is sustained effort without technique breakdown.",
                exercises = listOf(
                    trainingRef("Running_Treadmill", seconds = 360),
                    trainingRef("Rowing_Stationary", seconds = 300),
                    trainingRef("Rope_Jumping", seconds = 90),
                    trainingRef("Dumbbell_Lunges", reps = 12),
                    trainingRef("Mountain_Climbers", seconds = 50),
                    trainingRef("Plank", seconds = 60),
                    trainingRef("Calf_Stretch_Hands_Against_Wall", seconds = 45)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.MOBILITY}_${TrainingLevels.BEGINNER}",
                goal = TrainingGoals.MOBILITY,
                level = TrainingLevels.BEGINNER,
                durationMinutes = 15,
                titleRu = "Разминка утром",
                titleEn = "Morning Warm-Up",
                descriptionRu = "Мягкая короткая разминка для спины, бедер, плеч и голеностопа. Двигайтесь без боли, без пружинящих рывков и с ровным дыханием.",
                descriptionEn = "A gentle short warm-up for back, hips, shoulders, and ankles. Move without pain, bouncing, or breath-holding.",
                exercises = listOf(
                    trainingRef("Cat_Stretch", seconds = 45),
                    trainingRef("Childs_Pose", seconds = 45),
                    trainingRef("90_90_Hamstring", seconds = 45),
                    trainingRef("Kneeling_Hip_Flexor", seconds = 45),
                    trainingRef("Arm_Circles", seconds = 35),
                    trainingRef("Ankle_Circles", seconds = 35)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.MOBILITY}_${TrainingLevels.INTERMEDIATE}",
                goal = TrainingGoals.MOBILITY,
                level = TrainingLevels.INTERMEDIATE,
                durationMinutes = 25,
                titleRu = "Спина после сидячего дня",
                titleEn = "Back After Sitting",
                descriptionRu = "Мобилити-сессия для спины, сгибателей бедра, задней поверхности и плеч. Хорошо подходит после учебы, офиса или долгой дороги.",
                descriptionEn = "A mobility session for back, hip flexors, hamstrings, and shoulders. Good after studying, office work, or a long commute.",
                exercises = listOf(
                    trainingRef("Worlds_Greatest_Stretch", seconds = 55),
                    trainingRef("Hip_Circles_prone", seconds = 45),
                    trainingRef("Intermediate_Hip_Flexor_and_Quad_Stretch", seconds = 50),
                    trainingRef("Spinal_Stretch", seconds = 50),
                    trainingRef("Round_The_World_Shoulder_Stretch", seconds = 45),
                    trainingRef("Childs_Pose", seconds = 55)
                )
            ),
            TrainingTemplate(
                id = "seed_${TrainingGoals.MOBILITY}_${TrainingLevels.ADVANCED}",
                goal = TrainingGoals.MOBILITY,
                level = TrainingLevels.ADVANCED,
                durationMinutes = 30,
                titleRu = "Восстановление после тренировки",
                titleEn = "Post-Workout Recovery",
                descriptionRu = "Восстановительный поток для дней между тяжелыми тренировками. Работайте по ощущениям, не превращайте растяжку в силовое усилие.",
                descriptionEn = "A recovery flow for days between hard sessions. Work by feel and do not turn stretching into a strength effort.",
                exercises = listOf(
                    trainingRef("Worlds_Greatest_Stretch", seconds = 60),
                    trainingRef("Cat_Stretch", seconds = 45),
                    trainingRef("Kneeling_Hip_Flexor", seconds = 50),
                    trainingRef("90_90_Hamstring", seconds = 50),
                    trainingRef("Adductor_Groin", seconds = 50),
                    trainingRef("Spinal_Stretch", seconds = 50),
                    trainingRef("Childs_Pose", seconds = 60)
                )
            )
        )
    }
    private fun trainingRef(exerciseId: String, seconds: Int? = null, reps: Int? = null): TrainingExerciseRef {
        return TrainingExerciseRef(
            exerciseId = exerciseId,
            durationSec = seconds,
            reps = reps
        )
    }

    private fun SourceExercise.toEntityOrNull(): ExerciseEntity? {
        val safeId = id.trim()
        if (safeId.isBlank()) return null

        val safeName = name.trim().ifBlank { safeId }
        val normalizedLevel = normalizeLevel(level)
        val normalizedEquipment = normalizeEquipment(equipment)
        val normalizedCategory = category.lowercase().trim()
        val primary = normalizePrimaryMuscle(primaryMuscles, normalizedCategory)
        val secondary = normalizeSecondaryMuscle(secondaryMuscles, primary)
        val type = normalizeType(normalizedCategory, force)
        val contraindications = normalizeContraindications(normalizedCategory)

        val defaultDurationSec = if (type == "time") {
            when (normalizedCategory) {
                "cardio", "кардио", "plyometrics", "плиометрика" -> 45
                "stretching", "растяжка" -> 40
                else -> 35
            }
        } else {
            0
        }

        val defaultReps = if (type == "reps") {
            when (normalizedLevel) {
                TrainingLevels.BEGINNER -> 10
                TrainingLevels.INTERMEDIATE -> 12
                TrainingLevels.ADVANCED -> 15
                else -> 10
            }
        } else {
            0
        }

        val safeDescription = instructions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { descriptionFromCategory(normalizedCategory) }

        return ExerciseEntity(
            id = safeId,
            name = safeName,
            description = safeDescription,
            type = type,
            defaultDurationSec = defaultDurationSec,
            defaultReps = defaultReps,
            musclePrimary = primary,
            muscleSecondary = secondary,
            equipmentTags = normalizedEquipment,
            contraindications = contraindications,
            level = normalizedLevel,
            mediaResName = null
        )
    }

    private fun normalizeLevel(raw: String): String {
        return when (raw.lowercase().trim()) {
            "beginner", "новичок" -> TrainingLevels.BEGINNER
            "intermediate", "средний", "промежуточный" -> TrainingLevels.INTERMEDIATE
            "expert", "advanced", "эксперт", "продвинутый" -> TrainingLevels.ADVANCED
            else -> TrainingLevels.BEGINNER
        }
    }

    private fun normalizeEquipment(raw: String?): List<String> {
        val tag = when (raw.orEmpty().lowercase().trim()) {
            "" -> EquipmentTags.NO_EQUIPMENT
            "body only", "собственный вес", "только тело" -> EquipmentTags.NO_EQUIPMENT
            "dumbbell", "гантель", "гантели" -> EquipmentTags.DUMBBELLS
            "kettlebells", "kettlebell", "гири", "гиря" -> EquipmentTags.KETTLEBELL
            "barbell", "штанга" -> EquipmentTags.BARBELL
            "cable", "кабель", "кабельный тренажер" -> EquipmentTags.CABLE
            "machine", "тренажер", "машина" -> EquipmentTags.MACHINE
            "bands", "эспандерные ленты", "резинки", "полосы" -> EquipmentTags.BANDS
            "medicine ball", "медбол", "медицинский мяч" -> EquipmentTags.MEDICINE_BALL
            "exercise ball", "фитбол", "мяч для упражнений" -> EquipmentTags.EXERCISE_BALL
            "foam roll", "роллер", "роллер (foam roller)", "пенопластовый рулон" -> EquipmentTags.FOAM_ROLLER
            "e-z curl bar", "ez-гриф", "стержень для завивки e-z" -> EquipmentTags.EZ_BAR
            else -> EquipmentTags.OTHER
        }
        return listOf(tag)
    }

    private fun normalizePrimaryMuscle(raw: List<String>, category: String): String {
        val first = raw.firstOrNull()?.lowercase()?.trim().orEmpty()

        if (category in setOf("stretching", "растяжка")) return "stretch"
        if (category in setOf("cardio", "кардио", "plyometrics", "плиометрика")) return "cardio"

        return when (first) {
            "abdominals", "пресс", "брюшной пресс", "core" -> "core"
            "quadriceps", "квадрицепсы",
            "hamstrings", "бицепс бедра", "подколенные сухожилия",
            "calves", "икроножные мышцы",
            "glutes", "ягодицы",
            "adductors", "приводящие мышцы бедра",
            "abductors", "отводящие мышцы бедра" -> "legs"
            "chest", "грудные мышцы",
            "shoulders", "плечи",
            "triceps", "трицепсы",
            "biceps", "бицепсы",
            "lats", "широчайшие мышцы спины",
            "middle back", "средняя часть спины",
            "lower back", "поясница",
            "forearms", "предплечья",
            "traps", "трапеции",
            "neck", "шея" -> "upper"
            else -> if (category in setOf("strength", "сила")) "upper" else "core"
        }
    }

    private fun normalizeSecondaryMuscle(raw: List<String>, primary: String): String? {
        val first = raw.firstOrNull()?.lowercase()?.trim().orEmpty()
        val mapped = when (first) {
            "abdominals", "пресс", "брюшной пресс" -> "core"
            "quadriceps", "квадрицепсы",
            "hamstrings", "бицепс бедра", "подколенные сухожилия",
            "calves", "икроножные мышцы",
            "glutes", "ягодицы",
            "adductors", "приводящие мышцы бедра",
            "abductors", "отводящие мышцы бедра" -> "legs"
            "chest", "грудные мышцы",
            "shoulders", "плечи",
            "triceps", "трицепсы",
            "biceps", "бицепсы",
            "lats", "широчайшие мышцы спины",
            "middle back", "средняя часть спины",
            "lower back", "поясница",
            "forearms", "предплечья",
            "traps", "трапеции",
            "neck", "шея" -> "upper"
            else -> null
        }
        return if (mapped == primary) null else mapped
    }

    private fun normalizeType(category: String, force: String?): String {
        val normalizedForce = force.orEmpty().lowercase().trim()
        return when {
            category in setOf("stretching", "растяжка", "cardio", "кардио", "plyometrics", "плиометрика") -> "time"
            normalizedForce in setOf("static", "изометрия") -> "time"
            else -> "reps"
        }
    }

    private fun normalizeContraindications(category: String): List<String> {
        return when (category) {
            "plyometrics", "плиометрика" -> listOf("no_jumps", "knees", "quiet")
            "cardio", "кардио" -> listOf("quiet")
            else -> emptyList()
        }
    }

    private fun descriptionFromCategory(category: String): String {
        return when (category) {
            "stretching", "растяжка" -> "Упражнение на мобильность и контроль амплитуды движения."
            "cardio", "кардио" -> "Упражнение на пульс и функциональную выносливость."
            "plyometrics", "плиометрика" -> "Взрывное упражнение с контролем приземления."
            else -> "Базовое силовое упражнение с контролем техники."
        }
    }

    private data class SourceExercise(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("force") val force: String? = null,
        @SerializedName("level") val level: String,
        @SerializedName("mechanic") val mechanic: String? = null,
        @SerializedName("equipment") val equipment: String? = null,
        @SerializedName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
        @SerializedName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
        @SerializedName("instructions") val instructions: List<String> = emptyList(),
        @SerializedName("category") val category: String
    )

    private data class TrainingTemplate(
        val id: String,
        val goal: String,
        val level: String,
        val durationMinutes: Int,
        val titleRu: String,
        val titleEn: String,
        val descriptionRu: String,
        val descriptionEn: String,
        val exercises: List<TrainingExerciseRef>
    )

    private data class TrainingExerciseRef(
        val exerciseId: String,
        val durationSec: Int?,
        val reps: Int?
    )

    private data class GeneratedTrainings(
        val trainings: List<TrainingEntity>,
        val links: List<TrainingExerciseEntity>
    )

    private companion object {
        const val RU_ASSET = "bd_ru.json"
        const val EN_ASSET = "bd_en.json"
        const val SEED_VERSION = 6
        const val MIN_EXERCISES_PER_TRAINING = 4
    }
}
