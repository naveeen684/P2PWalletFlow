package com.paytm.exercise.wallet.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterCommitEvents {
    private static final Logger log = LoggerFactory.getLogger(AfterCommitEvents.class);
    private final MeterRegistry metrics;

    public AfterCommitEvents(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    public void walletCreated(UUID walletId) {
        afterCommit(() -> log.atInfo().addKeyValue("event", "wallet_created").addKeyValue("wallet_id", walletId).log("Wallet created"));
    }

    public void transferCompleted(UUID transferId, UUID fromWalletId, UUID toWalletId, long amountPaise) {
        afterCommit(() -> {
            metrics.counter("wallet_transfers_completed_total").increment();
            log.atInfo().addKeyValue("event", "transfer_created").addKeyValue("transfer_id", transferId).log("Transfer created");
            log.atInfo().addKeyValue("event", "wallet_debited").addKeyValue("transfer_id", transferId)
                    .addKeyValue("wallet_id", fromWalletId).addKeyValue("amount_paise", amountPaise).log("Wallet debited");
            log.atInfo().addKeyValue("event", "wallet_credited").addKeyValue("transfer_id", transferId)
                    .addKeyValue("wallet_id", toWalletId).addKeyValue("amount_paise", amountPaise).log("Wallet credited");
        });
    }

    public void transferDeclined(UUID transferId, String reason) {
        afterCommit(() -> {
            metrics.counter("wallet_transfers_declined_total", "reason", reason).increment();
            if ("INSUFFICIENT_FUNDS".equals(reason)) {
                metrics.counter("wallet_transfers_declined_insufficient_funds_total").increment();
            }
            log.atInfo().addKeyValue("event", "transfer_declined").addKeyValue("transfer_id", transferId)
                    .addKeyValue("reason", reason).log("Transfer declined");
        });
    }

    public void idempotentReplay(UUID transferId) {
        afterCommit(() -> {
            metrics.counter("wallet_transfers_idempotent_replays_total").increment();
            log.atInfo().addKeyValue("event", "idempotent_replay_hit").addKeyValue("transfer_id", transferId).log("Idempotent replay returned");
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

