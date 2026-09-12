# Wallet & P2P transfer service

An API-only Java 25/PostgreSQL service designed to keep balances correct under concurrent requests. Money is always integer paise. The service is intentionally small, but its core movement is database-transactional, ledgered, idempotent, and horizontally safe.

## Run locally (with Docker)

1. Copy `.env.example` to `.env` and replace the three secret values with long random strings.
2. Run `docker compose up --build`.
3. Wait for `GET http://localhost:8080/actuator/health/readiness` to return `UP`.

## Run locally (without Docker)

If you don't have Docker (or virtualization is disabled), you can run the service directly using Java and Maven:
1. Ensure you have **PostgreSQL**, **Java 21**, and **Maven** installed.
2. In PostgreSQL, create a database named `wallet` with a local user and password.
3. Add all required variables to `.env`, then use the launcher for your shell:
   ```bash
  # macOS/Linux/Git Bash
  sh scripts/run-local.sh
  # bash scripts/run-local.sh also works
   ```

    On Windows PowerShell, run `.\scripts\run-local.ps1` to load `.env` without printing its values.

    Local runs also write JSON logs to `logs/wallet-transfer-service.log` and keep the same logs in the terminal. Rotated logs are kept for seven days.

## Test the API

### Interviewer access

Do not share `WALLET_BOOTSTRAP_ADMIN_TOKEN` with an interviewer. It can create users and fund wallets. Instead, create a dedicated temporary user and share only that user's bearer token.

Run this locally as the operator, replacing the placeholders with values kept outside the repository:

```bash
ADMIN_TOKEN='<admin-token-kept-private>'
BASE_URL='http://localhost:8080'
USER_JSON=$(curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"external_id":"interviewer-demo"}' "$BASE_URL/admin/users")
INTERVIEWER_TOKEN=$(jq -r .bearer_token <<<"$USER_JSON")
printf '%s\n' "$INTERVIEWER_TOKEN"
```

The API returns the user token once in `bearer_token`. Send that token to the interviewer through an approved private channel, or run the checks yourself while screen sharing. Do not commit it, put it in this README, or send the admin token. This project does not send email automatically; an email containing a bearer token is not a secure delivery mechanism unless your organization explicitly approves it. Use an isolated database for demos and rotate or discard the demo credentials afterward.

The interviewer can use the user token for `POST /wallets`, `GET /wallets/{walletId}`, and transfers owned by that user. Admin provisioning and funding remain operator-only.

### 1. Interactive Swagger UI
The easiest way to explore and test the API is via the built-in OpenAPI Swagger UI. Once the application is running, navigate to:
👉 **http://localhost:8080/swagger-ui.html**
*(Click "Authorize" and enter your Bearer token to test secured endpoints).*

### 2. Concurrency Burst Test (Automated Verification)
To mathematically prove the application's correctness under extreme load (no race conditions, exact idempotency, and strict conservation of money), run the included bash script. It requires `curl`, `jq`, and `xargs`:

```bash
bash scripts/verify-live.sh http://localhost:8080 "your-admin-token-here"
```

The script defaults to six concurrent clients to match the free Neon connection limit. Set `WALLET_TEST_PARALLELISM` explicitly only when the target database supports more connections.

The default run validates the required workload of 50 wallet requests, 30 idempotent retries, and 240 bidirectional transfers while keeping concurrency within the free-tier database connection budget. For a stricter 32-client contention run, use `WALLET_TEST_PARALLELISM=32`; production-sized database resources are required for unrestricted load. The correctness workload is validated against the deployed service, but this free-tier setup is not a production load or p99 performance certification.

### 3. Manual Verification (cURL)
The bootstrap admin bearer token is the `WALLET_BOOTSTRAP_ADMIN_TOKEN` you configured. Create a user, save the one-time returned token, then create that user's wallet:

```bash
ADMIN_TOKEN='your-admin-token'
USER=$(curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"external_id":"alice"}' http://localhost:8080/admin/users)
ALICE_TOKEN=$(jq -r .bearer_token <<<"$USER")
ALICE_WALLET=$(curl -sS -X POST -H "Authorization: Bearer $ALICE_TOKEN" http://localhost:8080/wallets | jq -r .wallet_id)
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount_paise":100000,"idempotency_key":"fund-alice-1"}' \
  "http://localhost:8080/admin/wallets/$ALICE_WALLET/fund"
```

## API behavior

`POST /wallets` gets or creates exactly one wallet for the bearer-token user. `POST /transfers` requires `from`, `to`, `amount_paise`, and `idempotency_key`; the caller must own `from`. Repeating an identical key and payload returns the original `200` JSON. Reusing a key with a different actor or payload returns `409 Conflict`. A declined transfer is a final `200` response with `status: DECLINED`, never a partial debit.

The admin-only user and funding endpoints exist only to provision isolated live-test accounts. Funding uses a finite system treasury through the same transfer transaction, never a naked balance increment.

See [the design record](docs/decision-record.md) for architectural choices.

## Observability

This application is built for production observability:
* **Structured Logs:** All outputs are formatted as structured JSON containing an `X-Correlation-ID` to trace requests perfectly across distributed systems (tokens are securely excluded from logs).
* **Prometheus Metrics:** Standard HTTP metrics and custom domain metrics are exposed at `GET /actuator/prometheus`. 
  * Look for `wallet_transfers_created_total`, `wallet_transfers_declined_total`, and `wallet_idempotent_replays_total` to track business health in real-time.

## Deploy to Render and Neon

Live deployment:

- API base URL: https://p2pwalletflow.onrender.com
- Swagger UI: https://p2pwalletflow.onrender.com/swagger-ui.html
- OpenAPI JSON: https://p2pwalletflow.onrender.com/v3/api-docs
- Readiness: https://p2pwalletflow.onrender.com/actuator/health/readiness
- Prometheus metrics: https://p2pwalletflow.onrender.com/actuator/prometheus

1. Create a free Neon PostgreSQL project and copy its TLS connection values.
2. Push this repository to GitHub, create a Render Blueprint from `render.yaml`, and select the Docker runtime.
3. Add the datasource environment variables in Render. The application and Flyway migrations use the same datasource connection.
   ```text
   SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require
   SPRING_DATASOURCE_USERNAME=<neon-role>
   WALLET_TOKEN_PEPPER=<long-random-secret>
   WALLET_BOOTSTRAP_ADMIN_TOKEN=<long-random-admin-token>
   ```
  Set the datasource password through Render's secret environment settings; do not put its value in this file. Render secrets are never committed to Git.
4. Confirm the Render health check, exercise `/actuator/prometheus`, run the live burst script, and record the structured log stream during the burst.

Render Free and Neon Free are suitable for the exercise but are not an HA production environment. Do not publish the bootstrap admin token or user tokens in the repository.
