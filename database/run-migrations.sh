#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
migration_dir="$repo_root/database/migrations"
plan_only=false
[[ ${1:-} == "--plan" ]] && plan_only=true

mapfile -t migrations < <(find "$migration_dir" -maxdepth 1 -type f -name '*.sql' -print 2>/dev/null | sort)
if (( ${#migrations[@]} == 0 )); then
    echo "Không có migration .sql trong $migration_dir" >&2
    exit 1
fi

for migration in "${migrations[@]}"; do
    version=$(basename "$migration" .sql)
    [[ $version =~ ^[A-Za-z0-9._-]+$ ]] || {
        echo "Tên migration không an toàn: $version" >&2
        exit 1
    }
    if grep -Eiq '^[[:space:]]*GO[[:space:]]*$' "$migration"; then
        echo "Migration không được chứa GO vì runner bao toàn bộ file trong transaction: $version" >&2
        exit 1
    fi
    checksum=$(sha256sum "$migration" | awk '{print $1}')
    printf '%s %s\n' "$version" "$checksum"
done

$plan_only && exit 0

app_props="$repo_root/src/main/resources/application.properties"
if [[ -f "$app_props" ]]; then
    while IFS='=' read -r key val || [[ -n "$key" ]]; do
        key=$(echo "$key" | tr -d '[:space:]')
        val=$(echo "$val" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
        [[ -z "$key" || "$key" =~ ^# ]] && continue
        if [[ $key =~ ^TC_SQL_[A-Z0-9_]+$ ]] && [[ -z "${!key:-}" ]]; then
            export "$key=$val"
        fi
    done < "$app_props"
fi

: "${TC_SQL_HOST:?Thiếu TC_SQL_HOST}"
TC_SQL_TARGET_DB="${TC_SQL_TARGET_DB:-${TC_SQL_TEST_DB:-${TC_SQL_DEV_DB:-TicketsCenter_Dev}}}"
: "${TC_SQL_MIGRATION_LOGIN:?Thiếu TC_SQL_MIGRATION_LOGIN}"
: "${TC_SQL_MIGRATION_PASSWORD:?Thiếu TC_SQL_MIGRATION_PASSWORD}"

case "$TC_SQL_TARGET_DB" in
    "${TC_SQL_DEV_DB:-TicketsCenter_Dev}"|"${TC_SQL_TEST_DB:-TicketsCenter_Test}"|"${TC_SQL_BENCH_DB:-TicketsCenter_Bench}") ;;
    *) echo "Database target không nằm trong allowlist: $TC_SQL_TARGET_DB" >&2; exit 1 ;;
esac

export TC_SQL_TARGET_DB

if ! command -v sqlcmd >/dev/null 2>&1; then
    driver_version=$(sed -n 's:.*<mssql.jdbc.version>\([^<]*\)</mssql.jdbc.version>.*:\1:p' "$repo_root/pom.xml")
    driver_jar="${HOME}/.m2/repository/com/microsoft/sqlserver/mssql-jdbc/$driver_version/mssql-jdbc-$driver_version.jar"
    [[ -f "$driver_jar" ]] || {
        echo "Không tìm thấy sqlcmd hoặc Microsoft JDBC driver trong Maven cache" >&2
        exit 1
    }
    exec java --class-path "$driver_jar" "$repo_root/database/JdbcMigrationRunner.java" "$migration_dir"
fi

wrapper=$(mktemp "${TMPDIR:-/tmp}/ticketscenter-migrations.XXXXXX.sql")
trap 'rm -f -- "$wrapper"' EXIT

{
    printf ':On Error exit\n'
    printf 'SET NOCOUNT ON;\nSET XACT_ABORT ON;\nBEGIN TRANSACTION;\n'
    printf "DECLARE @lockResult int; EXEC @lockResult = sys.sp_getapplock @Resource = N'TicketsCenterMigration', @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;\n"
    printf "IF @lockResult < 0 THROW 51000, N'Cannot acquire migration lock', 1;\n"
    printf "IF OBJECT_ID(N'dbo.tc_schema_migrations', N'U') IS NULL CREATE TABLE dbo.tc_schema_migrations (version varchar(100) NOT NULL PRIMARY KEY, checksum_sha256 char(64) NOT NULL, applied_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), applied_by sysname NOT NULL DEFAULT ORIGINAL_LOGIN());\n"
    for migration in "${migrations[@]}"; do
        version=$(basename "$migration" .sql)
        checksum=$(sha256sum "$migration" | awk '{print $1}')
        printf "IF EXISTS (SELECT 1 FROM dbo.tc_schema_migrations WHERE version = '%s' AND checksum_sha256 <> '%s') THROW 51001, N'Migration checksum mismatch: %s', 1;\n" "$version" "$checksum" "$version"
        printf "IF NOT EXISTS (SELECT 1 FROM dbo.tc_schema_migrations WHERE version = '%s')\nBEGIN\n" "$version"
        printf ':r "%s"\n' "$migration"
        printf "INSERT dbo.tc_schema_migrations(version, checksum_sha256) VALUES ('%s', '%s');\nEND;\n" "$version" "$checksum"
    done
    printf 'COMMIT TRANSACTION;\n'
} >"$wrapper"

SQLCMDPASSWORD="$TC_SQL_MIGRATION_PASSWORD" sqlcmd \
    -S "$TC_SQL_HOST,${TC_SQL_PORT:-1433}" \
    -d "$TC_SQL_TARGET_DB" \
    -U "$TC_SQL_MIGRATION_LOGIN" \
    -b -X -l "${TC_SQL_CONNECT_TIMEOUT_SEC:-5}" \
    -i "$wrapper"
