package com.hiveapp.platform.client.account.event;

import java.util.UUID;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AccountCreatedEvent extends ApplicationEvent {
    private final UUID accountId;

    public AccountCreatedEvent(Object source, UUID accountId) {
        super(source);
        this.accountId = accountId;
    }
}
