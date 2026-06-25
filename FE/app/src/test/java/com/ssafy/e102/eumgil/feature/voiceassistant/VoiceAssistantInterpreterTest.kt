package com.ssafy.e102.eumgil.feature.voiceassistant

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAssistantInterpreterTest {
    private val interpreter = RuleBasedVoiceAssistantInterpreter()

    @Test
    fun `report commands resolve to open report`() = runTest {
        listOf("제보", "신고", "불편 제보", "제보해줘").forEach { command ->
            val action = interpreter.interpret(command, VoiceAssistantContext())

            assertEquals(VoiceAssistantAction.OpenReport(), action)
        }
    }

    @Test
    fun `busan station search commands resolve to search place`() = runTest {
        listOf("부산역 찾아줘", "부산역 검색해줘").forEach { command ->
            val action = interpreter.interpret(command, VoiceAssistantContext())

            assertEquals(VoiceAssistantAction.SearchPlace(query = "부산역"), action)
        }
    }

    @Test
    fun `unknown command resolves to unknown command action`() = runTest {
        val action = interpreter.interpret("날씨 알려줘", VoiceAssistantContext())

        assertEquals(VoiceAssistantAction.UnknownCommand(rawCommand = "날씨 알려줘"), action)
    }
}
