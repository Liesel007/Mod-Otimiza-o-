param(
    [string]$Instancia = "$env:USERPROFILE\curseforge\minecraft\Instances\HL 1.20.1"
)

$pastaMods = Join-Path $Instancia "mods"
if (-not (Test-Path $pastaMods)) {
    Write-Error "Pasta de mods não encontrada: $pastaMods"
    exit 1
}

$mods = Get-ChildItem -Path $pastaMods -Filter *.jar | Sort-Object Name | Select-Object -ExpandProperty Name
$mods | Set-Content -Encoding UTF8 "mods-instalados.txt"
$mods
Write-Host "`n$($mods.Count) mods salvos em mods-instalados.txt"
