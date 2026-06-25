package com.ssafy.e102.domain.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AdminRoadNetworkEditAsyncConfig {

	@Bean
	public TaskExecutor adminRoadNetworkEditTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("admin-road-edit-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(10);
		executor.initialize();
		return executor;
	}
}
