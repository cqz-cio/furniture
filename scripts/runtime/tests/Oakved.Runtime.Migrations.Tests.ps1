$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'Oakved.Runtime.psm1'
Import-Module $modulePath -Force

function Get-CaughtMigrationMessage {
    param([scriptblock]$Action)

    try {
        & $Action
        throw 'Expected the action to throw.'
    }
    catch {
        return $_.Exception.Message
    }
}

function New-MigrationGateFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root
    )

    $migrations = Join-Path $Root 'migrations'
    $baseline = Join-Path $Root 'oakved-baseline.sql'
    New-Item -ItemType Directory -Path $migrations -Force | Out-Null
    Set-Content -LiteralPath $baseline -Value 'CREATE TABLE baseline_table (id int);' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $migrations 'V001__one.sql') -Value 'CREATE TABLE one_table (id int);' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $migrations 'V002__two.sql') -Value 'INSERT INTO one_table VALUES (1);' -Encoding Ascii

    $lockState = [pscustomobject]@{
        Acquired = $true
        ReleaseSucceeds = $true
        Alive = $false
        Terminated = $false
        Events = New-Object 'System.Collections.Generic.List[string]'
    }

    [pscustomobject]@{
        Target = [pscustomobject]@{ RuntimeId = 'codex_feature_12345678' }
        Layout = [pscustomobject]@{ Migrations = $migrations; Baseline = $baseline }
        RuntimeRoot = Join-Path $Root 'runtime'
        Baseline = $baseline
        MigrationOne = Join-Path $migrations 'V001__one.sql'
        MigrationTwo = Join-Path $migrations 'V002__two.sql'
        LockState = $lockState
        LockProvider = New-TestLockLeaseProvider -State $lockState
    }
}

function New-TestLockLeaseProvider {
    param(
        [Parameter(Mandatory = $true)]
        [object]$State
    )

    return {
        param($Database, $LockName)

        $State.Events.Add('acquire')
        $State.Alive = [bool]$State.Acquired
        $release = {
            param($lease)
            $lease.State.Events.Add('release')
            $lease.State.Alive = $false
            return [bool]$lease.State.ReleaseSucceeds
        }
        $terminate = {
            param($lease)
            $lease.State.Events.Add('terminate')
            $lease.State.Terminated = $true
            $lease.State.Alive = $false
        }
        $isAlive = { param($lease) return [bool]$lease.State.Alive }

        [pscustomobject]@{
            Acquired = [bool]$State.Acquired
            State = $State
            IsAlive = $isAlive
            Release = $release
            Terminate = $terminate
        }
    }.GetNewClosure()
}

Describe 'Oakved migration catalog' {
    It 'rejects gaps and duplicate versions' {
        { Get-OakvedMigrationCatalog -Files @('V001__one.sql','V003__three.sql') -ContentProvider { param($f) 'SELECT 1;' } } |
            Should Throw 'Migration catalog must be contiguous.'
        { Get-OakvedMigrationCatalog -Files @('V001__one.sql','V001__other.sql') -ContentProvider { param($f) 'SELECT 1;' } } |
            Should Throw 'Duplicate migration version 001.'
    }

    It 'normalizes line endings before hashing' {
        $a = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`r`n" }
        $b = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`n" }
        $a[0].Checksum | Should Be $b[0].Checksum
    }

    It 'uses the baseline generator canonical trailing newline before hashing' {
        $a = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`r`n`r`n" }
        $b = Get-OakvedMigrationCatalog -Files @('V001__one.sql') -ContentProvider { "SELECT 1;`n" }
        $a[0].Checksum | Should Be $b[0].Checksum
    }

    It 'rejects invalid migration filenames' {
        $message = Get-CaughtMigrationMessage {
            Get-OakvedMigrationCatalog -Files @('V1__not_canonical.sql') -ContentProvider { 'SELECT 1;' }
        }

        $message | Should Be 'Invalid migration filename V1__not_canonical.sql.'
    }

    It 'rejects migration filename casing that is not canonical' {
        { Get-OakvedMigrationCatalog -Files @('v001__one.sql') -ContentProvider { 'SELECT 1;' } } |
            Should Throw 'Invalid migration filename v001__one.sql.'
        { Get-OakvedMigrationCatalog -Files @('V001__One.sql') -ContentProvider { 'SELECT 1;' } } |
            Should Throw 'Invalid migration filename V001__One.sql.'
        { Get-OakvedMigrationCatalog -Files @('V001__one.SQL') -ContentProvider { 'SELECT 1;' } } |
            Should Throw 'Invalid migration filename V001__one.SQL.'
    }

    It 'returns only the ordered pending suffix' {
        $catalog = @(
            [pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='abc'},
            [pscustomobject]@{Version='002';ScriptName='V002__two.sql';Description='two';Checksum='def'}
        )
        $ledger = @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='abc'})

        $pending = @(Compare-OakvedMigrationLedger -Catalog $catalog -Ledger $ledger)

        $pending.Count | Should Be 1
        $pending[0].Version | Should Be '002'
    }

    It 'rejects checksum mismatch and database-ahead state' {
        $catalog = @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='abc'})
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='def'}) } |
            Should Throw 'Checksum mismatch for V001__one.sql.'
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='002';ScriptName='V002__two.sql';Description='two';Checksum='abc'}) } |
            Should Throw 'Database contains migration 002 that is not present in the selected branch.'
    }

    It 'rejects renamed, redescribed, and non-prefix applied migrations' {
        $catalog = @(
            [pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='one';Checksum='abc'},
            [pscustomobject]@{Version='002';ScriptName='V002__two.sql';Description='two';Checksum='def'}
        )

        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='001';ScriptName='V001__renamed.sql';Description='one';Checksum='abc'}) } |
            Should Throw 'Script name mismatch for migration 001.'
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='001';ScriptName='V001__one.sql';Description='changed';Checksum='abc'}) } |
            Should Throw 'Description mismatch for V001__one.sql.'
        { Compare-OakvedMigrationLedger -Catalog $catalog -Ledger @([pscustomobject]@{Version='002';ScriptName='V002__two.sql';Description='two';Checksum='def'}) } |
            Should Throw 'Applied migration ledger must be an ordered prefix of the selected branch catalog.'
    }
}

