# 00 : vérifie les prérequis du poste B. N'installe rien, ne modifie rien.
# NON EXÉCUTÉ sur le poste A (poste de développement) : à lancer sur le poste B.
. "$PSScriptRoot\commun.ps1"

$manques = @()
function Show-Outil([string]$Nom, [string[]]$Commande, [bool]$Obligatoire) {
    if (-not (Get-Command $Commande[0] -ErrorAction SilentlyContinue)) {
        if ($Obligatoire) { Write-Host ("[MANQUE]  {0,-10} introuvable" -f $Nom) -ForegroundColor Red; $script:manques += $Nom }
        else { Write-Host ("[OPTION]  {0,-10} introuvable (facultatif)" -f $Nom) -ForegroundColor Yellow }
        return
    }
    $arguments = @(); if ($Commande.Count -gt 1) { $arguments = $Commande[1..($Commande.Count - 1)] }
    $v = (& cmd /c $Commande[0] @arguments 2>&1 | Select-Object -First 1)
    Write-Host ("[OK]      {0,-10} {1}" -f $Nom, $v)
}

Write-Host "Projet : $Projet"
Show-Outil 'java' @('java', '-version') $true
if ($env:JAVA_HOME) { Write-Host "          JAVA_HOME = $env:JAVA_HOME" } else { Write-Host '          JAVA_HOME non défini (mvnw utilise java du PATH)' }
Show-Outil 'docker' @('docker', '--version') $true
if (Get-Command docker -ErrorAction SilentlyContinue) {
    & cmd /c docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -eq 0) { Write-Host '          Docker Desktop : démarré' }
    else { Write-Host '          Docker Desktop : NON démarré (ouvrez Docker Desktop avant 04_e2e.ps1)' -ForegroundColor Red; $manques += 'docker (démon)' }
}
Show-Outil 'node' @('node', '--version') $true
Show-Outil 'npm' @('npm', '--version') $true
try { $py = Get-PythonCommande; $arguments = @(); if ($py.Count -gt 1) { $arguments = $py[1..($py.Count - 1)] }
      Write-Host ("[OK]      {0,-10} {1}" -f 'python', (& $py[0] @arguments --version)) }
catch { Write-Host '[MANQUE]  python     3.10+ introuvable' -ForegroundColor Red; $manques += 'python' }
Show-Outil 'flutter' @('flutter', '--version') $false

foreach ($port in 8090, 18080, 27018) {
    if (Test-PortLibre $port) { Write-Host "[OK]      port $port libre" }
    else { Write-Host "[OCCUPÉ]  port $port déjà utilisé : arrêtez le programme qui l'occupe" -ForegroundColor Red; $manques += "port $port" }
}
if (Test-Path (Join-Path $Projet 'BACK\.env')) { Write-Host '[INFO]    BACK\.env existe : les scripts du kit ne le lisent pas (backend lancé hors de BACK, réglages passés en arguments).' }

if ($manques.Count -gt 0) { Write-Host "`nÀ corriger : $($manques -join ', ')" -ForegroundColor Red; exit 1 }
Write-Host "`nPrérequis réunis."
exit 0
