package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hiveapp.platform.client.account.service.impl.AccountShellServiceImpl;
import com.hiveapp.platform.generated.PlatformPermissions;
import dev.karroumi.permissionizer.*;
import dev.karroumi.permissionizer.spring.PermissionInterceptor;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class NotificationGuardBoundaryTest {
  @Test
  void everyPublicBoundaryRejectsBeforeTouchingDomainDataWithoutAuthority() throws Exception {
    for (var type :
        List.of(
            AccountShellServiceImpl.class,
            OperatorNotificationService.class,
            CustomerCommunicationAdminService.class)) {
      var constructor = type.getConstructors()[0];
      Object[] dependencies =
          Arrays.stream(constructor.getParameterTypes()).map(t -> mock(t)).toArray();
      var factory = new AspectJProxyFactory(constructor.newInstance(dependencies));
      factory.setProxyTargetClass(true);
      factory.addAspect(new PermissionInterceptor());
      Object proxy = factory.getProxy();
      try (var denied = PermissionGuard.withPermissions()) {
        for (var method : type.getDeclaredMethods()) {
          if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) continue;
          assertThat(method.getAnnotation(PermissionNode.class)).as(method.toString()).isNotNull();
          Object[] arguments =
              Arrays.stream(method.getParameterTypes())
                  .map(t -> t == boolean.class ? false : null)
                  .toArray();
          assertThatThrownBy(() -> method.invoke(proxy, arguments))
              .as(method.toString())
              .hasRootCauseInstanceOf(PermissionDeniedException.class);
        }
      }
      verifyNoInteractions(dependencies);
    }
  }

  @Test
  void detailAliasReusesReadAuthorityInsteadOfCreatingAnInternalGrant() {
    var data = mock(CommunicationService.class);
    var service =
        new OperatorNotificationService(
            data,
            mock(NotificationEventRepository.class),
            mock(CommunicationEntryRepository.class),
            java.time.Clock.systemUTC());
    UUID id = UUID.randomUUID();
    try (var read =
        PermissionGuard.withPermissions(PlatformPermissions.Notifications.Read.permission())) {
      service.detail(id);
    }
    verify(data).detail(id, true);
  }
}
