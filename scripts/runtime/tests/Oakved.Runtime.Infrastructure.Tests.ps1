Import-Module (Join-Path (Split-Path -Parent $PSScriptRoot) 'Oakved.Runtime.psm1') -Force

Describe 'ERP infrastructure endpoints' {
    It 'reads active ports only from the two named ERP containers with a finite deadline' {
        $module = Get-Module Oakved.Runtime
        $runner = {
            param($spec, $inputPath, $outputPath, $timeout)
            if ($timeout -ne 15000) { throw 'Missing bounded inspect timeout' }
            if ($spec.Arguments[2] -notmatch 'NetworkSettings.Ports' -or $spec.Arguments[2] -match 'HostConfig') { throw 'Inspect must use actual active ports' }
            $json = switch ($spec.Arguments[-1]) {
                'yudao-mysql-local' { '{"Running":true,"Ports":{"3306/tcp":[{"HostIp":"127.0.0.1","HostPort":"13306"}]}}' }
                'yudao-redis-local' { '{"Running":true,"Ports":{"6379/tcp":[{"HostIp":"0.0.0.0","HostPort":"16379"},{"HostIp":"::","HostPort":"16379"}]}}' }
                default { throw 'unexpected container' }
            }
            [pscustomobject]@{ ExitCode = 0; StdOut = [Text.Encoding]::UTF8.GetBytes($json) }
        }
        $result = & $module { param($runner) Get-OakvedInfrastructureEndpoints -NativeProcessRunner $runner } $runner
        $result.MySqlPort | Should Be 13306
        $result.RedisPort | Should Be 16379
    }

    It 'rejects absent, empty, remote-only and ambiguous bindings instead of falling back to default ports' {
        $module = Get-Module Oakved.Runtime
        foreach ($ports in @('{}', '{"3306/tcp":null}', '{"3306/tcp":[]}',
            '{"3306/tcp":[{"HostIp":"192.168.1.5","HostPort":"3306"}]}',
            '{"3306/tcp":[{"HostIp":"127.0.0.1","HostPort":"3306"},{"HostIp":"127.0.0.1","HostPort":"13306"}]}')) {
            $json = '{"Running":true,"Ports":' + $ports + '}'
            $runner = { [pscustomobject]@{ ExitCode = 0; StdOut = [Text.Encoding]::UTF8.GetBytes($json) } }.GetNewClosure()
            { & $module { param($runner) Get-OakvedInfrastructureEndpoints -NativeProcessRunner $runner } $runner } | Should Throw 'no unique active localhost port'
        }
    }

    It 'rejects stopped containers and inspect errors' {
        $module = Get-Module Oakved.Runtime
        { & $module { Get-OakvedInfrastructureEndpoints -NativeProcessRunner {
            [pscustomobject]@{ ExitCode = 0; StdOut = [Text.Encoding]::UTF8.GetBytes('{"Running":false,"Ports":{}}') }
        } } } | Should Throw 'is stopped'
        { & $module { Get-OakvedInfrastructureEndpoints -NativeProcessRunner {
            [pscustomobject]@{ ExitCode = 1; StdOut = [byte[]]@() }
        } } } | Should Throw 'Cannot inspect ERP container'
    }
}
