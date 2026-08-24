<#!
.SYNOPSIS
Downloads the frontend, backend, and container dependencies required by the project.

.DESCRIPTION
Run from the repository root:
  .\scripts\install-dependencies.ps1

Use -SkipInfrastructure when MySQL and MQTT are already running.
#>
param(
    [switch]$SkipInfrastructure
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Require-Command([string]$CommandName) {
    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $CommandName"
    }
}

Require-Command 'npm'
Require-Command 'mvn'

Push-Location (Join-Path $projectRoot 'frontend')
try {
    npm ci
}
finally {
    Pop-Location
}

Push-Location (Join-Path $projectRoot 'backend')
try {
    mvn dependency:go-offline
}
finally {
    Pop-Location
}

if (-not $SkipInfrastructure) {
    Require-Command 'docker'
    Push-Location $projectRoot
    try {
        docker compose pull
        docker compose up -d
    }
    finally {
        Pop-Location
    }
}

Write-Host 'Dependencies are ready.' -ForegroundColor Green
