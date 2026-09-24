#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
catalina_home=${CATALINA_HOME:-/usr/share/tomcat11}
http_port=${TC_TOMCAT_HTTP_PORT:-18080}
war_path="$repo_root/target/ticketscenter.war"
base_dir=$(mktemp -d "${TMPDIR:-/tmp}/ticketscenter-tomcat.XXXXXX")
log_path="$base_dir/tomcat.log"
tomcat_pid=""

cleanup() {
    if [[ -n "$tomcat_pid" ]] && kill -0 "$tomcat_pid" 2>/dev/null; then
        kill "$tomcat_pid" 2>/dev/null || true
        wait "$tomcat_pid" 2>/dev/null || true
    fi
    rm -rf -- "$base_dir"
}
trap cleanup EXIT

if [[ ! -x "$catalina_home/bin/makebase.sh" ]]; then
    echo "Không tìm thấy Tomcat makebase.sh tại $catalina_home/bin" >&2
    exit 1
fi

if [[ ! -f "$war_path" ]]; then
    echo "Chưa có $war_path; chạy mvn -B package trước." >&2
    exit 1
fi

rm -rf -- "$base_dir"
"$catalina_home/bin/makebase.sh" "$base_dir" >/dev/null
if [[ ! -f "$base_dir/conf/server.xml" && -d "$catalina_home/etc" ]]; then
    cp "$catalina_home/etc/"* "$base_dir/conf/"
fi
if [[ ! -f "$base_dir/conf/server.xml" ]]; then
    echo "Không tìm thấy template server.xml trong Tomcat $catalina_home" >&2
    exit 1
fi
sed -i "s/port=\"8080\"/port=\"$http_port\"/" "$base_dir/conf/server.xml"

wait_for_health() {
    local attempt body
    for attempt in {1..60}; do
        if ! kill -0 "$tomcat_pid" 2>/dev/null; then
            echo "Tomcat child đã dừng trước khi health sẵn sàng; log cuối:" >&2
            tail -n 80 "$log_path" >&2 || true
            return 1
        fi
        if body=$(curl --fail --silent --show-error \
            "http://127.0.0.1:$http_port/ticketscenter/health/live" 2>/dev/null); then
            [[ "$body" == '{"data":{"status":"UP"}}' ]] || {
                echo "Health body không đúng: $body" >&2
                return 1
            }
            return 0
        fi
        sleep 0.25
    done
    echo "Tomcat không trả health trong 15 giây; log cuối:" >&2
    tail -n 80 "$log_path" >&2 || true
    return 1
}

port_is_open() {
    (exec 3<>"/dev/tcp/127.0.0.1/$http_port") 2>/dev/null
}

wait_for_port_closed() {
    local attempt
    for attempt in {1..40}; do
        port_is_open || return 0
        sleep 0.25
    done
    echo "Cổng $http_port vẫn mở sau khi Tomcat child dừng." >&2
    return 1
}

start_and_verify() {
    if port_is_open; then
        echo "Cổng $http_port đã được process khác sử dụng; không thể chứng minh health thuộc Tomcat test." >&2
        return 1
    fi
    CATALINA_HOME="$catalina_home" CATALINA_BASE="$base_dir" \
        "$catalina_home/bin/catalina.sh" run >>"$log_path" 2>&1 &
    tomcat_pid=$!
    wait_for_health
    kill "$tomcat_pid"
    wait "$tomcat_pid" 2>/dev/null || true
    wait_for_port_closed
}

cp "$war_path" "$base_dir/webapps/ticketscenter.war"
start_and_verify

rm -rf -- "$base_dir/webapps/ticketscenter" "$base_dir/work/Catalina"
cp "$war_path" "$base_dir/webapps/ticketscenter.war"
start_and_verify

echo "PASS: WAR deploy và redeploy trả HTTP 200 với JSON health cố định."
