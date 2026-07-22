Set-StrictMode -Version Latest

$script:SnapshotMagic = [Text.Encoding]::ASCII.GetBytes('OAKVEDDB1')
$script:SnapshotTagLength = 32
$script:SnapshotSaltLength = 16
$script:SnapshotIvLength = 16
$script:SnapshotIterations = 210000

function New-OakvedPortablePassword {
    [CmdletBinding()]
    param()

    $bytes = New-Object byte[] 24
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }

    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-OakvedDerivedKeys {
    param(
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][byte[]]$Salt,
        [Parameter(Mandatory = $true)][int]$Iterations
    )

    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $Password,
        $Salt,
        $Iterations,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    try {
        $derived = $derive.GetBytes(64)
    }
    finally {
        $derive.Dispose()
    }

    $encryptionKey = New-Object byte[] 32
    $authenticationKey = New-Object byte[] 32
    [Array]::Copy($derived, 0, $encryptionKey, 0, 32)
    [Array]::Copy($derived, 32, $authenticationKey, 0, 32)
    [Array]::Clear($derived, 0, $derived.Length)

    return [pscustomobject]@{
        EncryptionKey = $encryptionKey
        AuthenticationKey = $authenticationKey
    }
}

function New-OakvedSnapshotPrefix {
    param(
        [Parameter(Mandatory = $true)][int]$Iterations,
        [Parameter(Mandatory = $true)][byte[]]$Salt,
        [Parameter(Mandatory = $true)][byte[]]$InitializationVector
    )

    $stream = New-Object IO.MemoryStream
    $writer = New-Object IO.BinaryWriter($stream, [Text.Encoding]::UTF8, $true)
    try {
        $writer.Write($script:SnapshotMagic)
        $writer.Write($Iterations)
        $writer.Write($Salt)
        $writer.Write($InitializationVector)
        $writer.Flush()
        return $stream.ToArray()
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function Get-OakvedSnapshotHmac {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][byte[]]$Prefix,
        [Parameter(Mandatory = $true)][byte[]]$AuthenticationKey,
        [Parameter(Mandatory = $true)][long]$CipherOffset
    )

    $hmac = [Security.Cryptography.HMACSHA256]::new($AuthenticationKey)
    $stream = $null
    try {
        $null = $hmac.TransformBlock($Prefix, 0, $Prefix.Length, $Prefix, 0)
        $stream = [IO.File]::OpenRead($Path)
        $stream.Position = $CipherOffset
        $buffer = New-Object byte[] (1024 * 1024)
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $null = $hmac.TransformBlock($buffer, 0, $read, $buffer, 0)
        }
        $null = $hmac.TransformFinalBlock((New-Object byte[] 0), 0, 0)
        return [byte[]]$hmac.Hash.Clone()
    }
    finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
        $hmac.Dispose()
    }
}

function Test-OakvedFixedTimeEquals {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Left,
        [Parameter(Mandatory = $true)][byte[]]$Right
    )

    if ($Left.Length -ne $Right.Length) {
        return $false
    }

    $difference = 0
    for ($index = 0; $index -lt $Left.Length; $index++) {
        $difference = $difference -bor ($Left[$index] -bxor $Right[$index])
    }
    return $difference -eq 0
}

function Compress-OakvedPortableFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$InputPath,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $input = [IO.File]::OpenRead($InputPath)
    try {
        $output = [IO.File]::Create($OutputPath)
        try {
            $gzip = New-Object IO.Compression.GZipStream(
                $output,
                [IO.Compression.CompressionMode]::Compress,
                $true
            )
            try {
                $input.CopyTo($gzip)
            }
            finally {
                $gzip.Dispose()
            }
        }
        finally {
            $output.Dispose()
        }
    }
    finally {
        $input.Dispose()
    }
}

function Expand-OakvedPortableFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$InputPath,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $input = [IO.File]::OpenRead($InputPath)
    try {
        $gzip = New-Object IO.Compression.GZipStream(
            $input,
            [IO.Compression.CompressionMode]::Decompress,
            $true
        )
        try {
            $output = [IO.File]::Create($OutputPath)
            try {
                $gzip.CopyTo($output)
            }
            finally {
                $output.Dispose()
            }
        }
        finally {
            $gzip.Dispose()
        }
    }
    finally {
        $input.Dispose()
    }
}

