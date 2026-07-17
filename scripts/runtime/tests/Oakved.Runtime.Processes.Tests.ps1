$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'Oakved.Runtime.psm1'
Import-Module $modulePath -Force

function New-RuntimeFixture {
    param([string]$Root)

    $cloud = Join-Path $Root 'cloud'
    $admin = Join-Path $Root 'admin'
    $storefront = Join-Path $Root 'storefront'
    $migrations = Join-Path $cloud 'sql\mysql\migrations'
    New-Item -ItemType Directory -Path $cloud, $admin, $storefront, $migrations -Force | Out-Null
    $wrapper = Join-Path $cloud 'tools\Invoke-MavenJdk17.ps1'
    $jar = Join-Path $cloud 'yudao-server\target\yudao-server.jar'
    $adminBin = Join-Path $admin 'node_modules\.bin'
    $storefrontBin = Join-Path $storefront 'node_modules\.bin'
    New-Item -ItemType Directory -Path (Split-Path -Parent $wrapper), (Split-Path -Parent $jar), $adminBin, $storefrontBin -Force | Out-Null
    Set-Content -LiteralPath $wrapper -Value '# wrapper' -Encoding Ascii
    Set-Content -LiteralPath $jar -Value 'jar' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $admin 'package.json') -Value '{}' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $admin 'pnpm-lock.yaml') -Value 'lockfileVersion: 9' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $adminBin 'vite.cmd') -Value '@exit /b 0' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $storefront 'package.json') -Value '{}' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $storefront 'package-lock.json') -Value '{}' -Encoding Ascii
    Set-Content -LiteralPath (Join-Path $storefrontBin 'vite.cmd') -Value '@exit /b 0' -Encoding Ascii

    [pscustomobject]@{
        Target = [pscustomobject]@{
            Branch = 'feature/runtime'; Commit = ('a' * 40); Dirty = $false
            Worktree = $Root; RuntimeId = 'feature_runtime_12345678'
        }
        Layout = [pscustomobject]@{
            Worktree = $Root; YudaoCloud = $cloud; AdminUi = $admin; FurnitureWeb = $storefront
            Migrations = $migrations; MavenJdk17 = $wrapper; ServerJar = $jar
        }
        Database = [pscustomobject]@{
            Name = 'oakved_feature_runtime_12345678'; Version = '020'; CatalogVersion = '020'
        }
    }
}

