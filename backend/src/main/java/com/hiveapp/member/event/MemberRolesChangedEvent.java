package com.hiveapp.member.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MemberRolesChangedEvent extends BaseDomainEvent {

    private final UUID memberId;
    private final UUID accountId;

    public MemberRolesChangedEvent(UUID memberId, UUID accountId) {
        super();
        this.memberId = memberId;
        this.accountId = accountId;
    }
}
