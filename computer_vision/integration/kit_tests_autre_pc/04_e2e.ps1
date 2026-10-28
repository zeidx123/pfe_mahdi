# 04 : bout en bout avec une base MongoDB de TEST jetable.
# NON EXÉCUTÉ sur le poste A (Java absent, Docker et backend interdits sur ce poste) : à lancer sur le poste B,
# après 01_service_python.ps1 (environnement .venv_kit) et avec Docker Desktop démarré.
#
#  1. conteneur gesticollecte-mongo-test (mongo:7, port 27018, sans volume) ; refus s'il existe déjà ;
#  2. service Python sur 127.0.0.1:8090 ;
#  3. backend (JAR construit par mvnw) sur le port 18080, lancé depuis resultats_autre_pc\e2e_run (aucun .env lu),
#     tous les réglages passés en arguments : base de test, comptes de test, VISION_ENGINE=LOCAL, seuils vides,
#     clés Groq et OpenRouter vides, secret JWT aléatoire propre à cette exécution ;
#  4. e2e_vision.py --mode LOCAL (33 images) ; puis backend relancé en mode LLM sans clé, e2e_vision.py --mode LLM ;
#  5. arrêt du backend et du service, suppression du SEUL conteneur créé par ce script.
. "$PSScriptRoot\commun.ps1"
# Pas de $ErrorActionPreference = 'Stop' : sous Windows PowerShell 5.1, il ferait échouer les commandes natives
# qui écrivent sur stderr. Chaque étape vérifie $LASTEXITCODE et s'arrête par throw.

$conteneur = 'gesticollecte-mongo-test'
$baseMongo = 'gesticollecte_e2e'
$portApi = 18080
$run = Join-Path $Resultats 'e2e_run'
New-Item -ItemType Directory -Force (Join-Path $run 'photos') | Out-Null
$python = Get-PythonKit

if (& docker ps -a --filter "name=^/$conteneur$" --format '{{.Names}}') {
    throw "Le conteneur $conteneur existe déjà : supprimez-le vous-même s'il est inutile (docker rm -f $conteneur), puis relancez."
}
foreach ($port in 8090, $portApi, 27018) { if (-not (Test-PortLibre $port)) { throw "Port $port occupé." } }

function Start-Backend([string]$Moteur, [string]$Jar) {
    $octets = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($octets)
    $jwt = [Convert]::ToBase64String($octets)  # secret jetable, jamais écrit sur disque
    $arguments = @('-jar', "`"$Jar`"",
        "--server.port=$portApi",
        "--spring.data.mongodb.uri=mongodb://127.0.0.1:27018/$baseMongo",
        "--spring.data.mongodb.database=$baseMongo",
        '--app.seed.enabled=true', '--app.bootstrap.enabled=false',
        "--app.jwt.secret=$jwt",
        "--ai.vision-engine=$Moteur",
        '--ai.local-vision.base-url=http://127.0.0.1:8090',
        '--ai.local-vision.token=', '--ai.local-vision.propre-threshold=',
        '--ai.local-vision.min-sharpness=', '--ai.local-vision.min-brightness=', '--ai.local-vision.max-brightness=',
        '--ai.collection-provider=GROQ', '--ai.groq.api-key=', '--ai.open-router.api-key=', '--ai.gemini.api-key=',
        "--ai.storage.photos-dir=`"$(Join-Path $run 'photos')`"",
        "--ai.storage.cv-dir=`"$(Join-Path $run 'cv')`"",
        "--ai.storage.documents-dir=`"$(Join-Path $run 'documents')`"")
    $journal = Join-Path $Resultats "backend_$($Moteur.ToLower())"
    return Start-Process -FilePath 'java' -ArgumentList $arguments -WorkingDirectory $run -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$journal.out.log" -RedirectStandardError "$journal.err.log"
}

$creeParLeScript = $false
$service = $null
$backend = $null
$codes = @{}
try {
    Write-Host '== Construction du JAR (tests non relancés ici : voir 02_tests_backend.ps1)'
    Push-Location (Join-Path $Projet 'BACK')
    try { & .\mvnw.cmd -q -B -DskipTests package *> (Join-Path $Resultats 'e2e_build.log') } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'Construction du JAR impossible (voir e2e_build.log).' }
    $jar = Get-ChildItem (Join-Path $Projet 'BACK\target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
    if (-not $jar) { throw 'JAR introuvable dans BACK\target.' }

    Write-Host "== MongoDB de test : $conteneur (port 27018)"
    & docker run -d --name $conteneur -p 27018:27017 mongo:7 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Démarrage du conteneur impossible.' }
    $creeParLeScript = $true
    for ($i = 0; $i -lt 30; $i++) {
        & docker exec $conteneur mongosh --quiet --eval 'db.runCommand({ping:1}).ok' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }; Start-Sleep -Seconds 2
    }

    Write-Host '== Service de vision local'
    $service = Start-ServiceVision (Join-Path $Resultats 'service_e2e')

    foreach ($moteur in 'LOCAL', 'LLM') {
        Write-Host "== Backend en mode $moteur (port $portApi)"
        $backend = Start-Backend $moteur $jar.FullName
        $env:PYTHONIOENCODING = 'utf-8'
        & $python (Join-Path $Kit 'e2e_vision.py') --mode $moteur --api "http://127.0.0.1:$portApi/api" `
            --conteneur $conteneur --base-mongo $baseMongo --sortie $Resultats
        $codes[$moteur] = $LASTEXITCODE
        Stop-Processus $backend; $backend = $null
        Start-Sleep -Seconds 3
    }
} finally {
    Stop-Processus $backend
    Stop-Processus $service
    if ($creeParLeScript) {
        Write-Host "== Suppression du conteneur de test $conteneur"
        & docker rm -f $conteneur | Out-Null
    }
}

foreach ($k in $codes.Keys) { Write-Host ("Bout en bout {0} : {1}" -f $k, $(if ($codes[$k] -eq 0) { 'RÉUSSI' } else { 'ÉCHEC' })) }
Write-Host "Résultats : $Resultats (e2e_local*.csv/json, e2e_llm*.csv/json, backend_*.log, service_e2e.*.log)"
if ($codes['LOCAL'] -ne 0 -or $codes['LLM'] -ne 0) { exit 1 }
exit 0
