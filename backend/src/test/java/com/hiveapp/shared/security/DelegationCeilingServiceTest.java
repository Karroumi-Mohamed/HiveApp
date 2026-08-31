package com.hiveapp.shared.security;

import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.dto.MemberPermissionDto;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelegationCeilingServiceTest {

    @Mock private EffectivePermissionService effectivePermissionService;
    @Mock private MemberRepository memberRepository;
    @InjectMocks private DelegationCeilingService service;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void delegationIsEvaluatedInTheRequestedEffectScope() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        String permission = "platform.company.read_single";
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorId, accountId, accountId, null, null, false));
        when(effectivePermissionService.getEffectivePermissions(actorId, accountId, companyId))
                .thenReturn(new MemberPermissionDto(
                        UUID.randomUUID(), accountId, companyId, false, Set.of(permission)));

        assertThatCode(() -> service.requireActorCanDelegate(
                accountId, companyId, List.of(permission))).doesNotThrowAnyException();
    }

    @Test
    void actorCannotDelegatePermissionMissingFromTheRequestedScope() {
        UUID actorId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorId, accountId, accountId, null, null, false));
        when(effectivePermissionService.getEffectivePermissions(actorId, accountId, companyId))
                .thenReturn(new MemberPermissionDto(
                        UUID.randomUUID(), accountId, companyId, false, Set.of()));

        assertThatThrownBy(() -> service.requireActorCanDelegate(
                accountId, companyId, List.of("platform.company.delete")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("requested scope");
    }
}
