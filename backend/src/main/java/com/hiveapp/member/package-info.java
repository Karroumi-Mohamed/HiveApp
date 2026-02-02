@org.springframework.modulith.ApplicationModule(
    displayName = "Member",
    allowedDependencies = {"shared", "identity", "account", "role", "plan", "subscription", "permission"}
)
package com.hiveapp.member;
