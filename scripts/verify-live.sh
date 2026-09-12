#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:?Usage: $0 BASE_URL ADMIN_TOKEN}"
ADMIN_TOKEN="${2:?Usage: $0 BASE_URL ADMIN_TOKEN}"
PARALLELISM="${WALLET_TEST_PARALLELISM:-6}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }
JQ_BIN="$(command -v jq || true)"
if [ -z "$JQ_BIN" ] && [ -f "$ROOT_DIR/jq.exe" ]; then
  JQ_BIN="$ROOT_DIR/jq.exe"
fi
[ -n "$JQ_BIN" ] || { echo "jq is required (install jq or keep jq.exe at the repository root)" >&2; exit 2; }
jq() { "$JQ_BIN" "$@"; }
case "$PARALLELISM" in
  ''|*[!0-9]*) echo "WALLET_TEST_PARALLELISM must be a positive integer" >&2; exit 2 ;;
esac
[ "$PARALLELISM" -gt 0 ] || { echo "WALLET_TEST_PARALLELISM must be greater than zero" >&2; exit 2; }

curl --fail --silent --show-error "$BASE_URL/actuator/health/readiness" >/dev/null || {
  echo "Application readiness check failed; verify the app and database are running." >&2
  exit 1
}

RUN_ID="$(date +%s)-$$"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

admin_post() {
  curl --fail --silent --show-error -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}$1" -d "$2"
}

create_user() {
  admin_post /admin/users "{\"external_id\":\"$1\"}"
}

wallet_for() {
  curl --fail --silent --show-error -H "Authorization: Bearer $1" -X POST "${BASE_URL}/wallets"
}

fund() {
  admin_post "/admin/wallets/$1/fund" "{\"amount_paise\":$2,\"idempotency_key\":\"$3\"}" >/dev/null
}

ALICE_JSON="$(create_user "burst-alice-${RUN_ID}")"
BOB_JSON="$(create_user "burst-bob-${RUN_ID}")"
FRESH_JSON="$(create_user "burst-fresh-${RUN_ID}")"
ALICE_TOKEN="$(jq -r .bearer_token <<<"$ALICE_JSON")"
BOB_TOKEN="$(jq -r .bearer_token <<<"$BOB_JSON")"
FRESH_TOKEN="$(jq -r .bearer_token <<<"$FRESH_JSON")"
ALICE_WALLET="$(wallet_for "$ALICE_TOKEN" | jq -r .wallet_id)"
BOB_WALLET="$(wallet_for "$BOB_TOKEN" | jq -r .wallet_id)"
fund "$ALICE_WALLET" 500000 "fund-alice-${RUN_ID}"
fund "$BOB_WALLET" 500000 "fund-bob-${RUN_ID}"

export BASE_URL FRESH_TOKEN ALICE_TOKEN BOB_TOKEN ALICE_WALLET BOB_WALLET TMP_DIR RUN_ID

echo "[1/3] concurrent wallet get-or-create"
seq 1 50 | xargs -P "$PARALLELISM" -I{} sh -c 'curl --fail --silent --show-error -H "Authorization: Bearer $FRESH_TOKEN" -X POST "$BASE_URL/wallets" > "$TMP_DIR/wallet-{}"'
WALLET_COUNT="$(jq -r .wallet_id "$TMP_DIR"/wallet-* | sort -u | wc -l | tr -d ' ')"
[[ "$WALLET_COUNT" == "1" ]] || { echo "FAILED: expected one wallet, got \$WALLET_COUNT. Values were: $(jq -r .wallet_id \"$TMP_DIR\"/wallet-* | sort -u | tr \"\\n\" \" \")" >&2; exit 1; }

echo "[2/3] idempotent retry storm"
IDEMPOTENCY_KEY="storm-${RUN_ID}"
export IDEMPOTENCY_KEY
seq 1 30 | xargs -P "$PARALLELISM" -I{} sh -c 'curl --fail --silent --show-error -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" -X POST "$BASE_URL/transfers" -d "{\"from\":\"$ALICE_WALLET\",\"to\":\"$BOB_WALLET\",\"amount_paise\":1000,\"idempotency_key\":\"$IDEMPOTENCY_KEY\"}" > "$TMP_DIR/replay-{}"'
REPLAY_COUNT="$(sort "$TMP_DIR"/replay-* | uniq | wc -l | tr -d ' ')"
[[ "$REPLAY_COUNT" == "1" ]] || { echo "FAILED: replay responses differ" >&2; exit 1; }

echo "[3/3] bidirectional contention and conservation"
BEFORE_A="$(curl --fail --silent --show-error -H "Authorization: Bearer $ALICE_TOKEN" "$BASE_URL/wallets/$ALICE_WALLET" | jq -r .balance_paise)"
BEFORE_B="$(curl --fail --silent --show-error -H "Authorization: Bearer $BOB_TOKEN" "$BASE_URL/wallets/$BOB_WALLET" | jq -r .balance_paise)"
export BEFORE_A BEFORE_B
seq 1 240 | xargs -P "$PARALLELISM" -I{} sh -c '
  index=$1
  if [ $((index % 2)) -eq 0 ]; then
    token="$ALICE_TOKEN"
    from="$ALICE_WALLET"
    to="$BOB_WALLET"
  else
    token="$BOB_TOKEN"
    from="$BOB_WALLET"
    to="$ALICE_WALLET"
  fi
  curl --fail --silent --show-error -H "Authorization: Bearer $token" -H "Content-Type: application/json" -X POST "$BASE_URL/transfers" -d "{\"from\":\"$from\",\"to\":\"$to\",\"amount_paise\":750,\"idempotency_key\":\"contention-$RUN_ID-$index\"}" > /dev/null
' sh {}
AFTER_A="$(curl --fail --silent --show-error -H "Authorization: Bearer $ALICE_TOKEN" "$BASE_URL/wallets/$ALICE_WALLET" | jq -r .balance_paise)"
AFTER_B="$(curl --fail --silent --show-error -H "Authorization: Bearer $BOB_TOKEN" "$BASE_URL/wallets/$BOB_WALLET" | jq -r .balance_paise)"
[[ $((BEFORE_A + BEFORE_B)) -eq $((AFTER_A + AFTER_B)) ]] || { echo "FAILED: conservation broken" >&2; exit 1; }
[[ "$AFTER_A" -ge 0 && "$AFTER_B" -ge 0 ]] || { echo "FAILED: negative balance observed" >&2; exit 1; }

echo "PASS: one wallet, exactly-once retry, conserved concurrent transfers."

