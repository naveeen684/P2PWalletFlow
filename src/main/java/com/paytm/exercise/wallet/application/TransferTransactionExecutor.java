package com.paytm.exercise.wallet.application;

import com.paytm.exercise.wallet.config.WalletProperties;
import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.DeclineReason;
import com.paytm.exercise.wallet.domain.Money;
import com.paytm.exercise.wallet.domain.RequestFingerprint;
import com.paytm.exercise.wallet.domain.TransferCommand;
import com.paytm.exercise.wallet.domain.TransferRecord;
import com.paytm.exercise.wallet.domain.TransferStatus;
import com.paytm.exercise.wallet.domain.TransferType;
import com.paytm.exercise.wallet.domain.Wallet;
import com.paytm.exercise.wallet.infrastructure.TransferRepository;
import com.paytm.exercise.wallet.infrastructure.WalletRepository;
import com.paytm.exercise.wallet.observability.AfterCommitEvents;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransferTransactionExecutor {
    public static final UUID TREASURY_WALLET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final TransferRepository transfers;
    private final WalletRepository wallets;
    private final AfterCommitEvents events;
    private final WalletProperties properties;

    public TransferTransactionExecutor(TransferRepository transfers, WalletRepository wallets,
                                       AfterCommitEvents events, WalletProperties properties) {
        this.transfers = transfers;
        this.wallets = wallets;
        this.events = events;
        this.properties = properties;
    }

    @Transactional(timeoutString = "${wallet.transaction-timeout-seconds}")
    public TransferRecord execute(TransferCommand command, AuthenticatedPrincipal actor) {
        validate(command);
        String fingerprint = RequestFingerprint.sha256(command.type(), actor.userId(), command.fromWalletId(),
                command.toWalletId(), command.amountPaise());
        TransferRecord pending = new TransferRecord(UUID.randomUUID(), command.idempotencyKey(), actor.userId(), command.type(),
                command.fromWalletId(), command.toWalletId(), command.amountPaise(), fingerprint, TransferStatus.PENDING, null, Instant.now());

        List<Wallet> lockedWallets = wallets.lockPair(command.fromWalletId(), command.toWalletId());
        if (lockedWallets.size() != 2) {
            throw new NotFoundException("Wallet");
        }
        Map<UUID, Wallet> byId = new HashMap<>();
        lockedWallets.forEach(wallet -> byId.put(wallet.id(), wallet));
        Wallet source = byId.get(command.fromWalletId());
        Wallet destination = byId.get(command.toWalletId());
        authorize(command, actor, source);

        if (!transfers.insertPending(pending)) {
            TransferRecord existing = transfers.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Unique idempotency record was not readable"));
            if (!existing.requestHash().equals(fingerprint)) {
                throw new IdempotencyConflictException();
            }
            events.idempotentReplay(existing.id());
            return existing;
        }

        if (source.balancePaise() < command.amountPaise()) {
            return decline(pending, command.type() == TransferType.FUNDING ? DeclineReason.TREASURY_DEPLETED : DeclineReason.INSUFFICIENT_FUNDS);
        }
        if (destination.balancePaise() > properties.maxMoneyPaise() - command.amountPaise()) {
            return decline(pending, DeclineReason.BALANCE_LIMIT_EXCEEDED);
        }
        if (!wallets.conditionalDebit(source.id(), command.amountPaise())) {
            return decline(pending, command.type() == TransferType.FUNDING ? DeclineReason.TREASURY_DEPLETED : DeclineReason.INSUFFICIENT_FUNDS);
        }
        if (!wallets.creditWithinLimit(destination.id(), command.amountPaise(), properties.maxMoneyPaise())) {
            throw new IllegalStateException("Destination credit failed after deterministic balance validation");
        }
        transfers.appendBalancedLedgerEntries(pending.id(), source.id(), destination.id(), command.amountPaise());
        transfers.complete(pending.id());
        TransferRecord completed = transfers.findById(pending.id()).orElseThrow(() -> new IllegalStateException("Completed transfer missing"));
        events.transferCompleted(completed.id(), source.id(), destination.id(), command.amountPaise());
        return completed;
    }

    private TransferRecord decline(TransferRecord pending, DeclineReason reason) {
        transfers.decline(pending.id(), reason);
        TransferRecord declined = transfers.findById(pending.id()).orElseThrow(() -> new IllegalStateException("Declined transfer missing"));
        events.transferDeclined(declined.id(), reason.name());
        return declined;
    }

    private void authorize(TransferCommand command, AuthenticatedPrincipal actor, Wallet source) {
        if (command.type() == TransferType.P2P && !source.userId().equals(actor.userId())) {
            throw new ForbiddenException();
        }
        if (command.type() == TransferType.FUNDING && (!actor.isAdmin() || !source.id().equals(TREASURY_WALLET_ID))) {
            throw new ForbiddenException();
        }
    }

    private void validate(TransferCommand command) {
        if (command.amountPaise() <= 0 || command.amountPaise() > Money.MAX_MONEY_PAISE) {
            throw new ValidationException("amount_paise must be a positive integer within the supported range.");
        }
        if (command.fromWalletId().equals(command.toWalletId())) {
            throw new ValidationException("Source and destination wallets must be different.");
        }
        if (command.idempotencyKey() == null || !command.idempotencyKey().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ValidationException("idempotency_key must contain 1 to 128 URL-safe characters.");
        }
    }
}

