package com.hiveapp.platform.client.account.service;

import java.util.UUID;

public interface WorkspaceProvisioningService {

    /**
     * Provisions a full workspace for a newly self-registered user:
     * creates the Account, sets the owner Member, and assigns the FREE subscription.
     * Must NOT be called for invite-accepted users — they join an existing workspace.
     */
    void provision(UUID userId, String email);
}
