$ErrorActionPreference = "Stop"
if (Test-Path Variable:\PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Import-DotEnvFile {
    param([Parameter(Mandatory = $true)][string]$Path)

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

function Wait-PreprocessingService {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$TimeoutSeconds = 60
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 5
            if ($health.status -eq "UP" -and $health.service -eq "preprocessing-service") {
                return $true
            }
        } catch {
            # The service can refuse connections while Docker finishes starting it.
        }

        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)

    return $false
}

function Wait-AudioTranscriptionService {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 5
            if ($health.status -eq "UP" -and $health.service -eq "audio-transcription") {
                return $true
            }
        } catch {
            # The model can still be downloading/loading after general health is available.
        }

        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)

    return $false
}

function Show-PreprocessingDiagnostics {
    Push-Location $repoRoot
    try {
        Write-Host "preprocessing-service Compose status:"
        & docker compose ps preprocessing
        Write-Host "Last sanitized preprocessing-service logs:"
        & docker compose logs --tail 50 preprocessing |
            ForEach-Object {
                $_ -replace '(?i)(token|secret|password|api[_-]?key)=\S+', '$1=<redacted>'
            }
    } finally {
        Pop-Location
    }
}

function Wait-TcpPort {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSeconds = 60,
        [Parameter(Mandatory = $true)][System.Management.Automation.Job]$Job
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if ($Job.State -eq "Failed" -or $Job.State -eq "Stopped") {
            Receive-Job -Job $Job -Keep -ErrorAction SilentlyContinue
            throw "Spring Boot stopped before port $Port became available."
        }

        $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($listener) {
            return $true
        }

        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)

    Receive-Job -Job $Job -Keep -ErrorAction SilentlyContinue
    return $false
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$envFile = Join-Path $repoRoot ".env"
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $backendDir "mvnw.cmd"
$frontendPackage = Join-Path $frontendDir "package.json"

Write-Host "IDENTIPAT-IA - DEV con Angular hot reload (PowerShell 5.1)"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing .env. Create it from .env.example and fill in the required values."
}

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper not found at backend\mvnw.cmd."
}

if (-not (Test-Path -LiteralPath $frontendPackage -PathType Leaf)) {
    throw "Frontend package.json not found at frontend\package.json."
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm is not available. Install Node.js and add 'npm' to PATH."
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
$aiProvider = if ([string]::IsNullOrWhiteSpace($env:IDENTIPAT_AI_PROVIDER)) {
    "openai"
} else {
    $env:IDENTIPAT_AI_PROVIDER.Trim().ToLowerInvariant()
}

if ($aiEnabled -and $aiProvider -notin @("openai", "gemini")) {
    throw "IDENTIPAT_AI_PROVIDER must be 'openai' or 'gemini'"
}

if ($aiEnabled -and $aiProvider -eq "openai" -and
    ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -or
     $env:OPENAI_API_KEY.Trim().Equals($historicalApiKeyPlaceholder, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "OPENAI_API_KEY is not configured in .env"
}

if ($aiEnabled -and $aiProvider -eq "gemini") {
    foreach ($requiredName in @("GOOGLE_CLOUD_PROJECT", "GOOGLE_CLOUD_LOCATION", "GEMINI_MODEL", "GEMINI_API_VERSION", "GEMINI_TIMEOUT")) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredName))) {
            throw "$requiredName is not configured in .env"
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:GOOGLE_APPLICATION_CREDENTIALS) -or
        -not (Test-Path -LiteralPath $env:GOOGLE_APPLICATION_CREDENTIALS -PathType Leaf)) {
        throw "GOOGLE_APPLICATION_CREDENTIALS must reference a readable local ADC file"
    }
}

if ($aiEnabled -and $aiProvider -eq "openai") {
    Write-Host "OPENAI_API_KEY: configured"
} elseif ($aiEnabled -and $aiProvider -eq "gemini") {
    Write-Host "Gemini Vertex AI ADC: configured"
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
    & docker compose up -d --build db preprocessing
    if ($LASTEXITCODE -ne 0) {
        & docker compose ps preprocessing
        throw "Could not start Compose services 'db' and 'preprocessing' (exit code $LASTEXITCODE). Verify Docker and whether port 8090 is already in use."
    }
} finally {
    Pop-Location
}

