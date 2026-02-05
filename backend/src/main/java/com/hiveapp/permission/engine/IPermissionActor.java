package com.hiveapp.permission.engine;

import java.util.UUID;

/**
 * Polymorphic actor interface for the Permission Engine.
 *
 * Both Member and AdminUser implement this interface so the engine
 * can generically resolve permissions for any actor type.
 */
public interface IPermissionActor {

    UUID getId();

    UUID getActorAccountId();

    boolean isActorActive();
}
