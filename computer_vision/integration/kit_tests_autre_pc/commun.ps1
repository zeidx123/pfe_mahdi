# Fonctions communes aux scripts du kit (chargé par . "$PSScriptRoot\commun.ps1").
# Chemins : le kit est dans <projet>\computer_vision\integration\kit_tests_autre_pc.

$Kit = $PSScriptRoot
$Projet = (Resolve-Path (Join-Path $Kit '..\..\..')).Path
$Resultats = Join-Path $Kit 'resultats_autre_pc'
New-Item -ItemType Directory -Force $Resultats | Out-Null

function Get-PythonCommande {
    # Renvoie la commande Python 3.10+ sous forme de tableau (exécutable puis arguments).
    foreach ($candidat in @(@('python'), @('py', '-3'), @('python3'))) {
        if (Get-Command $candidat[0] -ErrorAction SilentlyContinue) {
            $arguments = @()
            if ($candidat.Count -gt 1) { $arguments = $candidat[1..($candidat.Count - 1)] }
            try {
                $version = & $candidat[0] @arguments -c "import sys; print('%d.%d' % sys.version_info[:2])" 2>$null
                if ($LASTEXITCODE -eq 0 -and [version]"$version" -ge [version]'3.10') { return ,$candidat }
            } catch { }
        }
    }
    throw 'Python 3.10 ou plus récent introuvable.'
}

function Get-PythonKit {
    # Python de l'environnement virtuel du kit (créé par 01_service_python.ps1).
    $python = Join-Path $Kit '.venv_kit\Scripts\python.exe'
    if (-not (Test-Path $python)) { throw "Environnement du kit absent : lancez d'abord 01_service_python.ps1." }
    return $python
}

function Test-PortLibre([int]$Port) {
    $occupe = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return -not $occupe
}

function Start-ServiceVision([string]$Journal) {
    $python = Get-PythonKit
    $app = Join-Path $Projet 'computer_vision\service\app.py'
    $p = Start-Process -FilePath $python -ArgumentList @("`"$app`"", '--port', '8090') -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$Journal.out.log" -RedirectStandardError "$Journal.err.log"
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $sante = Invoke-RestMethod 'http://127.0.0.1:8090/health' -TimeoutSec 2
            if ($sante.status -eq 'ok') { return $p }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $p.HasExited) { $p.Kill() }
    throw "Le service de vision n'a pas démarré (voir $Journal.err.log)."
}

function Stop-Processus($Processus) {
    if ($Processus -and -not $Processus.HasExited) {
        # Arrête aussi les processus enfants éventuels.
        & taskkill /PID $Processus.Id /T /F 2>$null | Out-Null
    }
}
