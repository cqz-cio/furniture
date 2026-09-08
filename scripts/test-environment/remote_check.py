"""Executed over authenticated SSH, using stdin for credentials. Read-only checks."""

import hmac
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile


def command(args, env=None):
    return subprocess.run(args, capture_output=True, text=True, timeout=5, env=env)


def service_environment():
    state = command(["systemctl", "is-active", "oakved-yudao.service"])
    if state.returncode != 0 or state.stdout.strip() != "active":
        raise RuntimeError("Backend service is not active.")
    pid = command(["systemctl", "show", "oakved-yudao.service", "-p", "MainPID", "--value"]).stdout.strip()
    if not pid.isdigit() or int(pid) <= 1:
        raise RuntimeError("Backend process unavailable.")
    entries = Path("/proc/" + pid + "/environ").read_bytes().split(b"\0")
    return dict(e.decode("utf-8").split("=", 1) for e in entries if b"=" in e)


def request(path, port=80, tenant=None, method="GET", origin=None):
    headers = {"Host": "124.220.2.69"}
    if tenant is not None:
        headers["tenant-id"] = str(tenant)
    if origin:
        headers["Origin"] = origin
    if method == "OPTIONS":
        headers.update({"Access-Control-Request-Method": "GET", "Access-Control-Request-Headers": "tenant-id"})
    req = urllib.request.Request("http://127.0.0.1:" + str(port) + path, headers=headers, method=method)
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        response = opener.open(req, timeout=4)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read(1048576)
        content_type = response.headers.get_content_type()
        data = json.loads(body) if "json" in content_type else None
        return response.status, response.headers, data


def successful_api(result):
    status, _, data = result
    return status == 200 and isinstance(data, dict) and data.get("code") == 0


def denied_api(result):
    status, _, data = result
    return status in (401, 403) or (status == 200 and isinstance(data, dict) and data.get("code") in (401, 403))


