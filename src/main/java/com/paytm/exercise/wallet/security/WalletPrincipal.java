package com.paytm.exercise.wallet.security;

import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import java.util.UUID;

public record WalletPrincipal(UUID userId, String externalId, boolean admin) {
    public AuthenticatedPrincipal toDomain() {
        return new AuthenticatedPrincipal(userId, externalId, admin
                ? com.paytm.exercise.wallet.domain.Role.ADMIN
                : com.paytm.exercise.wallet.domain.Role.USER);
    }
}

