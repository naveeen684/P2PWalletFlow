package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.domain.Wallet;
import java.util.UUID;

public record WalletResponse(UUID walletId, long balancePaise) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.id(), wallet.balancePaise());
    }
}

