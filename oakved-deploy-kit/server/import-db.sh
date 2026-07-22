#!/usr/bin/env bash
set -euo pipefail

SQL_FILE="${1:-/opt/oakved/sql/oakved-full.sql}"
DB_NAME="${DB_NAME:-oakved}"
DB_ROOT_USER="${DB_ROOT_USER:-root}"

if [ ! -f "$SQL_FILE" ]; then
  echo "SQL file not found: $SQL_FILE" >&2
  exit 1
fi

mysql -u "$DB_ROOT_USER" -p -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u "$DB_ROOT_USER" -p "$DB_NAME" < "$SQL_FILE"

echo "Imported $SQL_FILE into database $DB_NAME"
