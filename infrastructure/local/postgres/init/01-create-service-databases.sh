#!/bin/sh
set -eu

create_service_database() {
  db_name="$1"
  db_user="$2"
  db_password="$3"

  psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=db_name="$db_name" \
    --set=db_user="$db_user" \
    --set=db_password="$db_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')
\gexec
SQL

  psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=db_name="$db_name" \
    --set=db_user="$db_user" <<'SQL'
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db_name')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db_name', :'db_user')
\gexec
SQL
}

create_service_database "$CATALOG_DB_NAME" "$CATALOG_DB_USER" "$CATALOG_DB_PASSWORD"
create_service_database "$ORDER_DB_NAME" "$ORDER_DB_USER" "$ORDER_DB_PASSWORD"
create_service_database "$INVENTORY_DB_NAME" "$INVENTORY_DB_USER" "$INVENTORY_DB_PASSWORD"
create_service_database "$PAYMENT_DB_NAME" "$PAYMENT_DB_USER" "$PAYMENT_DB_PASSWORD"
create_service_database "$NOTIFICATION_DB_NAME" "$NOTIFICATION_DB_USER" "$NOTIFICATION_DB_PASSWORD"
