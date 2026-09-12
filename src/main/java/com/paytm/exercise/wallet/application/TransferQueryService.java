package com.paytm.exercise.wallet.application;

import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.TransferRecord;
import com.paytm.exercise.wallet.domain.Wallet;
import com.paytm.exercise.wallet.infrastructure.TransferRepository;
import com.paytm.exercise.wallet.infrastructure.WalletRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferQueryService {
    private final TransferRepository transfers;
    private final WalletRepository wallets;

    public TransferQueryService(TransferRepository transfers, WalletRepository wallets) {
        this.transfers = transfers;
        this.wallets = wallets;
    }

    public TransferRecord get(UUID transferId, AuthenticatedPrincipal principal) {
        TransferRecord transfer = transfers.findById(transferId).orElseThrow(() -> new NotFoundException("Transfer"));
        if (principal.isAdmin()) {
            return transfer;
        }
        Wallet from = wallets.findById(transfer.fromWalletId()).orElseThrow(() -> new NotFoundException("Transfer"));
        Wallet to = wallets.findById(transfer.toWalletId()).orElseThrow(() -> new NotFoundException("Transfer"));
        if (!principal.userId().equals(from.userId()) && !principal.userId().equals(to.userId())) {
            throw new NotFoundException("Transfer");
        }
        return transfer;
    }
}

