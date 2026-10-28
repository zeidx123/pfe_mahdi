# Rapport d'intégration du modèle local de propreté dans l'« Agent 3 Vision »

Poste A, 2026-10-28.

**Préfixes de chemins**

| préfixe | chemin |
|---|---|
| R | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\` |
| J | `R\BACK\src\main\java\com\municipalite\gestiontournees\` |
| T | `R\BACK\src\test\java\com\municipalite\gestiontournees\` |
| M | `R\MOBILE\` |
| F | `R\FRONT\` |
| CV | `R\computer_vision\` |

Tout ce qui n'a pas pu être exécuté est marqué **NON TESTÉ**.

## 1. Prérequis constatés (poste A) et périmètre d'exécution

| outil | constat |
|---|---|
| Java, Maven | absents du PATH. Un JDK Temurin 17.0.20.1+1 portable a été téléchargé dans `CV\integration\outils\` pour la seule compilation (étape 0b). |
| `mvnw` | présent (`R\BACK\mvnw.cmd`, Maven 3.9.16) |
| Node / npm | v24.19.0 / 11.17.0 |
| Flutter, Dart | absents |
| Python | 3.13.15 |
| Docker | 29.8.2 présent, Desktop démarré. Seuls `docker --version` et `docker info` ont été exécutés, par `00_verifier_prerequis.ps1` ; aucun conteneur n'a été lancé. |

| exécuté sur le poste A | non exécuté sur le poste A |
|---|---|
| service Python et ses 14 tests (deux fois : `.venv_service`, puis `.venv_kit` via le kit) | tout test Java (`mvnw test`) |
| construction du jeu et test de cohérence par HTTP réel | `flutter analyze` / `flutter test` |
| mesure EXIF du dataset (lecture seule) | backend, MongoDB, conteneur Docker, bout en bout |
| `npm ci`, `npm run lint`, `npm run build` | scripts 02, 03 et 04 du kit |
| compilation Java seule : `mvnw -q -B -DskipTests test-compile` | exécution du modèle Llama |
| scripts 00 et 01 du kit ; analyse syntaxique de tous les `.ps1` | |
| `preparer_transfert.ps1` | |

## 2. Fichiers créés ou modifiés

Détail complet (rôle, date, compilé, testé) : **`CV\integration\JOURNAL_INTEGRATION.md`**. Les originaux des fichiers modifiés sont dans **`CV\integration\sauvegardes\`**.

**Modifiés**
- **BACK** :
  - `J\ai\config\AiProperties.java`
  - `R\BACK\src\main\resources\application.properties`
  - `R\BACK\.env.example`
  - `J\ai\vision\model\ImageAnalysis.java`
  - `J\ai\vision\service\ImageAnalysisService.java`
  - `J\tournee\model\TourneeVisit.java`
  - `J\tournee\service\TourneeService.java`
  - `J\rapport\service\PdfRapportService.java`
  - `T\tournee\service\TourReviewRegressionTest.java` (ajouts seulement)
  - `T\tournee\service\TourneeWorkflowTest.java` (ajouts seulement)
- **MOBILE** :
  - `M\lib\models\tour_progress.dart`
  - `M\lib\pages\active_tracking_page.dart`
  - `M\test\vision_quota_test.dart` (ajouts seulement)
- **FRONT** :
  - `F\src\pages\tournees\TourneeDetailsModal.jsx`
  - `F\src\pages\tournees\TourneePage.jsx`
  - `F\src\services\aiService.js`
- **Racine** : `R\README.md`

**Créés**
- **BACK** :
  - `J\ai\common\LocalVisionClient.java`
  - `J\ai\vision\service\LocalCleanlinessMapper.java`
  - `T\ai\common\LocalVisionClientTest.java`
  - `T\ai\vision\LocalCleanlinessMapperTest.java`
  - `T\ai\vision\ImageAnalysisServiceLocalTest.java`
  - `T\ai\config\LocalVisionPropertiesTest.java`
- **Lanceurs** : `R\scripts\run-local-vision.bat` et `.sh`
- **`CV\service\`** : `app.py`, `test_service.py`, `requirements.txt`, `README_service.md`, `modele\`, `exemples\`
- **`CV\integration\`** : jeu, kit, scripts et rapports

**Ce qui n'a pas été touché**
- `CollectionAiClient`, `GroqClient`, `OpenRouterClient`, l'extraction de CV, le RAG, l'optimisation des tournées, `AnalysePhotosPage.jsx` ;
- `ImageAnalysisServiceTest.java` (sauvegardé, inchangé).

**Vérifications faites**
- Les modifications sont des ajouts ciblés. Les seules lignes retirées sont celles qui ont été remplacées :
  - dans `ImageAnalysisService`, le bloc LLM, ré-indenté dans un `else`, avec un contenu identique ;
  - deux lignes de `TourneeService` ;
  - le bouton « Reprendre la photo APRÈS » du mobile.
- Les fins de ligne CRLF des fichiers existants sont conservées. Les nouveaux fichiers Java sont en LF.
- Numéros de ligne de l'état des lieux revérifiés avant modification :
  - `ImageAnalysisService.java` 39-52 et 92-134 ;
  - `TourneeService.java` 166-215 ;
  - `PdfRapportService.java` 104-105 ;
  - `active_tracking_page.dart` 886-935.
- Aucun écart avec l'état des lieux, sinon la remarque de D5 (§ 5).

## 3. Résultats exécutés ici

| contrôle | commande | résultat |
|---|---|---|
| Tests du service | `cd CV\service ; .venv_service\Scripts\python -m unittest -v test_service` | **14 réussis, 0 échec** (health, 3 prédictions, égalité bit à bit avec `exemple_pretraitement.py`, EXIF, octets invalides, type refusé, > 12 Mo, seuil invalide, chemin inconnu, porte de qualité, mesures identiques à e7, jeton absent ou faux, SHA-256 erroné, modèle modifié) |
| Mêmes tests par le kit | `powershell -File kit_tests_autre_pc\01_service_python.ps1` | 14 réussis ; cohérence CONFORME (journaux dans `CV\integration\execution_poste_A\kit_01\`) |
| Cohérence, 30 images droites | `python kit_tests_autre_pc\test_coherence.py` (service réel sur 127.0.0.1:8090) | écart absolu maximal **0,000** (critère ≤ 1e-5) ; accord de l'argmax **30/30** |
| Cohérence, 3 images tournées avec balise EXIF 6, 8 et 6 | idem | écart maximal **0,000** par rapport à l'image droite d'origine ; `exif_orientation_applied` vrai |
| Cohérence, qualité | idem | écart netteté **0,000**, luminosité **0,000** (critère ≤ 1e-6) |
| Pour information : sans `exif_transpose` | `construire_jeu_coherence.py` | écarts 8,4e-4 / **1,77e-2** / 2,0e-6 sur les 3 images tournées. Classe inchangée sur ces 3 images, mais l'écart dépasse largement la tolérance. |
| EXIF dans le dataset | idem, sur `dataset_entrainement_v2\images` | **0 / 9 483** images ont une orientation ≠ 1 (aucune balise). L'entraînement a donc vu des images « droites » au sens EXIF. Appliquer `exif_transpose` ne change rien pour elles et corrige les photos de téléphone tournées. |
| Lint web | `npm run lint` | code 0 ; 5 avertissements, tous dans des fichiers non modifiés (`AssistantPage.jsx`, `AuthContext.jsx`, `PointCollectePage.jsx`) |
| Build web | `npm ci` puis `npm run build` | codes 0 ; avertissement préexistant sur la taille d'un bloc (> 500 kB) |
| Compilation Java (étape 0b) | `mvnw -q -B -DskipTests test-compile` (JAVA_HOME limité à la session) | **compilé** : code principal et classes de test, journal vide (aucune erreur ni avertissement) |

**Détail du JDK de l'étape 0b**
- Fichier : `OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip`.
- SHA-256 attendu (fourni par `api.adoptium.net`) = obtenu = `e53a79c3…7cb0`.
- **Le binaire est servi depuis github.com** (lien de téléchargement renvoyé par l'API d'Adoptium), et non depuis adoptium.net même.

## 4. Ce qui reste à tester sur le poste B

| quoi | script du kit | critère de réussite |
|---|---|---|
| Prérequis | `00_verifier_prerequis.ps1` | code 0 |
| Service Python et cohérence (déjà réussis sur le poste A, à rejouer sur le poste B) | `01_service_python.ps1` | 14 tests OK, cohérence CONFORME |
| Tests Java ciblés (11 classes, dont les 4 nouvelles), puis suite complète — **NON TESTÉ** | `02_tests_backend.ps1` | `BUILD SUCCESS` deux fois ; les tests existants doivent passer sans modification |
| Mobile : `flutter analyze` et `flutter test`, dont 3 nouveaux tests — **NON TESTÉ** | `03_tests_mobile_web.ps1` | codes 0 |
| Web : lint et build (déjà réussis sur le poste A) | `03_tests_mobile_web.ps1` | codes 0 |
| Bout en bout LOCAL, 33 images par l'API réelle — **NON TESTÉ** | `04_e2e.ps1` | probabilités en base = référence à 1e-5 ; même classe ; `decision = A_VERIFIER` partout ; `confidence` nulle ; fin de tournée possible |
| Retour en arrière (backend relancé en LLM, sans clé) — **NON TESTÉ** | `04_e2e.ps1` (second passage) | `ANALYSIS_UNAVAILABLE` / `NOT_CONFIGURED`, aucun champ local, comme aujourd'hui |
| Affichage réel mobile, web et PDF (vérification visuelle) — **NON TESTÉ** | aucun script | probabilités, pastille, « Photo à refaire », justification visibles |

## 5. Écarts par rapport aux décisions D1 à D14

| décision | écart ou précision | raison |
|---|---|---|
| D12 | La copie `CV\service\modele\model_card.json` a son SHA-256 ONNX **corrigé**. `CV\export\model_card.json` indique `c235229e…`, alors que le fichier livré a pour empreinte `da40e684…`. Le fichier livré est bien celui de l'évaluation finale : écart de 4,8e-7 avec `resultats\predictions.csv` sur 40 images de valid, dont les probabilités sont arrondies à 1e-6. L'original n'est pas modifié ; la copie contient un champ `note_sha256`. | Sans correction, le service refuserait de démarrer (D12). La cause de l'écart n'est pas établie. **Décision à prendre** : régénérer la fiche d'export. |
| D12 | `/health` n'exige pas le jeton ; seul `/predict` l'exige. Un corps de plus de 12 Mo est lu puis jeté (jusqu'à 48 Mo) avant la réponse 413. | `/health` n'expose aucune donnée sensible et sert aux scripts. La lecture du corps garantit que le client reçoit bien la réponse 413. |
| D13 | Deux réglages ajoutés : `ai.local-vision.model-name` et `ai.local-vision.model-sha256` (`LOCAL_VISION_MODEL_NAME`, `LOCAL_VISION_MODEL_SHA256`), par défaut `proprete_mobilenetv3l` et `da40e684…`. Le client refuse une réponse dont le SHA-256 diffère (`MODEL_MISMATCH`). | Le backend n'a pas accès à `model_card.json`. D14 exige pourtant `model = nom + SHA-256` ; cela garantit en plus que l'analyse enregistrée vient bien de ce modèle. |
| D5 | `TourneeResponse` n'a pas été modifié. | Il expose déjà les objets `TourneeVisit` entiers (`visits`) : les nouveaux champs y figurent automatiquement, comme `analysisStatus`. |
| D5 / D6 | En cas de panne (`ANALYSIS_UNAVAILABLE`), `decision` et les probabilités restent **nulles**. | Il n'y a pas eu d'analyse ; le statut `ANALYSIS_UNAVAILABLE` suffit à demander une revue humaine. |
| D6 | Un seuil hors de ]0, 1] est ignoré, ce qui donne toujours `A_VERIFIER`. | Choix prudent face à une erreur de configuration. |
| D7 | Luminosité calculée sur l'image entière après orientation. | Dans e7, `g640` est l'image entière, car les images du dataset font 640×640. Le code n'impose aucun redimensionnement à 640. |
| D10 web | La justification est lue par `GET /api/ai/vision/analyses/{id}` (`aiService.getPhotoAnalysis`, ajouté). | D5 ne copie pas `description` dans la visite ; l'endpoint existant est réservé à un utilisateur authentifié ayant accès à la tournée. |
| D1 | `ImageAnalysisService` : `@RequiredArgsConstructor(onConstructor_ = @Autowired)` et un constructeur à 5 arguments conservé (client local nul, d'où `NOT_CONFIGURED` en mode LOCAL). | Permet de garder `ImageAnalysisServiceTest` **strictement inchangé**. En mode LLM, `evaluateEvidence` compare maintenant la version attendue à `evaluationConfiguration().promptVersion()`, qui vaut toujours `point-cleanliness-v1` : comportement identique. |
| Étape 6 | Les tests du mode LOCAL sont dans une **nouvelle classe** `ImageAnalysisServiceLocalTest`. | Pour ne pas toucher au fichier de tests existant. |
| Étape 10 (04) | Démarrer la tournée exige OpenRouteService : clé et réseau, sans repli dans le code. Sans lui, `e2e_vision.py` passe la tournée `EN_COURS` **directement dans la base de test** (`docker exec … mongosh`) et le note dans `e2e_*_resume.json`. Le reste du parcours (arrivée, photo, analyse, fin) passe par l'API réelle. | Pas d'autre moyen de tester sans clé ORS. |
| Étape 10 (02) | Les tests ciblés incluent en plus `ImageAnalysisServiceLocalTest` et `AiConfigTest`. | Ils couvrent le changement. |
| Étape 10 (04) | Le backend de test tourne sur le port **18080**, lancé depuis `resultats_autre_pc\e2e_run`. Les réglages sont passés en arguments, les clés sont vides et un secret JWT aléatoire est généré à chaque exécution. | Évite de lire un éventuel `BACK\.env` (avec de vraies clés) et d'entrer en conflit avec un backend de développement sur 8080. |
| Étape 10 (c) | Le zip contient `BACK\.env.example`. | C'est un modèle sans secret, nécessaire à la documentation ; aucun fichier `.env` réel n'est inclus. |
| Règles | Maven a écrit dans `%USERPROFILE%\.m2\` (dépôt de dépendances et wrapper), hors du projet. `npm ci` et `npm run build` ont créé `F\node_modules` et `F\dist`. La compilation a créé `R\BACK\target`. | Effets normaux des commandes demandées ; tous exclus du zip. |

## 6. Retour en arrière et arrêt du service

1. Dans `BACK\.env` (ou l'environnement), mettre `VISION_ENGINE=LLM`, ou supprimer la ligne : `LLM` est la valeur par défaut. Redémarrer le backend.
2. Arrêter le service Python avec Ctrl+C dans sa fenêtre (`scripts\run-local-vision.bat`). Sinon : `Get-CimInstance Win32_Process -Filter "Name='python.exe'" | ? CommandLine -like '*app.py*' | % { Stop-Process -Id $_.ProcessId }`.
3. Le flux Groq/OpenRouter reprend tel quel : son code n'a pas été modifié, et le mode LLM réutilise exactement le chemin d'origine.
4. Les analyses déjà faites en mode LOCAL restent en base avec `provider=LOCAL` et `promptVersion=local-cleanliness-v1`. `reusableVisitAnalysis` ne les réutilise jamais en mode LLM, et inversement (testé dans `ImageAnalysisServiceLocalTest.analysesOfAnotherEngineAreNeverReused`, **NON TESTÉ** à l'exécution). Les nouveaux champs restent nuls pour les analyses LLM, et le mobile, le web et le PDF les traitent comme facultatifs.
5. Retour complet au code d'origine : recopier les fichiers de `CV\integration\sauvegardes\` à leur place, puis supprimer les fichiers créés listés dans le journal.

## 7. Paquet de transfert

| élément | valeur |
|---|---|
| Zip | `CV\integration\transfert\pfe_mahdi_transfert.zip` |
| Taille | 34,6 Mo (463 fichiers), racine `pfe_mahdi\` |
| SHA-256 | `dbda97b6b1b30ac33c87eda2e434babc15750b03566b32d6c8b9061dbc3dad5c` (aussi dans `transfert\SHA256.txt`) |
| Liste complète | `CV\integration\transfert\liste_contenu.txt` |
| Contrôles du script | 0 fichier `.env` (seul `BACK\.env.example`, sans secret, est inclus) ; 0 dossier exclu (`.venv*`, `node_modules`, `target`, `build`, `dist`, `.dart_tool`, `uploads`, `outils`, `transfert`) ; 0 motif de clé connu (Groq, OpenRouter, Google, JWT) dans les fichiers texte |

| contenu | fichiers |
|---|---|
| `BACK` (sans `target`, `uploads` ni `.env`) | 191 |
| `MOBILE` (sans `build`, `.dart_tool`) | 108 |
| `FRONT` (sans `node_modules`, `dist`) | 52 |
| `scripts` | 18 |
| `README.md` | 1 |
| `computer_vision\service` : `app.py`, `test_service.py`, `requirements.txt`, `README_service.md`, `modele\` (3), `exemples\` (4) | 11 |
| `computer_vision\export` : `proprete_mobilenetv3l.onnx`, `model_card.json`, `exemple_pretraitement.py` | 3 |
| `computer_vision\code\e7_qualite_image.py` | 1 |
| `computer_vision\integration` : rapports, journal, sauvegardes (17 originaux), journaux du poste A, kit (scripts, `jeu_coherence\` de 33 images et 2 références, `resultats_autre_pc\LISEZMOI.md`) | 78 |

Exclus aussi : le dossier Llama, le dataset, les embeddings et les modèles d'entraînement, `.venv_service`, `.venv_kit` et le JDK portable.

**À noter** : la copie de ce rapport placée dans le zip a été écrite **avant** la création du zip. Sa section 7 ne contient donc pas ces valeurs ; elles font foi ici et dans `transfert\SHA256.txt`.

## 8. Partie L : dossier Llama (lecture seule)

Dossier : `R\llm_Agent 3  Agent Vision par Ordinateur & Contrôle Qualité de Collecte\` (deux espaces après « 3 »). Il est situé **dans** `pfe_mahdi`, et non à la racine de `mahdi`. Il n'est pas inclus dans le zip.

| élément | constat |
|---|---|
| Contenu | **un seul fichier** : `Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf`, 4 920 739 232 octets (4,58 Gio), daté du 21/09/2026 |
| Programme (llama.cpp, llamafile, Ollama…) | **aucun** dans le dossier. `llama-server`, `llama-cli`, `llamafile`, `ollama`, `lms` et `koboldcpp` sont introuvables dans le PATH et sous `mahdi\`. Aucune option `--version` ou `--help` n'a donc pu être lancée. |
| Format (lu dans l'en-tête GGUF, sans charger le modèle) | GGUF version 3, 292 tenseurs, 33 métadonnées |
| Modèle | `general.name = Meta Llama 3.1 8B Instruct`, architecture `llama`, 32 couches, contexte 131 072, vocabulaire 128 256, langues déclarées dont `fr` |
| Quantification | `general.file_type = 15` (Q4_K_M), `quantization_version = 2`, quantifié avec une matrice d'importance (`quantize.imatrix.*`) |
| Licence | métadonnée `general.license = llama3.1`. **Aucun fichier de licence** dans le dossier. La licence communautaire Llama 3.1 demande de fournir une copie de la licence et la mention « Built with Llama » en cas de redistribution (à vérifier sur le texte officiel). |
| Lancement possible | Aucun README, rien dans les fichiers. **DÉDUIT** : avec llama.cpp, `llama-server -m "<chemin>\Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf" --host 127.0.0.1 --port 8081 -c 8192`. Avec Ollama, un `Modelfile` contenant `FROM <chemin du .gguf>`, puis `ollama create`. |
| Serveur HTTP compatible OpenAI | Pas avec le seul fichier présent. **DÉDUIT** : `llama-server` (llama.cpp) et Ollama exposent tous deux `/v1/chat/completions`. |
| Capacité | **Modèle de texte uniquement** : il ne voit pas les images et ne peut pas remplacer le classifieur. Il pourrait au mieux reformuler la justification. |

**Questions pour une intégration éventuelle comme générateur de texte facultatif**
1. Quel programme d'exécution choisir, en quelle version, et où le stocker (environ 5 Go de modèle, plus le binaire) ?
2. Le gabarit de D8 (« sans LLM ») reste-t-il la référence, avec le LLM désactivé par défaut et sans effet sur le statut ni la décision ?
3. Quelle latence est acceptable sur le poste cible, avec un modèle 8B en Q4 sur CPU ?
4. Qui fournit le fichier de licence et la mention exigée ?
5. Comment empêcher le texte généré d'affirmer plus que les probabilités, par exemple « collecte prouvée » ?

## 9. Points d'attention

- **Limites du modèle** :
  - étiquettes d'entraînement en partie extrapolées ;
  - classe CANNOT_DETERMINE apprise sur des images **synthétiques** ;
  - précision de PROPRE d'environ **0,71** sur le test (30 DEBORDEMENT parmi les prédits PROPRE) ;
  - images surtout issues du web chinois ;
  - **rien n'est validé sur le terrain**.

  Ces limites sont rappelées dans `README.md`, `README_service.md` et les commentaires de `LocalVisionClient` et `LocalCleanlinessMapper`.
- **Seuils non fixés** : `VISION_PROPRE_THRESHOLD` est vide, donc la décision vaut toujours `A_VERIFIER`. Les seuils de qualité sont vides, donc la porte est désactivée. Les valeurs d'exemple en commentaire (netteté 118,68 ; luminosité 30) viennent de l'étape 7, mesurées sur des images synthétiques : elles sont optimistes et doivent être recalibrées sur des photos réelles.
- **La décision n'a aucun effet** sur l'état de la visite ni sur la fin de tournée. Elle ordonne seulement la revue humaine (commentaires dans `LocalCleanlinessMapper`, `ImageAnalysisService.applyLocalPrediction`, `TourneeVisit`, `TourneeService.copyLocalAnalysis` ; tests dans `TourneeWorkflowTest` et `TourReviewRegressionTest`, **NON TESTÉ**).
- **Code Java, Dart et JSX** : relu et recoupé avec les signatures réelles. Le Java compile ; il n'a pas été exécuté. Le Dart n'a même pas été compilé (Flutter absent) : surveiller sur le poste B les « if-case » de collection (`if (... case final x?)`, Dart ≥ 3.0, SDK du projet ≥ 3.5) et `FilledButton.icon`.
- **Étape 0b** : le JDK a été téléchargé depuis github.com (lien fourni par l'API d'Adoptium, empreinte vérifiée).
- **Bout en bout** : sans clé OpenRouteService, la tournée de test est démarrée par écriture directe dans la base de test, ce qui ne teste pas `/optimiser` ni `/start`.
- **Jeton** : `LOCAL_VISION_TOKEN` est vide par défaut. Le service n'écoute que sur 127.0.0.1, mais un jeton est conseillé si d'autres programmes tournent sur la machine.
- **Fiche d'export** : le SHA-256 de `CV\export\model_card.json` est périmé (§ 5).
