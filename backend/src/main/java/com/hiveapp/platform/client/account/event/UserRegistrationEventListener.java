package com.hiveapp.platform.client.account.event;

import com.hiveapp.identity.event.UserRegisteredEvent;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationEventListener {

    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void handleUserRegistration(UserRegisteredEvent event) {
        log.info("Provisioning Workspace for newly registered user: {}", event.userId());
        
        var user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("User not found for ID {}", event.userId());
            return;
        }
        
        String slug = event.email().substring(0, event.email().indexOf('@'));
        String workspaceName = slug + "'s Workspace";
        
        Account account = new Account();
        account.setOwnerId(event.userId());
        account.setName(workspaceName);
        account.setSlug(slug);
        account.setActive(true);
        account = accountRepository.save(account);
        
        Member member = new Member();
        member.setAccount(account);
        member.setUser(user);
        member.setDisplayName(slug);
        member.setOwner(true);
        member.setActive(true);
        memberRepository.save(member);
        
        eventPublisher.publishEvent(new AccountCreatedEvent(this, account.getId()));
    }
}
