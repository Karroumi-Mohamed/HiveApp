package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.service.CommercialCampaignAdminService;
import com.hiveapp.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommercialCampaignAdminControllerTest {

    @Test
    void anyCampaignLockRaceUsesTheStableVersionConflictContract() {
        CommercialCampaignAdminController controller =
                new CommercialCampaignAdminController(mock(CommercialCampaignAdminService.class));

        var response = controller.handleLockConflict(
                new CannotAcquireLockException("simulated Campaign race"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.STALE_RESOURCE_VERSION);
    }
}
