#!/usr/bin/env bash
set -Eeuo pipefail
set +x

: "${SANITIZED_BACKUP_FILE:?Set SANITIZED_BACKUP_FILE to a sanitized custom-format pg_dump created at Flyway V2}"
[[ -f "$SANITIZED_BACKUP_FILE" ]] || { echo "Backup file not found" >&2; exit 2; }
for command_name in docker psql createdb sha256sum openssl; do
  command -v "$command_name" >/dev/null || {
    echo "Required command not found: $command_name" >&2
    exit 2
  }
done

container="financeflow-restore-drill-$(openssl rand -hex 8)"
password="$(openssl rand -hex 24)"
port=""
created=0

if docker ps -a --format '{{.Names}}' | grep -Fxq "$container"; then
  echo "Refusing to reuse existing container name: $container" >&2
  exit 3
fi

cleanup() {
  if [[ "$created" == "1" ]]; then
    docker rm -f "$container" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Backup SHA-256:"
sha256sum "$SANITIZED_BACKUP_FILE"

docker run --detach --rm \
  --name "$container" \
  --env POSTGRES_PASSWORD="$password" \
  --env POSTGRES_DB=postgres \
  --publish 127.0.0.1::5432 \
  postgres:16 >/dev/null
created=1

port="$(docker port "$container" 5432/tcp | sed -n 's/.*://p' | head -n1)"
[[ "$port" =~ ^[0-9]+$ ]] || { echo "Could not resolve isolated PostgreSQL port" >&2; exit 4; }

ready=0
for attempt in $(seq 1 60); do
  if docker exec "$container" pg_isready -U postgres -d postgres >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
[[ "$ready" == "1" ]] || { echo "Disposable PostgreSQL did not become ready" >&2; exit 4; }

export PGPASSWORD="$password"
createdb -h 127.0.0.1 -p "$port" -U postgres finance_restore_drill
docker run --rm \
  --network "container:$container" \
  --volume "$SANITIZED_BACKUP_FILE:/backup.dump:ro" \
  --env PGPASSWORD="$password" \
  postgres:16 pg_restore --exit-on-error --no-owner --no-acl \
    --host=127.0.0.1 --port=5432 --username=postgres \
    --dbname=finance_restore_drill /backup.dump

version="$(psql -X -A -t -h 127.0.0.1 -p "$port" -U postgres -d finance_restore_drill \
  -c "SELECT COALESCE(MAX(CASE WHEN version ~ '^[0-9]+$' THEN version::int END), 0) FROM flyway_schema_history WHERE success = true;")"
[[ "$version" == "2" ]] || {
  echo "Expected a sanitized pre-V3 snapshot (latest successful numeric Flyway version 2); found '$version'. No migration was run." >&2
  exit 5
}

failed="$(psql -X -A -t -h 127.0.0.1 -p "$port" -U postgres -d finance_restore_drill \
  -c "SELECT count(*) FROM flyway_schema_history WHERE success = false;")"
[[ "$failed" == "0" ]] || { echo "Restored Flyway history contains failed migrations" >&2; exit 6; }

echo "Restore succeeded in a disposable PostgreSQL 16 container."
echo "Running Flyway validate + migrate against the isolated restored copy only."

docker run --rm \
  --network "container:$container" \
  --volume "$PWD/src/main/resources/db/migration:/flyway/sql:ro" \
  --env FLYWAY_URL="jdbc:postgresql://127.0.0.1:5432/finance_restore_drill" \
  --env FLYWAY_USER=postgres \
  --env FLYWAY_PASSWORD="$password" \
  --env FLYWAY_LOCATIONS=filesystem:/flyway/sql \
  flyway/flyway:10.20.1 validate

docker run --rm \
  --network "container:$container" \
  --volume "$PWD/src/main/resources/db/migration:/flyway/sql:ro" \
  --env FLYWAY_URL="jdbc:postgresql://127.0.0.1:5432/finance_restore_drill" \
  --env FLYWAY_USER=postgres \
  --env FLYWAY_PASSWORD="$password" \
  --env FLYWAY_LOCATIONS=filesystem:/flyway/sql \
  flyway/flyway:10.20.1 migrate

final_version="$(psql -X -A -t -h 127.0.0.1 -p "$port" -U postgres -d finance_restore_drill \
  -c "SELECT COALESCE(MAX(CASE WHEN version ~ '^[0-9]+$' THEN version::int END), 0) FROM flyway_schema_history WHERE success = true;")"
[[ "$final_version" == "9" ]] || {
  echo "Expected Flyway V9 after migration; found '$final_version'" >&2
  exit 7
}

echo "Checking critical schema and data invariants from migrations V3-V9."
psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$port" -U postgres -d finance_restore_drill <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'transactions'
          AND column_name = 'transfer_destination_applied'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'V5 invariant failed: transactions.transfer_destination_applied missing or nullable';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname = 'idx_subscription_stripe_subscription_unique'
          AND indexdef ILIKE '%UNIQUE%'
    ) THEN
        RAISE EXCEPTION 'V6 invariant failed: unique Stripe subscription index missing';
    END IF;

    IF EXISTS (
        SELECT stripe_subscription_id
        FROM subscription
        WHERE stripe_subscription_id IS NOT NULL
        GROUP BY stripe_subscription_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 invariant failed: duplicate Stripe subscription IDs remain';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'stripe_webhook_events'
    ) THEN
        RAISE EXCEPTION 'V4 invariant failed: stripe_webhook_events table missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'subscription'
          AND column_name = 'stripe_last_event_created'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'subscription'
          AND column_name = 'stripe_last_event_id'
    ) THEN
        RAISE EXCEPTION 'V7 invariant failed: Stripe event ordering columns missing';
    END IF;

    IF to_regclass('account_deletion_outbox') IS NULL
       OR to_regclass('deleted_user_identities') IS NULL THEN
        RAISE EXCEPTION 'V8 invariant failed: account deletion tables missing';
    END IF;

    IF to_regclass('shedlock') IS NULL THEN
        RAISE EXCEPTION 'V9 invariant failed: shedlock table missing';
    END IF;
END
$$;
SQL

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
