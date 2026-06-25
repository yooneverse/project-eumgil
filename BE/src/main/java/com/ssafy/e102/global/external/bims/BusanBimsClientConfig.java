package com.ssafy.e102.global.external.bims;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(BusanBimsProperties.class)
public class BusanBimsClientConfig {

	public static final String BIMS_TASK_EXECUTOR = "bimsTaskExecutor";

	@Bean(name = BIMS_TASK_EXECUTOR)
	public ThreadPoolTaskExecutor bimsTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5);
		executor.setMaxPoolSize(5);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("bims-");
		return executor;
	}
}
