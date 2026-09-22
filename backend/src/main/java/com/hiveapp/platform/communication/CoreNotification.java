package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import java.util.UUID;
import com.hiveapp.platform.generated.PlatformPermissions;
import dev.karroumi.permissionizer.Permission;

/** Presentation and permission requirements are fixed by the business event, never its sender. */
public enum CoreNotification implements NotificationDefinition {
  INTERNAL_INFORMATION("account.information", Topic.ACCOUNT, Kind.NOTICE, null, null),
  MEMBER_CREATED(
      "account.member_created", Topic.ACCOUNT, Kind.NOTICE, PlatformPermissions.Staff.Read.permission(), "/app/members"),
  MEMBER_ACCESS_CHANGED(
      "account.member_access_changed",
      Topic.ACCOUNT,
      Kind.NOTICE,
      PlatformPermissions.Staff.Read.permission(),
      "/app/members"),
  B2B_REQUEST(
      "collaboration.requested",
      Topic.COLLABORATION,
      Kind.ACTION,
      PlatformPermissions.B2b.Read_detail.permission(),
      "/app/collaborations/"),
  B2B_CHANGED(
      "collaboration.changed",
      Topic.COLLABORATION,
      Kind.NOTICE,
      PlatformPermissions.B2b.Read_detail.permission(),
      "/app/collaborations/"),
  PAYMENT_RECEIVED(
      "billing.payment_received",
      Topic.BILLING,
      Kind.NOTICE,
      PlatformPermissions.Subscription.Read_invoice_document.permission(),
      "/app/subscription/invoices/"),
  PAYMENT_FAILED(
      "billing.payment_failed",
      Topic.BILLING,
      Kind.WARNING,
      PlatformPermissions.Subscription.Read_invoice.permission(),
      "/app/subscription?tab=invoices&invoice="),
  BILLING_ATTENTION(
      "operations.billing_attention",
      Topic.OPERATIONS,
      Kind.WARNING,
      PlatformPermissions.Billing.Read_invoice.permission(),
      "/admin/billing/invoices/"),
  OFFER_AVAILABLE(
      "commercial.offer_available",
      Topic.COMMERCIAL,
      Kind.OFFER,
      PlatformPermissions.Subscription.Offer_detail.permission(),
      "/app/offers/");

  private final String key;
  private final Topic topic;
  private final Kind kind;
  private final Permission permission;
  private final String path;

  CoreNotification(String key, Topic topic, Kind kind, Permission permission, String path) {
    this.key = key;
    this.topic = topic;
    this.kind = kind;
    this.permission = permission;
    this.path = path;
  }

  public String key() {
    return key;
  }

  public Topic topic() {
    return topic;
  }

  public Kind kind() {
    return kind;
  }

  public Permission requiredPermission() {
    return permission;
  }

  public boolean platform() {
    return this == BILLING_ATTENTION;
  }

  public boolean optional() {
    return this == INTERNAL_INFORMATION
        || this == MEMBER_CREATED
        || this == MEMBER_ACCESS_CHANGED
        || this == OFFER_AVAILABLE;
  }

  public Purpose purpose() {
    return this == OFFER_AVAILABLE ? Purpose.MARKETING : Purpose.SERVICE;
  }

  public String actionPath(UUID id) {
    if (path == null) return null;
    if (this == PAYMENT_FAILED) {
      if (id == null) throw new IllegalArgumentException("This event requires an invoice.");
      return path + id;
    }
    if (!path.endsWith("/")) return path;
    if (id == null) throw new IllegalArgumentException("This event requires a business resource.");
    return path + id + (this == PAYMENT_RECEIVED || this == PAYMENT_FAILED ? "/document" : "");
  }
}
