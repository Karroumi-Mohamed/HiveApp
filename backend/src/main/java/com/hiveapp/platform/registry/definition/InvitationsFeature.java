package com.hiveapp.platform.registry.definition;

public final class InvitationsFeature {

    public static final String KEY = "invitations";
    public static final String CODE = "platform." + KEY;

    private InvitationsFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.clientWorkspace(CODE)
                .displayName("Workspace Invitations")
                .description("Client workspace invitation sending, listing, and revocation")
                .sortOrder(50)
                .build();
    }
}
