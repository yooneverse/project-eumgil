package com.ssafy.e102.global.external.ai;

public interface AiVoiceAnalysisClient {

	AiVoiceAnalyzeResult analyze(AiVoiceAnalyzeCommand command);
}
