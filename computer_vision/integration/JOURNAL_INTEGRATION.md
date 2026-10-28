# Journal de l'intégration du moteur de vision local

Date de toutes les opérations : 2026-10-28 (poste A). Préfixes : R = `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\`,
J = `R\BACK\src\main\java\com\municipalite\gestiontournees\`, T = `R\BACK\src\test\java\com\municipalite\gestiontournees\`,
CV = `R\computer_vision\`.

- **Compilé : oui** = compilé par `mvnw -q -B -DskipTests test-compile` (JDK 17 portable), code principal et classes de test, sans erreur ni avertissement.
- **Testé : non (poste B)** = aucun test exécuté sur le poste A.
- Les originaux de tous les fichiers existants modifiés sont dans `CV\integration\sauvegardes\<chemin relatif à R>`.

## Fichiers existants modifiés (sauvegardés avant la première modification)

| fichier | rôle de la modification | compilé | testé |
|---|---|---|---|
| `J\ai\config\AiProperties.java` | enum `VisionEngine {LLM, LOCAL}`, champ `visionEngine`, classe `LocalVision` (URL, délai, jeton, nom et SHA-256 du modèle, seuils) | oui | non (poste B) |
| `R\BACK\src\main\resources\application.properties` | `ai.vision-engine=${VISION_ENGINE:LLM}` et `ai.local-vision.*` | oui (ressource) | non (poste B) |
| `R\BACK\.env.example` | noms des nouvelles variables, valeurs vides, exemples de seuils en commentaire (« mesuré sur des images synthétiques… ») | sans objet | non (poste B) |
| `J\ai\vision\model\ImageAnalysis.java` | champs `probabilities`, `predictedClass`, `qualityIssue`, `sharpness`, `meanBrightness`, `decision`, `modelSha256` | oui | non (poste B) |
| `J\ai\vision\service\ImageAnalysisService.java` | aiguillage LOCAL dans `analyzeStoredEvidence`, `applyLocalPrediction`, `evaluationConfiguration` (D14), constructeur de compatibilité à 5 arguments ; mode LLM inchangé | oui | non (poste B) |
| `J\tournee\model\TourneeVisit.java` | `analysisProbabilities`, `analysisDecision`, `analysisQualityIssue` | oui | non (poste B) |
| `J\tournee\service\TourneeService.java` | `copyLocalAnalysis` : copie dans `analyzeVisit` (nouvelle analyse et réutilisation), effacement dans `savePointPhoto` | oui | non (poste B) |
| `J\rapport\service\PdfRapportService.java` | cellule « IA / Revue » : probabilités, photo à refaire, « À vérifier par le superviseur » | oui | non (poste B) |
| `T\tournee\service\TourReviewRegressionTest.java` | 2 tests ajoutés ; tests existants inchangés | oui | non (poste B) |
| `T\tournee\service\TourneeWorkflowTest.java` | 1 test ajouté ; tests existants inchangés | oui | non (poste B) |
| `R\MOBILE\lib\models\tour_progress.dart` | `probabilitiesText`, `retakeMessage`, `needsSupervisorReview`, libellés de qualité | non (Flutter absent) | non (poste B) |
| `R\MOBILE\lib\pages\active_tracking_page.dart` | probabilités, pastille « À vérifier par le superviseur », « Photo à refaire : … », bouton « Reprendre la photo APRÈS » mis en avant (`_retakeAfterButton`) | non (Flutter absent) | non (poste B) |
| `R\MOBILE\test\vision_quota_test.dart` | 3 tests de widget ajoutés | non (Flutter absent) | non (poste B) |
| `R\FRONT\src\pages\tournees\TourneeDetailsModal.jsx` | probabilités, pastille, photo à refaire, justification (`description`) | `npm run build` : oui | lint et build : oui ; tests : aucun test web n'existe |
| `R\FRONT\src\pages\tournees\TourneePage.jsx` | pastille « À vérifier : n » par tournée | `npm run build` : oui | lint et build : oui |
| `R\FRONT\src\services\aiService.js` | `getPhotoAnalysis(id)` (`GET /api/ai/vision/analyses/{id}`) | `npm run build` : oui | lint et build : oui |
| `R\README.md` | section « Moteur de vision local » (démarrage, variables, correspondance, limites, retour en arrière) | sans objet | sans objet |

`T\ai\vision\ImageAnalysisServiceTest.java` a été sauvegardé mais **n'a pas été modifié** : les tests du mode LOCAL sont dans une classe à part.

## Fichiers créés

| fichier | rôle | compilé | testé |
|---|---|---|---|
| `J\ai\common\LocalVisionClient.java` | client OkHttp du service local, réponse validée, catégories d'erreur sans fuite de secrets | oui | non (poste B) |
| `J\ai\vision\service\LocalCleanlinessMapper.java` | correspondance D3, décision D6, gabarits D8 | oui | non (poste B) |
| `T\ai\common\LocalVisionClientTest.java` | intercepteur factice, aucun réseau | oui | non (poste B) |
| `T\ai\vision\LocalCleanlinessMapperTest.java` | tableau de 14 cas, plus les gabarits | oui | non (poste B) |
| `T\ai\vision\ImageAnalysisServiceLocalTest.java` | mode LOCAL : statut, aucune réutilisation entre moteurs, panne, `confidence` nulle | oui | non (poste B) |
| `T\ai\config\LocalVisionPropertiesTest.java` | liaison des propriétés (`VISION_ENGINE` absent → LLM, seuils vides acceptés) | oui | non (poste B) |
| `R\scripts\run-local-vision.bat`, `R\scripts\run-local-vision.sh` | lanceurs du service | sans objet | non |
| `CV\service\app.py` | service HTTP local (http.server + onnxruntime) | sans objet | **oui, poste A** (14 tests) |
| `CV\service\test_service.py` | 14 tests unittest | sans objet | **oui, poste A** |
| `CV\service\requirements.txt`, `README_service.md` | dépendances figées, documentation | sans objet | installation testée sur le poste A |
| `CV\service\modele\` | copie du `.onnx`, `model_card.json` (SHA-256 corrigé, voir rapport §5), `SHA256SUMS.txt` | sans objet | vérification au démarrage testée |
| `CV\service\exemples\` | 3 images de valid et `attendus.json` | sans objet | oui |
| `CV\integration\construire_jeu_coherence.py` | construit le jeu de 33 images et ses références, mesure l'EXIF du dataset | sans objet | exécuté sur le poste A |
| `CV\integration\kit_tests_autre_pc\jeu_coherence\` | 33 images, `reference_coherence.json`, `reference_qualite.json` | sans objet | utilisé sur le poste A |
| `CV\integration\kit_tests_autre_pc\test_coherence.py` | comparaison HTTP service / références | sans objet | **oui, poste A** |
| `CV\integration\kit_tests_autre_pc\commun.ps1`, `00_…ps1`, `01_…ps1` | kit : fonctions communes, prérequis, service et cohérence | sans objet | **exécutés sur le poste A** (00 : Java signalé manquant, comme attendu ; 01 : réussi) |
| `CV\integration\kit_tests_autre_pc\02_…`, `03_…`, `04_e2e.ps1`, `e2e_vision.py` | tests backend, mobile et web, bout en bout | sans objet | **non exécutés** (poste B) ; syntaxe PowerShell vérifiée par l'analyseur |
| `CV\integration\kit_tests_autre_pc\LISEZMOI_AUTRE_PC.md`, `resultats_autre_pc\LISEZMOI.md` | documentation du kit | sans objet | sans objet |
| `CV\integration\preparer_transfert.ps1` | construction et contrôle du zip | sans objet | exécuté sur le poste A |
| `CV\integration\exif_dataset.json`, `exif_sans_transpose.json`, `coherence_service.csv` | mesures de l'étape 2 | sans objet | sans objet |
| `CV\integration\execution_poste_A\` | journaux du poste A (service, compilation, npm, kit 01) | sans objet | sans objet |
| `CV\integration\RAPPORT_INTEGRATION.md`, ce journal | rapports | sans objet | sans objet |

## Créé hors du code livré (non transféré)

- `CV\service\.venv_service\`, `CV\integration\kit_tests_autre_pc\.venv_kit\` : environnements Python.
- `CV\integration\outils\` : JDK Temurin 17.0.20.1+1 portable (zip et dossier extrait, `jdk_sha256.txt`).
- `CV\integration\transfert\` : zip et empreinte.
- Produits par les commandes demandées :
  - `R\BACK\target\` (compilation) ;
  - `R\FRONT\node_modules\` et `R\FRONT\dist\` (npm) ;
  - **hors du projet** : `%USERPROFILE%\.m2\` (dépôt et wrapper Maven téléchargés par `mvnw`), cache pip de l'utilisateur.
