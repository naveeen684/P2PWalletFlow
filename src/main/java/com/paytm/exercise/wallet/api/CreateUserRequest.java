package com.paytm.exercise.wallet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}") String externalId
) {
}

