#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    printf '%s\n' 'Missing .env. Copy .env.example to .env and set the required values.' >&2
    exit 1
fi

set -a
. "$ENV_FILE"
set +a

require_env() {
    variable_name=$1
    eval "variable_value=\${$variable_name-}"
    if [ -z "$variable_value" ]; then
        printf 'Missing required .env variable: %s\n' "$variable_name" >&2
        exit 1
    fi
    unset variable_value
}

for variable_name in \
    SPRING_DATASOURCE_URL \
    SPRING_DATASOURCE_USERNAME \
    SPRING_DATASOURCE_PASSWORD \
    WALLET_TOKEN_PEPPER \
    WALLET_BOOTSTRAP_ADMIN_TOKEN
do
    require_env "$variable_name"
done

export SPRING_PROFILES_ACTIVE=local
exec "$ROOT_DIR/mvnw" spring-boot:run
