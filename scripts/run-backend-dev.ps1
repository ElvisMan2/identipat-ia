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
            throw "Línea $lineNumber inválida en .env: falta el separador '='."
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        if ([string]::IsNullOrWhiteSpace($name)) {
            throw "Línea $lineNumber inválida en .env: el nombre de variable está vacío."
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
$mavenWrapper = Join-Path $backendDir "mvnw.cmd"

Write-Host "IDENTIPAT-IA — Backend DEV"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "No se encontró .env.`nCrea uno a partir de .env.example y completa los valores requeridos."
}

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "No se encontró el Maven Wrapper en backend\mvnw.cmd."
}

# Parser intencionalmente pequeño: ignora blancos/comentarios completos, divide por el
# primer '=', recorta el contorno del valor y elimina solo comillas exteriores parejas.
# No interpreta export, comentarios inline, interpolación, expansión ni comandos.
Import-DotEnvFile -Path $envFile
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "Spring profile: dev"
Write-Host ".env: cargado"

$aiEnabled = if ([string]::IsNullOrWhiteSpace($env:IDENTIPAT_AI_ENABLED)) {
    $true
} else {
    $env:IDENTIPAT_AI_ENABLED.Trim().Equals("true", [System.StringComparison]::OrdinalIgnoreCase)
}

$historicalApiKeyPlaceholder = "replace-with-local-openai-key"
if ($aiEnabled -and
    ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY) -or
     $env:OPENAI_API_KEY.Trim().Equals($historicalApiKeyPlaceholder, [System.StringComparison]::OrdinalIgnoreCase))) {
    throw "OPENAI_API_KEY no está configurada en .env"
}

if ($aiEnabled) {
    Write-Host "OPENAI_API_KEY: configurada"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no está disponible. Instala Docker Desktop y asegúrate de que 'docker' esté en PATH."
}

& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Engine no responde (exit code $LASTEXITCODE)."
}

Push-Location $repoRoot
try {
    & docker compose up -d db
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo levantar el servicio Compose 'db' (exit code $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

Write-Host "PostgreSQL DEV: listo"
Write-Host "Iniciando Spring Boot..."

$mavenExitCode = 1
Push-Location $backendDir
try {
    & $mavenWrapper spring-boot:run
    $mavenExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $mavenExitCode
