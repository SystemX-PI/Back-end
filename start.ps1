# Run from PowerShell; works from any current directory.
$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Abra o Docker Desktop e tente novamente." }
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "../Front-End/Dockerfile"))) {
        throw "Clone Front-End ao lado de Back-end. Para subir somente a API, use docker compose up --build -d."
    }
    docker compose --profile full up --build -d --wait --wait-timeout 600
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel iniciar. Confira docker compose --profile full logs --tail=80." }
    Write-Host "Sistema disponivel em http://localhost:5173"
    Write-Host "API: http://localhost:8080/swagger-ui/index.html"
} finally {
    Pop-Location
}
