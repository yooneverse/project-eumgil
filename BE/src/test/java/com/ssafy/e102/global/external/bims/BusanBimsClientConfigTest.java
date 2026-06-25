package com.ssafy.e102.global.external.bims;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BusanBimsClientConfigTest {

	@Test
	@DisplayName("BIMS executor는 전역 동시 호출 수를 5개로 제한한다")
	void bimsTaskExecutorLimitsConcurrencyToFive() {
		ThreadPoolTaskExecutor executor = new BusanBimsClientConfig().bimsTaskExecutor();

		assertThat(executor.getCorePoolSize()).isEqualTo(5);
		assertThat(executor.getMaxPoolSize()).isEqualTo(5);
		assertThat(executor.getQueueCapacity()).isEqualTo(50);
		assertThat(executor.getThreadNamePrefix()).isEqualTo("bims-");
	}
}
