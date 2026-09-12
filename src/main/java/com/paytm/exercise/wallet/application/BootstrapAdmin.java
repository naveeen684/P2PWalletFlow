package com.paytm.exercise.wallet.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final AuthService authService;

    public BootstrapAdmin(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.bootstrapAdmin();
        log.atInfo().addKeyValue("event", "bootstrap_admin_ready").log("Bootstrap admin token is configured");
    }
}

