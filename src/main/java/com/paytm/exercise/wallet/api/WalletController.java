package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.application.WalletService;
import com.paytm.exercise.wallet.security.CurrentPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> getOrCreate() {
        return ResponseEntity.ok(WalletResponse.from(wallets.getOrCreate(CurrentPrincipal.require())));
    }

    @GetMapping("/{walletId}")
    public WalletResponse get(@PathVariable UUID walletId) {
        return WalletResponse.from(wallets.get(walletId, CurrentPrincipal.require()));
    }
}

