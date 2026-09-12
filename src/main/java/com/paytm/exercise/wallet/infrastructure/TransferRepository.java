package com.paytm.exercise.wallet.infrastructure;

import com.paytm.exercise.wallet.domain.DeclineReason;
import com.paytm.exercise.wallet.domain.TransferRecord;
import com.paytm.exercise.wallet.domain.TransferStatus;
import com.paytm.exercise.wallet.domain.TransferType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {
    private static final RowMapper<TransferRecord> TRANSFER_MAPPER = (rs, rowNum) -> new TransferRecord(
            rs.getObject("id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getObject("actor_user_id", UUID.class),
            TransferType.valueOf(rs.getString("transfer_type")),
            rs.getObject("from_wallet_id", UUID.class),
            rs.getObject("to_wallet_id", UUID.class),
            rs.getLong("amount_paise"),
            rs.getString("request_hash"),
            TransferStatus.valueOf(rs.getString("status")),
            rs.getString("decline_reason") == null ? null : DeclineReason.valueOf(rs.getString("decline_reason")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public TransferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertPending(TransferRecord transfer) {
        return jdbc.update("""
                INSERT INTO transfers (id, idempotency_key, actor_user_id, transfer_type, from_wallet_id, to_wallet_id,
                                       amount_paise, request_hash, status, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, transfer.id(), transfer.idempotencyKey(), transfer.actorUserId(), transfer.transferType().name(),
                transfer.fromWalletId(), transfer.toWalletId(), transfer.amountPaise(), transfer.requestHash(),
                Timestamp.from(transfer.createdAt()), Timestamp.from(transfer.createdAt())) == 1;
    }

    public Optional<TransferRecord> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM transfers WHERE idempotency_key = ?", TRANSFER_MAPPER, key).stream().findFirst();
    }

    public Optional<TransferRecord> findById(UUID id) {
        return jdbc.query("SELECT * FROM transfers WHERE id = ?", TRANSFER_MAPPER, id).stream().findFirst();
    }

    public void complete(UUID id) {
        jdbc.update("UPDATE transfers SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void decline(UUID id, DeclineReason reason) {
        jdbc.update("UPDATE transfers SET status = 'DECLINED', decline_reason = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?",
                reason.name(), id);
    }

    public void appendBalancedLedgerEntries(UUID transferId, UUID fromWalletId, UUID toWalletId, long amountPaise) {
        jdbc.update("INSERT INTO ledger_entries (id, transfer_id, wallet_id, direction, amount_paise) VALUES (?, ?, ?, 'DEBIT', ?)",
                UUID.randomUUID(), transferId, fromWalletId, amountPaise);
        jdbc.update("INSERT INTO ledger_entries (id, transfer_id, wallet_id, direction, amount_paise) VALUES (?, ?, ?, 'CREDIT', ?)",
                UUID.randomUUID(), transferId, toWalletId, amountPaise);
    }

    public long countLedgerEntries(UUID transferId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", Long.class, transferId);
        return count == null ? 0 : count;
    }
}

