@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared::security", "shared::event", "identity::event", "shared :: exception", "permission"}
)
@Permission(key = "account", description = "Account Management")
package com.hiveapp.platform.client.account;

import com.hiveapp.permission.Permission;