Describe 'Get-OakvedDatabaseName' {
    It 'uses the runtime id and remains within MySQL identifier limits' {
        $name = Get-OakvedDatabaseName 'codex_feature_12345678'
        $name | Should Be 'oakved_codex_feature_12345678'
        $name.Length | Should BeLessThan 65
    }

    It 'rejects runtime ids that are not safe MySQL identifier fragments' {
        { Get-OakvedDatabaseName 'unsafe-runtime' } | Should Throw 'RuntimeId must contain only lowercase letters, digits, and underscores.'
    }
}

Describe 'Invoke-OakvedDatabaseGate orchestration' {
    It 'reads BOM-less migration files as UTF-8 like the baseline generator' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'utf8')
        $unicodeSql = "CREATE TABLE furniture_name (name varchar(20)); -- 家具`n"
        [IO.File]::WriteAllText($fixture.MigrationOne, $unicodeSql, (New-Object Text.UTF8Encoding($false)))
        $expectedCatalog = @(Get-OakvedMigrationCatalog -Files @($fixture.MigrationOne, $fixture.MigrationTwo) -ContentProvider {
            param($path)
            [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
        })
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { return $expectedCatalog }
            return ''
        }

        $result = Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -LockLeaseProvider $fixture.LockProvider

        $result.Version | Should Be '002'
    }

    It 'leaves an empty database for the packaged Flyway baseline' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'empty')
        $sqlFileCalls = 0
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=*' -and $Sql -notlike '*table_name=*') { return '0' }
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*table_name=*') { return '0' }
            return ''
        }

        $result = Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' `
            -MySqlCommandProvider $mysql -SqlFileProvider { param($Database, $Path) $sqlFileCalls++ } -LockLeaseProvider $fixture.LockProvider

        $result.Engine | Should Be 'flyway'
        $result.Version | Should Be $null
        $result.CatalogVersion | Should Be '002'
        $result.RequiresMigration | Should Be $true
        $sqlFileCalls | Should Be 0
        $fixture.LockState.Events.Count | Should Be 0
    }

    It 'reports Flyway history without invoking the legacy runner' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'flyway')
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=*' -and $Sql -notlike '*table_name=*') { return '12' }
            if ($Sql -like "SHOW TABLES LIKE 'flyway_schema_history'*") { return 'flyway_schema_history' }
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*table_name=*') { return '1' }
            if ($Sql -like 'SELECT COALESCE(LPAD(MAX(CAST(version AS UNSIGNED))*') { return '002' }
            return ''
        }

        $result = Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' `
            -MySqlCommandProvider $mysql -LockLeaseProvider $fixture.LockProvider

        $result.Engine | Should Be 'flyway'
        $result.Version | Should Be '002'
        $result.RequiresMigration | Should Be $false
        $fixture.LockState.Events.Count | Should Be 0
    }

    It 'always releases an acquired lock when validation fails' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'release')
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'CREATE DATABASE*') { return '' }
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like "SHOW TABLES LIKE 'flyway_schema_history'*") { return '' }
            if (-not $fixture.LockState.Alive) { throw 'gate command ran without a live lock lease' }
            if ($Sql -like 'SELECT version,*') { throw 'ledger read failed' }
            return ''
        }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -LockLeaseProvider $fixture.LockProvider
        }

        $message | Should Be 'ledger read failed'
        ($fixture.LockState.Events -join ',') | Should Be 'acquire,release,terminate'
        $fixture.LockState.Alive | Should Be $false
        $fixture.LockState.Terminated | Should Be $true
    }

    It 'fails closed when the migration lock cannot be acquired' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'lock failure')
        $fixture.LockState.Acquired = $false
        $tableChecks = 0
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { $tableChecks++; return '1' }
            return ''
        }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -LockLeaseProvider $fixture.LockProvider
        }

        $message | Should Be 'Could not acquire migration lock for oakved_codex_feature_12345678.'
        $tableChecks | Should Be 0
        ($fixture.LockState.Events -join ',') | Should Be 'acquire,terminate'
    }

    It 'does not create a backup when no migrations are pending' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'current')
        $catalog = @(Get-OakvedMigrationCatalog -Files @($fixture.MigrationOne, $fixture.MigrationTwo) -ContentProvider { param($path) Get-Content -LiteralPath $path -Raw })
        $backupCalls = 0
        $migrationCalls = 0
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'CREATE DATABASE*') { return '' }
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like "SHOW TABLES LIKE 'flyway_schema_history'*") { return '' }
            if (-not $fixture.LockState.Alive) { throw 'gate command ran without a live lock lease' }
            if ($Sql -like 'SELECT version,*') { return $catalog }
            return ''
        }
        $backup = { param($Database, $Path) $backupCalls++ }
        $sqlFile = { param($Database, $Path) $migrationCalls++ }

        $result = Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider $sqlFile -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider

        $backupCalls | Should Be 0
        $migrationCalls | Should Be 0
        $result.BackupPath | Should Be $null
        $result.AppliedCount | Should Be 0
        $result.Version | Should Be '002'
        $result.CatalogVersion | Should Be '002'
        ($fixture.LockState.Events -join ',') | Should Be 'acquire,release,terminate'
    }

    It 'validates the required backup before executing pending SQL' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'invalid backup')
        $sqlCalls = 0
        $readCount = 0
        $capture = [pscustomobject]@{ BackupPath = $null }
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { $readCount++; return @() }
            return ''
        }
        $backup = {
            param($Database, $Path)
            $capture.BackupPath = $Path
            New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
            Set-Content -LiteralPath $Path -Value '-- empty dump' -Encoding Ascii
        }.GetNewClosure()
        $sqlFile = { param($Database, $Path) $sqlCalls++ }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider $sqlFile -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider
        }

        $message | Should Match '^Database backup validation failed: '
        $sqlCalls | Should Be 0
        (Test-Path -LiteralPath $capture.BackupPath) | Should Be $false
    }

    It 'removes zero-length and invalid UTF-8 backups without masking validation errors' {
        foreach ($case in @('empty', 'invalid-utf8')) {
            $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive "invalid backup $case")
            $capture = [pscustomobject]@{ BackupPath = $null }
            $mysql = {
                param($Database, $Sql)
                if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
                if ($Sql -like 'SELECT version,*') { return @() }
                return ''
            }
            $backup = {
                param($Database, $Path)
                $capture.BackupPath = $Path
                New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
                if ($case -eq 'empty') {
                    [IO.File]::WriteAllBytes($Path, [byte[]]@())
                }
                else {
                    [IO.File]::WriteAllBytes($Path, [byte[]]@(0xff, 0xfe, 0xfd))
                }
            }.GetNewClosure()

            $message = Get-CaughtMigrationMessage {
                Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider { param($Database, $Path) } -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider
            }

            $message | Should Match '^Database backup validation failed: '
            (Test-Path -LiteralPath $capture.BackupPath) | Should Be $false
        }
    }

    It 'applies migrations in order and inserts each ledger row only after its file succeeds' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'ordered')
        $catalog = @(Get-OakvedMigrationCatalog -Files @($fixture.MigrationOne, $fixture.MigrationTwo) -ContentProvider { param($path) Get-Content -LiteralPath $path -Raw })
        $ledger = New-Object 'System.Collections.Generic.List[object]'
        $events = New-Object 'System.Collections.Generic.List[string]'
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { return @($ledger.ToArray()) }
            if ($Sql -match "VALUES \('(?<version>\d{3})'") {
                $events.Add("ledger:$($Matches.version)")
                $row = $catalog | Where-Object Version -eq $Matches.version
                $ledger.Add($row)
            }
            return ''
        }
        $backup = {
            param($Database, $Path)
            if (-not $fixture.LockState.Alive) { throw 'backup ran without a live lock lease' }
            New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
            Set-Content -LiteralPath $Path -Value 'CREATE TABLE backed_up (id int);' -Encoding Ascii
        }
        $sqlFile = {
            param($Database, $Path)
            if (-not $fixture.LockState.Alive) { throw 'migration ran without a live lock lease' }
            $events.Add("file:$([IO.Path]::GetFileName($Path))")
        }

        $result = Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider $sqlFile -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider -UtcNowProvider { [datetime]'2026-07-17T01:02:03Z' }

        ($events -join ',') | Should Be 'file:V001__one.sql,ledger:001,file:V002__two.sql,ledger:002'
        $result.AppliedCount | Should Be 2
        $result.BackupPath | Should Be (Join-Path $fixture.RuntimeRoot 'backups\oakved_codex_feature_12345678\20260717T010203Z.sql')
        (Test-Path -LiteralPath $result.BackupPath -PathType Leaf) | Should Be $true
    }

    It 'does not insert a ledger row for a failed migration file' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'file failure')
        $ledgerInserts = New-Object 'System.Collections.Generic.List[string]'
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { return @() }
            if ($Sql -match "VALUES \('(?<version>\d{3})'") { $ledgerInserts.Add($Matches.version) }
            return ''
        }
        $backup = {
            param($Database, $Path)
            New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
            Set-Content -LiteralPath $Path -Value 'INSERT INTO backed_up VALUES (1);' -Encoding Ascii
        }
        $sqlFile = {
            param($Database, $Path)
            if ([IO.Path]::GetFileName($Path) -eq 'V002__two.sql') { throw 'migration file failed' }
        }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider $sqlFile -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider
        }

        $message | Should Match '^Migration V002__two.sql failed.*operator inspection is required\.$'
        ($ledgerInserts -join ',') | Should Be '001'
        ($fixture.LockState.Events -join ',') | Should Be 'acquire,release,terminate'
    }

    It 'fails closed when the post-migration ledger is not an exact catalog match' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'post verify')
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { return @() }
            return ''
        }
        $backup = {
            param($Database, $Path)
            New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
            Set-Content -LiteralPath $Path -Value 'CREATE TABLE backed_up (id int);' -Encoding Ascii
        }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -SqlFileProvider { param($Database, $Path) } -BackupProvider $backup -LockLeaseProvider $fixture.LockProvider
        }

        $message | Should Be 'Final migration ledger does not match selected branch catalog.'
    }

    It 'surfaces combined lock release and termination failures' {
        $fixture = New-MigrationGateFixture -Root (Join-Path $TestDrive 'combined lock cleanup failure')
        $catalog = @(Get-OakvedMigrationCatalog -Files @($fixture.MigrationOne, $fixture.MigrationTwo) -ContentProvider { param($path) Get-Content -LiteralPath $path -Raw })
        $mysql = {
            param($Database, $Sql)
            if ($Sql -like 'SELECT COUNT(*) FROM information_schema.tables*') { return '1' }
            if ($Sql -like 'SELECT version,*') { return $catalog }
            return ''
        }
        $lockProvider = {
            param($Database, $LockName)
            [pscustomobject]@{
                Acquired = $true
                IsAlive = { param($lease) return $true }
                Release = { param($lease) return $false }
                Terminate = { param($lease) throw 'simulated lock session cleanup failure' }
            }
        }

        $message = Get-CaughtMigrationMessage {
            Invoke-OakvedDatabaseGate -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $fixture.RuntimeRoot -MySqlRootPassword 'fixture' -MySqlCommandProvider $mysql -LockLeaseProvider $lockProvider
        }

        $message | Should Be 'Could not release migration lock for oakved_codex_feature_12345678. Migration lock session termination also failed.'
    }
}

