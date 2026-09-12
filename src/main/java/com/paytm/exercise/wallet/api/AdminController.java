package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.application.AuthService;
import com.paytm.exercise.wallet.application.TransferService;
import com.paytm.exercise.wallet.application.TransferTransactionExecutor;
import com.paytm.exercise.wallet.domain.TransferCommand;
import com.paytm.exercise.wallet.domain.TransferType;
import com.paytm.exercise.wallet.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AuthService authService;
    private final TransferService transfers;

    public AdminController(AuthService authService, TransferService transfers) {
        this.authService = authService;
        this.transfers = transfers;
    }

    @PostMapping("/users")
    public ResponseEntity<UserProvisionResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(UserProvisionResponse.from(authService.createUser(request.externalId())));
    }

    @PostMapping("/wallets/{walletId}/fund")
    public ResponseEntity<TransferResponse> fund(@PathVariable UUID walletId, @Valid @RequestBody FundWalletRequest request) {
        var command = new TransferCommand(TransferType.FUNDING, TransferTransactionExecutor.TREASURY_WALLET_ID, walletId,
                request.amountPaise(), request.idempotencyKey());
        return ResponseEntity.ok(TransferResponse.from(transfers.submit(command, CurrentPrincipal.require())));
    }
}

