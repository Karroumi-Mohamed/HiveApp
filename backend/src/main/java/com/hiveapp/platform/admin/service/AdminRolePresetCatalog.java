package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminPermissionSummaryDto;
import com.hiveapp.platform.admin.dto.AdminRolePresetDto;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminRolePresetCatalog {

    private static final List<PresetDefinition> DEFINITIONS = List.of(
            new PresetDefinition(
                    "PLATFORM_OBSERVER",
                    "Observateur plateforme",
                    "Consulte l’activité opérationnelle sans pouvoir la modifier.",
                    Set.of(
                            "platform.admin_users.overview",
                            "platform.admin_users.read",
                            "platform.admin_users.read_detail",
                            "platform.admin_users.read_permissions",
                            "platform.roles.read",
                            "platform.roles.read_detail",
                            "platform.roles.read_holders",
                            "platform.roles.read_history",
                            "platform.roles.list_presets",
                            "platform.roles.list_grantable_permissions",
                            "platform.roles.preview_impact",
                            "platform.plans.overview",
                            "platform.plans.list",
                            "platform.plans.read_detail",
                            "platform.plans.list_features",
                            "platform.plans.list_subscribers",
                            "platform.plans.list_add_ons",
                            "platform.plans.read_add_on",
                            "platform.plans.list_quota_packages",
                            "platform.plans.read_quota_package",
                            "platform.price_books.list",
                            "platform.price_books.read",
                            "platform.price_books.read_history",
                            "platform.price_books.preview_activation",
                            "platform.subscriptions.search_accounts",
                            "platform.subscriptions.read",
                            "platform.subscriptions.read_changes",
                            "platform.registry.read",
                            "platform.registry.feature_catalog",
                            "platform.registry.permission_catalog",
                            "platform.registry.sync_status",
                            "platform.registry.control_history")),
            new PresetDefinition(
                    "CUSTOMER_OPERATIONS",
                    "Opérations clients",
                    "Consulte les comptes et abonnements et traite les opérations client courantes.",
                    Set.of(
                            "platform.plans.list",
                            "platform.plans.read_detail",
                            "platform.plans.list_features",
                            "platform.plans.list_subscribers",
                            "platform.plans.lookup_subscriber_owner_email",
                            "platform.subscriptions.search_accounts",
                            "platform.subscriptions.read",
                            "platform.subscriptions.read_changes",
                            "platform.subscriptions.create",
                            "platform.subscriptions.create_trial",
                            "platform.subscriptions.update_overrides",
                            "platform.subscriptions.confirm_checkout")),
            new PresetDefinition(
                    "COMMERCIAL_OPERATIONS",
                    "Opérations commerciales",
                    "Gère le catalogue commercial, les offres et les abonnements.",
                    Set.of(
                            "platform.plans.overview",
                            "platform.plans.list",
                            "platform.plans.read_detail",
                            "platform.plans.list_features",
                            "platform.plans.list_subscribers",
                            "platform.plans.lookup_subscriber_owner_email",
                            "platform.plans.create",
                            "platform.plans.update",
                            "platform.plans.duplicate",
                            "platform.plans.revise",
                            "platform.plans.transition_status",
                            "platform.plans.preview_delete",
                            "platform.plans.delete",
                            "platform.plans.assign_feature",
                            "platform.plans.update_feature",
                            "platform.plans.remove_feature",
                            "platform.plans.list_add_ons",
                            "platform.plans.read_add_on",
                            "platform.plans.create_add_on",
                            "platform.plans.update_add_on",
                            "platform.plans.revise_add_on",
                            "platform.plans.transition_add_on",
                            "platform.plans.delete_add_on",
                            "platform.plans.assign_add_on_feature",
                            "platform.plans.update_add_on_feature",
                            "platform.plans.remove_add_on_feature",
                            "platform.plans.list_quota_packages",
                            "platform.plans.read_quota_package",
                            "platform.plans.create_quota_package",
                            "platform.plans.update_quota_package",
                            "platform.plans.transition_quota_package",
                            "platform.plans.delete_quota_package",
                            "platform.price_books.list",
                            "platform.price_books.read",
                            "platform.price_books.read_history",
                            "platform.price_books.create",
                            "platform.price_books.update_draft",
                            "platform.price_books.preview_activation",
                            "platform.price_books.activate",
                            "platform.price_books.pause",
                            "platform.price_books.reactivate",
                            "platform.price_books.revise",
                            "platform.price_books.archive",
                            "platform.price_books.delete_draft",
                            "platform.subscriptions.search_accounts",
                            "platform.subscriptions.read",
                            "platform.subscriptions.read_changes",
                            "platform.subscriptions.create",
                            "platform.subscriptions.create_trial",
                            "platform.subscriptions.update_overrides",
                            "platform.subscriptions.confirm_checkout")),
            new PresetDefinition(
                    "ACCESS_ADMINISTRATOR",
                    "Administration des accès",
                    "Gère les opérateurs, les rôles et leurs permissions.",
                    Set.of(
                            "platform.admin_users.overview",
                            "platform.admin_users.read",
                            "platform.admin_users.read_detail",
                            "platform.admin_users.read_permissions",
                            "platform.admin_users.create",
                            "platform.admin_users.rename",
                            "platform.admin_users.change_email",
                            "platform.admin_users.send_email_verification",
                            "platform.admin_users.resend_activation",
                            "platform.admin_users.generate_temporary_access",
                            "platform.admin_users.toggle_active",
                            "platform.admin_users.bulk_set_active",
                            "platform.admin_users.assign_role",
                            "platform.admin_users.remove_role",
                            "platform.admin_users.bulk_assign_role",
                            "platform.admin_users.bulk_resend_activation",
                            "platform.roles.read",
                            "platform.roles.read_detail",
                            "platform.roles.read_holders",
                            "platform.roles.read_history",
                            "platform.roles.list_presets",
                            "platform.roles.list_grantable_permissions",
                            "platform.roles.preview_impact",
                            "platform.roles.create",
                            "platform.roles.create_from_preset",
                            "platform.roles.update",
                            "platform.roles.duplicate",
                            "platform.roles.replace_permissions",
                            "platform.roles.transition_status",
                            "platform.roles.delete",
                            "platform.roles.grant_permission",
                            "platform.roles.revoke_permission")));

    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final AdminMutationAuthorizer adminMutationAuthorizer;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional(readOnly = true)
    public void validateAtStartup() {
        DEFINITIONS.forEach(this::resolvePermissions);
    }

    @Transactional(readOnly = true)
    public List<AdminRolePresetDto> availablePresets() {
        AdminMutationAuthorizer.GrantCeiling ceiling = adminMutationAuthorizer.currentActorGrantCeiling();
        return DEFINITIONS.stream()
                .filter(definition -> ceiling.allowsAll(definition.permissionCodes()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminRolePresetDto requireAvailable(String code) {
        PresetDefinition definition = DEFINITIONS.stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("AdminRolePreset", "code", code));
        if (!adminMutationAuthorizer.currentActorGrantCeiling().allowsAll(definition.permissionCodes())) {
            throw new com.hiveapp.shared.exception.ForbiddenException(
                    "The acting administrator cannot grant every permission in this preset.");
        }
        return toDto(definition);
    }

    private AdminRolePresetDto toDto(PresetDefinition definition) {
        return new AdminRolePresetDto(
                definition.code(),
                definition.name(),
                definition.description(),
                resolvePermissions(definition).stream().map(AdminRolePresetCatalog::toSummary).toList());
    }

    private List<Permission> resolvePermissions(PresetDefinition definition) {
        Map<String, Permission> byCode = new LinkedHashMap<>();
        permissionRepository.findAllByCodeIn(definition.permissionCodes())
                .forEach(permission -> byCode.put(permission.getCode(), permission));
        return definition.permissionCodes().stream()
                .sorted()
                .map(code -> {
                    Permission permission = byCode.get(code);
                    if (permission == null) {
                        throw new IllegalStateException(
                                "Admin role preset " + definition.code() + " references missing permission " + code);
                    }
                    permissionGrantValidator.requirePlatformAdminRoleGrantable(code);
                    return permission;
                })
                .toList();
    }

    private static AdminPermissionSummaryDto toSummary(Permission permission) {
        return new AdminPermissionSummaryDto(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getAction(),
                permission.getResource());
    }

    private record PresetDefinition(
            String code,
            String name,
            String description,
            Set<String> permissionCodes
    ) {}
}
