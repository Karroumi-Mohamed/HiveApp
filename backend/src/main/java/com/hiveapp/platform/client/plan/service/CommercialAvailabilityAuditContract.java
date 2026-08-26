package com.hiveapp.platform.client.plan.service;

/** Stable action/resource names shared by the detailed writer and failed-attempt recorder. */
public final class CommercialAvailabilityAuditContract {

    public static final String RESOURCE_TYPE = "COMMERCIAL_AVAILABILITY";
    public static final String PLAN_CHANGE_ACTION =
            "platform.commercial_availability.plan_availability_changed";
    public static final String ADD_ON_CHANGE_ACTION =
            "platform.commercial_availability.add_on_visibility_changed";
    public static final String QUOTA_CHANGE_ACTION =
            "platform.commercial_availability.quota_visibility_changed";
    public static final String PLAN_MUTATION_ACTION =
            "platform.commercial_availability.update_plan_policy";
    public static final String ADD_ON_MUTATION_ACTION =
            "platform.commercial_availability.update_add_on_visibility";
    public static final String QUOTA_MUTATION_ACTION =
            "platform.commercial_availability.update_quota_visibility";

    private CommercialAvailabilityAuditContract() {}
}
