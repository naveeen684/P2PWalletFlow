package com.paytm.exercise.wallet.application;

import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.Wallet;
import com.paytm.exercise.wallet.infrastructure.WalletRepository;
import com.paytm.exercise.wallet.observability.AfterCommitEvents;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final AfterCommitEvents events;

    public WalletService(WalletRepository wallets, AfterCommitEvents events) {
        this.wallets = wallets;
        this.events = events;
    }

    @Transactional
    public Wallet getOrCreate(AuthenticatedPrincipal principal) {
        WalletRepository.WalletCreation creation = wallets.getOrCreate(principal.userId());
        if (creation.created()) {
            events.walletCreated(creation.wallet().id());
        }
        return creation.wallet();
    }

    public Wallet get(UUID walletId, AuthenticatedPrincipal principal) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet"));
        if (!principal.isAdmin() && !wallet.userId().equals(principal.userId())) {
            throw new NotFoundException("Wallet");
        }
        return wallet;
    }
}

