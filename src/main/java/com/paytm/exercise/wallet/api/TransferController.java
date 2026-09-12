package com.paytm.exercise.wallet.api;

import com.paytm.exercise.wallet.application.TransferQueryService;
import com.paytm.exercise.wallet.application.TransferService;
import com.paytm.exercise.wallet.domain.TransferCommand;
import com.paytm.exercise.wallet.domain.TransferType;
import com.paytm.exercise.wallet.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transfers;
    private final TransferQueryService queries;

    public TransferController(TransferService transfers, TransferQueryService queries) {
        this.transfers = transfers;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
        var command = new TransferCommand(TransferType.P2P, request.from(), request.to(), request.amountPaise(), request.idempotencyKey());
        return ResponseEntity.ok(TransferResponse.from(transfers.submit(command, CurrentPrincipal.require())));
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return TransferResponse.from(queries.get(transferId, CurrentPrincipal.require()));
    }
}

