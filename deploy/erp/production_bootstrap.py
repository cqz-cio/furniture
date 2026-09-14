"""Adopt the existing production Docker deployment using a verified fresh DB clone."""
import json
from pathlib import Path
import re
import tempfile

from bootstrap import Bootstrap, run_bootstrap
from bootstrap_image import migrate, prepare_images
from bootstrap_policy import sha256
from common import fingerprint, require
from server import http_json
import production_policy


class ProductionBootstrap(Bootstrap):
    policy = production_policy
    environment = "production"
    database_prefix = "oakved_cd_production_"
    user_prefix = "erp_pd_"

    @property
    def profile(self):
        return {**self.policy.PROFILE, "target_version": self.release["database_version"]}

    def containers(self, running=None):
        result = {}
        for service, digest in self.profile["source_images"].items():
            name = self.profile["source_project"] + "-" + service + "-1"
            info = json.loads(self.run(["docker", "inspect", name], "inspect-old-" + service))[0]
            labels = info["Config"].get("Labels", {})
            require(re.fullmatch(r"[a-f0-9]{64}", info["Id"]) and info["Image"] == digest,
                    "Existing production container image changed; inventory again")
            require(labels.get("com.docker.compose.project") == self.profile["source_project"]
                    and labels.get("com.docker.compose.service") == service
                    and labels.get("com.docker.compose.project.config_files") == self.profile["source_compose"],
                    "Existing production container ownership changed")
            require(not info["Mounts"], "Existing production storage needs a reviewed mapping")
            if running is not None:
                require(info["State"]["Running"] is running, "Existing production container running state changed")
            restart = info["HostConfig"]["RestartPolicy"]
            require(restart["Name"] in ("no", "always", "unless-stopped") and restart["MaximumRetryCount"] == 0,
                    "Unreviewed production restart policy")
            values = dict(row.split("=", 1) for row in info["Config"].get("Env", []) if "=" in row)
            if service == "erp-backend":
                require(info["HostConfig"]["NetworkMode"] == "host"
                        and values.get("SERVER_PORT") == "48081"
                        and values.get("SERVER_ADDRESS") == "127.0.0.1", "Old backend binding changed")
            else:
                require(info["HostConfig"]["PortBindings"] == {"80/tcp": [{"HostIp": "127.0.0.1", "HostPort": "18080"}]},
                        "Old admin binding changed")
            result[service] = {"id": info["Id"], "image": info["Image"], "restart": restart,
                               "running": info["State"]["Running"], "runtime": values}
        return result

    def source_runtime(self):
        containers = self.containers(running=True)
        values = containers["erp-backend"]["runtime"]
        self.policy.backend_environment(values, self.database_prefix + "live_" + "0" * 16,
                                        self.user_prefix + "a_" + "0" * 16, "0" * 64)
        require(values.get("YUDAO_REDIS_HOST") == "127.0.0.1" and values.get("YUDAO_REDIS_DATABASE") == "0",
                "Production Redis target changed")
        require(values.get("SPRING_FLYWAY_ENABLED") == "false", "Existing migration setting changed")
        require(self.database.version(self.profile["source_database"]) == self.profile["source_version"],
                "Production source schema changed")
        require(sha256(Path(self.profile["source_compose"]).read_bytes()) == self.profile["compose_hash"],
                "Existing production compose file changed")
        require(self.run(["systemctl", "is-active", self.profile["service"]], "legacy-status") == "active",
                "Legacy background service state changed")
        pid = self.run(["systemctl", "show", self.profile["service"], "-p", "MainPID", "--value"], "legacy-pid")
        require(pid.isdigit() and int(pid) > 0, "Legacy background process unavailable")
        legacy = dict(row.decode().split("=", 1) for row in Path("/proc/" + pid + "/environ").read_bytes().split(b"\0") if b"=" in row)
        require(legacy.get("YUDAO_DB_URL") == values["YUDAO_DB_URL"] and legacy.get("SERVER_PORT") == "48080",
                "Legacy background service database or port changed")
        status, health = http_json("http://127.0.0.1:48081/actuator/health")
        require(status == 200 and isinstance(health, dict) and health.get("status") == "UP", "Live production health check failed")
        self.source_containers = containers
        return containers["erp-backend"]["id"] + ":" + pid, values

    def routes(self):
        expanded = self.run(["nginx", "-T"], "nginx-inventory")
        sections = re.split(r"(?m)^# configuration file ([^\n]+):\n", expanded)
        actual = {str(Path(sections[i]).resolve()) for i in range(1, len(sections)-1, 2)
                  if re.search(r"proxy_pass\s+http://127\.0\.0\.1:(48080|48081|18080)(?=[/;])", sections[i+1])}
        require(actual == set(self.profile["nginx_files"]), "Production ERP proxy inventory changed")
        canonical = Path(self.profile["nginx_main"])
        require(canonical.resolve() == canonical and Path(self.profile["nginx_enabled"]).resolve() == canonical,
                "Production proxy symlink changed")
        originals = {str(canonical): canonical.read_text()}
        return originals, sha256(expanded.encode())

    def preflight(self):
        snapshot = super().preflight()
        snapshot["containers"] = self.source_containers
        self.website_snapshot()
        self.validate_route_plans(snapshot["routes"])
        return snapshot

    def validate_route_plans(self, plans):
        """Let Nginx parse each plan in a private config tree, without a reload."""
        expanded = self.run(["nginx", "-T"], "proxy-plan-inventory")
        sections = re.split(r"(?m)^# configuration file ([^\n]+):\n", expanded)
        for mode, plan in plans.items():
            with tempfile.TemporaryDirectory(prefix="nginx-plan-", dir=self.commands.directory) as directory:
                root = Path(directory)
                for i in range(1, len(sections)-1, 2):
                    source = Path(sections[i])
                    if not source.is_relative_to("/etc/nginx"):
                        continue  # Certbot's external TLS include keeps its verified path.
                    target = root / source.relative_to("/etc/nginx")
                    require(target.resolve().is_relative_to(root), "Proxy plan path escapes temporary configuration")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    text = plan.get(str(source.resolve()), sections[i+1])
                    target.write_text(text.replace("/etc/nginx/", str(root) + "/"))
                require((root / "nginx.conf").is_file(), "Nginx main configuration missing")
                # Preserve Nginx's compiled module prefix; only change its config.
                self.run(["nginx", "-t", "-c", str(root / "nginx.conf")], "validate-proxy-" + mode)

    def verify_prepared(self, snapshot):
        super().verify_prepared(snapshot)
        old = json.loads((self.work() / "source.json").read_text())
        require(old["containers"] == snapshot["containers"], "Production Docker configuration changed; prepare again")

    def prepare_image_runtime(self, work):
        return prepare_images(self.commands, self.release, work, self.payload["helper"], environment="production")

    def migrate_clone(self, runtime, target, env):
        return migrate(self.commands, runtime, target, env, profile=self.profile)

    def clone_and_migrate(self, target, backup, receipt, runtime):
        result = super().clone_and_migrate(target, backup, receipt, runtime)
        before = {(r["tenant_id"], r["key"]): r["findings"] for r in result["before_audit"]}
        require(all(r["findings"] <= before[(r["tenant_id"], r["key"])] for r in result["after_audit"]),
                "Production migration introduced additional data audit findings")
        if self.profile["target_version"] == 50:
            require(self.database.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                    "AND table_name IN ('website_page','website_page_revision')", target) == "2",
                    "V050 page CMS tables missing after migration")
        return result

    def website_snapshot(self):
        # Protect the other active Nginx files and catalogue process. The ERP proxy
        # itself is changed only by the exact, hash-checked renderer.
        expanded = self.run(["nginx", "-T"], "protected-proxies")
        sections = re.split(r"(?m)^# configuration file ([^\n]+):\n", expanded)
        result = {str(Path(sections[i]).resolve()): sha256(sections[i+1].encode())
                  for i in range(1, len(sections)-1, 2)
                  if str(Path(sections[i]).resolve()) != self.profile["nginx_main"]}
        require(self.run(["systemctl", "is-active", "vanz-catalogue.service"], "catalogue-status") == "active",
                "Existing catalogue is not active")
        result["catalogue_pid"] = self.run(["systemctl", "show", "vanz-catalogue.service", "-p", "MainPID", "--value"], "catalogue-pid")
        # / redirects to the protected /catalog/ entry. Check without following
        # redirects so a login response is not confused with a broken service.
        status, _ = http_json("http://127.0.0.1:3000/")
        require(status == 302, "Existing catalogue redirect response changed")
        protected_status, _ = http_json("http://127.0.0.1:3000/catalog/")
        require(protected_status == 401, "Existing catalogue authentication response changed")
        result["catalogue_status"] = status
        return result

    def recorded_containers(self):
        return json.loads((self.work() / "source.json").read_text())["containers"]

    def verify_container_ownership(self, actual, expected):
        require(set(actual) == set(expected) and all(actual[k]["id"] == expected[k]["id"]
                and actual[k]["image"] == expected[k]["image"] and actual[k]["runtime"] == expected[k]["runtime"]
                for k in actual), "Production containers changed outside this cutover")

    def stop_legacy(self):
        expected = self.recorded_containers()
        self.verify_container_ownership(self.containers(running=True), expected)
        # Prevent Docker restart policies from resurrecting writers during a reboot.
        for service, info in expected.items():
            self.run(["docker", "update", "--restart=no", info["id"]], "disable-old-" + service)
            self.run(["docker", "stop", "--time", "30", info["id"]], "stop-old-" + service, seconds=45)
        super().stop_legacy()
        self.verify_previous_containers_stopped()

    def restore_previous_containers(self, snapshot):
        expected = snapshot["containers"]
        self.verify_container_ownership(self.containers(), expected)
        for service, info in expected.items():
            self.run(["docker", "update", "--restart=" + info["restart"]["Name"], info["id"]], "restore-policy-" + service)
            self.run(["docker", "start", info["id"]], "restore-old-" + service, seconds=45)
        self.containers(running=True)

    def verify_previous_containers_stopped(self):
        containers = self.containers(running=False)
        self.verify_container_ownership(containers, self.recorded_containers())
        require(all(v["restart"]["Name"] == "no" for v in containers.values()), "Old Docker restart policy was re-enabled")

    def disable_legacy(self):
        self.verify_previous_containers_stopped()
        super().disable_legacy()


def main(payload):
    if payload["operation"] == "preflight":
        state = Path(payload["root"]) / "state.json"
        if state.exists() and json.loads(state.read_text()).get("current"):
            from server import main as managed_main
            return managed_main(payload)
    return run_bootstrap(payload, ProductionBootstrap, production_policy)
