package com.paytm.exercise.wallet.application;

import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.TransferCommand;
import com.paytm.exercise.wallet.domain.TransferRecord;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private static final int MAX_ATTEMPTS = 10;
    private final TransferTransactionExecutor executor;

    public TransferService(TransferTransactionExecutor executor) {
        this.executor = executor;
    }

    public TransferRecord submit(TransferCommand command, AuthenticatedPrincipal actor) {
        for (int attempt = 1; ; attempt++) {
            try {
                return executor.execute(command, actor);
            } catch (DataAccessException exception) {
                if (attempt >= MAX_ATTEMPTS || !retryable(exception)) {
                    throw exception;
                }
                pause(attempt);
            }
        }
    }

    private boolean retryable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                return "40P01".equals(state) || "40001".equals(state) || "55P03".equals(state);
            }
            current = current.getCause();
        }
        return false;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep((1L << Math.min(attempt, 6)) * 25L + ThreadLocalRandom.current().nextLong(50L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying transient database failure", interrupted);
        }
    }
}

