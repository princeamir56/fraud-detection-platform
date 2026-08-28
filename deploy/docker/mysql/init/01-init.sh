#!/bin/bash
# =============================================================================
# MySQL first-boot init (runs once, via /docker-entrypoint-initdb.d).
#
# Creates the six per-service schemas and the shared application user that every
# DB-backed service connects as (SPRING_PROFILES_ACTIVE=docker -> MYSQL_USER /
# MYSQL_PASSWORD). Each service owns its schema; Flyway then manages its tables.
#
# Credentials come from the container environment (MYSQL_APP_USER /
# MYSQL_APP_PASSWORD, defaulting to fraud/fraud) so .env stays the single source
# of truth. Connects over the local socket with the root password the entrypoint
# has already configured.
# =============================================================================
set -euo pipefail

APP_USER="${MYSQL_APP_USER:-fraud}"
APP_PASSWORD="${MYSQL_APP_PASSWORD:-fraud}"

DATABASES=(
  fraud_customer
  fraud_account
  fraud_transaction
  fraud_detection
  fraud_alert
  fraud_notification
)

echo "[mysql-init] creating schemas and application user '${APP_USER}'"

{
  echo "CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';"
  for db in "${DATABASES[@]}"; do
    echo "CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo "GRANT ALL PRIVILEGES ON \`${db}\`.* TO '${APP_USER}'@'%';"
  done
  echo "FLUSH PRIVILEGES;"
} | mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}"

echo "[mysql-init] done: ${DATABASES[*]}"
