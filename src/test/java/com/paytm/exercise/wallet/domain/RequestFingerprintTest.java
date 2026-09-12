package com.paytm.exercise.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {
    @Test
    void changesWhenAnyMoneyMovementFieldChanges() {
        UUID actor = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        String baseline = RequestFingerprint.sha256(TransferType.P2P, actor, from, to, 100);

        assertThat(RequestFingerprint.sha256(TransferType.P2P, actor, from, to, 100)).isEqualTo(baseline);
        assertThat(RequestFingerprint.sha256(TransferType.P2P, actor, from, to, 101)).isNotEqualTo(baseline);
        assertThat(RequestFingerprint.sha256(TransferType.FUNDING, actor, from, to, 100)).isNotEqualTo(baseline);
        assertThat(RequestFingerprint.sha256(TransferType.P2P, UUID.randomUUID(), from, to, 100)).isNotEqualTo(baseline);
    }
}

