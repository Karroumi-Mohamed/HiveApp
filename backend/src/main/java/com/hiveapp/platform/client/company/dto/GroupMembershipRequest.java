package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.Size;

public record GroupMembershipRequest(@Size(max = 160) String positionTitle) {}
