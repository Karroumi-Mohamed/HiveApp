package com.hiveapp.identity.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UserCreatedEvent extends BaseDomainEvent {

    private final UUID userId;
    private final String email;
    private final String accountName;

    public UserCreatedEvent(UUID userId, String email, String accountName) {
        super();
        this.userId = userId;
        this.email = email;
        this.accountName = accountName;
    }
}
