package com.paytm.exercise.wallet.application;

import org.springframework.http.HttpStatus;

public final class IdempotencyConflictException extends ApiException {
    public IdempotencyConflictException() {
        super(HttpStatus.CONFLICT, "idempotency_conflict", "The idempotency key was already used for a different request.");
    }
}

