package com.paytm.exercise.wallet.domain;

import java.util.UUID;

public record Wallet(UUID id, UUID userId, long balancePaise) {
}

