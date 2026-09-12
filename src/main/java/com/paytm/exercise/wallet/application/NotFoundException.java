package com.paytm.exercise.wallet.application;

import org.springframework.http.HttpStatus;

public final class NotFoundException extends ApiException {
    public NotFoundException(String resource) {
        super(HttpStatus.NOT_FOUND, "not_found", resource + " was not found.");
    }
}

