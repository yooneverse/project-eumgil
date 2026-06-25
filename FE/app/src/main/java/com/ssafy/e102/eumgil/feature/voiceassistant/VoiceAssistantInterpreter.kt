package com.ssafy.e102.eumgil.feature.voiceassistant

import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeHistoryItem
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeIntent
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeMode
import com.ssafy.e102.eumgil.core.model.toJsonString
import com.ssafy.e102.eumgil.data.repository.VoiceAnalyzeRepository

interface VoiceAssistantInterpreter {
    suspend fun interpret(
        transcript: String,
        context: VoiceAssistantContext = VoiceAssistantContext(),
    ): VoiceAssistantAction
}

class RuleBasedVoiceAssistantInterpreter : VoiceAssistantInterpreter {
    override suspend fun interpret(
        transcript: String,
        context: VoiceAssistantContext,
    ): VoiceAssistantAction {
        val command = transcript.normalizedCommand()
        return when {
            command in reportCommands -> VoiceAssistantAction.OpenReport()
            command in busanStationSearchCommands ->
                VoiceAssistantAction.SearchPlace(
                    query = BUSAN_STATION_QUERY,
                    editingTarget = context.editingTarget,
                )

            else -> VoiceAssistantAction.UnknownCommand(rawCommand = command)
        }
    }

    private fun String.normalizedCommand(): String =
        trim()
            .replace(WHITESPACE_REGEX, " ")

    private companion object {
        const val BUSAN_STATION_QUERY = "부산역"

        val WHITESPACE_REGEX = Regex("\\s+")

        val reportCommands =
            setOf(
                "제보",
                "신고",
                "불편 제보",
                "제보해줘",
            )

        val busanStationSearchCommands =
            setOf(
                "부산역 찾아줘",
                "부산역 검색해줘",
            )
    }
}

class AiVoiceAssistantInterpreter(
    private val voiceAnalyzeRepository: VoiceAnalyzeRepository,
) : VoiceAssistantInterpreter {

    private val conversationHistory = mutableListOf<VoiceAnalyzeHistoryItem>()

    override suspend fun interpret(
        transcript: String,
        context: VoiceAssistantContext,
    ): VoiceAssistantAction {
        val result = voiceAnalyzeRepository.analyze(
            text = transcript,
            mode = VoiceAnalyzeMode.MOBILITY_IMPAIRED,
            history = conversationHistory.toList(),
            currentRoute = context.currentRoute,
        )

        return when (result.intent) {
            VoiceAnalyzeIntent.PLACE_SEARCH -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.SearchPlace(
                    query = result.placeName.orEmpty(),
                    editingTarget = context.editingTarget,
                )
            }
            VoiceAnalyzeIntent.CATEGORY_SEARCH -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.CategorySearch(category = result.category.orEmpty())
            }
            VoiceAnalyzeIntent.NAVIGATE -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.Navigate(
                    departure = result.departure,
                    destination = result.destination.orEmpty(),
                )
            }
            VoiceAnalyzeIntent.SHOW_BOOKMARKS -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.ShowBookmarks()
            }
            VoiceAnalyzeIntent.SHOW_FAVORITE_ROUTES -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.OpenSavedRoutes()
            }
            VoiceAnalyzeIntent.LOGOUT -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.Logout()
            }
            VoiceAnalyzeIntent.REPORT -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.OpenReport(reportType = result.reportType, description = result.description)
            }
            VoiceAnalyzeIntent.NAVIGATION_END -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.StopNavigation()
            }
            VoiceAnalyzeIntent.OPEN_MY_PAGE -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.OpenMyPage()
            }
            VoiceAnalyzeIntent.OPEN_MAP -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.OpenMap()
            }
            VoiceAnalyzeIntent.ASK -> {
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "user", content = transcript))
                conversationHistory.add(VoiceAnalyzeHistoryItem(role = "assistant", content = result.toJsonString()))
                VoiceAssistantAction.Ask(message = result.confirmationMessage.orEmpty())
            }
            VoiceAnalyzeIntent.UNKNOWN -> {
                conversationHistory.clear()
                VoiceAssistantAction.UnknownCommand(rawCommand = transcript)
            }
            else -> {
                conversationHistory.clear()
                VoiceAssistantAction.UnknownCommand(rawCommand = transcript)
            }
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}
