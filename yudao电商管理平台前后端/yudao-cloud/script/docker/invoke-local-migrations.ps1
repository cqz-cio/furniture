[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

throw @"
The standalone SQL migration runner has been retired.
Start yudao-server instead: Flyway reads the migrations packaged inside yudao-server.jar,
validates flyway_schema_history, and applies only pending versions.
"@
