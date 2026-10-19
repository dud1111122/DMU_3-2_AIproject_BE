#!/usr/bin/env bash
# Drops and recreates a database in the local PostgreSQL container and applies db/schema.sql.
#
#   scripts/reset-db.sh <dbname>
#
# Env overrides: PG_CONTAINER (default dshorts-postgres), PG_USER (default dshorts).
# Uses `docker exec ... psql` so no local psql install is needed. Refuses to touch template/system DBs.
set -euo pipefail

DB_NAME="${1:-}"
CONTAINER="${PG_CONTAINER:-dshorts-postgres}"
PG_USER="${PG_USER:-dshorts}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA="$SCRIPT_DIR/../src/main/resources/db/schema.sql"

if [[ -z "$DB_NAME" ]]; then
  echo "usage: $0 <dbname>" >&2
  exit 1
fi
if [[ ! "$DB_NAME" =~ ^[a-z][a-z0-9_]{0,62}$ ]]; then
  echo "invalid database name: $DB_NAME (use lowercase letters, digits, underscore)" >&2
  exit 1
fi
case "$DB_NAME" in
  postgres|template0|template1)
    echo "refusing to reset system database: $DB_NAME" >&2
    exit 1
    ;;
esac

psql_admin() {
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -q -U "$PG_USER" -d postgres "$@"
}

echo "Resetting database '$DB_NAME' in container '$CONTAINER'..."
psql_admin -c "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE);"
psql_admin -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$PG_USER\";"
docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -q -U "$PG_USER" -d "$DB_NAME" < "$SCHEMA"

TABLES=$(docker exec -i "$CONTAINER" psql -tA -U "$PG_USER" -d "$DB_NAME" \
  -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")
echo "Done: $TABLES tables. JDBC URL: jdbc:postgresql://localhost:5432/$DB_NAME"
