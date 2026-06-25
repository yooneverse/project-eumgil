package com.ssafy.e102.domain.admin.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ssafy.e102.domain.admin.service.AdminRoadNetworkEditJobService;

@Component
public class AdminRoadNetworkEditJobRecovery {

	private final AdminRoadNetworkEditJobService jobService;

	public AdminRoadNetworkEditJobRecovery(AdminRoadNetworkEditJobService jobService) {
		this.jobService = jobService;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failUnfinishedJobs() {
		jobService.failUnfinishedJobsOnStartup();
	}
}