def check_server(payload):
    checks = []
    report = {"credential_source": "active_server" if payload.get("server_config") else "provided_secrets", "checks": checks}

    def check(name, fn):
        try:
            result = fn()
            status = "skip" if result is None else ("pass" if result else "fail")
        except Exception:
            status = "fail"
        checks.append({"name": name, "status": status})
        return status == "pass"

    runtime = service_environment()
    check("backend_service_active", lambda: True)
    names = ("YUDAO_DB_PASSWORD", "YUDAO_REDIS_PASSWORD", "VANZ_WEBSITE_INQUIRY_SHARED_SECRET")
    expected = {n: runtime.get(n, "") for n in names} if payload.get("server_config") else payload["credentials"]
    for name in names:
        if payload.get("server_config"):
            check(name + "_configured", lambda n=name: bool(runtime.get(n)))
        else:
            check(name + "_matches_runtime", lambda n=name: bool(expected.get(n)) and hmac.compare_digest(
                expected[n].encode(), runtime.get(n, "").encode()))
    check("spring_redis_alias_matches", lambda: bool(expected.get(names[1])) and hmac.compare_digest(
        expected[names[1]].encode(), runtime.get("SPRING_DATA_REDIS_PASSWORD", "").encode()))
    check("security_mock_disabled", lambda: runtime.get("YUDAO_SECURITY_MOCK_ENABLE", "").lower() == "false")

    url = urllib.parse.urlsplit(runtime["YUDAO_DB_URL"].removeprefix("jdbc:"))
    if url.scheme != "mysql" or url.hostname != "127.0.0.1" or (url.port or 3306) != 3306:
        raise RuntimeError("Unexpected database target.")
    db_args = ["mysql", "--no-defaults", "--protocol=TCP", "--connect-timeout=3", "-h", "127.0.0.1", "-P", "3306",
               "-u", runtime["YUDAO_DB_USERNAME"], "--batch", "--skip-column-names", "--database=" + url.path.lstrip("/")]
    db_env = dict(os.environ, MYSQL_PWD=expected[names[0]])

    def db_query(sql):
        result = command(db_args + ["-e", sql], env=db_env)
        if result.returncode:
            raise RuntimeError("Database check failed.")
        return result.stdout.strip()

    check("mysql_authentication", lambda: db_query("SELECT 1") == "1")

    def redis_ping():
        host = runtime.get("SPRING_DATA_REDIS_HOST", runtime.get("YUDAO_REDIS_HOST"))
        port = runtime.get("SPRING_DATA_REDIS_PORT", runtime.get("YUDAO_REDIS_PORT", "6379"))
        if host != "127.0.0.1" or port != "6379":
            return False
        result = command(["redis-cli", "-h", host, "-p", port, "PING"], dict(os.environ, REDISCLI_AUTH=expected[names[1]]))
        return result.returncode == 0 and result.stdout.strip() == "PONG"

    check("redis_authentication", redis_ping)
    check("inquiry_enabled", lambda: runtime.get("VANZ_WEBSITE_INQUIRY_ENABLED", "").lower() == "true")

    with zipfile.ZipFile("/opt/oakved/backend/yudao-server.jar") as jar:
        config = jar.read("BOOT-INF/classes/application.yaml")
        check("inquiry_secret_bound_in_jar", lambda: b"${VANZ_WEBSITE_INQUIRY_SHARED_SECRET" in config)
        versions = [int(m.group(1)) for name in jar.namelist()
                    if "/db/migration/" in name and (m := re.search(r"/V(\d+)__[^/]+\.sql$", name))]
        report["jar_migration"] = max(versions) if versions else None

    def migration_state():
        value = db_query("SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
        if not value.isdigit():
            return False
        report["database_migration"] = int(value)
        return True

    check("database_migration_readable", migration_state)
    report["repository_migration"] = payload["latest_migration"]
    report["flyway_enabled"] = runtime.get("SPRING_FLYWAY_ENABLED", "").lower() == "true"
    aligned = report.get("database_migration") == report["jar_migration"] == payload["latest_migration"]
    checks.append({"name": "migration_matches_repository", "status": "pass" if aligned else "warn"})

    check("backend_health", lambda: request("/actuator/health", 48080)[2].get("status") == "UP")
    check("anonymous_admin_access_denied", lambda: denied_api(request("/admin-api/system/auth/get-permission-info", tenant=1)))
    origin = "http://124.220.2.69:18081"
    nav_path = "/app-api/seo/navigation/public?siteId=1&locale=en"
    blog_path = "/app-api/seo/blog/public?siteId=1&locale=en&pageSize=1"
    for tenant in (162, 121):
        check("cms_navigation_tenant_" + str(tenant), lambda t=tenant: successful_api(request(nav_path, tenant=t)))
        check("cms_blog_tenant_" + str(tenant), lambda t=tenant: successful_api(request(blog_path, tenant=t)))

    def blog_detail():
        page = request(blog_path, tenant=162)
        if not successful_api(page):
            return False
        items = page[2]["data"]["items"]
        if not items:
            return None
        slug = items[0]["slug"]
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", slug):
            return False
        detail_path = "/app-api/seo/blog/public/" + slug + "?siteId=1&locale=en"
        result = request(detail_path, tenant=162)
        if not successful_api(result) or not isinstance(result[2].get("data"), dict):
            return False
        return result[2]["data"].get("slug") == slug

    check("published_blog_detail", blog_detail)
    check("cms_cors_get", lambda: request(nav_path, tenant=162, origin=origin)[1].get("Access-Control-Allow-Origin") in (origin, "*"))

    def preflight():
        status, headers, _ = request(nav_path, method="OPTIONS", origin=origin)
        allowed = [h.strip().lower() for h in headers.get("Access-Control-Allow-Headers", "").split(",")]
        methods = [m.strip().upper() for m in headers.get("Access-Control-Allow-Methods", "").split(",")]
        return status in (200, 204) and headers.get("Access-Control-Allow-Origin") in (origin, "*") and "tenant-id" in allowed and "GET" in methods

    check("cms_cors_preflight", preflight)

    def admin_bundle():
        files = list(Path("/opt/oakved/frontend/admin").rglob("*.js"))
        return bool(files) and all(b"api.vanzhome.com" not in path.read_bytes() for path in files)

    check("deployed_admin_has_no_production_api_reference", admin_bundle)
    return report


if __name__ == "__main__":
    try:
        result = check_server(json.load(sys.stdin))
    except Exception:
        result = {"checks": [{"name": "remote_diagnostic", "status": "fail"}]}
    print(json.dumps(result))
