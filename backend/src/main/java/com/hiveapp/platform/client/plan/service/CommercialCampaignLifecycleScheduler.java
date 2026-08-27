package com.hiveapp.platform.client.plan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class CommercialCampaignLifecycleScheduler {

    private final CommercialCampaignLifecycleProcessor processor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${hiveapp.campaigns.lifecycle-delay-ms:60000}")
    public void processDueCampaigns() {
        processor.processDue(clock.instant());
    }
}
