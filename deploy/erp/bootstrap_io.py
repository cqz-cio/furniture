"""Bounded private command logs and restricted clone database operations."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

from common import require, write_json
from bootstrap_policy import PROFILE, OWNED_USER, owned_database


def emit(stage, **fields):
    try:
        print(json.dumps({"stage": stage, **fields}), flush=True)
    except BrokenPipeError:
        # A lost CI connection must not prevent the server from recording/recovering a cutover.
        pass


class Commands:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.number = 0

    def run(self, args, label, seconds=30, data=None, input_file=None, output_file=None, env=None, idle=60):
        require(re.fullmatch(r"[a-z0-9-]+", label), "Invalid command label")
        self.number += 1
        log = self.directory / f"{self.number:03d}-{label}.log"
        start = changed = last_emit = time.monotonic()
        if label != "database-check":
            emit(label, timeout_seconds=seconds)
        with log.open("ab") as errors:
            output = output_file or errors
            source = input_file
            if data is not None:
                # File stdin avoids pipe deadlocks for large SQL bundles.
                import tempfile
                source = tempfile.TemporaryFile()
                source.write(data.encode() if isinstance(data, str) else data)
                source.seek(0)
            child = subprocess.Popen(args, stdin=source or subprocess.DEVNULL, stdout=output, stderr=errors,
                                     env=env, start_new_session=True)
            last_size = 0
            try:
                while child.poll() is None:
                    time.sleep(0.25)
                    now = time.monotonic()
                    size = log.stat().st_size + (os.fstat(output.fileno()).st_size if output_file else 0)
                    if input_file:
                        size += input_file.tell()
                    if size != last_size:
                        last_size, changed = size, now
                    require(now - start < seconds, label + " exceeded its deadline")
                    require(not idle or now - changed < idle, label + " had no progress for 60 seconds")
                    if now - last_emit >= 10:
                        emit(label, elapsed_seconds=round(now-start), progress_bytes=size)
                        last_emit = now
                require(child.returncode == 0, label + " failed; details are in the server's private command log")
            finally:
                if child.poll() is None:
                    os.killpg(child.pid, signal.SIGKILL)
                    child.wait(timeout=5)
                if data is not None:
                    source.close()
                write_json(log.with_suffix(".json"), {"label": label, "pid": child.pid,
                    "elapsed_seconds": round(time.monotonic()-start, 2), "exit_code": child.returncode,
                    "timeout_seconds": seconds})
        return log.read_text(errors="replace") if not output_file else ""


class Database:
    def __init__(self, commands):
        self.commands = commands
        self.created_users = set()
        self.args = ["mysql", "--defaults-extra-file=/etc/mysql/debian.cnf", "--protocol=SOCKET",
                     "--socket=/var/run/mysqld/mysqld.sock", "--connect-timeout=5", "--batch", "--raw", "--skip-column-names"]

    def query(self, sql, database=None):
        args = self.args + (["--database=" + database] if database else [])
        return self.commands.run(args, "database-check", data=sql, seconds=20).strip()

    def version(self, database):
        require(re.fullmatch(r"[A-Za-z0-9_]+", database), "Invalid database identifier")
        values = self.query("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0; "
            "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", database).splitlines()
        require(len(values) == 2 and values[0] == "0" and values[1].isdigit(), "Invalid Flyway ledger")
        return int(values[1])

    def counts(self, database):
        values = {}
        for table in ("product_spu", "product_sku", "system_users"):
            values[table] = self.query(f"SELECT tenant_id,COUNT(*) FROM {table} GROUP BY tenant_id ORDER BY tenant_id", database)
        for table in ("infra_file", "infra_file_content"):
            values[table] = self.query(f"SELECT COUNT(*) FROM {table}", database)
        values["attachment_bytes"] = self.query("SELECT COALESCE(SUM(OCTET_LENGTH(content)),0) FROM infra_file_content", database)
        return values

    def backup(self, database, path):
        require(database == PROFILE["source_database"], "First-cutover backup must target the verified legacy database")
        # No --databases, USE or CREATE DATABASE: restoration must remain inside the restricted clone.
        with path.open("xb") as stream:
            self.commands.run(["mysqldump", "--defaults-extra-file=/etc/mysql/debian.cnf", "--protocol=SOCKET",
                "--socket=/var/run/mysqld/mysqld.sock", "--single-transaction", "--quick", "--routines", "--events",
                "--triggers", "--set-gtid-purged=OFF", "--no-tablespaces", database], "database-backup", 180, output_file=stream)
        require(path.stat().st_size > 100, "Backup is empty")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        with path.open("rb") as stream:
            tables = [match[1].decode() for line in stream if (match := re.match(rb"CREATE TABLE `([A-Za-z0-9_]+)`", line))]
        require(tables and len(set(tables)) == len(tables), "Backup table catalog is empty or ambiguous")
        receipt = {"sha256": digest, "bytes": path.stat().st_size, "source": database, "tables": sorted(tables)}
        write_json(path.with_suffix(".json"), receipt)
        return receipt

    def create(self, name):
        owned_database(name)
        require(self.query(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{name}'") == "0", "Owned database already exists")
        charset, collation = self.query("SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + PROFILE["source_database"] + "'").split("\t")
        require(all(re.fullmatch(r"[A-Za-z0-9_]+", v) for v in (charset, collation)), "Invalid source charset or collation")
        self.query(f"CREATE DATABASE `{name}` CHARACTER SET {charset} COLLATE {collation}")

    def create_user(self, database, user, password):
        owned_database(database)
        require(OWNED_USER.fullmatch(user) and re.fullmatch(r"[a-f0-9]{64}", password), "Invalid clone credentials")
        require(self.query(f"SELECT COUNT(*) FROM mysql.user WHERE User='{user}'") == "0", "Owned account already exists")
        self.query(f"CREATE USER '{user}'@'localhost' IDENTIFIED BY '{password}' WITH MAX_USER_CONNECTIONS 20")
        self.created_users.add(user)
        # Exact schema privileges only; no global privileges and no legacy database access.
        # Database grant patterns treat '_' as a wildcard unless escaped (partial_revokes=OFF).
        schema_pattern = database.replace("_", "\\_")
        self.query(f"GRANT ALL PRIVILEGES ON `{schema_pattern}`.* TO '{user}'@'localhost'")

    def remove_user(self, user):
        require(OWNED_USER.fullmatch(user), "Only an owned clone account may be removed")
        for value in self.query(f"SELECT ID FROM information_schema.processlist WHERE USER='{user}'").splitlines():
            require(value.isdigit(), "Invalid owned session id")
            self.query("KILL CONNECTION " + value)
        self.query(f"DROP USER IF EXISTS '{user}'@'localhost'")
        self.created_users.discard(user)

    @contextmanager
    def restricted(self, database, user, password):
        # Caller journals the exact generated name before this method. No reuse of arbitrary users.
        try:
            self.create_user(database, user, password)
            yield dict(os.environ, MYSQL_PWD=password, ERP_CLONE_USER=user, ERP_CLONE_PASSWORD=password)
        finally:
            if user in self.created_users:
                self.remove_user(user)

    def restore(self, path, receipt, database, user, env):
        owned_database(database)
        with path.open("rb") as stream:
            require(hashlib.file_digest(stream, "sha256").hexdigest() == receipt["sha256"], "Backup checksum changed")
        with path.open("rb") as stream:
            for line in stream:
                require(not re.match(rb"\s*(USE\b|(?:CREATE|DROP)\s+DATABASE\b)", line, re.I), "Backup can escape the selected schema")
        with path.open("rb") as stream:
            self.commands.run(["mysql", "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", "--port=3306",
                "--user=" + user, "--connect-timeout=5", "--database=" + database], "database-restore", 180, input_file=stream, env=env)
        require(self.version(database) == 47, "Restored ledger differs from V047")
        tables = self.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + database + "' ORDER BY TABLE_NAME").splitlines()
        require(tables == receipt["tables"], "Restored table catalog differs from the verified backup")

    def audit(self, database, checks):
        result = []
        for check in checks:
            count = int(self.query("SET TRANSACTION READ ONLY; START TRANSACTION;\n" + check["count_sql"] + ";\nCOMMIT;", database))
            breakdown = self.query("SET TRANSACTION READ ONLY; START TRANSACTION;\n" + check["breakdown_sql"] + ";\nCOMMIT;", database)
            result.append({"tenant_id": check["tenant_id"], "key": check["key"], "findings": count, "breakdown": breakdown})
        return result
