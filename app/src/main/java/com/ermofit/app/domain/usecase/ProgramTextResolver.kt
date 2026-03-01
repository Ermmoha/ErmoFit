package com.ermofit.app.domain.usecase

import com.ermofit.app.data.datastore.UserPreferencesManager
import com.ermofit.app.data.local.entity.ProgramEntity
import com.ermofit.app.data.local.entity.ProgramTextEntity
import com.ermofit.app.data.repository.LocalDataRepository
import com.ermofit.app.domain.ContentLanguageResolver
import com.ermofit.app.domain.model.ResolvedProgramText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ProgramTextResolver @Inject constructor(
    private val localDataRepository: LocalDataRepository,
    private val preferencesManager: UserPreferencesManager,
    private val contentLanguageResolver: ContentLanguageResolver
) {
    private val preferredLangFlow: Flow<String> = preferencesManager.observeLanguage()
        .map(contentLanguageResolver::resolveToLangCode)
        .distinctUntilChanged()

    fun observePreferredLangCode(): Flow<String> = preferredLangFlow

    fun observeTextForProgram(programId: String): Flow<ResolvedProgramText> {
        return preferredLangFlow.flatMapLatest { preferredLang ->
            val fallbackLang = contentLanguageResolver.fallbackFor(preferredLang)
            localDataRepository.observeProgramText(
                programId = programId,
                preferredLang = preferredLang,
                fallbackLang = fallbackLang
            ).map { text ->
                resolveText(text = text, preferredLang = preferredLang)
            }
        }
    }

    fun observeTextsForPrograms(
        programsFlow: Flow<List<ProgramEntity>>
    ): Flow<Map<String, ResolvedProgramText>> {
        return combine(programsFlow, preferredLangFlow) { programs, preferredLang ->
            programs to preferredLang
        }.flatMapLatest { (programs, preferredLang) ->
            val ids = programs.map { it.id }
            if (ids.isEmpty()) {
                return@flatMapLatest flowOf(emptyMap<String, ResolvedProgramText>())
            }
            val fallbackLang = contentLanguageResolver.fallbackFor(preferredLang)
            localDataRepository.observeProgramTextsByIds(
                ids = ids,
                preferredLang = preferredLang,
                fallbackLang = fallbackLang
            ).map { rows ->
                val prioritized = linkedMapOf<String, ProgramTextEntity>()
                rows.forEach { row ->
                    if (prioritized[row.programId] == null) {
                        prioritized[row.programId] = row
                    }
                }
                programs.associate { program ->
                    val text = resolveText(
                        text = prioritized[program.id],
                        preferredLang = preferredLang
                    )
                    program.id to text
                }
            }
        }
    }

    private fun resolveText(
        text: ProgramTextEntity?,
        preferredLang: String
    ): ResolvedProgramText {
        val noTranslation = if (preferredLang == "ru") "\u041d\u0435\u0442 \u043f\u0435\u0440\u0435\u0432\u043e\u0434\u0430" else "No translation"
        val title = text?.title?.takeIf { it.isNotBlank() } ?: noTranslation
        val description = text?.description?.takeIf { it.isNotBlank() } ?: noTranslation
        val langCode = text?.langCode ?: preferredLang
        val hasPreferredText = text?.let { item ->
            item.langCode == preferredLang &&
                item.title.isNotBlank() &&
                item.description.isNotBlank()
        } == true
        return ResolvedProgramText(
            title = title,
            description = description,
            langCode = langCode,
            isFallback = !hasPreferredText
        )
    }
}

