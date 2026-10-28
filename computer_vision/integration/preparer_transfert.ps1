# Prépare le paquet de transfert vers le poste B : transfert\pfe_mahdi_transfert.zip, SHA256.txt, liste_contenu.txt.
# Le zip a pour racine pfe_mahdi\ afin que les chemins relatifs du kit restent valables après décompression.
# Exclus : dossiers .venv*, node_modules, target, build, dist, .dart_tool, uploads, __pycache__, .git, .gradle ;
# tout fichier .env (seul le modèle .env.example, sans secret, est gardé) ; données et embeddings d'entraînement.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$Integration = $PSScriptRoot
$CV = (Resolve-Path (Join-Path $Integration '..')).Path
$Projet = (Resolve-Path (Join-Path $CV '..')).Path
$Sortie = Join-Path $Integration 'transfert'
New-Item -ItemType Directory -Force $Sortie | Out-Null
$Zip = Join-Path $Sortie 'pfe_mahdi_transfert.zip'
if (Test-Path $Zip) { Remove-Item $Zip -Force }

$dossiersExclus = @('node_modules', 'target', 'build', 'dist', '.dart_tool', 'uploads', '__pycache__', '.git', '.gradle')
function Test-Exclu([string]$Relatif) {
    $segments = $Relatif -split '[\\/]'
    foreach ($s in $segments[0..($segments.Count - 2)]) {
        if ($dossiersExclus -contains $s -or $s -like '.venv*') { return $true }
    }
    $nom = $segments[-1]
    if ($nom -eq '.env' -or ($nom -like '.env.*' -and $nom -ne '.env.example')) { return $true }
    return $false
}

# (source relative à pfe_mahdi, filtre éventuel de sous-dossiers exclus en plus)
$sources = @(
    @{ Chemin = 'BACK' }, @{ Chemin = 'MOBILE' }, @{ Chemin = 'FRONT' }, @{ Chemin = 'scripts' }, @{ Chemin = 'README.md' },
    @{ Chemin = 'computer_vision\service' },
    @{ Chemin = 'computer_vision\export\proprete_mobilenetv3l.onnx' },
    @{ Chemin = 'computer_vision\export\model_card.json' },
    @{ Chemin = 'computer_vision\export\exemple_pretraitement.py' },
    @{ Chemin = 'computer_vision\code\e7_qualite_image.py' },
    @{ Chemin = 'computer_vision\integration'; Sauf = @('computer_vision\integration\outils', 'computer_vision\integration\transfert') }
)

$fichiers = New-Object System.Collections.Generic.List[string]
foreach ($src in $sources) {
    $complet = Join-Path $Projet $src.Chemin
    if (-not (Test-Path $complet)) { throw "Source absente : $($src.Chemin)" }
    $liste = if (Test-Path $complet -PathType Leaf) { @(Get-Item $complet -Force) } else { Get-ChildItem $complet -Recurse -File -Force }
    foreach ($f in $liste) {
        $rel = $f.FullName.Substring($Projet.Length + 1)
        if (Test-Exclu $rel) { continue }
        $exclu = $false
        foreach ($s in @($src.Sauf)) { if ($s -and $rel.StartsWith($s + '\')) { $exclu = $true } }
        if (-not $exclu) { $fichiers.Add($rel) }
    }
}

$archive = [System.IO.Compression.ZipFile]::Open($Zip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($rel in $fichiers) {
        $entree = 'pfe_mahdi/' + ($rel -replace '\\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $Projet $rel), $entree,
            [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally { $archive.Dispose() }

# Vérifications : relecture du zip, absence de .env et de motifs de clés connus (valeurs jamais affichées).
$lu = [System.IO.Compression.ZipFile]::OpenRead($Zip)
$noms = @($lu.Entries | ForEach-Object { $_.FullName })
$envs = @($noms | Where-Object { ($_ -split '/')[-1] -match '^\.env($|\.)' -and ($_ -split '/')[-1] -ne '.env.example' })
$interdits = @($noms | Where-Object { $_ -match '/(\.venv[^/]*|node_modules|target|build|dist|\.dart_tool|uploads|outils|transfert)/' })
$motifs = 'gsk_[A-Za-z0-9]{20,}', 'sk-or-v1-[A-Za-z0-9]{20,}', 'AIza[0-9A-Za-z_\-]{30,}', 'eyJhbGciOi[A-Za-z0-9_\-]{20,}'
$suspects = @()
foreach ($e in $lu.Entries) {
    if ($e.Length -gt 5MB -or $e.FullName -match '\.(onnx|jpg|jpeg|png|gif|jar|zip|ttf|otf|ico|webp)$') { continue }
    $lecteur = New-Object System.IO.StreamReader($e.Open())
    $texte = $lecteur.ReadToEnd(); $lecteur.Dispose()
    foreach ($m in $motifs) { if ($texte -match $m) { $suspects += "$($e.FullName) (motif $($m.Substring(0, 6))…)" } }
}
$lu.Dispose()

$taille = (Get-Item $Zip).Length
$hash = (Get-FileHash $Zip -Algorithm SHA256).Hash.ToLower()
"$hash  pfe_mahdi_transfert.zip" | Out-File (Join-Path $Sortie 'SHA256.txt') -Encoding ascii
$noms | Out-File (Join-Path $Sortie 'liste_contenu.txt') -Encoding utf8

Write-Host "Zip : $Zip"
Write-Host ("Taille : {0:N1} Mo ; fichiers : {1}" -f ($taille / 1MB), $noms.Count)
Write-Host "SHA-256 : $hash"
foreach ($racine in 'BACK', 'MOBILE', 'FRONT', 'scripts', 'computer_vision/service', 'computer_vision/export', 'computer_vision/code', 'computer_vision/integration') {
    $n = @($noms | Where-Object { $_.StartsWith("pfe_mahdi/$racine/") }).Count
    Write-Host ("  {0,-30} {1,5} fichiers" -f $racine, $n)
}
Write-Host "Fichiers .env : $($envs.Count) ; dossiers exclus présents : $($interdits.Count) ; motifs de clés : $($suspects.Count)"
if ($suspects.Count -gt 0) { $suspects | ForEach-Object { Write-Host "  suspect : $_" } }
if ($envs.Count -gt 0 -or $interdits.Count -gt 0 -or $suspects.Count -gt 0) { Write-Host 'CONTRÔLE ÉCHOUÉ' -ForegroundColor Red; exit 1 }
Write-Host 'Contrôles réussis.'
exit 0
