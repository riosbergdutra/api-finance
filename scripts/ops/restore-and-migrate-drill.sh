#!/usr/bin/env bash
set -Eeuo pipefail

: "${SANITIZED_BACKUP_FILE:?Set SANITIZED_BACKUP_FILE to a sanitized custom-format pg_dump created at Flyway V2}"
[[ -f "$SANITIZED_BACKUP_FILE" ]] || { echo "Backup file not found" >&2; exit 2; }
command -v docker >/dev/null
command -v pg_restore >/dev/null
command -v psql >/dev/null
command -v sha256sum >/dev/null
command -v openssl >/dev/null

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
trap cleanup EXIT INT TERM

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

for attempt in $(seq 1 60); do
  if docker exec "$container" pg_isready -U postgres -d postgres >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec "$container" pg_isready -U postgres -d postgres >/dev/null

export PGPASSWORD="$password"
createdb -h 127.0.0.1 -p "$port" -U postgres finance_restore_drill
pg_restore --exit-on-error --no-owner --no-acl \
  --host=127.0.0.1 --port="$port" --username=postgres \
  --dbname=finance_restore_drill "$SANITIZED_BACKUP_FILE"

version="$(psql -X -A -t -h 127.0.0.1 -p "$port" -U postgres -d finance_restore_drill \
  -c "SELECT COALESCE(MAX(CASE WHEN version ~ '^[0-9]+;")"
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
  -c "SELECT MAX(CASE WHEN version ~ '^[0-9]+;")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END), 0) FROM flyway_schema_history WHERE success = true;")"
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
  -c "SELECT MAX(CASE WHEN version ~ '^[0-9]+;")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END) FROM flyway_schema_history WHERE success = true;")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END), 0) FROM flyway_schema_history WHERE success = true;")"
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
  -c "SELECT MAX(version::int) FROM flyway_schema_history WHERE success = true AND version ~ '^[0-9]+$';")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END) FROM flyway_schema_history WHERE success = true;")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END) FROM flyway_schema_history WHERE success = true;")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
 THEN version::int END), 0) FROM flyway_schema_history WHERE success = true;")"
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
  -c "SELECT MAX(version::int) FROM flyway_schema_history WHERE success = true AND version ~ '^[0-9]+$';")"
[[ "$final_version" == "9" ]] || { echo "Expected Flyway V9 after migration; found '$final_version'" >&2; exit 7; }

unset PGPASSWORD password
echo "PASS: sanitized V2 backup restored and Flyway V3-V9 applied in disposable PostgreSQL."
echo "The temporary container will now be removed; no existing Docker volume or database was touched."