function Protect-OakvedPortableFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$InputPath,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [Parameter(Mandatory = $true)][string]$Password,
        [ValidateRange(100000, 2000000)][int]$Iterations = $script:SnapshotIterations
    )

    $salt = New-Object byte[] $script:SnapshotSaltLength
    $iv = New-Object byte[] $script:SnapshotIvLength
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($salt)
        $random.GetBytes($iv)
    }
    finally {
        $random.Dispose()
    }

    $keys = Get-OakvedDerivedKeys -Password $Password -Salt $salt -Iterations $Iterations
    $prefix = New-OakvedSnapshotPrefix -Iterations $Iterations -Salt $salt `
        -InitializationVector $iv
    $cipherOffset = $prefix.Length + $script:SnapshotTagLength

    $aes = [Security.Cryptography.Aes]::Create()
    $input = $null
    $output = $null
    $crypto = $null
    try {
        $aes.KeySize = 256
        $aes.BlockSize = 128
        $aes.Mode = [Security.Cryptography.CipherMode]::CBC
        $aes.Padding = [Security.Cryptography.PaddingMode]::PKCS7
        $aes.Key = $keys.EncryptionKey
        $aes.IV = $iv

        $input = [IO.File]::OpenRead($InputPath)
        $output = [IO.File]::Create($OutputPath)
        $output.Write($prefix, 0, $prefix.Length)
        $emptyTag = New-Object byte[] $script:SnapshotTagLength
        $output.Write($emptyTag, 0, $emptyTag.Length)

        $encryptor = $aes.CreateEncryptor()
        $crypto = [Security.Cryptography.CryptoStream]::new(
            $output,
            $encryptor,
            [Security.Cryptography.CryptoStreamMode]::Write,
            $true
        )
        $input.CopyTo($crypto)
        $crypto.FlushFinalBlock()
    }
    finally {
        if ($null -ne $crypto) {
            $crypto.Dispose()
        }
        if ($null -ne $output) {
            $output.Dispose()
        }
        if ($null -ne $input) {
            $input.Dispose()
        }
        $aes.Dispose()
    }

    try {
        $tag = Get-OakvedSnapshotHmac -Path $OutputPath -Prefix $prefix `
            -AuthenticationKey $keys.AuthenticationKey -CipherOffset $cipherOffset
        $output = [IO.File]::Open($OutputPath, [IO.FileMode]::Open, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $output.Position = $prefix.Length
            $output.Write($tag, 0, $tag.Length)
        }
        finally {
            $output.Dispose()
        }
    }
    finally {
        [Array]::Clear($keys.EncryptionKey, 0, $keys.EncryptionKey.Length)
        [Array]::Clear($keys.AuthenticationKey, 0, $keys.AuthenticationKey.Length)
    }
}

function Unprotect-OakvedPortableFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$InputPath,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $input = [IO.File]::OpenRead($InputPath)
    $reader = New-Object IO.BinaryReader($input, [Text.Encoding]::UTF8, $true)
    try {
        $magic = $reader.ReadBytes($script:SnapshotMagic.Length)
        if (-not (Test-OakvedFixedTimeEquals -Left $magic -Right $script:SnapshotMagic)) {
            throw 'Snapshot format is not recognized.'
        }
        $iterations = $reader.ReadInt32()
        if ($iterations -lt 100000 -or $iterations -gt 2000000) {
            throw 'Snapshot key-derivation settings are invalid.'
        }
        $salt = $reader.ReadBytes($script:SnapshotSaltLength)
        $iv = $reader.ReadBytes($script:SnapshotIvLength)
        $storedTag = $reader.ReadBytes($script:SnapshotTagLength)
        $cipherOffset = $input.Position
        if ($salt.Length -ne $script:SnapshotSaltLength -or
            $iv.Length -ne $script:SnapshotIvLength -or
            $storedTag.Length -ne $script:SnapshotTagLength -or
            $input.Length -le $cipherOffset) {
            throw 'Snapshot is truncated.'
        }
    }
    finally {
        $reader.Dispose()
        $input.Dispose()
    }

    $prefix = New-OakvedSnapshotPrefix -Iterations $iterations -Salt $salt `
        -InitializationVector $iv
    $keys = Get-OakvedDerivedKeys -Password $Password -Salt $salt -Iterations $iterations
    try {
        $actualTag = Get-OakvedSnapshotHmac -Path $InputPath -Prefix $prefix `
            -AuthenticationKey $keys.AuthenticationKey -CipherOffset $cipherOffset
        if (-not (Test-OakvedFixedTimeEquals -Left $storedTag -Right $actualTag)) {
            throw 'Snapshot password is incorrect or the snapshot has been modified.'
        }

        $aes = [Security.Cryptography.Aes]::Create()
        $input = $null
        $output = $null
        $crypto = $null
        try {
            $aes.KeySize = 256
            $aes.BlockSize = 128
            $aes.Mode = [Security.Cryptography.CipherMode]::CBC
            $aes.Padding = [Security.Cryptography.PaddingMode]::PKCS7
            $aes.Key = $keys.EncryptionKey
            $aes.IV = $iv

            $input = [IO.File]::OpenRead($InputPath)
            $input.Position = $cipherOffset
            $decryptor = $aes.CreateDecryptor()
            $crypto = [Security.Cryptography.CryptoStream]::new(
                $input,
                $decryptor,
                [Security.Cryptography.CryptoStreamMode]::Read,
                $true
            )
            $output = [IO.File]::Create($OutputPath)
            $crypto.CopyTo($output)
        }
        finally {
            if ($null -ne $output) {
                $output.Dispose()
            }
            if ($null -ne $crypto) {
                $crypto.Dispose()
            }
            if ($null -ne $input) {
                $input.Dispose()
            }
            $aes.Dispose()
        }
    }
    finally {
        [Array]::Clear($keys.EncryptionKey, 0, $keys.EncryptionKey.Length)
        [Array]::Clear($keys.AuthenticationKey, 0, $keys.AuthenticationKey.Length)
    }
}

Export-ModuleMember -Function @(
    'New-OakvedPortablePassword',
    'Compress-OakvedPortableFile',
    'Expand-OakvedPortableFile',
    'Protect-OakvedPortableFile',
    'Unprotect-OakvedPortableFile'
)
