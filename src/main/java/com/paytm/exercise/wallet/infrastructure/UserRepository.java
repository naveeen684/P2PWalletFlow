package com.paytm.exercise.wallet.infrastructure;

import com.paytm.exercise.wallet.domain.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private static final RowMapper<UserAccount> USER_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getObject("id", UUID.class), rs.getString("external_id"));
    private static final RowMapper<TokenAccount> TOKEN_MAPPER = (rs, rowNum) -> new TokenAccount(
            rs.getObject("user_id", UUID.class), rs.getString("external_id"), Role.valueOf(rs.getString("role")));
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByExternalId(String externalId) {
        return jdbc.query("SELECT id, external_id FROM users WHERE external_id = ?", USER_MAPPER, externalId).stream().findFirst();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbc.query("SELECT id, external_id FROM users WHERE id = ?", USER_MAPPER, id).stream().findFirst();
    }

    public UserAccount create(String externalId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, external_id) VALUES (?, ?)", id, externalId);
        return new UserAccount(id, externalId);
    }

    public void createToken(UUID userId, String tokenHash, Role role, Instant expiresAt) {
        jdbc.update("INSERT INTO api_tokens (id, token_hash, user_id, role, expires_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tokenHash, userId, role.name(), expiresAt);
    }

    public Optional<TokenAccount> findActiveToken(String tokenHash) {
        return jdbc.query("""
                SELECT t.user_id, u.external_id, t.role
                FROM api_tokens t JOIN users u ON u.id = t.user_id
                WHERE t.token_hash = ? AND t.active = TRUE AND (t.expires_at IS NULL OR t.expires_at > CURRENT_TIMESTAMP)
                """, TOKEN_MAPPER, tokenHash).stream().findFirst();
    }

    public record UserAccount(UUID id, String externalId) {
    }

    public record TokenAccount(UUID userId, String externalId, Role role) {
    }
}

