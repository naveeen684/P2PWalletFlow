package com.paytm.exercise.wallet.application;

import org.springframework.http.HttpStatus;

public final class ValidationException extends ApiException {
    public ValidationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", message);
    }
}

