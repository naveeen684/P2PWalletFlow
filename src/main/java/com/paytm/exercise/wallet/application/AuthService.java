package com.paytm.exercise.wallet.application;

import com.paytm.exercise.wallet.config.WalletProperties;
import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.Role;
import com.paytm.exercise.wallet.infrastructure.UserRepository;
import com.paytm.exercise.wallet.infrastructure.UserRepository.TokenAccount;
import com.paytm.exercise.wallet.infrastructure.UserRepository.UserAccount;
import com.paytm.exercise.wallet.security.TokenHasher;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final TokenHasher tokenHasher;
    private final WalletProperties properties;

    public AuthService(UserRepository users, TokenHasher tokenHasher, WalletProperties properties) {
        this.users = users;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
    }

    public AuthenticatedPrincipal authenticate(String rawToken) {
        TokenAccount token = users.findActiveToken(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_token", "A valid bearer token is required."));
        return new AuthenticatedPrincipal(token.userId(), token.externalId(), token.role());
    }

    @Transactional
    public ProvisionedUser createUser(String externalId) {
        if (users.findByExternalId(externalId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "user_exists", "A user with that external ID already exists.");
        }
        UserAccount user = users.create(externalId);
        String token = generateToken();
        users.createToken(user.id(), tokenHasher.hash(token), Role.USER, null);
        return new ProvisionedUser(user.id(), user.externalId(), token);
    }

    @Transactional
    public void bootstrapAdmin() {
        String externalId = "BOOTSTRAP_ADMIN";
        if (users.findByExternalId(externalId).isEmpty()) {
            UserAccount admin = users.create(externalId);
            users.createToken(admin.id(), tokenHasher.hash(properties.bootstrapAdminToken()), Role.ADMIN, null);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ProvisionedUser(java.util.UUID id, String externalId, String bearerToken) {
    }
}

