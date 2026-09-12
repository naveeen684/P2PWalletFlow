package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.domain.TransferRecord;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        String status,
        String type,
        UUID from,
        UUID to,
        long amountPaise,
        String declineReason,
        Instant createdAt
) {
    public static TransferResponse from(TransferRecord transfer) {
        return new TransferResponse(transfer.id(), transfer.status().name(), transfer.transferType().name(),
                transfer.fromWalletId(), transfer.toWalletId(), transfer.amountPaise(),
                transfer.declineReason() == null ? null : transfer.declineReason().name(), transfer.createdAt());
    }
}

