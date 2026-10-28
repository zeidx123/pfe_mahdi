# 03 : application chauffeur (flutter analyze, flutter test, si Flutter est présent) puis web superviseur
# (npm ci, npm run lint, npm run build).
# NON EXÉCUTÉ sur le poste A pour Flutter (absent) ; la partie web y a été exécutée à part.
. "$PSScriptRoot\commun.ps1"

$codes = @{}
if (Get-Command flutter -ErrorAction SilentlyContinue) {
    Push-Location (Join-Path $Projet 'MOBILE')
    try {
        & cmd /c flutter pub get *> (Join-Path $Resultats 'flutter_pub_get.log')
        & cmd /c flutter analyze *> (Join-Path $Resultats 'flutter_analyze.log'); $codes['flutter analyze'] = $LASTEXITCODE
        & cmd /c flutter test *> (Join-Path $Resultats 'flutter_test.log'); $codes['flutter test'] = $LASTEXITCODE
        Get-Content (Join-Path $Resultats 'flutter_test.log') -Tail 5
    } finally { Pop-Location }
} else {
    Write-Host 'Flutter introuvable : tests mobiles ignorés (facultatif).' -ForegroundColor Yellow
}

Push-Location (Join-Path $Projet 'FRONT')
try {
    & cmd /c npm ci *> (Join-Path $Resultats 'npm_ci.log'); $codes['npm ci'] = $LASTEXITCODE
    & cmd /c npm run lint *> (Join-Path $Resultats 'npm_lint.log'); $codes['npm run lint'] = $LASTEXITCODE
    & cmd /c npm run build *> (Join-Path $Resultats 'npm_build.log'); $codes['npm run build'] = $LASTEXITCODE
} finally { Pop-Location }

$echec = $false
foreach ($k in $codes.Keys) {
    if ($codes[$k] -eq 0) { Write-Host "[OK]     $k" } else { Write-Host "[ÉCHEC]  $k (code $($codes[$k]))" -ForegroundColor Red; $echec = $true }
}
Write-Host "Journaux : $Resultats"
if ($echec) { exit 1 }
exit 0
