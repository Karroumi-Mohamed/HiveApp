package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.entity.User;

public record ClientAuthenticationResult(User user, boolean passwordChangeRequired) {
}