Describe 'Oakved production MySQL wrappers' {
    InModuleScope Oakved.Runtime {
        It 'streams BOM-less non-ASCII stdin bytes through the native runner without text conversion' {
            $inputPath = Join-Path $TestDrive 'native-input.sql'
            $inputBytes = [Text.Encoding]::UTF8.GetBytes("INSERT INTO furniture_name VALUES ('家具');`n")
            [IO.File]::WriteAllBytes($inputPath, $inputBytes)
            $childCommand = 'if ($env:OAKVED_TEST_SECRET -ne "child-only") { exit 3 }; $inputStream = [Console]::OpenStandardInput(); $outputStream = [Console]::OpenStandardOutput(); [Console]::Error.Write("warning-only"); $inputStream.CopyTo($outputStream); $outputStream.Flush()'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{ OAKVED_TEST_SECRET = 'child-only' }
            }

            $nativeResult = Invoke-OakvedNativeProcess -Spec $spec -InputPath $inputPath

            [Convert]::ToBase64String($nativeResult.StdOut) | Should Be ([Convert]::ToBase64String($inputBytes))
            [Text.Encoding]::UTF8.GetString($nativeResult.StdErr) | Should Be 'warning-only'
            $nativeResult.ExitCode | Should Be 0
        }

        It 'keeps the password out of query arguments and parses stdout without stderr pollution' {
            $capture = [pscustomobject]@{ Spec = $null }
            $runner = {
                param($Spec, $InputPath, $OutputPath)
                $capture.Spec = $Spec
                [pscustomobject]@{
                    ExitCode = 0
                    StdOut = [Text.Encoding]::UTF8.GetBytes("1`n")
                    StdErr = [Text.Encoding]::UTF8.GetBytes('mysql: [Warning] password warning')
                }
            }.GetNewClosure()

            $output = Invoke-OakvedDockerMySql -Database 'oakved_test' -Sql 'SELECT 1;' -RootPassword 'super-secret' -NativeProcessRunner $runner

            $output.Trim() | Should Be '1'
            $output | Should Not Match 'Warning'
            ($capture.Spec.Arguments -join ' ') | Should Not Match 'super-secret'
            ($capture.Spec.Arguments -join ',') | Should Match 'exec,-i,-e,MYSQL_PWD,yudao-mysql-local,mysql'
            $capture.Spec.Environment.MYSQL_PWD | Should Be 'super-secret'
        }

        It 'fails a nonzero query without exposing stdout stderr or the password' {
            $runner = {
                param($Spec, $InputPath, $OutputPath)
                [pscustomobject]@{
                    ExitCode = 1
                    StdOut = [Text.Encoding]::UTF8.GetBytes("1`n")
                    StdErr = [Text.Encoding]::UTF8.GetBytes('super-secret warning')
                }
            }

            { Invoke-OakvedDockerMySql -Database 'oakved_test' -Sql 'SELECT 1;' -RootPassword 'super-secret' -NativeProcessRunner $runner } |
                Should Throw 'MySQL command failed in Docker container yudao-mysql-local.'
        }

        It 'streams the original SQL file bytes to the child runner' {
            $path = Join-Path $TestDrive 'bomless-migration.sql'
            $bytes = [Text.Encoding]::UTF8.GetBytes("CREATE TABLE furniture_name (name varchar(20)); -- 家具`n")
            [IO.File]::WriteAllBytes($path, $bytes)
            $capture = [pscustomobject]@{ InputBytes = $null; Spec = $null }
            $runner = {
                param($Spec, $InputPath, $OutputPath)
                $capture.Spec = $Spec
                $capture.InputBytes = [IO.File]::ReadAllBytes($InputPath)
                [pscustomobject]@{
                    ExitCode = 0
                    StdOut = [byte[]]@()
                    StdErr = [Text.Encoding]::UTF8.GetBytes('warning-only')
                }
            }.GetNewClosure()

            $null = Invoke-OakvedSqlFile -Database 'oakved_test' -Path $path -RootPassword 'super-secret' -NativeProcessRunner $runner

            [Convert]::ToBase64String($capture.InputBytes) | Should Be ([Convert]::ToBase64String($bytes))
            ($capture.Spec.Arguments -join ' ') | Should Not Match 'super-secret'
        }

        It 'writes mysqldump stdout bytes directly and excludes stderr warnings from the backup' {
            $path = Join-Path $TestDrive 'backup.sql'
            $dumpBytes = [Text.Encoding]::UTF8.GetBytes("CREATE TABLE furniture_name (name varchar(20));`nINSERT INTO furniture_name VALUES ('家具');`n")
            $capture = [pscustomobject]@{ Spec = $null }
            $runner = {
                param($Spec, $InputPath, $OutputPath)
                $capture.Spec = $Spec
                [IO.File]::WriteAllBytes($OutputPath, $dumpBytes)
                [pscustomobject]@{
                    ExitCode = 0
                    StdOut = [byte[]]@()
                    StdErr = [Text.Encoding]::UTF8.GetBytes('mysqldump warning-only')
                }
            }.GetNewClosure()

            Backup-OakvedDatabase -Database 'oakved_test' -Path $path -RootPassword 'super-secret' -NativeProcessRunner $runner

            [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) | Should Be ([Convert]::ToBase64String($dumpBytes))
            [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($path)) | Should Not Match 'warning-only'
            ($capture.Spec.Arguments -contains '--hex-blob') | Should Be $true
            ($capture.Spec.Arguments -join ' ') | Should Not Match 'super-secret'
        }

        It 'rejects a nonzero stderr-only dump and removes its partial backup' {
            $path = Join-Path $TestDrive 'failed-backup.sql'
            $runner = {
                param($Spec, $InputPath, $OutputPath)
                [IO.File]::WriteAllBytes($OutputPath, [Text.Encoding]::UTF8.GetBytes('partial'))
                [pscustomobject]@{
                    ExitCode = 2
                    StdOut = [byte[]]@()
                    StdErr = [Text.Encoding]::UTF8.GetBytes('mysqldump failed')
                }
            }

            { Backup-OakvedDatabase -Database 'oakved_test' -Path $path -RootPassword 'super-secret' -NativeProcessRunner $runner } |
                Should Throw 'Database backup failed for oakved_test.'
            (Test-Path -LiteralPath $path) | Should Be $false
        }

        It 'holds and releases the advisory lock on the same dedicated session' {
            $state = [pscustomobject]@{
                Alive = $true
                Terminated = $false
                Writes = New-Object 'System.Collections.Generic.List[string]'
                Reads = New-Object 'System.Collections.Generic.Queue[string]'
                Spec = $null
            }
            $state.Reads.Enqueue('1')
            $state.Reads.Enqueue('1')
            $rawSession = [pscustomobject]@{
                WriteLine = { param($line) $state.Writes.Add($line) }.GetNewClosure()
                ReadStdOutLine = { return $state.Reads.Dequeue() }.GetNewClosure()
                IsAlive = { return [bool]$state.Alive }.GetNewClosure()
                Close = { $state.Alive = $false }.GetNewClosure()
                Terminate = { $state.Terminated = $true; $state.Alive = $false }.GetNewClosure()
            }
            $factory = {
                param($Spec)
                $state.Spec = $Spec
                return $rawSession
            }.GetNewClosure()

            $lease = Open-OakvedMySqlLockLease -Database 'oakved_test' -LockName 'oakved_schema_oakved_test' -RootPassword 'super-secret' -NativeSessionFactory $factory

            $lease.Acquired | Should Be $true
            (& $lease.IsAlive) | Should Be $true
            (& $lease.Release) | Should Be $true
            & $lease.Terminate
            ($state.Writes -join ',') | Should Be "SELECT GET_LOCK('oakved_schema_oakved_test',30);,SELECT RELEASE_LOCK('oakved_schema_oakved_test');,quit"
            $state.Terminated | Should Be $true
            ($state.Spec.Arguments -join ' ') | Should Not Match 'super-secret'
            $state.Spec.Environment.MYSQL_PWD | Should Be 'super-secret'
        }

        It 'terminates the dedicated session when advisory lock acquisition fails' {
            $state = [pscustomobject]@{ Alive = $true; Terminated = $false }
            $rawSession = [pscustomobject]@{
                WriteLine = { param($line) }.GetNewClosure()
                ReadStdOutLine = { return '0' }.GetNewClosure()
                IsAlive = { return [bool]$state.Alive }.GetNewClosure()
                Close = { $state.Alive = $false }.GetNewClosure()
                Terminate = { $state.Terminated = $true; $state.Alive = $false }.GetNewClosure()
            }
            $factory = { param($Spec) return $rawSession }.GetNewClosure()

            $lease = Open-OakvedMySqlLockLease -Database 'oakved_test' -LockName 'oakved_schema_oakved_test' -RootPassword 'super-secret' -NativeSessionFactory $factory

            $lease.Acquired | Should Be $false
            $state.Terminated | Should Be $true
        }

        It 'preserves lock acquisition and first cleanup failure when termination reports prior failed cleanup' {
            $rawSession = [pscustomobject]@{
                WriteLine = { param($line) }
                ReadStdOutLine = {
                    throw 'Native session read failed. Native session cleanup also failed: Native process cleanup failed: simulated first termination failure.'
                }
                IsAlive = { return $true }
                Close = { }
                Terminate = { throw 'Native session cleanup previously failed.' }
            }
            $factory = { param($Spec) return $rawSession }.GetNewClosure()

            { Open-OakvedMySqlLockLease -Database 'oakved_test' -LockName 'oakved_schema_oakved_test' -RootPassword 'super-secret' -NativeSessionFactory $factory } |
                Should Throw 'Native session read failed. Native session cleanup also failed: Native process cleanup failed: simulated first termination failure. Lock acquisition session remains unusable because cleanup did not complete.'
        }

        It 'streams actual child stdout bytes directly to OutputPath' {
            $outputPath = Join-Path $TestDrive 'actual-output.sql'
            $expectedBytes = [Text.Encoding]::UTF8.GetBytes("CREATE TABLE furniture_name (name varchar(20)); -- 家具`n")
            $payload = [Convert]::ToBase64String($expectedBytes)
            $childCommand = '$bytes = [Convert]::FromBase64String($env:OAKVED_PAYLOAD); $output = [Console]::OpenStandardOutput(); $output.Write($bytes, 0, $bytes.Length); $output.Flush()'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{ OAKVED_PAYLOAD = $payload }
            }

            $nativeResult = Invoke-OakvedNativeProcess -Spec $spec -OutputPath $outputPath -TimeoutMilliseconds 5000

            $nativeResult.ExitCode | Should Be 0
            [Convert]::ToBase64String([IO.File]::ReadAllBytes($outputPath)) | Should Be ([Convert]::ToBase64String($expectedBytes))
        }

        It 'removes OutputPath when an actual child exits nonzero' {
            $outputPath = Join-Path $TestDrive 'nonzero-output.sql'
            $childCommand = '$bytes = [Text.Encoding]::UTF8.GetBytes("partial"); $output = [Console]::OpenStandardOutput(); $output.Write($bytes, 0, $bytes.Length); $output.Flush(); exit 7'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }

            $nativeResult = Invoke-OakvedNativeProcess -Spec $spec -OutputPath $outputPath -TimeoutMilliseconds 5000

            $nativeResult.ExitCode | Should Be 7
            (Test-Path -LiteralPath $outputPath) | Should Be $false
        }

        It 'bounds a hung child under input backpressure and removes partial output' {
            $inputPath = Join-Path $TestDrive 'large-input.sql'
            $outputPath = Join-Path $TestDrive 'hung-output.sql'
            $pidPath = Join-Path $TestDrive 'hung.pid'
            $readyPath = Join-Path $TestDrive 'hung.ready'
            [IO.File]::WriteAllBytes($inputPath, (New-Object byte[] (2 * 1024 * 1024)))
            $pidPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pidPath))
            $readyPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($readyPath))
            $childCommand = '$pidPath=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("{0}")); $readyPath=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("{1}")); [IO.File]::WriteAllText($pidPath,[string]$PID); $deadline=[datetime]::UtcNow.AddSeconds(10); while (-not [IO.File]::Exists($readyPath)) {{ if ([datetime]::UtcNow -ge $deadline) {{ throw "startup handshake timed out" }}; Start-Sleep -Milliseconds 25 }}; $bytes=[Text.Encoding]::UTF8.GetBytes("partial"); $output=[Console]::OpenStandardOutput(); $output.Write($bytes,0,$bytes.Length); $output.Flush(); Start-Sleep -Seconds 60' -f $pidPayload, $readyPayload
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $helperCommand = '$pidPath=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("{0}")); $readyPath=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("{1}")); $deadline=[datetime]::UtcNow.AddSeconds(10); while (-not [IO.File]::Exists($pidPath)) {{ if ([datetime]::UtcNow -ge $deadline) {{ exit 2 }}; Start-Sleep -Milliseconds 25 }}; [IO.File]::WriteAllText($readyPath,"ready")' -f $pidPayload, $readyPayload
            $helperEncoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($helperCommand))
            $helper = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-NonInteractive', '-EncodedCommand', $helperEncoded) -PassThru -WindowStyle Hidden
            $stopwatch = [Diagnostics.Stopwatch]::StartNew()

            try {
                { Invoke-OakvedNativeProcess -Spec $spec -InputPath $inputPath -OutputPath $outputPath -TimeoutMilliseconds 6000 } |
                    Should Throw 'Native process timed out after 6000 milliseconds.'
            }
            finally {
                $stopwatch.Stop()
                if (-not $helper.WaitForExit(2000)) { $helper.Kill(); $null = $helper.WaitForExit(2000) }
                $helper.Dispose()
            }

            $stopwatch.Elapsed.TotalSeconds | Should BeLessThan 10
            (Test-Path -LiteralPath $outputPath) | Should Be $false
            if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) {
                throw 'Child startup handshake did not create the PID marker.'
            }
            $childPid = [int]([IO.File]::ReadAllText($pidPath))
            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
        }

        It 'uses one actual interactive child for framed requests and bounded cleanup' {
            $childCommand = '[Console]::Error.Write(("w" * 131072)); while (($line = [Console]::In.ReadLine()) -ne $null) { if ($line -like "SELECT GET_LOCK*") { [Console]::Out.WriteLine("1"); [Console]::Out.Flush() } elseif ($line -like "SELECT RELEASE_LOCK*") { [Console]::Out.WriteLine("1"); [Console]::Out.Flush() } elseif ($line -eq "quit") { break } }'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 3000
            $childPid = $session.ProcessId

            & $session.WriteLine "SELECT GET_LOCK('oakved_schema_test',30);"
            (& $session.ReadStdOutLine) | Should Be '1'
            & $session.WriteLine "SELECT RELEASE_LOCK('oakved_schema_test');"
            (& $session.ReadStdOutLine) | Should Be '1'
            & $session.WriteLine 'quit'
            & $session.Close
            & $session.Terminate

            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
        }

        It 'kills and reaps an actual interactive child on read timeout' {
            $pidPath = Join-Path $TestDrive 'interactive-read.pid'
            $childCommand = '[IO.File]::WriteAllText($env:OAKVED_PID_PATH, [string]$PID); Start-Sleep -Seconds 60'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{ OAKVED_PID_PATH = $pidPath }
            }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 500
            $childPid = $session.ProcessId
            $stopwatch = [Diagnostics.Stopwatch]::StartNew()

            { & $session.ReadStdOutLine } | Should Throw 'Native session read timed out after 500 milliseconds.'
            $stopwatch.Stop()

            $stopwatch.Elapsed.TotalSeconds | Should BeLessThan 5
            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
        }

        It 'kills and reaps an actual interactive child on close timeout' {
            $childCommand = 'while (($line = [Console]::In.ReadLine()) -ne $null) { if ($line -eq "quit") { Start-Sleep -Seconds 60 } }'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 1500
            $childPid = $session.ProcessId
            & $session.WriteLine 'quit'

            { & $session.Close } | Should Throw 'Native session close timed out after 1500 milliseconds.'

            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
        }

        It 'kills and reaps an actual interactive child on write timeout' {
            $childCommand = 'Start-Sleep -Seconds 60'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 500
            $childPid = $session.ProcessId
            $largeLine = 'x' * (2 * 1024 * 1024)

            { & $session.WriteLine $largeLine } | Should Throw 'Native session write timed out after 500 milliseconds.'

            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
        }

        It 'reports a deterministic kill exception instead of suppressing it' {
            $fake = New-Object psobject
            $fake | Add-Member -MemberType NoteProperty -Name HasExited -Value $false
            $fake | Add-Member -MemberType ScriptMethod -Name Kill -Value { throw 'simulated secret-bearing kill failure' }
            $fake | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($milliseconds) return $false }

            { Stop-OakvedNativeProcess -Process $fake -ReapTimeoutMilliseconds 25 } |
                Should Throw 'Native process cleanup failed: termination failed; process was not reaped; process is still running.'
        }

        It 'reports an unreaped process that remains alive after termination' {
            $fake = New-Object psobject
            $fake | Add-Member -MemberType NoteProperty -Name HasExited -Value $false
            $fake | Add-Member -MemberType ScriptMethod -Name Kill -Value { }
            $fake | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($milliseconds) return $false }

            { Stop-OakvedNativeProcess -Process $fake -ReapTimeoutMilliseconds 25 } |
                Should Throw 'Native process cleanup failed: process was not reaped; process is still running.'
        }

        It 'preserves a one-shot timeout together with cleanup failure' {
            $childCommand = 'Start-Sleep -Seconds 60'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $stopper = {
                param($process, $reapTimeoutMilliseconds)
                if (-not $process.HasExited) {
                    $process.Kill()
                    $null = $process.WaitForExit(5000)
                }
                throw 'Native process cleanup failed: simulated cleanup report.'
            }

            { Invoke-OakvedNativeProcess -Spec $spec -TimeoutMilliseconds 300 -NativeProcessStopper $stopper } |
                Should Throw 'Native process timed out after 300 milliseconds. Native process cleanup also failed: Native process cleanup failed: simulated cleanup report.'
        }

        It 'retains failed session cleanup state when the child survives and prevents reuse' {
            $childCommand = 'Start-Sleep -Seconds 60'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $stopper = { param($process, $reapTimeoutMilliseconds) throw 'Native process cleanup failed: simulated child survived.' }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 300 -NativeProcessStopper $stopper
            $childPid = $session.ProcessId
            try {
                $message = $null
                try { $null = & $session.ReadStdOutLine } catch { $message = $_.Exception.Message }

                $message | Should Be 'Native session read timed out after 300 milliseconds. Native session cleanup also failed: Native process cleanup failed: simulated child survived.'
                $session.State.Disposed | Should Be $false
                $session.State.CleanupFailed | Should Be $true
                (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Not Be $null
                { & $session.WriteLine 'reuse' } | Should Throw 'Native session cleanup previously failed.'
            }
            finally {
                $survivor = Get-Process -Id $childPid -ErrorAction SilentlyContinue
                if ($null -ne $survivor) {
                    $survivor.Kill()
                    $null = $survivor.WaitForExit(5000)
                    $survivor.Dispose()
                }
            }
        }

        It 'disposes a confirmed-exited session even when the stopper reports cleanup failure' {
            $childCommand = 'Start-Sleep -Seconds 60'
            $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCommand))
            $spec = [pscustomobject]@{
                FileName = 'powershell.exe'
                Arguments = @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand)
                Environment = @{}
            }
            $stopper = {
                param($process, $reapTimeoutMilliseconds)
                if (-not $process.HasExited) {
                    $process.Kill()
                    $null = $process.WaitForExit($reapTimeoutMilliseconds)
                }
                throw 'Native process cleanup failed: simulated termination report.'
            }
            $session = Start-OakvedNativeSession -Spec $spec -TimeoutMilliseconds 300 -NativeProcessStopper $stopper
            $childPid = $session.ProcessId

            { $null = & $session.ReadStdOutLine } |
                Should Throw 'Native session read timed out after 300 milliseconds. Native session cleanup also failed: Native process cleanup failed: simulated termination report.'
            $session.State.Disposed | Should Be $true
            $session.State.CleanupFailed | Should Be $false
            (& $session.IsAlive) | Should Be $false
            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should Be $null
            { & $session.Terminate } | Should Not Throw
        }
    }
}
