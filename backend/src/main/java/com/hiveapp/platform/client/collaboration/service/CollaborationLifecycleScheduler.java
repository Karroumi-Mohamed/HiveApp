package com.hiveapp.platform.client.collaboration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollaborationLifecycleScheduler {

    private final CollaborationAutomaticResumeService automaticResumeService;

    @Scheduled(fixedDelayString = "${hiveapp.collaborations.lifecycle-delay-ms:60000}")
    public void resumeDueCollaborations() {
        automaticResumeService.resumeDueCollaborations();
    }
}
