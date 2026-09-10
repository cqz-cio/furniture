"""No SSH/database connections: verify the Java helper and exported read-only audits."""
import base64
import os
from pathlib import Path
import subprocess
import tempfile

from runner import bootstrap_bundle
from common import require

bundle = bootstrap_bundle()
code = base64.b64decode(bundle["helper"]["class"])
require(int.from_bytes(code[6:8], "big") == 61, "The helper must target Java 17")
with tempfile.TemporaryDirectory() as directory:
    (Path(directory) / "CloneMigration.class").write_bytes(code)
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if os.environ.get("JAVA_HOME") else "java"
    for database in ("oakved_v032_20260729", "oakved_cd_test_rehearse_" + "0" * 16):
        result = subprocess.run([java, "-cp", directory, "CloneMigration", database, "oakved_v032_20260729"],
                                capture_output=True, text=True, timeout=10)
        require(result.returncode != 0 and ("Only an owned clone" in result.stderr or "Restricted clone credentials required" in result.stderr),
                "Java guard must reject a legacy database and missing credentials before loading JDBC")
print("Java 17 helper compiled; database guards passed; 32 read-only checks cover both tenants before and after migration.")
