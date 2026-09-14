"""First adoption of the inventoried production Docker pair. Pure checks only."""
import re
import bootstrap_policy
from common import require, validate_release

PROFILE = {
    "environment": "production", "host": "43.153.40.182", "user": "ubuntu",
    "root": "/opt/oakved-deploy/production",
    "host_key": "r0V3s8JxHX911k3LI9eBOdDXEwMfJpbl3X2EotAnw7k",
    "service": "oakved-yudao.service", "java": "/usr/bin/java",
    "jar": "/opt/oakved/current/backend/yudao-server.jar",
    "source_database": "codex_release_v47_20260814_162836",
    "source_version": 48, "target_version": 49,
    "source_backend_port": 48081, "source_admin_port": 18080,
    "backend_port": 48082, "admin_port": 18081,
    "nginx_main": "/etc/nginx/sites-available/oakved.conf",
    "nginx_enabled": "/etc/nginx/sites-enabled/oakved.conf",
    "nginx_files": ["/etc/nginx/sites-available/oakved.conf"],
    "nginx_hash": "bf08061cacbee38d0a74435e607fc7e96d965ec66b1d7e8906caaecf8a36f259",
    "source_project": "furniture-erp-production",
    "source_compose": "/opt/oakved/docker/docker-compose.erp-prod.yml",
    "compose_hash": "4642dd1029a425d50a0162d758a551e435e1fb29e89f68db996836133ac80032",
    "source_images": {
        "erp-backend": "sha256:b7f80c29c37e566bd0b88c2f3a7b43d2aac7aaaf7d6a6d6d88bb6302af703414",
        "erp-admin": "sha256:c0b7e224f8a79ca548f0d77edec525b1c2e933f4c4989d8f1d1cc0975783bba3",
    },
    "reserve_bytes": 10 * 1024**3,
}
OWNED_DB = re.compile(r"oakved_cd_production_(?:rehearse|live)_[0-9a-f]{16}\Z")
OWNED_USER = re.compile(r"erp_pd_[ma]_[0-9a-f]{16}\Z")


def validate_target(environment, root, release, operation, confirmed=False):
    require(environment == "production" and root == PROFILE["root"], "Use the verified production root")
    require(operation in bootstrap_policy.OPERATIONS, "Unknown production onboarding operation")
    validate_release(release)
    require(release["schema"] == 1 and release["repository"] == "cqz-cio/furniture",
            "Production onboarding requires the published GHCR release manifest")
    require(release["config"]["production"] == {
        "api_base_url": "https://api.vanzhome.com", "storefront_url": "https://www.vanzhome.com"},
        "Production image URLs differ from the verified domains")
    require(release["database_version"] in (49, 50),
            "First production adoption supports V048 to tested V049/V050 releases; review later migrations first")
    require(operation == "prepare" or confirmed is True,
            "Explicitly confirm the production maintenance cutover/recovery in workflow inputs")


def owned_database(name):
    require(OWNED_DB.fullmatch(name or "") and name != PROFILE["source_database"],
            "Only an owned isolated production clone is allowed")
    return name


def backend_environment(runtime, database, user, password):
    return bootstrap_policy.backend_environment(runtime, database, user, password,
        profile=PROFILE, database_validator=owned_database, user_pattern=OWNED_USER)


def nginx_plan(originals, token):
    require(set(originals) == set(PROFILE["nginx_files"]), "Unexpected production ERP proxy files")
    require(re.fullmatch(r"[a-f0-9]{48}", token), "Invalid private probe token")
    path = PROFILE["nginx_main"]
    original = originals[path]
    require(bootstrap_policy.sha256(original.encode()) == PROFILE["nginx_hash"],
            "Production proxy changed; inventory and review the profile again")
    require("/actuator/" not in original and "erp_cd_probe" not in original, "Proxy is already managed")
    gate = (f'        if ($http_x_erp_cd_probe != "{token}") {{ return 503; }}\n'
            '        if ($request_method !~ ^(GET|HEAD|OPTIONS)$) { return 503; }\n')
    pattern = r"(?m)^[ \t]*proxy_pass http://127\.0\.0\.1:(48081|18080)(?=[/;])[^\n]*"
    matches = list(re.finditer(pattern, original))
    require([m[1] for m in matches].count("48081") == 3 and [m[1] for m in matches].count("18080") == 1,
            "Expected the three backend routes and one admin route")
    maintenance = original
    for match in reversed(matches):
        maintenance = maintenance[:match.start()] + gate + maintenance[match.start():]
    result = {"maintenance": {path: maintenance}}
    for mode, source in (("candidate", maintenance), ("open", original)):
        text = source.replace("http://127.0.0.1:48081", "http://127.0.0.1:48082")
        text = text.replace("http://127.0.0.1:18080", "http://127.0.0.1:18081")
        anchor = "    listen [::]:443 ssl http2 ipv6only=on;"
        require(text.count(anchor) == 1, "Cannot identify the existing HTTPS server")
        blocks = ""
        for endpoint in ("health", "info"):
            blocks += "    location = /actuator/" + endpoint + " {\n" + (gate if mode == "candidate" else "")
            blocks += "        proxy_pass http://127.0.0.1:48082;\n    }\n\n"
        result[mode] = {path: text.replace(anchor, blocks + anchor, 1)}
    return result
