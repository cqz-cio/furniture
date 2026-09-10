"""Pure validation/rendering for the verified legacy test host. No side effects."""
import hashlib
import re
from urllib.parse import urlsplit, urlunsplit

from common import require, validate_release

PROFILE = {
    "host": "124.220.2.69", "user": "ubuntu", "root": "/opt/oakved-deploy/test",
    "host_key": "Dt9o/jcNSKagssvHK9BIoSGZSBrd1QjCIBH7rdRM7Ho",
    "service": "oakved-yudao.service", "source_database": "oakved_v032_20260729",
    "java": "/usr/lib/jvm/java-17-openjdk-amd64/bin/java",
    "jar": "/opt/oakved/backend/yudao-server.jar",
    "nginx_main": "/etc/nginx/conf.d/oakved.conf",
    "nginx_files": ["/etc/nginx/conf.d/oakved.conf", "/etc/nginx/conf.d/vanz-tob-ip-8081.conf"],
    "backend_port": 48081, "admin_port": 18080, "source_version": 47, "target_version": 49,
    "reserve_bytes": 10 * 1024**3,
}
OPERATIONS = ("prepare", "cutover", "recover")
OWNED_DB = re.compile(r"oakved_cd_test_(?:rehearse|live)_[0-9a-f]{16}\Z")
OWNED_USER = re.compile(r"erp_cd_[ma]_[0-9a-f]{16}\Z")


def validate_target(environment, root, release, operation, confirmed=False):
    require(environment == "test" and root == PROFILE["root"], "Bootstrap is limited to the verified test environment")
    require(operation in OPERATIONS, "Unknown bootstrap operation")
    validate_release(release)
    require(release["repository"] == "cqz-cio/furniture", "Wrong repository for test onboarding")
    require(release["config"]["test"] == {"api_base_url": "http://" + PROFILE["host"],
            "storefront_url": "http://" + PROFILE["host"]}, "Test image URLs do not match the verified host")
    require(release["database_version"] == PROFILE["target_version"], "This legacy onboarding profile supports V047 to V049 only")
    require(operation == "prepare" or confirmed is True, "Confirm the test maintenance cutover in the workflow inputs")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def owned_database(name, source=PROFILE["source_database"]):
    require(OWNED_DB.fullmatch(name or "") and name != source, "Only an owned isolated test database is allowed")
    return name


def backend_environment(runtime, database, user, password):
    owned_database(database)
    require(OWNED_USER.fullmatch(user) and password, "Missing restricted application credentials")
    url = urlsplit(runtime["YUDAO_DB_URL"].removeprefix("jdbc:"))
    require(not url.username and not url.password, "Embedded JDBC credentials are unsupported")
    require((url.scheme, url.hostname, url.port or 3306, url.path) ==
            ("mysql", "127.0.0.1", 3306, "/" + PROFILE["source_database"]), "Legacy database target changed")
    require(not any(k.startswith("SPRING_DATASOURCE") for k in runtime), "Review explicit datasource overrides before onboarding")
    prefixes = ("YUDAO_", "SPRING_", "VANZ_", "WX_", "AI_", "OPENAI_", "MAIL_", "PAY_", "SMS_", "OAUTH_", "ALIYUN_", "TENCENT_", "GOOGLE_")
    values = {k: v for k, v in runtime.items() if k.startswith(prefixes)}
    values.update(YUDAO_DB_URL="jdbc:" + urlunsplit(url._replace(path="/" + database)),
                  YUDAO_DB_USERNAME=user, YUDAO_DB_PASSWORD=password,
                  JAVA_OPTS="-Xms256m -Xmx1024m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2 -Djava.security.egd=file:/dev/./urandom")
    for key, value in values.items():
        require(re.fullmatch(r"[A-Z][A-Z0-9_]*", key) and not any(c in value for c in "\r\n\0"), "Environment contains unsupported multiline values")
    return "".join(k + "=" + v + "\n" for k, v in sorted(values.items()))


def nginx_plan(originals, token):
    """Replace only known ERP destinations and the admin alias; preserve all other bytes."""
    require(set(originals) == set(PROFILE["nginx_files"]), "Unexpected active ERP proxy files; review the host profile")
    require(re.fullmatch(r"[a-f0-9]{48}", token), "Invalid private probe token")
    result = {"maintenance": {}, "candidate": {}, "open": {}}
    gate = (f'        if ($http_x_erp_cd_probe != "{token}") {{ return 503; }}\n'
            '        if ($request_method !~ ^(GET|HEAD|OPTIONS)$) { return 503; }\n')
    for path, original in originals.items():
        require("erp_cd_probe" not in original and not re.search(r"127\.0\.0\.1:48081", original), "Proxy is already managed or partially switched")
        proxies = list(re.finditer(r"(?m)^[ \t]*proxy_pass http://127\.0\.0\.1:48080(?=[/;])[^\n]*", original))
        require(len(proxies) == 2, "Expected two legacy ERP routes per verified proxy file")
        maintenance = original
        for match in reversed(proxies):
            maintenance = maintenance[:match.start()] + gate + maintenance[match.start():]
        opened = original.replace("http://127.0.0.1:48080", "http://127.0.0.1:48081")
        candidate = maintenance.replace("http://127.0.0.1:48080", "http://127.0.0.1:48081")
        if path == PROFILE["nginx_main"]:
            require(len(re.findall(r"(?m)^server\s*\{", original)) == 1 and original.rstrip().endswith("}"), "Expected the single IP server block")
            require("/actuator/" not in original, "Existing actuator routing needs review")
            pattern = r"location /admin/\s*\{\s*alias /opt/oakved/frontend/admin/;\s*try_files \$uri \$uri/ /admin/index\.html;\s*\}"
            require(len(re.findall(pattern, original)) == 1, "Legacy admin location changed")
            admin = "location /admin/ {\n        proxy_pass http://127.0.0.1:18080;\n        proxy_set_header Host $host;\n    }"
            opened = re.sub(pattern, lambda _: admin, opened)
            candidate = re.sub(pattern, lambda _: admin.replace("        proxy_pass", gate + "        proxy_pass"), candidate)
            maintenance = re.sub(pattern, lambda m: m[0].replace("{", "{\n" + gate, 1), maintenance)
            for mode, text in (("open", opened), ("candidate", candidate)):
                blocks = ""
                for endpoint in ("health", "info"):
                    blocks += "\n    location = /actuator/" + endpoint + " {\n" + (gate if mode == "candidate" else "")
                    blocks += "        proxy_pass http://127.0.0.1:48081;\n    }\n"
                offset = text.rfind("}")
                result[mode][path] = text[:offset] + blocks + text[offset:]
        else:
            result["open"][path], result["candidate"][path] = opened, candidate
        result["maintenance"][path] = maintenance
    return result


def validate_audit_bundle(bundle):
    require(set(bundle) == {"before", "after"}, "Both audit phases are required")
    for checks in bundle.values():
        require(len(checks) == 16 and {v["tenant_id"] for v in checks} == {121, 162}, "Audit must cover both existing tenants")
        require(len({(v['tenant_id'], v['key']) for v in checks}) == 16, "Duplicate audit check")
        for check in checks:
            # Queries are exported from the trusted repository, never workflow text inputs.
            for key in ("count_sql", "breakdown_sql"):
                sql = check[key].strip()
                require(sql.upper().startswith("SELECT") and ";" not in sql
                        and not re.search(r"\b(INTO\s+(OUTFILE|DUMPFILE)|FOR\s+UPDATE|SLEEP\s*\()", sql, re.I), "Only read-only audit queries are supported")