function New-HealthyRuntimeProviders {
    param($Capture)

    $Capture.NextPid = 100
    $Capture | Add-Member -NotePropertyName Ready -NotePropertyValue $false -Force
    return [pscustomobject]@{
        ProcessStarter = {
            param($spec)
            $Capture.Specs += $spec
            $Capture.NextPid++
            if ($spec.Role -eq 'storefront') { $Capture.Ready = $true }
            [pscustomobject]@{ Id = $Capture.NextPid; StartTime = [datetime]'2026-07-17T01:00:00Z' }
        }.GetNewClosure()
        ProcessProvider = {
            param($id)
            [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-07-17T01:00:00Z' }
        }
        ProcessTreeProvider = { param($id) @($id, ($id + 1000)) }
        ListenerProvider = {
            if (-not $Capture.Ready) { return @() }
            @(
                [pscustomobject]@{ Port = 48080; Pid = ($Capture.NextPid + 998) },
                [pscustomobject]@{ Port = 80; Pid = ($Capture.NextPid + 999) },
                [pscustomobject]@{ Port = 5173; Pid = ($Capture.NextPid + 1000) }
            )
        }
        HttpProvider = {
            param($url)
            if ($url -match 'get-id-by-name') { return [pscustomobject]@{ StatusCode = 200; Content = '{"code":0,"data":1}' } }
            [pscustomobject]@{ StatusCode = 200; Content = 'ok' }
        }
    }
}

Describe 'Oakved managed lifecycle primitives' {
    It 'runs a managed command with shell metacharacters safely preserved and captures its logs' {
        $stdout = Join-Path $TestDrive 'managed.stdout.log'
        $stderr = Join-Path $TestDrive 'managed.stderr.log'
        $spec = [pscustomobject]@{
            Role = 'probe'; FilePath = 'powershell.exe'; Arguments = @('-NoProfile', '-Command', "[Console]::WriteLine('managed&process ok')")
            WorkingDirectory = $TestDrive; Environment = @{}; StdOutLog = $stdout; StdErrLog = $stderr
        }
        $module = Get-Module Oakved.Runtime
        $null = & $module { param($innerSpec) Start-OakvedManagedProcess -Spec $innerSpec } $spec
        $deadline = [datetime]::UtcNow.AddSeconds(5)
        while ([datetime]::UtcNow -lt $deadline) {
            if ((Test-Path -LiteralPath $stdout) -and (Get-Item -LiteralPath $stdout).Length -gt 0) { break }
            Start-Sleep -Milliseconds 50
        }
        (Get-Content -LiteralPath $stdout -Raw) | Should Match 'managed&process ok'
    }

    It 'treats an already exited managed PID as stopped' {
        $module = Get-Module Oakved.Runtime
        { & $module { Stop-OakvedManagedProcess -Pid 2147483647 } } | Should Not Throw
    }

    It 'refuses an unknown fixed-port owner' {
        { Assert-OakvedPortsAvailable -Ports @(80, 5173, 48080) -ListenerProvider { @([pscustomobject]@{ Port = 5173; Pid = 999 }) } -ManagedPids @() } |
            Should Throw 'Port 5173 is owned by unmanaged PID 999.'
    }

    It 'accepts a fixed-port owner in a managed process tree' {
        { Assert-OakvedPortsAvailable -Ports @(5173) -ListenerProvider { @([pscustomobject]@{ Port = 5173; Pid = 999 }) } `
                -ManagedPids @(42) -ProcessTreeProvider { param($id) @(42, 999) } } | Should Not Throw
    }

    It 'does not stop a reused PID with a different start time' {
        $manifest = [pscustomobject]@{ Processes = @([pscustomobject]@{ Pid = 42; StartTime = '2026-01-01T00:00:00Z'; Role = 'backend' }) }
        $script:stopped = @()
        Stop-OakvedRuntime -Manifest $manifest `
            -ProcessProvider { param($id) [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-01-02T00:00:00Z' } } `
            -Stopper { param($id) $script:stopped += $id }
        $script:stopped.Count | Should Be 0
    }

    It 'stops only a PID whose recorded start time still matches' {
        $manifest = [pscustomobject]@{ Processes = @([pscustomobject]@{ Pid = 42; StartTime = '2026-01-01T00:00:00.0000000Z'; Role = 'backend' }) }
        $script:stopped = @()
        Stop-OakvedRuntime -Manifest $manifest `
            -ProcessProvider { param($id) [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-01-01T00:00:00Z' } } `
            -Stopper { param($id) $script:stopped += $id }
        $script:stopped | Should Be @(42)
    }

    It 'writes a manifest through a same-directory temporary file then atomically replaces it' {
        $script:writes = @()
        Write-OakvedManifest -Manifest @{ branch = 'main' } -Path 'D:\state\runtime.json' `
            -Writer { param($path, $content) $script:writes += $path } `
            -Mover { param($source, $destination) $script:writes += $destination }
        $script:writes[0] | Should Be 'D:\state\runtime.json.tmp'
        $script:writes[-1] | Should Be 'D:\state\runtime.json'
    }
}

Describe 'Get-OakvedBuildFingerprint' {
    It 'changes provenance for commit dirty state catalog and relevant lockfile content' {
        $target = [pscustomobject]@{ Commit = 'aaa'; Dirty = $false }
        $files = [ordered]@{ Backend = @('pom.xml'); Admin = @('pnpm-lock.yaml'); Storefront = @('package-lock.json') }
        $content = @{ 'pom.xml' = 'a'; 'pnpm-lock.yaml' = 'b'; 'package-lock.json' = 'c' }
        $provider = { param($path) $content[$path] }.GetNewClosure()
        $first = Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider

        $target.Commit = 'bbb'
        (Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider).Value | Should Not Be $first.Value
        $target.Commit = 'aaa'; $target.Dirty = $true
        (Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider).Value | Should Not Be $first.Value
        $target.Dirty = $false
        (Get-OakvedBuildFingerprint -Target $target -CatalogVersion '021' -RelevantFiles $files -ContentProvider $provider).Value | Should Not Be $first.Value
        $content['pnpm-lock.yaml'] = 'changed'
        (Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider).Value | Should Not Be $first.Value
        $content['pnpm-lock.yaml'] = 'b'; $content['package-lock.json'] = 'changed'
        (Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider).Value | Should Not Be $first.Value
    }

    It 'keeps backend component stable when only a frontend lockfile changes' {
        $target = [pscustomobject]@{ Commit = 'aaa'; Dirty = $false }
        $files = [ordered]@{ Backend = @('pom.xml'); Admin = @('pnpm-lock.yaml'); Storefront = @('package-lock.json') }
        $content = @{ 'pom.xml' = 'a'; 'pnpm-lock.yaml' = 'b'; 'package-lock.json' = 'c' }
        $provider = { param($path) $content[$path] }.GetNewClosure()
        $first = Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider
        $content['pnpm-lock.yaml'] = 'changed'
        $second = Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider
        $second.Backend | Should Be $first.Backend
    }

    It 'changes the backend component when a backend build file changes' {
        $target = [pscustomobject]@{ Commit = 'aaa'; Dirty = $false }
        $files = [ordered]@{ Backend = @('pom.xml'); Admin = @('pnpm-lock.yaml'); Storefront = @('package-lock.json') }
        $content = @{ 'pom.xml' = 'a'; 'pnpm-lock.yaml' = 'b'; 'package-lock.json' = 'c' }
        $provider = { param($path) $content[$path] }.GetNewClosure()
        $first = Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider
        $content['pom.xml'] = 'changed'
        $second = Get-OakvedBuildFingerprint -Target $target -CatalogVersion '020' -RelevantFiles $files -ContentProvider $provider
        $second.Backend | Should Not Be $first.Backend
    }
}

Describe 'Start-OakvedRuntime orchestration' {
    It 'rejects any selected build or working path outside the resolved worktree before the database gate' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'contained')
        $fixture.Layout.AdminUi = Join-Path $TestDrive 'other-worktree\admin'
        $script:gateCalled = $false
        { Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot (Join-Path $TestDrive 'contained-state') -MySqlRootPassword 'secret' `
                -DatabaseGateProvider { $script:gateCalled = $true; $fixture.Database } -ListenerProvider { @() } } |
            Should Throw 'Selected runtime path is outside the resolved worktree.'
        $script:gateCalled | Should Be $false
    }

    It 'runs the database gate before build or any process starter' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'ordered')
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100; Events = @() }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot (Join-Path $TestDrive 'state') -MySqlRootPassword 'secret' `
            -DatabaseGateProvider ({ $capture.Events += 'database'; $fixture.Database }.GetNewClosure()) `
            -BuildProvider ({ param($spec) $capture.Events += 'build' }.GetNewClosure()) `
            -ProcessStarter ({ param($spec) $capture.Events += "start:$($spec.Role)"; & $providers.ProcessStarter $spec }.GetNewClosure()) `
            -ProcessProvider $providers.ProcessProvider -ProcessTreeProvider $providers.ProcessTreeProvider `
            -ListenerProvider $providers.ListenerProvider -HttpProvider $providers.HttpProvider | Out-Null
        $capture.Events[0] | Should Be 'database'
        ($capture.Events -join ',') | Should Match '^database,(build,)?start:backend'
    }

    It 'builds the backend only when its fingerprint changes using the selected JDK 17 Maven wrapper' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'fingerprint')
        $runtimeRoot = Join-Path $TestDrive 'fingerprint-state'
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100; Builds = @() }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        $common = @{
            Target = $fixture.Target; Layout = $fixture.Layout; RuntimeRoot = $runtimeRoot; MySqlRootPassword = 'secret'
            DatabaseGateProvider = ({ $fixture.Database }.GetNewClosure())
            ProcessStarter = $providers.ProcessStarter; ProcessProvider = $providers.ProcessProvider
            ProcessTreeProvider = $providers.ProcessTreeProvider; ListenerProvider = $providers.ListenerProvider
            HttpProvider = $providers.HttpProvider
            BuildProvider = { param($spec) $capture.Builds += $spec }.GetNewClosure()
        }
        Start-OakvedRuntime @common | Out-Null
        Stop-OakvedRuntime -ManifestPath (Join-Path $runtimeRoot 'runtime.json') -ProcessProvider $providers.ProcessProvider -Stopper { param($id) }
        $capture.Ready = $false
        Start-OakvedRuntime @common | Out-Null
        $capture.Builds.Count | Should Be 1
        $capture.Builds[0].Arguments[3] | Should Be '-EncodedCommand'
        $decodedCommand = [Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($capture.Builds[0].Arguments[4]))
        $decodedCommand | Should Match ([regex]::Escape($fixture.Layout.MavenJdk17))
        $decodedCommand | Should Match ([regex]::Escape("-MavenArgs @('-pl', 'yudao-server', '-am', '-DskipTests', 'clean', 'package')"))
        $capture.Builds[0].WorkingDirectory | Should Be $fixture.Layout.YudaoCloud
    }

    It 'runs the selected wrapper through the default bounded build runner' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'default-build')
        $runtimeRoot = Join-Path $TestDrive 'default-build-state'
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100 }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $runtimeRoot -MySqlRootPassword 'secret' `
            -DatabaseGateProvider ({ $fixture.Database }.GetNewClosure()) `
            -ProcessStarter $providers.ProcessStarter -ProcessProvider $providers.ProcessProvider `
            -ProcessTreeProvider $providers.ProcessTreeProvider -ListenerProvider $providers.ListenerProvider -HttpProvider $providers.HttpProvider | Out-Null
        (Test-Path -LiteralPath (Join-Path $runtimeRoot 'logs\backend-build.stdout.log')) | Should Be $true
        (Test-Path -LiteralPath (Join-Path $runtimeRoot 'logs\backend-build.stderr.log')) | Should Be $true
    }

    It 'bootstraps missing frontend dependencies before starting processes' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'dependencies')
        Remove-Item -LiteralPath (Join-Path $fixture.Layout.AdminUi 'node_modules\.bin\vite.cmd') -Force
        Remove-Item -LiteralPath (Join-Path $fixture.Layout.FurnitureWeb 'node_modules\.bin\vite.cmd') -Force
        $runtimeRoot = Join-Path $TestDrive 'dependency-state'
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100; Builds = @() }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $runtimeRoot -MySqlRootPassword 'secret' `
            -DatabaseGateProvider ({ $fixture.Database }.GetNewClosure()) `
            -BuildProvider ({ param($spec) $capture.Builds += $spec }.GetNewClosure()) `
            -ProcessStarter $providers.ProcessStarter -ProcessProvider $providers.ProcessProvider `
            -ProcessTreeProvider $providers.ProcessTreeProvider -ListenerProvider $providers.ListenerProvider `
            -HttpProvider $providers.HttpProvider | Out-Null
        (@($capture.Builds | ForEach-Object Role) -join ',') | Should Match 'admin-dependencies'
        (@($capture.Builds | ForEach-Object Role) -join ',') | Should Match 'storefront-dependencies'
        ($capture.Builds | Where-Object Role -eq 'admin-dependencies').Arguments -join ' ' | Should Be 'install --frozen-lockfile'
        ($capture.Builds | Where-Object Role -eq 'storefront-dependencies').Arguments -join ' ' | Should Be 'ci'
        [IO.Path]::IsPathRooted([string](($capture.Builds | Where-Object Role -eq 'admin-dependencies').FilePath)) | Should Be $true
        [IO.Path]::IsPathRooted([string](($capture.Builds | Where-Object Role -eq 'storefront-dependencies').FilePath)) | Should Be $true
    }

    It 'uses exact strict-port child commands selected working directories and cache roots' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'commands')
        $runtimeRoot = Join-Path $TestDrive 'command-state'
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100 }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $runtimeRoot -MySqlRootPassword 'not-in-commands' `
            -DatabaseGateProvider ({ $fixture.Database }.GetNewClosure()) -BuildProvider { param($spec) } `
            -ProcessStarter $providers.ProcessStarter -ProcessProvider $providers.ProcessProvider `
            -ProcessTreeProvider $providers.ProcessTreeProvider -ListenerProvider $providers.ListenerProvider -HttpProvider $providers.HttpProvider | Out-Null

        $backend = $capture.Specs | Where-Object Role -eq 'backend'
        $admin = $capture.Specs | Where-Object Role -eq 'admin'
        $storefront = $capture.Specs | Where-Object Role -eq 'storefront'
        $backend.WorkingDirectory | Should Be $fixture.Layout.YudaoCloud
        ($backend.Arguments -join ' ') | Should Match '--spring.profiles.active=local'
        ($backend.Arguments -join ' ') | Should Match 'jdbc:mysql://127.0.0.1:3306/oakved_feature_runtime_12345678'
        (($backend.Arguments -join ' ') + ($backend.Environment.Values -join ' ')) | Should Not Match 'not-in-commands'
        $admin.FilePath | Should Be 'pnpm.cmd'
        ($admin.Arguments -join ' ') | Should Be 'dev -- --host 0.0.0.0 --port 80 --strictPort'
        $admin.WorkingDirectory | Should Be $fixture.Layout.AdminUi
        $admin.Environment.VITE_BASE_URL | Should Be 'http://127.0.0.1:48080'
        $admin.Environment.VITE_FURNITURE_WEB_URL | Should Be 'http://127.0.0.1:5173'
        $admin.Environment.VITE_CACHE_DIR | Should Match ([regex]::Escape($runtimeRoot))
        $storefront.FilePath | Should Be 'npm.cmd'
        ($storefront.Arguments -join ' ') | Should Be 'run dev -- --host 127.0.0.1 --port 5173 --strictPort'
        $storefront.WorkingDirectory | Should Be $fixture.Layout.FurnitureWeb
        $storefront.Environment.VITE_YUDAO_APP_API_BASE | Should Be 'http://127.0.0.1:48080/app-api'
        $storefront.Environment.VITE_CACHE_DIR | Should Match ([regex]::Escape($runtimeRoot))
    }

    It 'rolls back already started children when a later starter fails' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'rollback')
        $script:stopped = @()
        $rollback = [pscustomobject]@{ Count = 0; Database = $fixture.Database }
        $databaseGate = { $rollback.Database }.GetNewClosure()
        $starter = { param($spec) $rollback.Count++; if ($rollback.Count -eq 2) { throw 'admin failed' }; [pscustomobject]@{ Id = 42; StartTime = [datetime]'2026-01-01T00:00:00Z' } }.GetNewClosure()
        { Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot (Join-Path $TestDrive 'rollback-state') -MySqlRootPassword 'secret' `
                -DatabaseGateProvider $databaseGate -BuildProvider { param($spec) } `
                -ProcessStarter $starter `
                -ProcessProvider { param($id) [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-01-01T00:00:00Z' } } `
                -Stopper { param($id) $script:stopped += $id } -ListenerProvider { @() } -HttpProvider { param($url) } } | Should Throw 'admin failed'
        $script:stopped | Should Be @(42)
    }

    It 'rolls back all children and leaves no manifest when health times out' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'timeout')
        $runtimeRoot = Join-Path $TestDrive 'timeout-state'
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100 }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        $script:stopped = @()
        $timeoutDatabase = $fixture.Database
        $databaseGate = { $timeoutDatabase }.GetNewClosure()
        { Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot $runtimeRoot -MySqlRootPassword 'secret' `
                -DatabaseGateProvider $databaseGate -BuildProvider { param($spec) } `
                -ProcessStarter $providers.ProcessStarter -ProcessProvider $providers.ProcessProvider `
                -ProcessTreeProvider $providers.ProcessTreeProvider -ListenerProvider $providers.ListenerProvider `
                -HttpProvider { param($url) [pscustomobject]@{ StatusCode = 503; Content = 'down' } } `
                -Stopper { param($id) $script:stopped += $id } -HealthTimeoutMilliseconds 1 -SleepProvider { param($ms) } } | Should Throw 'Runtime health check timed out.'
        $script:stopped.Count | Should Be 3
        Test-Path -LiteralPath (Join-Path $runtimeRoot 'runtime.json') | Should Be $false
    }

    It 'writes the manifest only after every health gate succeeds' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'manifest-order')
        $capture = [pscustomobject]@{ Specs = @(); NextPid = 100; Events = @() }
        $providers = New-HealthyRuntimeProviders -Capture $capture
        Start-OakvedRuntime -Target $fixture.Target -Layout $fixture.Layout -RuntimeRoot (Join-Path $TestDrive 'manifest-state') -MySqlRootPassword 'secret' `
            -DatabaseGateProvider ({ $fixture.Database }.GetNewClosure()) -BuildProvider { param($spec) } `
            -ProcessStarter $providers.ProcessStarter -ProcessProvider $providers.ProcessProvider `
            -ProcessTreeProvider $providers.ProcessTreeProvider -ListenerProvider $providers.ListenerProvider `
            -HttpProvider { param($url) $capture.Events += "health:$url"; & $providers.HttpProvider $url }.GetNewClosure() `
            -ManifestWriter { param($manifest, $path) $capture.Events += 'manifest' }.GetNewClosure() | Out-Null
        $capture.Events[-1] | Should Be 'manifest'
        ($capture.Events | Where-Object { $_ -like 'health:*' }).Count | Should BeGreaterThan 2
    }
}

