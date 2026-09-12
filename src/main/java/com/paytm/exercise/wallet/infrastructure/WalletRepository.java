package com.paytm.exercise.wallet.infrastructure;

import com.paytm.exercise.wallet.domain.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WalletRepository {
    private static final RowMapper<Wallet> WALLET_MAPPER = (rs, rowNum) -> new Wallet(
            rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getLong("balance_paise"));
    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WalletCreation getOrCreate(UUID userId) {
        UUID id = UUID.randomUUID();
        boolean created = jdbc.update("INSERT INTO wallets (id, user_id, balance_paise) VALUES (?, ?, 0) ON CONFLICT (user_id) DO NOTHING",
                id, userId) == 1;
        Wallet wallet = findByUserId(userId).orElseThrow(() -> new IllegalStateException("Wallet insert/select invariant failed"));
        return new WalletCreation(wallet, created);
    }

    public Optional<Wallet> findById(UUID id) {
        return jdbc.query("SELECT id, user_id, balance_paise FROM wallets WHERE id = ?", WALLET_MAPPER, id).stream().findFirst();
    }

    public Optional<Wallet> findByUserId(UUID userId) {
        return jdbc.query("SELECT id, user_id, balance_paise FROM wallets WHERE user_id = ?", WALLET_MAPPER, userId).stream().findFirst();
    }

    public List<Wallet> lockPair(UUID first, UUID second) {
        UUID lower = first.compareTo(second) < 0 ? first : second;
        UUID upper = first.compareTo(second) < 0 ? second : first;
        List<Wallet> locked = new ArrayList<>(2);
        locked.addAll(jdbc.query("""
            SELECT id, user_id, balance_paise
            FROM wallets
            WHERE id = ?
            FOR UPDATE
            """, WALLET_MAPPER, lower));
        locked.addAll(jdbc.query("""
            SELECT id, user_id, balance_paise
            FROM wallets
            WHERE id = ?
            FOR UPDATE
            """, WALLET_MAPPER, upper));
        return locked;
    }

    public boolean conditionalDebit(UUID walletId, long amountPaise) {
        return jdbc.update("""
                UPDATE wallets SET balance_paise = balance_paise - ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND balance_paise >= ?
                """, amountPaise, walletId, amountPaise) == 1;
    }

    public boolean creditWithinLimit(UUID walletId, long amountPaise, long maximumBalance) {
        return jdbc.update("""
                UPDATE wallets SET balance_paise = balance_paise + ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND balance_paise <= ?
                """, amountPaise, walletId, maximumBalance - amountPaise) == 1;
    }

    public record WalletCreation(Wallet wallet, boolean created) {
    }
}

