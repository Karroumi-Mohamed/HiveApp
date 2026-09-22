package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.registry.definition.*;
import com.hiveapp.platform.generated.PlatformPermissions;
import com.hiveapp.platform.registry.definition.service.PlatformControlFeatureService;
import dev.karroumi.permissionizer.PermissionNode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PermissionNode(
    key = "customer_communications",
    description = "Customer Communications",
    guard = PermissionNode.Guard.ON)
public class CustomerCommunicationAdminService extends PlatformControlFeatureService {
  private final CommunicationService service;

  protected FeatureDefinition featureDefinition() {
    return FeatureDefinition.platformControl("platform.customer_communications")
        .displayName("Customer Communications")
        .description("One-way account information, warnings and offer announcements")
        .sortOrder(57)
        .build();
  }

  @PermissionNode(key = "read", description = "Read customer communication drafts and publications")
  public Page<Publication> list(Pageable p) {
    return service.publications(p);
  }

  @PermissionNode(key = "internal_detail", guard = PermissionNode.Guard.OFF)
  public Publication detail(UUID id) {
    if (!dev.karroumi.permissionizer.PermissionGuard.has(
        PlatformPermissions.Customer_communications.Read.permission()))
      throw new com.hiveapp.shared.exception.ForbiddenException(
          "Communication read permission is required.");
    return service.publication(id);
  }

  @PermissionNode(
      key = "choose_recipients",
      description = "Choose active communication recipient Accounts")
  public Page<AccountChoice> choices(String q, Pageable p) {
    return service.choices(q, p);
  }

  @PermissionNode(key = "internal_selected_recipients", guard = PermissionNode.Guard.OFF)
  public List<AccountChoice> selectedRecipients(UUID id) {
    if (!dev.karroumi.permissionizer.PermissionGuard.has(
        PlatformPermissions.Customer_communications.Choose_recipients.permission()))
      throw new com.hiveapp.shared.exception.ForbiddenException(
          "Recipient selection permission is required.");
    return service.selectedRecipients(id);
  }

  @PermissionNode(key = "create", description = "Create a customer communication draft")
  @Transactional
  public Publication create(Draft d) {
    return service.create(d);
  }

  @PermissionNode(key = "edit", description = "Edit a customer communication draft")
  @Transactional
  public Publication edit(UUID id, Edit d) {
    return service.edit(id, d);
  }

  @PermissionNode(
      key = "publish",
      description = "Publish or schedule a frozen customer communication")
  @Transactional
  public Publication publish(UUID id, Command c) {
    return service.publish(id, c);
  }

  @PermissionNode(
      key = "publish_marketing",
      description = "Permit publication of opted-in marketing communication")
  public void marketingAuthority() {}

  @PermissionNode(key = "cancel", description = "Withdraw a customer communication")
  @Transactional
  public Publication cancel(UUID id, Command c) {
    return service.cancel(id, c);
  }

  @PermissionNode(
      key = "read_results",
      description = "Read recipient identities, delivery and interaction results")
  public Page<Recipient> recipients(UUID id, Pageable p) {
    return service.recipients(id, p);
  }

  @PermissionNode(key = "retry_email", description = "Retry failed or suppressed customer email")
  @Transactional
  public void retry(UUID id) {
    service.retryEmail(id);
  }
}