Write-Host "PostgreSQL DEV: ready"

$preprocessingHealthUri = "http://localhost:8090/health"
Write-Host "Waiting up to 60 seconds for preprocessing-service..."
if (-not (Wait-PreprocessingService -Uri $preprocessingHealthUri -TimeoutSeconds 60)) {
    Show-PreprocessingDiagnostics
    throw "preprocessing-service did not report status=UP and service=preprocessing-service within 60 seconds. Spring Boot was not started."
}
Write-Host "preprocessing-service DEV: healthy"

$audioReadyTimeoutSeconds = 300
if (-not [string]::IsNullOrWhiteSpace($env:PREPROCESSING_AUDIO_READY_TIMEOUT_SECONDS)) {
    if (-not [int]::TryParse(
            $env:PREPROCESSING_AUDIO_READY_TIMEOUT_SECONDS.Trim(),
            [ref]$audioReadyTimeoutSeconds) -or $audioReadyTimeoutSeconds -lt 1) {
        throw "PREPROCESSING_AUDIO_READY_TIMEOUT_SECONDS must be a positive integer."
    }
}

$audioHealthUri = "http://localhost:8090/v1/audio/health"
Write-Host "Waiting up to $audioReadyTimeoutSeconds seconds for the local audio model..."
if (-not (Wait-AudioTranscriptionService `
        -Uri $audioHealthUri `
        -TimeoutSeconds $audioReadyTimeoutSeconds)) {
    Show-PreprocessingDiagnostics
    throw "Audio transcription did not report readiness within $audioReadyTimeoutSeconds seconds. Spring Boot was not started."
}
Write-Host "audio-transcription DEV: ready"

$serverPort = 8082
if (-not [string]::IsNullOrWhiteSpace($env:SERVER_PORT)) {
    $configuredServerPort = 0
    if (-not [int]::TryParse($env:SERVER_PORT.Trim(), [ref]$configuredServerPort) -or
        $configuredServerPort -lt 1 -or $configuredServerPort -gt 65535) {
        throw "SERVER_PORT must be an integer between 1 and 65535."
    }
    $serverPort = $configuredServerPort
}

$portProcesses = Get-NetTCPConnection -LocalPort $serverPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
foreach ($processId in $portProcesses) {
    Stop-Process -Id $processId -Force
    Write-Host "Stopped process $processId listening on port $serverPort."
}

$backendJob = $null
try {
    Push-Location $backendDir
    try {
        & $mavenWrapper clean
        if ($LASTEXITCODE -ne 0) {
            throw "Maven clean failed (exit code $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }

    Write-Host "Starting Spring Boot in background..."
    $backendJob = Start-Job -ScriptBlock {
        param($workingDirectory, $wrapperPath)
        Set-Location $workingDirectory
        & $wrapperPath spring-boot:run
    } -ArgumentList $backendDir, $mavenWrapper

    Write-Host "Waiting up to 60 seconds for Spring Boot on port $serverPort..."
    if (-not (Wait-TcpPort -Port $serverPort -TimeoutSeconds 60 -Job $backendJob)) {
        throw "Spring Boot did not start listening on port $serverPort within 60 seconds."
    }
    Write-Host "Spring Boot DEV: ready"

    Write-Host "Starting Angular dev server on http://localhost:4200 ..."
    Push-Location $frontendDir
    try {
        & npm start
        $frontendExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    if ($backendJob -and (Get-Job -Id $backendJob.Id -ErrorAction SilentlyContinue)) {
        Stop-Job -Job $backendJob -ErrorAction SilentlyContinue
        Receive-Job -Job $backendJob -Keep -ErrorAction SilentlyContinue
        Remove-Job -Job $backendJob -Force -ErrorAction SilentlyContinue
    }
}

exit $frontendExitCode
