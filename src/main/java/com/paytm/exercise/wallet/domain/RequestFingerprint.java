package com.paytm.exercise.wallet.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class RequestFingerprint {
    private RequestFingerprint() {
    }

    public static String sha256(TransferType type, UUID actorUserId, UUID fromWalletId, UUID toWalletId, long amountPaise) {
        String canonical = String.join("|", "v1", type.name(), actorUserId.toString(), fromWalletId.toString(),
                toWalletId.toString(), Long.toString(amountPaise));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}

