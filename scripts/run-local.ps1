$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing .env. Copy .env.example to .env and set the required values."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    if ($line -notmatch '^([^#=\s]+)\s*=\s*(.*)$') {
        throw "Invalid .env line."
    }

    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$required = @(
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD',
    'WALLET_TOKEN_PEPPER',
    'WALLET_BOOTSTRAP_ADMIN_TOKEN'
)

$missing = @($required | Where-Object { -not [Environment]::GetEnvironmentVariable($_, 'Process') })
if ($missing.Count -gt 0) {
    throw "Missing required .env variables: $($missing -join ', '). Add these names with your database values to .env; SPRING_FLYWAY_* variables are no longer used."
}

$env:SPRING_PROFILES_ACTIVE = 'local'
& (Join-Path $PSScriptRoot '..\mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
