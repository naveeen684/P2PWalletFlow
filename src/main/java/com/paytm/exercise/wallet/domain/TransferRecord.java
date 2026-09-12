package com.paytm.exercise.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record TransferRecord(
        UUID id,
        String idempotencyKey,
        UUID actorUserId,
        TransferType transferType,
        UUID fromWalletId,
        UUID toWalletId,
        long amountPaise,
        String requestHash,
        TransferStatus status,
        DeclineReason declineReason,
        Instant createdAt
) {
}

