package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.mapper.AccountMapper;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.hiveapp.platform.communication.CommunicationModels.*;
import com.hiveapp.platform.communication.CommunicationService;
import com.hiveapp.platform.generated.PlatformPermissions;
import org.springframework.data.domain.*;

@Service
@RequiredArgsConstructor
@PermissionNode(key = WorkspaceFeature.KEY, description = "Account & Workspace Management", guard = PermissionNode.Guard.ON)
public class AccountShellServiceImpl extends ClientWorkspaceFeatureService implements AccountShellService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final MemberRepository memberRepository;
    private final TokenSessionService tokenSessionService;
    private final CommunicationService communications;
    private final com.hiveapp.platform.communication.InternalNotificationService internalNotifications;

    @Override
    @PermissionNode(key = "read_communications", description = "Read own Account notices, warnings and messages")
    public Page<Item> communicationInbox(Kind kind, boolean archived, boolean unread, Pageable page) {
        return communications.inbox(kind, archived, unread, page);
    }

    @Override
    @PermissionNode(key = "internal_notification_inbox", guard = PermissionNode.Guard.OFF)
    public Page<Item> notificationInbox(Kind kind, Topic topic, boolean archived, boolean unread, Pageable page) {
        requireCommunicationRead();
        return communications.inbox(kind, topic, archived, unread, page, false);
    }

    @Override
    @PermissionNode(key = "choose_notification_recipients", description = "Choose active own-account notification recipients")
    public Page<MemberChoice> notificationRecipients(String search, Pageable page) {
        return internalNotifications.recipients(search, page);
    }

    @Override
    @Transactional
    @PermissionNode(key = "send_notification", description = "Send one-way information to selected own-account members")
    public void sendInternalNotification(InternalNotice notice) {
        if (!dev.karroumi.permissionizer.PermissionGuard.has(PlatformPermissions.Workspace.Choose_notification_recipients.permission()))
            throw new ForbiddenException("Notification recipient selection permission is required.");
        internalNotifications.send(notice);
    }

    @Override
    @PermissionNode(key = "internal_notification_settings", guard = PermissionNode.Guard.OFF)
    public java.util.List<NotificationSetting> notificationSettings() {
        requireCommunicationRead();
        return communications.settings(false);
    }

    @Override
    @Transactional
    @PermissionNode(key = "notification_preferences", description = "Manage own optional notification preferences")
    public NotificationSetting updateNotificationSetting(NotificationSetting setting) {
        requireCommunicationRead();
        return communications.setting(setting, false);
    }

    @Override
    @PermissionNode(key = "internal_communication_detail", guard = PermissionNode.Guard.OFF)
    public Item communicationDetail(UUID id) {
        requireCommunicationRead();
        return communications.detail(id);
    }

    @Override
    @PermissionNode(key = "mark_communication_read", description = "Mark own communication as read")
    public void readCommunication(UUID id) {
        requireCommunicationRead();
        communications.interact(id, Interaction.READ);
    }

    @Override
    @PermissionNode(key = "acknowledge_warning", description = "Acknowledge seeing an Account warning without resolving it")
    @Transactional
    public void acknowledgeCommunication(UUID id) {
        requireCommunicationRead();
        communications.interact(id, Interaction.ACKNOWLEDGE);
    }

    @Override
    @PermissionNode(key = "archive_communication", description = "Archive or restore own communications")
    public void archiveCommunication(UUID id, boolean archived) {
        requireCommunicationRead();
        communications.interact(id, archived ? Interaction.ARCHIVE : Interaction.RESTORE);
    }

    @Override
    @PermissionNode(key = "internal_communication_preferences", guard = PermissionNode.Guard.OFF)
    public Preference communicationPreference() {
        requireCommunicationRead();
        return communications.preference();
    }

    @Override
    @PermissionNode(key = "communication_preferences", description = "Manage Account marketing opt-in as owner")
    @Transactional
    public Preference updateCommunicationPreference(Preference request) {
        requireCommunicationRead();
        return communications.preference(request);
    }

    private void requireCommunicationRead() {
        if (!dev.karroumi.permissionizer.PermissionGuard.has(
                PlatformPermissions.Workspace.Read_communications.permission())) {
            throw new ForbiddenException("Reading communications is required.");
        }
    }

    @Override
    protected FeatureDefinition featureDefinition() {
        return WorkspaceFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "Read my account")
    @Transactional(readOnly = true)
    public AccountDto getAccount(UUID id) {
        return accountMapper.toDto(findCurrentAccount(id));
    }

    @Override
    @Transactional
    @PermissionNode(key = "delete", description = "Deactivate my account")
    public void deactivateAccount(UUID id) {
        var account = findCurrentAccount(id);
        var memberUserIds = memberRepository.findAllByAccountId(id).stream()
                .map(member -> member.getUser().getId())
                .toList();
        account.setActive(false);
        accountRepository.saveAndFlush(account);
        tokenSessionService.revokeAll(memberUserIds, TokenAudience.CLIENT);
    }

    private Account findCurrentAccount(UUID id) {
        var account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        requireCurrentAccount(account);
        return account;
    }

    private void requireCurrentAccount(Account account) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!account.getId().equals(currentAccountId)) {
            throw new ForbiddenException("Account does not belong to the current workspace");
        }
    }
}
