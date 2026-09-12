package com.paytm.exercise.wallet.domain;

import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, String externalId, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}

