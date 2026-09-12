package com.paytm.exercise.wallet.security;

import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentPrincipal {
    private CurrentPrincipal() {
    }

    public static AuthenticatedPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof WalletPrincipal principal)) {
            throw new IllegalStateException("Authenticated wallet principal is required");
        }
        return principal.toDomain();
    }
}

