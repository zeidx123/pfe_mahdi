# Kit de test du moteur de vision local (poste B)

Ce kit vérifie, sur un poste où Java, Docker et (facultativement) Flutter sont disponibles, l'intégration écrite sur
le poste A. Tous les chemins sont relatifs : le kit doit rester à sa place dans le projet décompressé
(`<projet>\computer_vision\integration\kit_tests_autre_pc`). Il ne lit pas `BACK\.env` et n'appelle ni Groq ni
OpenRouter. Les scripts 02 à 04 n'ont **pas** été exécutés sur le poste A ; 00 et 01 l'ont été.

## Prérequis

| outil | version | pour |
|---|---|---|
| JDK | 17 (Temurin conseillé), `java` dans le PATH ou `JAVA_HOME` défini | 02, 04 |
| Docker Desktop | ouvert (démon démarré) | 04 |
| Node.js / npm | 20 ou plus | 03 |
| Python | 3.10 ou plus | 01, 04 |
| Flutter | 3.24.5 (Dart 3.5.4), facultatif | 03 |
| Réseau | pip, Maven (mvnw), npm, image Docker `mongo:7` au premier lancement | 01 à 04 |

Ports utilisés et devant être libres : 8090 (service), 18080 (backend de test), 27018 (MongoDB de test).

## Ordre d'exécution

Depuis PowerShell, dans ce dossier (si l'exécution de scripts est bloquée :
`powershell -ExecutionPolicy Bypass -File .\00_verifier_prerequis.ps1`, etc.).

| # | script | ce qu'il fait | résultat attendu |
|---|---|---|---|
| 0 | `00_verifier_prerequis.ps1` | affiche les versions et ce qui manque ; n'installe rien | « Prérequis réunis. », code 0 |
| 1 | `01_service_python.ps1` | crée `.venv_kit`, installe les dépendances figées, lance les 14 tests du service et les 6 tests hors ligne du validateur de rédaction, démarre le service et compare ses sorties à `jeu_coherence\` | 14 + 6 tests OK ; 30/30 accords, écart 0 (≤ 1e-5) ; 3 images EXIF conformes ; « CONFORME » |
| 2 | `02_tests_backend.ps1` | `mvnw test` ciblé (15 classes, dont les 8 nouvelles : moteur local et rédaction), puis suite complète | `BUILD SUCCESS` deux fois |
| 3 | `03_tests_mobile_web.ps1` | `flutter analyze`, `flutter test` (si Flutter présent), `npm ci`, `npm run lint`, `npm run build` | tout en [OK] |
| 4 | `04_e2e.ps1` | MongoDB de test jetable (`gesticollecte-mongo-test`, port 27018), service, backend en mode LOCAL puis LLM sans clé ; 33 images envoyées par l'API réelle ; nettoyage | `Bout en bout LOCAL : RÉUSSI`, `Bout en bout LLM : RÉUSSI` |

Détails du bout en bout (04) :
- comptes de test créés dans la base de test par `TestDataSeeder` (`app.seed.enabled=true`) ;
- création d'un camion, d'un point et d'une tournée par l'API. Le démarrage normal (`/optimiser` puis `/start`)
  exige OpenRouteService (clé et réseau) ; sans lui, la tournée est passée `EN_COURS` directement dans la base de
  test, et le résumé l'indique (`demarrage_tournee`) ;
- mode LOCAL : probabilités enregistrées égales à `reference_coherence.json` à 1e-5, même classe, statut conforme
  (PROPRE→CLEANED, DEBORDEMENT→NOT_CLEANED, CANNOT_DETERMINE→CANNOT_DETERMINE), `decision = A_VERIFIER` partout
  (seuil vide), `confidence` nulle, justification non vide, fin de tournée possible ;
- mode LLM sans clé : `ANALYSIS_UNAVAILABLE` / `NOT_CONFIGURED`, aucun champ du moteur local (comportement d'avant) ;
- à la fin, seul le conteneur créé par le script est supprimé. Si un conteneur du même nom existe déjà, le script
  s'arrête sans rien toucher.

## Vérification manuelle de la rédaction (facultative)

Seule étape qui appelle un vrai fournisseur ; elle consomme votre quota. Elle n'est lancée par aucun script du kit.

1. Vérifier d'abord sans réseau : `python ..\..\..\scripts\evaluation\evaluate_narration.py` (mode `--dry-run` par défaut) écrit les 40 prompts dans `evaluation\narration\prompts_dry_run.jsonl`.
2. Dans la même session PowerShell : `$env:GROQ_API_KEY = '<votre clé>'` (ou `OPENROUTER_API_KEY` avec `--provider OPENROUTER`). Le script ne l'affiche ni ne l'écrit.
3. `python ..\..\..\scripts\evaluation\evaluate_narration.py --live --provider GROQ --sortie resultats_autre_pc\narration` (options `--model`, `--base-url`, `--timeout`, `--pause`).
4. Lire `resultats_autre_pc\narration\narration_live_resume.json` : `taux_acceptation_1re_tentative`, `taux_llm_final`, `taux_repli_gabarit`, `motifs_rejet_derniere_tentative`, `latence_mediane_ms`.
5. Lire `narration_live.csv`, ligne par ligne : la colonne `texte` (même accepté, un texte peut rester trompeur : le validateur ne voit pas tout), `motifs` (V1 longueur, V2 nombre inventé, V3 mot interdit, V4 mention automatique et contrôle humain absente, V5 pourcentage ou problème de qualité absent, V6 balisage, V7 lien), `categorie_repli` et `latence_ms`.
6. Critère à surveiller : un taux d'acceptation bas ou un motif dominant (par exemple V2 ou V3) indique que le prompt doit être revu avant d'activer `VISION_NARRATION=LLM`. Le lot s'arrête au premier 429 (limite de débit), sans nouvelle tentative.

## Où trouver les journaux

Tout est écrit dans `resultats_autre_pc\` :

| fichier | contenu |
|---|---|
| `tests_service.log`, `coherence_service.csv`, `service_coherence.*.log` | étape 1 |
| `tests_backend_cibles.log`, `tests_backend_complet.log`, `surefire-reports\` | étape 2 |
| `flutter_*.log`, `npm_*.log` | étape 3 |
| `e2e_build.log`, `backend_local.*.log`, `backend_llm.*.log`, `service_e2e.*.log`, `e2e_local.csv`, `e2e_local_resume.json`, `e2e_llm.csv`, `e2e_llm_resume.json`, `e2e_run\` (photos de test) | étape 4 |

Renvoyez le dossier `resultats_autre_pc\` complet au poste A. Il ne contient aucun secret : le secret JWT du
backend de test est généré à chaque exécution et n'est jamais écrit.

Pour supprimer ensuite ce que le kit a créé : `.venv_kit\`, `resultats_autre_pc\*` (sauf `LISEZMOI.md`) ;
le JAR et `target\` sont dans `BACK\`.
