package com.paytm.exercise.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paytm.exercise.wallet.application.AuthService;
import com.paytm.exercise.wallet.application.IdempotencyConflictException;
import com.paytm.exercise.wallet.application.TransferService;
import com.paytm.exercise.wallet.application.TransferTransactionExecutor;
import com.paytm.exercise.wallet.application.WalletService;
import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import com.paytm.exercise.wallet.domain.Role;
import com.paytm.exercise.wallet.domain.TransferCommand;
import com.paytm.exercise.wallet.domain.TransferRecord;
import com.paytm.exercise.wallet.domain.TransferStatus;
import com.paytm.exercise.wallet.domain.TransferType;
import com.paytm.exercise.wallet.domain.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "WALLET_TOKEN_PEPPER=test-pepper",
        "WALLET_BOOTSTRAP_ADMIN_TOKEN=test-admin",
        "spring.datasource.hikari.maximum-pool-size=8"
})
@Testcontainers(disabledWithoutDocker = true)
class WalletConcurrencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AuthService authService;
    @Autowired private WalletService walletService;
    @Autowired private TransferService transferService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM ledger_entries");
        jdbc.update("DELETE FROM transfers");
        jdbc.update("DELETE FROM api_tokens WHERE user_id NOT IN (SELECT id FROM users WHERE external_id IN ('SYSTEM_TREASURY', 'BOOTSTRAP_ADMIN'))");
        jdbc.update("DELETE FROM wallets WHERE id <> ?", TransferTransactionExecutor.TREASURY_WALLET_ID);
        jdbc.update("DELETE FROM users WHERE external_id NOT IN ('SYSTEM_TREASURY', 'BOOTSTRAP_ADMIN')");
    }

    @Test
    void concurrentGetOrCreateReturnsExactlyOneWallet() throws Exception {
        AuthService.ProvisionedUser user = authService.createUser("parallel-wallet-user");
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.id(), user.externalId(), Role.USER);

        List<Wallet> results = invokeConcurrently(50, () -> walletService.getOrCreate(principal));

        assertThat(results).extracting(Wallet::id).containsOnly(results.getFirst().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, user.id())).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyAppliesExactlyOnceAndDifferentBodyConflicts() throws Exception {
        TestUsers users = fundedUsers();
        String key = "retry-storm-" + UUID.randomUUID();
        TransferCommand command = new TransferCommand(TransferType.P2P, users.aliceWallet.id(), users.bobWallet.id(), 1_000, key);

        List<TransferRecord> results = invokeConcurrently(30, () -> transferService.submit(command, users.alice));

        assertThat(results).extracting(TransferRecord::id).containsOnly(results.getFirst().id());
        assertThat(results).extracting(TransferRecord::status).containsOnly(TransferStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?", Integer.class, results.getFirst().id())).isEqualTo(2);
        assertThat(walletService.get(users.aliceWallet.id(), users.alice).balancePaise()).isEqualTo(99_000);
        assertThat(walletService.get(users.bobWallet.id(), users.bob).balancePaise()).isEqualTo(101_000);

        TransferCommand changed = new TransferCommand(TransferType.P2P, users.aliceWallet.id(), users.bobWallet.id(), 1_001, key);
        assertThatThrownBy(() -> transferService.submit(changed, users.alice)).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void bidirectionalContentionConservesBalancesAndNeverOverdrafts() throws Exception {
        TestUsers users = fundedUsers();
        long before = users.aliceWallet.balancePaise() + users.bobWallet.balancePaise();
        List<Callable<TransferRecord>> calls = new ArrayList<>();
        for (int i = 0; i < 240; i++) {
            boolean forward = i % 2 == 0;
            calls.add(() -> transferService.submit(new TransferCommand(TransferType.P2P,
                    forward ? users.aliceWallet.id() : users.bobWallet.id(),
                    forward ? users.bobWallet.id() : users.aliceWallet.id(),
                    750, "contention-" + UUID.randomUUID()), forward ? users.alice : users.bob));
        }
        invokeConcurrently(calls);

        long aliceBalance = walletService.get(users.aliceWallet.id(), users.alice).balancePaise();
        long bobBalance = walletService.get(users.bobWallet.id(), users.bob).balancePaise();
        assertThat(aliceBalance).isGreaterThanOrEqualTo(0);
        assertThat(bobBalance).isGreaterThanOrEqualTo(0);
        assertThat(aliceBalance + bobBalance).isEqualTo(before);
    }

    private TestUsers fundedUsers() {
        AuthenticatedPrincipal admin = authService.authenticate("test-admin");
        AuthService.ProvisionedUser aliceUser = authService.createUser("alice-" + UUID.randomUUID());
        AuthService.ProvisionedUser bobUser = authService.createUser("bob-" + UUID.randomUUID());
        AuthenticatedPrincipal alice = new AuthenticatedPrincipal(aliceUser.id(), aliceUser.externalId(), Role.USER);
        AuthenticatedPrincipal bob = new AuthenticatedPrincipal(bobUser.id(), bobUser.externalId(), Role.USER);
        Wallet aliceWallet = walletService.getOrCreate(alice);
        Wallet bobWallet = walletService.getOrCreate(bob);
        transferService.submit(new TransferCommand(TransferType.FUNDING, TransferTransactionExecutor.TREASURY_WALLET_ID,
                aliceWallet.id(), 100_000, "fund-alice-" + UUID.randomUUID()), admin);
        transferService.submit(new TransferCommand(TransferType.FUNDING, TransferTransactionExecutor.TREASURY_WALLET_ID,
                bobWallet.id(), 100_000, "fund-bob-" + UUID.randomUUID()), admin);
        return new TestUsers(alice, bob, walletService.get(aliceWallet.id(), alice), walletService.get(bobWallet.id(), bob));
    }

    private <T> List<T> invokeConcurrently(int count, Callable<T> callable) throws Exception {
        List<Callable<T>> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            calls.add(callable);
        }
        return invokeConcurrently(calls);
    }

    private <T> List<T> invokeConcurrently(List<Callable<T>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(calls.size(), 32));
        try {
            List<Future<T>> results = executor.invokeAll(calls);
            List<T> values = new ArrayList<>();
            for (Future<T> result : results) {
                values.add(result.get());
            }
            return values;
        } finally {
            executor.shutdownNow();
        }
    }

    private record TestUsers(AuthenticatedPrincipal alice, AuthenticatedPrincipal bob, Wallet aliceWallet, Wallet bobWallet) {
    }
}
