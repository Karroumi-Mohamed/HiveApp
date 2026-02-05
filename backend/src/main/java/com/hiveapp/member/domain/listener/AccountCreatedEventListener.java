package com.hiveapp.member.domain.listener;

import com.hiveapp.account.event.AccountCreatedEvent;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.service.UserService;
import com.hiveapp.member.domain.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for AccountCreatedEvent and automatically creates the Owner Member.
 * This is the second link in the chain: User → Account → Owner Member.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventListener {

    private final MemberService memberService;
    private final UserService userService;

    @EventListener
    public void onAccountCreated(AccountCreatedEvent event) {
        log.info("Handling AccountCreatedEvent for account {} (owner: {})",
                event.getAccountId(), event.getOwnerId());

        User owner = userService.findUserOrThrow(event.getOwnerId());
        String displayName = owner.getFullName();

        memberService.createOwnerMember(
                event.getOwnerId(),
                event.getAccountId(),
                displayName
        );

        log.info("Owner member created for account {} (user: {})",
                event.getAccountId(), event.getOwnerId());
    }
}
