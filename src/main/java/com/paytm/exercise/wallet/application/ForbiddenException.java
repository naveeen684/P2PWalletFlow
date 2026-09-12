package com.paytm.exercise.wallet.application;

import org.springframework.http.HttpStatus;

public final class ForbiddenException extends ApiException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "forbidden", "The authenticated user is not allowed to perform this operation.");
    }
}

