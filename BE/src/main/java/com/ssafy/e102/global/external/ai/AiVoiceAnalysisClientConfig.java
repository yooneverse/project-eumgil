package com.ssafy.e102.global.external.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiVoiceAnalysisProperties.class)
public class AiVoiceAnalysisClientConfig {

	// Registers AI voice-analysis configuration properties.
}
