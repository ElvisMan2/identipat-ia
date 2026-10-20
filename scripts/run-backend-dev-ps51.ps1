$ErrorActionPreference = "Stop"
if (Test-Path Variable:\PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Import-DotEnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        $lineNumber++
        $usefulLine = $line.TrimStart()
        if ([string]::IsNullOrWhiteSpace($usefulLine) -or $usefulLine.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 0) {
            throw "Line $lineNumber invalid in .env: missing '=' separator."
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        if ([string]::IsNullOrWhiteSpace($name)) {
            throw "Line $lineNumber invalid in .env: variable name is empty."
        }

        $value = $line.Substring($separatorIndex + 1).Trim()
        if ($value.Length -ge 2) {
            $firstCharacter = $value[0]
            $lastCharacter = $value[$value.Length - 1]
            if (($firstCharacter -eq '"' -and $lastCharacter -eq '"') -or
                ($firstCharacter -eq "'" -and $lastCharacter -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$envFile = Join-Path $repoRoot ".env"
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $backendDir "mvnw.cmd"
$frontendBrowserDir = Join-Path $repoRoot "frontend\dist\identipat-ia\browser"
$staticDir = Join-Path $backendDir "src\main\resources\static"

Write-Host "Building frontend..."
Push-Location $frontendDir
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

Write-Host "Frontend build completed"
Write-Host "Copying frontend static files..."

if (-not (Test-Path -LiteralPath $frontendBrowserDir -PathType Container)) {
    throw "Frontend build not found at frontend\dist\identipat-ia\browser. Run the Angular build first."
}

if (-not (Test-Path -LiteralPath $staticDir -PathType Container)) {
    New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
}

Copy-Item `
    -Path (Join-Path $frontendBrowserDir "*") `
    -Destination $staticDir `
    -Recurse `
    -Force

Write-Host "Frontend static files copied"

Write-Host "IDENTIPAT-IA - Backend DEV (PowerShell 5.1)"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing .env. Create it from .env.example and fill in the required values."
}

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper not found at backend\mvnw.cmd."
}

Import-DotEnvFile -Path $envFile
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "Spring profile: dev"
Write-Host ".env: loaded"

$aiEnabled = if ([string]::IsNullOrWhiteSpace($env:IDENTIPAT_AI_ENABLED)) {
    $true
} else {
    $env:IDENTIPAT_AI_ENABLED.Trim().Equals("true", [System.StringComparison]::OrdinalIgnoreCase)
}

$historicalApiKeyPlaceholder = "replace-with-local-openai-key"
if ($aiEnabled -and
    ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -or
     $env:OPENAI_API_KEY.Trim().Equals($historicalApiKeyPlaceholder, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "OPENAI_API_KEY is not configured in .env"
}

if ($aiEnabled) {
    Write-Host "OPENAI_API_KEY: configured"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not available. Install Docker Desktop and add 'docker' to PATH."
}

& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Engine is not responding (exit code $LASTEXITCODE)."
}

Push-Location $repoRoot
try {
    & docker compose up -d db
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start Compose service 'db' (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

Write-Host "PostgreSQL DEV: ready"
Write-Host "Starting Spring Boot..."

$mavenExitCode = 1
Push-Location $backendDir
try {
    & $mavenWrapper clean
    if ($LASTEXITCODE -ne 0) {
        throw "Maven clean failed (exit code $LASTEXITCODE)."
    }

    & $mavenWrapper spring-boot:run
    $mavenExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $mavenExitCode
