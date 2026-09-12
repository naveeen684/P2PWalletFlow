package com.paytm.exercise.wallet.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import com.paytm.exercise.wallet.config.WalletProperties;

@Component
public class TokenHasher {
    private final byte[] pepper;

    public TokenHasher(WalletProperties properties) {
        this.pepper = properties.tokenPepper().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is required by the Java runtime", exception);
        }
    }
}

