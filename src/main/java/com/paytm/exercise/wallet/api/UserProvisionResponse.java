package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.application.AuthService.ProvisionedUser;
import java.util.UUID;

public record UserProvisionResponse(UUID userId, String externalId, String bearerToken) {
    public static UserProvisionResponse from(ProvisionedUser user) {
        return new UserProvisionResponse(user.id(), user.externalId(), user.bearerToken());
    }
}

