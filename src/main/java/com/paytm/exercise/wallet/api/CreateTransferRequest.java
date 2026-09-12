package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.domain.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID from,
        @NotNull UUID to,
        @NotNull @Min(1) @Max(Money.MAX_MONEY_PAISE) Long amountPaise,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String idempotencyKey
) {
}

