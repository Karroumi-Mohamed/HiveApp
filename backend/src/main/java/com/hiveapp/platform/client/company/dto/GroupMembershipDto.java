package com.hiveapp.platform.client.company.dto;

import java.util.UUID;

public record GroupMembershipDto(
        UUID id,
        UUID groupId,
        UUID memberId,
        String memberDisplayName,
        String positionTitle
) {}
