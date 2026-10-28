# 01 : environnement Python du kit, tests du service, puis test de cohérence avec jeu_coherence\.
# Critères : écart <= 1e-5 et même classe sur les 30 images droites ; images tournées (EXIF) = image droite à 1e-5 ;
# qualité à 1e-6 de reference_qualite.json. Journaux dans resultats_autre_pc\.
# (Exécuté sur le poste A avec l'environnement du service ; ce script lui-même est à lancer sur le poste B.)
. "$PSScriptRoot\commun.ps1"
# Pas de $ErrorActionPreference = 'Stop' : sous Windows PowerShell 5.1, il ferait échouer les commandes natives
# qui écrivent sur stderr. Chaque étape vérifie $LASTEXITCODE et s'arrête par throw.

$venv = Join-Path $Kit '.venv_kit'
$service = Join-Path $Projet 'computer_vision\service'
if (-not (Test-Path (Join-Path $venv 'Scripts\python.exe'))) {
    $py = Get-PythonCommande
    $arguments = @(); if ($py.Count -gt 1) { $arguments = $py[1..($py.Count - 1)] }
    Write-Host "Création de l'environnement $venv"
    & $py[0] @arguments -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw 'Création du venv impossible.' }
}
$python = Get-PythonKit
& $python -m pip install --disable-pip-version-check -q -r (Join-Path $service 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Installation des dépendances impossible.' }

$env:PYTHONIOENCODING = 'utf-8'
Write-Host "`n== Tests du service (unittest)"
Push-Location $service
try {
    & $python -m unittest -v test_service *> (Join-Path $Resultats 'tests_service.log')
    $codeTests = $LASTEXITCODE
} finally { Pop-Location }
Get-Content (Join-Path $Resultats 'tests_service.log') -Tail 4
if ($codeTests -ne 0) { Write-Host 'Tests du service : ÉCHEC' -ForegroundColor Red; exit 1 }

if (-not (Test-PortLibre 8090)) { throw 'Le port 8090 est occupé.' }
Write-Host "`n== Test de cohérence (service réel sur 127.0.0.1:8090)"
$p = Start-ServiceVision (Join-Path $Resultats 'service_coherence')
try {
    & $python (Join-Path $Kit 'test_coherence.py') --sortie (Join-Path $Resultats 'coherence_service.csv')
    $codeCoherence = $LASTEXITCODE
} finally { Stop-Processus $p }
if ($codeCoherence -ne 0) { Write-Host 'Cohérence : ÉCHEC' -ForegroundColor Red; exit 1 }
Write-Host "`nService Python : tests et cohérence réussis."
exit 0
