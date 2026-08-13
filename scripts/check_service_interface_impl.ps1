$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$violations = @()

Get-ChildItem -Path $root -Directory -Filter "mall-*-service" |
    Where-Object { $_.Name -ne "mall-common-oss" } |
    ForEach-Object {
        $module = $_.FullName
        $sourceRoot = Join-Path $module "src/main/java"
        if (-not (Test-Path $sourceRoot)) {
            return
        }

        Get-ChildItem -Path $sourceRoot -Recurse -Filter "*.java" |
            Where-Object {
                $_.FullName -match "\\service\\" -and
                $_.FullName -notmatch "\\service\\impl\\" -and
                $_.Name -notin @("PayService.java", "PayRequest.java", "PayResponse.java", "PayNotifyResult.java")
            } |
            ForEach-Object {
                $text = Get-Content -Raw -LiteralPath $_.FullName
                if ($text -match "(?m)^\s*public\s+class\s+\w+") {
                    $violations += $_.FullName.Substring($root.Length + 1)
                }
            }
    }

if ($violations.Count -gt 0) {
    Write-Host "Concrete service classes must live in service.impl:"
    $violations | Sort-Object | ForEach-Object { Write-Host " - $_" }
    exit 1
}

Write-Host "Service interface/impl structure check passed."
