# 02 : tests Java du backend (ciblés vision, puis suite complète). Aucun appel réseau à Groq ou OpenRouter :
# les clients HTTP sont simulés. Maven (mvnw) télécharge ses dépendances au premier lancement.
# NON EXÉCUTÉ sur le poste A (Java absent) : à lancer sur le poste B, avec un JDK 17.
. "$PSScriptRoot\commun.ps1"

$back = Join-Path $Projet 'BACK'
$cibles = @('ImageAnalysisServiceTest', 'ImageAnalysisServiceLocalTest', 'VisionEvaluationAccessTest', 'GroqClientTest',
            'OpenRouterClientTest', 'TourneeWorkflowTest', 'TourReviewRegressionTest', 'LocalVisionClientTest',
            'LocalCleanlinessMapperTest', 'LocalVisionPropertiesTest', 'AiConfigTest',
            'NarrativeValidatorTest', 'NarrativeServiceTest', 'VisionNarrativeAccessTest', 'VisionNarrationPropertiesTest') -join ','
foreach ($v in 'VISION_ENGINE', 'VISION_NARRATION', 'VISION_NARRATION_TIMEOUT_SECONDS', 'VISION_PROPRE_THRESHOLD', 'VISION_MIN_SHARPNESS', 'VISION_MIN_BRIGHTNESS', 'VISION_MAX_BRIGHTNESS', 'LOCAL_VISION_URL') {
    if (Test-Path "env:$v") { Write-Host "Attention : la variable $v est définie ; LocalVisionPropertiesTest suppose qu'elle ne l'est pas." -ForegroundColor Yellow }
}

Push-Location $back
try {
    Write-Host "== Tests ciblés : $cibles"
    & .\mvnw.cmd -B test "-Dtest=$cibles" "-Dsurefire.failIfNoSpecifiedTests=false" *> (Join-Path $Resultats 'tests_backend_cibles.log')
    $codeCibles = $LASTEXITCODE
    Get-Content (Join-Path $Resultats 'tests_backend_cibles.log') | Select-String 'Tests run:|BUILD|ERROR' | Select-Object -Last 15
    Write-Host "`n== Suite complète"
    & .\mvnw.cmd -B test *> (Join-Path $Resultats 'tests_backend_complet.log')
    $codeComplet = $LASTEXITCODE
    Get-Content (Join-Path $Resultats 'tests_backend_complet.log') | Select-String 'Tests run:|BUILD' | Select-Object -Last 5
    if (Test-Path 'target\surefire-reports') {
        Copy-Item 'target\surefire-reports' (Join-Path $Resultats 'surefire-reports') -Recurse -Force
    }
} finally { Pop-Location }

Write-Host "`nTests ciblés : code $codeCibles ; suite complète : code $codeComplet (0 = réussite). Journaux : $Resultats"
if ($codeCibles -ne 0 -or $codeComplet -ne 0) { exit 1 }
exit 0