Describe 'Get-OakvedRuntimeStatus' {
    It 'returns unhealthy nonzero-compatible status for corrupt and stale manifests' {
        $corrupt = Join-Path $TestDrive 'corrupt.json'
        Set-Content -LiteralPath $corrupt -Value '{bad' -Encoding Ascii
        $bad = Get-OakvedRuntimeStatus -ManifestPath $corrupt
        $bad.Healthy | Should Be $false
        $bad.ExitCode | Should Be 1

        $incomplete = Get-OakvedRuntimeStatus -Manifest ([pscustomobject]@{ RuntimeId = 'partial' })
        $incomplete.Healthy | Should Be $false
        $incomplete.ExitCode | Should Be 1
        ($incomplete.Mismatches -join ',') | Should Match 'manifest missing Branch'

        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'status')
        $manifest = [pscustomobject]@{
            RuntimeId = $fixture.Target.RuntimeId; Branch = 'different'; Commit = $fixture.Target.Commit
            Dirty = $false; Worktree = $fixture.Target.Worktree; Database = $fixture.Database
            CatalogVersion = '020'; Ports = @(80, 5173, 48080); Processes = @()
        }
        $stale = Get-OakvedRuntimeStatus -Manifest $manifest -Target $fixture.Target
        $stale.Healthy | Should Be $false
        $stale.ExitCode | Should Be 1
        ($stale.Mismatches -join ',') | Should Match 'branch'
    }

    It 'proves complete provenance process start times ports health and database catalog equality' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'healthy-status')
        $processes = @(
            [pscustomobject]@{ Pid = 101; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'backend'; Port = 48080 },
            [pscustomobject]@{ Pid = 102; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'admin'; Port = 80 },
            [pscustomobject]@{ Pid = 103; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'storefront'; Port = 5173 }
        )
        $manifest = [pscustomobject]@{
            RuntimeId = $fixture.Target.RuntimeId; Branch = $fixture.Target.Branch; Commit = $fixture.Target.Commit
            Dirty = $fixture.Target.Dirty; Worktree = $fixture.Target.Worktree; Database = $fixture.Database
            CatalogVersion = '020'; Ports = @(80, 5173, 48080); Processes = $processes; BuildFingerprint = 'fingerprint'
        }
        $status = Get-OakvedRuntimeStatus -Manifest $manifest -Target $fixture.Target `
            -DatabaseVersionProvider { param($database) '020' } `
            -ProcessProvider { param($id) [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-07-17T01:00:00Z' } } `
            -ProcessTreeProvider { param($id) @($id, ($id + 1000)) } `
            -ListenerProvider { @(
                    [pscustomobject]@{ Port = 48080; Pid = 1101 }, [pscustomobject]@{ Port = 80; Pid = 1102 }, [pscustomobject]@{ Port = 5173; Pid = 1103 }
                ) } `
            -HttpProvider { param($url) if ($url -match 'get-id-by-name') { [pscustomobject]@{ StatusCode = 200; Content = '{"code":0,"data":1}' } } else { [pscustomobject]@{ StatusCode = 200; Content = 'ok' } } }
        $status.Healthy | Should Be $true
        $status.ExitCode | Should Be 0
        $status.Branch | Should Be $fixture.Target.Branch
        $status.Commit | Should Be $fixture.Target.Commit
        $status.Worktree | Should Be $fixture.Target.Worktree
        $status.RuntimeId | Should Be $fixture.Target.RuntimeId
        $status.Database | Should Be $fixture.Database.Name
        $status.CatalogVersion | Should Be '020'
        $status.Processes.Count | Should Be 3
        $status.Ports.Count | Should Be 3
        $status.Health.Backend | Should Be $true
        $status.Health.Admin | Should Be $true
        $status.Health.Storefront | Should Be $true
    }

    It 'fails closed when live database-ledger and resolved-target proof are not supplied' {
        $fixture = New-RuntimeFixture -Root (Join-Path $TestDrive 'unverified-database')
        $manifest = [pscustomobject]@{
            RuntimeId = $fixture.Target.RuntimeId; Branch = $fixture.Target.Branch; Commit = $fixture.Target.Commit
            Dirty = $fixture.Target.Dirty; Worktree = $fixture.Target.Worktree; Database = $fixture.Database
            CatalogVersion = '020'; Ports = @(80, 5173, 48080); Processes = @(
                [pscustomobject]@{ Pid = 101; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'backend' },
                [pscustomobject]@{ Pid = 102; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'admin' },
                [pscustomobject]@{ Pid = 103; StartTime = '2026-07-17T01:00:00.0000000Z'; Role = 'storefront' }
            )
        }
        $status = Get-OakvedRuntimeStatus -Manifest $manifest `
            -ProcessProvider { param($id) [pscustomobject]@{ Id = $id; StartTime = [datetime]'2026-07-17T01:00:00Z' } } `
            -ProcessTreeProvider { param($id) @($id, ($id + 1000)) } `
            -ListenerProvider { @(
                    [pscustomobject]@{ Port = 48080; Pid = 1101 }, [pscustomobject]@{ Port = 80; Pid = 1102 }, [pscustomobject]@{ Port = 5173; Pid = 1103 }
                ) } `
            -HttpProvider { param($url) if ($url -match 'actuator') { [pscustomobject]@{ StatusCode = 200; Content = '{"status":"UP"}' } } else { [pscustomobject]@{ StatusCode = 200; Content = 'ok' } } }
        $status.Healthy | Should Be $false
        $status.ExitCode | Should Be 1
        ($status.Mismatches -join ',') | Should Match 'database ledger unverified'
        ($status.Mismatches -join ',') | Should Match 'target provenance unverified'
    }
}
