package com.paytm.exercise.wallet.domain;

import java.util.UUID;

public record TransferCommand(
        TransferType type,
        UUID fromWalletId,
        UUID toWalletId,
        long amountPaise,
        String idempotencyKey
) {
}

