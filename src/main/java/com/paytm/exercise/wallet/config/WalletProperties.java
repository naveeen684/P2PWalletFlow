package com.paytm.exercise.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
        String tokenPepper,
        String bootstrapAdminToken,
        long maxMoneyPaise,
        int transactionTimeoutSeconds
) {
}

