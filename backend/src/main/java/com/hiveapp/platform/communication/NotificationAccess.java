package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.security.context.*;
import dev.karroumi.permissionizer.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Live recipient validation shared by inbox and email. Stored targeting never confers access. */
@Component
@RequiredArgsConstructor
public class NotificationAccess {
  public record Viewer(UUID userId, UUID accountId, UUID companyId, boolean platform) {}

  private final MemberRepository members;
  private final AccountRepository accounts;
  private final AdminUserRepository admins;
  private final CompanyRepository companies;
  private final jakarta.persistence.EntityManager em;

  public void lockIdentity(Viewer viewer) {
    // A user row is stable across account/platform contexts; serialize concurrent preference
    // upserts.
    em.find(User.class, viewer.userId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  }

  public void validateTarget(NotificationPublisher.Target target) {
    if (target.accountId() != null) {
      if (!accounts.existsById(target.accountId())
          || (target.userId() != null
              && !members.existsByAccountIdAndUserId(target.accountId(), target.userId()))
          || (target.companyId() != null
              && companies.findByIdAndAccountId(target.companyId(), target.accountId()).isEmpty()))
        throw new IllegalArgumentException("Notification target is outside its account scope.");
    } else if (target.userId() != null && admins.findByUserId(target.userId()).isEmpty()) {
      throw new IllegalArgumentException("Notification target is not a platform operator.");
    }
  }

  public Viewer viewer(boolean platform) {
    var c = HiveAppContextHolder.getContext();
    if (c == null || c.actorUserId() == null || c.isB2B())
      throw new ForbiddenException("Use your own notification context.");
    if (platform) {
      var admin =
          admins
              .findByUserId(c.actorUserId())
              .orElseThrow(() -> new ForbiddenException("Active operator required."));
      if (!admin.isActive() || !admin.getUser().isActive())
        throw new ForbiddenException("Active operator required.");
      return new Viewer(c.actorUserId(), null, null, true);
    }
    var member =
        members
            .findByAccountIdAndUserId(c.currentAccountId(), c.actorUserId())
            .orElseThrow(() -> new ForbiddenException("Active account membership required."));
    if (!Objects.equals(c.currentAccountId(), c.clientAccountId())
        || !member.isActive()
        || !member.getUser().isActive()
        || !member.getAccount().isActive())
      throw new ForbiddenException("Active own-account membership required.");
    return new Viewer(c.actorUserId(), c.currentAccountId(), c.targetCompanyId(), false);
  }

  public boolean matches(CommunicationEntry entry, Viewer v) {
    return Objects.equals(entry.getAccountId(), v.accountId())
        && (v.platform()
            ? entry.getAudience() == Audience.PLATFORM || entry.getAudience() == Audience.OPERATOR
            : entry.getAudience() == Audience.ACCOUNT || entry.getAudience() == Audience.MEMBER)
        && (entry.getRecipientUserId() == null || entry.getRecipientUserId().equals(v.userId()))
        && (entry.getCompanyId() == null || entry.getCompanyId().equals(v.companyId()));
  }

  public Optional<User> emailRecipient(CommunicationEntry entry) {
    User user;
    UUID account = entry.getAccountId();
    if (account == null) {
      if (entry.getRecipientUserId() == null) return Optional.empty();
      var admin = admins.findByUserId(entry.getRecipientUserId()).orElse(null);
      if (admin == null || !admin.isActive()) return Optional.empty();
      user = admin.getUser();
    } else {
      var a = accounts.findById(account).orElse(null);
      if (a == null || !a.isActive()) return Optional.empty();
      UUID recipient =
          entry.getRecipientUserId() != null
              ? entry.getRecipientUserId()
              : a.getOwner() == null ? null : a.getOwner().getId();
      if (recipient == null) return Optional.empty();
      var member = members.findByAccountIdAndUserId(account, recipient).orElse(null);
      if (member == null || !member.isActive()) return Optional.empty();
      user = member.getUser();
      if (entry.getCompanyId() != null
          && companies
              .findByIdAndAccountId(entry.getCompanyId(), account)
              .filter(c -> c.isActive())
              .isEmpty()) return Optional.empty();
    }
    if (!user.isActive() || !user.isEmailVerified()) return Optional.empty();
    var context =
        new HiveAppPermissionContext(
            user.getId(), account, account, entry.getCompanyId(), null, false);
    Permission read =
        account == null
            ? PlatformPermissions.Notifications.Read.permission()
            : PlatformPermissions.Workspace.Read_communications.permission();
    if (!PermissionGuard.has(read, context)
        || (entry.getRequiredPermission() != null
            && !PermissionGuard.has(new Permission(entry.getRequiredPermission()), context)))
      return Optional.empty();
    return Optional.of(user);
  }
}
