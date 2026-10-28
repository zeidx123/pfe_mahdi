# État des lieux du flux « Agent 3 Vision » avant intégration du modèle local

Rapport établi en **lecture seule** (aucun fichier de BACK, MOBILE, FRONT ou `computer_vision\` modifié ; aucun programme lancé, aucun accès réseau). Date : 2026-10-28.

**Conventions**
- « **LU** » : constaté dans un fichier, avec sa ligne. « **DÉDUIT** » : conclusion tirée de ce qui a été lu, sans vérification à l'exécution.
- Préfixes de chemins, utilisés pour garder les tableaux lisibles :

| préfixe | chemin complet |
|---|---|
| `R\` | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\` |
| `J\` | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\BACK\src\main\java\com\municipalite\gestiontournees\` |
| `T\` | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\BACK\src\test\java\com\municipalite\gestiontournees\` |
| `M\` | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\MOBILE\` |
| `F\` | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\FRONT\` |

---

## 0. Écarts entre la documentation fournie et le code (synthèse)

| # | La documentation dit | Le code montre (LU) |
|---|---|---|
| 1 | Le moteur renvoie 5 statuts : PROPRE, DEBORDEMENT, NON_CONFORME, INACCESSIBLE, CANNOT_DETERMINE | **4 statuts** : `CLEANED`, `NOT_CLEANED`, `OBSTRUCTED`, `CANNOT_DETERMINE` (`J\ai\vision\service\ImageAnalysisService.java:40`, `J\ai\common\GroqClient.java:27`), plus un 5ᵉ statut technique `ANALYSIS_UNAVAILABLE` (`ImageAnalysisService.java:137`). **Aucune** occurrence de PROPRE, DEBORDEMENT, NON_CONFORME, INACCESSIBLE dans BACK, MOBILE ou FRONT. |
| 2 | `AiProblem` est un enum (DEBORDEMENT, BAC_CASSE, NON_CONFORME, ACCES) | `AiProblem` est un **document MongoDB** (collection `ai_problems`) avec un champ texte `typeProbleme` (`J\ai\vision\model\AiProblem.java:15-36`). Aucun code ne crée d'`AiProblem` : le dépôt n'est que lu (`ImageAnalysisService.java:234`, `J\ai\assistant\service\tools\AssistantTools.java:72`). Les codes ACCES, BAC_CASSE, DEBORDEMENT, NON_CONFORME n'existent que dans le générateur du guide PDF chauffeur (`R\generate_pdf_guide.py:249-264`), comme codes d'incident à l'usage des chauffeurs. |
| 3 | Le chauffeur envoie 1 ou 2 photos par `POST /api/ai/vision/analyze` (multipart, 1 ou 2 images) | Le parcours réel du chauffeur passe par **`/api/tournees/{id}/points/{pointId}/photo`** (une photo par appel, `stage=BEFORE|AFTER`) puis par **`POST /api/tournees/{id}/points/{pointId}/analyse`** (`J\tournee\controller\TourneeController.java:84-91` et `128-131`). `/api/ai/vision/analyze` existe mais n'accepte qu'**une** photo (`VisionController.java:41-53`) et **n'est appelé ni par le mobile ni par une page routée du web** (seulement par `F\src\services\aiService.js:50`, sans appelant dans le web). |
| 4 | `POST /api/ai/vision/evaluate` | Il existe, mais c'est un **outil d'expérimentation réservé au SUPERVISEUR** (photo APRÈS obligatoire, AVANT facultative, plus `expectedModel` et `expectedPromptVersion`), sans lien avec une tournée (`VisionController.java:31-39`, `ImageAnalysisService.java:73-83`). |
| 5 | Formats JPEG, PNG ou WebP | **JPEG et PNG seulement** côté serveur (`ImageAnalysisService.java:210-218`, `J\tournee\service\TourneeService.java:227`) et côté mobile (`M\lib\services\photo_draft_service.dart:27-43`). WebP figure dans les types acceptés par `GroqClient` (ligne 28), mais n'y arrive jamais. |
| 6 | 10 Mo maximum | Exact dans le code (`ImageAnalysisService.java:203`, `211` ; `TourneeService.java:225` ; `photo_draft_service.dart:28`). La limite multipart de Spring est plus large : 20 Mo par fichier, 22 Mo par requête (`R\BACK\src\main\resources\application.properties:39`, `41`). |
| 7 | `ImageAnalysisService` appelle `GroqClient.analyzeImages` | Il appelle **`CollectionAiClient.analyzeImages`** (`J\ai\common\CollectionAiClient.java:25-30`), qui aiguille vers `GroqClient` **ou** `OpenRouterClient` selon `ai.collection-provider` (défaut `GROQ`). |
| 8 | Schéma JSON strict | Exact (`GroqClient.java:73-82`, `strict: true`). Mais, sur une erreur HTTP 400, le client **refait l'appel en `json_object` non contraint** (`GroqClient.java:125-127`) ; la validation se fait ensuite en Java. |
| 9 | Le modèle renvoie une justification factuelle en français, « stockée et liée à la réclamation ou au point » | Elle est stockée dans `ImageAnalysis.description` (`ImageAnalysisService.java:122`) et liée à la **visite** de la tournée (`TourneeVisit.analysisId`), **jamais à une réclamation** : le paquet `reclamation` ne contient aucune référence à une photo ou à une analyse. Elle n'est **affichée nulle part** dans l'interface routée : le mobile et le web n'affichent que le libellé du statut. |
| 10 | `pointcollecte/PointValidationTest.java` vérifie le passage automatique du point à VALIDÉ après une analyse réussie | Ce test vérifie la **validation des champs d'un point** : mise à jour partielle, coordonnées et taux de remplissage invalides (`T\pointcollecte\PointValidationTest.java:19-56`). **Aucun état VALIDÉ n'existe** : `PointCollecte` n'a pas de champ de statut et aucun code ne change l'état d'un point ou d'une visite d'après l'IA. |
| 11 | Moteur hébergé chez Groq | Modèle **figé dans le code** à `qwen/qwen3.8-27b` (`GroqClient.java:98-101`), URL figée à `https://api.groq.com/openai/v1` (ligne 103). |
| 12 | (Documentation du modèle local) Prétraitement obligatoire : « orientation EXIF appliquée » | La référence d'entraînement **n'applique pas** l'orientation EXIF : `pretraiter()` fait `Image.open(...).convert("RGB").resize(...)` sans `ImageOps.exif_transpose` (`R\computer_vision\export\exemple_pretraitement.py:28-29`). Aucune trace d'EXIF dans BACK, MOBILE ni FRONT. |
| 13 | (Hors vision, signalé) Page web « Vision IA — Inspection Photos » | `F\src\pages\analyses\AnalysePhotosPage.jsx` n'est **importée par aucune route** (`F\src\routes\AppRoutes.jsx:44-60`). Elle attend des statuts `ANOMALIE` / `NORMAL` et un champ `confidence` que le backend ne produit pas, et mentionne « Gemini Vision API ». C'est du code mort. |

---

## 3.1 Localisation et technologies

| composant | chemin | technologie (LU) | versions (LU) | dépendances / build |
|---|---|---|---|---|
| BACK | `R\BACK\` | Java / Spring Boot (web, security, data-mongodb, validation, websocket, mail), MongoDB | Java **17** (`pom.xml:22`), Spring Boot **3.2.5** (`pom.xml:9-10`), OkHttp 4.12.0 (`pom.xml:127-128`), Lombok 1.18.32 | **Maven** via wrapper `mvnw` / `mvnw.cmd`, Maven 3.9.16 (`R\BACK\.mvn\wrapper\maven-wrapper.properties`) |
| MOBILE | `R\MOBILE\` | Flutter (Android et **web**) | Dart SDK `>=3.5.0 <4.0.0` (`pubspec.yaml:7`), Flutter `>=3.24.0` (`pubspec.lock:946`) ; Flutter **3.24.5** / Dart 3.5.4 recommandés (`R\README.md:39`, `.github\workflows\cross-platform.yml:38`) | `pub` (`pubspec.yaml`) : `dio ^5.4.0`, `image_picker ^1.1.2`, `flutter_map`, etc. |
| FRONT (web superviseur) | `R\FRONT\` | React + Vite | React **^19.2.8**, Vite **^8.2.0**, react-router-dom ^7.18.2, axios ^1.19.0 (`F\package.json`) | **npm** (`package-lock.json`), Node 22 en CI (`cross-platform.yml:28`) |

Outils présents sur ce poste (commandes en lecture seule) : **Java, Maven, Flutter et Dart sont introuvables dans le PATH** ; Docker Desktop est présent ; Node v24.19.0 ; Python 3.13.15. Les tests Java et Flutter ne peuvent donc pas être lancés en l'état depuis ce poste.

---

## 3.2 Le flux vision de bout en bout

### 3.2.1 Trajet réel (parcours du chauffeur)

| étape | où (LU) | ce qui se passe |
|---|---|---|
| 1. Arrivée | `M\lib\services\tournee_service.dart:26-27` → `POST /api/tournees/{id}/points/{pointId}/arrive` → `TourneeService.arrive` (`TourneeService.java:144-148`) | La visite passe à `ARRIVED`. Sans arrivée, aucune photo n'est acceptée (`TourneeService.java:169`). |
| 2. Prise de vue | `M\lib\pages\active_tracking_page.dart:224-262`, méthode `_capture(stage)` | `ImagePicker.pickImage(source: camera` (galerie sur le web)`, imageQuality: 80, maxWidth: 1600, preferredCameraDevice: rear)`. La photo est d'abord gardée en brouillon local (`PhotoDraftService.save`). Bouton « Photo AVANT facultative », **désactivé dès qu'une photo APRÈS existe** (`active_tracking_page.dart:895-900`). |
| 3. Envoi | `M\lib\services\tournee_service.dart:69-80`, `upload(draft)` | `POST /api/tournees/{tourId}/points/{pointId}/photo`, multipart : champ `stage` (`BEFORE` ou `AFTER`) et champ `photo` (fichier `before.jpg` / `after.png`, type MIME détecté par les octets). |
| 4. Authentification | `M\lib\services\api_service.dart:26-30` | En-tête `Authorization: Bearer <jwt>`, envoyé uniquement à l'origine du serveur configuré. |
| 5. Stockage de la photo | `TourneeController.uploadPointPhoto` (`TourneeController.java:84-91`) → `TourneeService.savePointPhoto` (`TourneeService.java:167-177`) → `storePhoto` (`224-234`) | Contrôle de l'**en-tête Content-Type** (JPEG ou PNG), ≤ 10 Mo, `ImageIO.read` non nul. Écriture brute `UUID.jpg|png` dans `ai.storage.photos-dir`. `afterPhoto` ou `beforePhoto` est renseigné sur la `TourneeVisit` ; **l'analyse et la revue précédentes sont effacées** (lignes 174-175). |
| 6. Déclenchement de l'analyse | `active_tracking_page.dart:280-298` (automatique si `_automaticAnalysis`, désactivé par défaut : `AUTO_VISION_ANALYSIS`, lignes 37-38 ; interrupteur à l'écran lignes 846-852) ou bouton « Analyser la photo » (lignes 950-970) | `POST /api/tournees/{id}/points/{pointId}/analyse`, sans corps (`tournee_service.dart:28-29`). |
| 7. Orchestration | `TourneeController.analyze` (`TourneeController.java:128-131`) → `TourneeService.analyzeVisit` (`TourneeService.java:181-215`) | Exige une photo APRÈS. Verrou en mémoire par (tournée, point), pour éviter les appels en double (lignes 42-43 et 184-186). Réutilise une analyse existante via `reusableVisitAnalysis` (lignes 194-204), sinon appelle `analyzeStoredEvidence(afterUrl, beforeUrl, pointId, tourId, chauffeurId)` (ligne 205). Relit ensuite la tournée et refuse le résultat si les photos ont changé entre-temps (lignes 208-210). |
| 8. Analyse | `ImageAnalysisService.analyzeStoredEvidence` (`ImageAnalysisService.java:92-134`) | Construit un `ImageAnalysis` (provider, modèle, `promptVersion = "point-cleanliness-v1"`, `inputMode` = `AFTER_ONLY` ou `BEFORE_AFTER`). Relit les fichiers (`readStoredPhoto`, 194-208), valide les octets (`validatedImage`, 210-218 : signatures JPEG / PNG). Liste d'images **[AVANT, APRÈS]** ou **[APRÈS]** (lignes 106-108), puis `buildVisionPrompt(paired)` (148-164). |
| 9. Aiguillage | `CollectionAiClient.analyzeImages` (`CollectionAiClient.java:25-30`) | `GROQ` → `GroqClient.analyzeImages`, `OPENROUTER` → `OpenRouterClient.analyzeImages`. |
| 10. Appel Groq | `GroqClient.analyzeImages` (`GroqClient.java:61-89`) → `complete` (91-179) | 1 à 2 images, encodées en base64 dans des `data:` URL (lignes 69-72). `POST https://api.groq.com/openai/v1/chat/completions`, `temperature: 0`, `reasoning_effort: "none"`, `max_completion_tokens: 1024`, `response_format = json_schema strict`. |
| 11. Contrôle de la réponse | `GroqClient.java:83-88`, puis `ImageAnalysisService.java:111-122` | `status` doit faire partie des 4 libellés ; `description` doit être un texte non vide de 2000 caractères au plus. Sinon `ANALYSIS_UNAVAILABLE` avec une `failureCategory`. |
| 12. Persistance | `imageAnalysisRepository.save(analysis)` (`ImageAnalysisService.java:133`), collection `image_analyses` | Puis `TourneeVisit.analysisId` et `TourneeVisit.analysisStatus` sont enregistrés dans le document de la tournée (`TourneeService.java:211`). |
| 13. Effets de bord | — | **Aucun** (LU) : pas de changement d'état du point ni de la visite, pas d'`AiProblem` créé (`problemCreated(false)`, ligne 104), pas de notification, pas de lien avec une réclamation. Commentaire explicite, ligne 132 : « A model output is evidence for review, not an automatically established incident. » |
| 14. Affichage mobile | `active_tracking_page.dart:905-935` et `1250-1262` ; libellés dans `M\lib\models\tour_progress.dart:91-100` | Libellé du statut seulement (« Le point apparaît nettoyé », etc.), avec une couleur. **La description n'est pas affichée.** |
| 15. Affichage web | `F\src\pages\tournees\TourneeDetailsModal.jsx:10-13` et `79-98` | « Observation IA : <libellé> », observation du chauffeur, avis du superviseur, formulaire de revue. **La description n'est pas affichée.** |
| 16. Rapport PDF | `J\rapport\service\PdfRapportService.java:104-105` | « IA : <statut brut> », puis « Revue : <reviewLabel> » ou « À examiner ». |

### 3.2.2 Contrat de `POST /api/tournees/{id}/points/{pointId}/analyse` (chemin réellement utilisé)
- Rôles : `SUPERVISEUR` ou `CHAUFFEUR` (`TourneeController.java:24`), avec contrôle d'accès à la tournée (`access.requireTourAccess`, `TourneeService.java:235`).
- Requête : aucun corps.
- Réponse : `TourneeResponse` complet (`TourneeService.mapToResponse`, lignes 316-399) ; la visite concernée contient notamment :

```json
{ "pointId": "…", "status": "ARRIVED", "beforePhoto": "uuid.jpg|null", "afterPhoto": "uuid.jpg",
  "analysisId": "66f…", "analysisStatus": "NOT_CLEANED", "driverAssessment": null,
  "reviewLabel": null, "reviewNotes": null, "reviewedBy": null, "reviewedAt": null }
```

### 3.2.3 Contrat des endpoints `/api/ai/vision/*` (`VisionController.java`)

| endpoint | rôle | entrée | sortie |
|---|---|---|---|
| `GET /api/ai/vision/evaluation-config` (24-29) | SUPERVISEUR | — | `{provider, model, promptVersion}` |
| `POST /api/ai/vision/evaluate` (31-39) | SUPERVISEUR | multipart : `afterPhoto` (obligatoire), `beforePhoto` (facultatif), `expectedModel`, `expectedPromptVersion` (409 si différents de la configuration) | `ImageAnalysis` sans tournée |
| `POST /api/ai/vision/analyze` (41-53) | SUPERVISEUR, CHAUFFEUR | multipart : `photo`, `pointCollecteId`, `tourneeId`, `latitude` et `longitude` facultatifs (**non utilisés** par `analyzeImage`, `ImageAnalysisService.java:86-89`) | `ImageAnalysis` (une seule photo, `AFTER_ONLY`) ; **ne met pas à jour la visite** |
| `GET /api/ai/vision/analyses/{id}` (55-61) | authentifié + accès à la tournée | — | `ImageAnalysis` (seul endroit où la `description` est accessible) |
| `PATCH /api/ai/vision/confirm/{id}` (63-69) | SUPERVISEUR, CHAUFFEUR | `{validee: bool, correctionChauffeur: libellé}` | `ImageAnalysis` ; **aucun appelant** dans MOBILE ni dans une page routée du web |
| `PATCH /api/ai/vision/analyses/{id}/review` (71-77) | SUPERVISEUR | `{label, notes}` | `ImageAnalysis` |
| `GET /api/ai/vision/analyses`, `GET /api/ai/problems` (84-90) | SUPERVISEUR | — | listes |

Document `ImageAnalysis` (`J\ai\vision\model\ImageAnalysis.java`). Champs **réellement renseignés** : `id`, `pointCollecteId`, `tourneeId`, `chauffeurId`, `photoUrl` (= après), `afterPhotoUrl`, `beforePhotoUrl`, `inputMode`, `provider`, `model`, `promptVersion`, `failureCategory`, `providerHttpStatus`, `status`, `description`, `problemCreated=false`, `createdAt`, les champs de revue et de confirmation. Champs **jamais renseignés** (DÉDUIT de l'absence d'écriture) : `typeAnomalie`, `confidence`, `severity`, `problemId`.

Exemple de réponse :

```json
{ "id":"66f…", "pointCollecteId":"p1", "tourneeId":"t1", "chauffeurId":"u7",
  "photoUrl":"/uploads/photos/2c1….jpg", "afterPhotoUrl":"/uploads/photos/2c1….jpg", "beforePhotoUrl":null,
  "inputMode":"AFTER_ONLY", "provider":"GROQ", "model":"qwen/qwen3.8-27b", "promptVersion":"point-cleanliness-v1",
  "failureCategory":null, "providerHttpStatus":null, "status":"NOT_CLEANED",
  "description":"Des sacs restent visibles au pied du conteneur.", "confidence":null,
  "problemCreated":false, "createdAt":"2026-10-28T09:12:03" }
```

### 3.2.4 Statuts (valeurs réelles)

| valeur | origine | sens (prompt, `ImageAnalysisService.java:155-158`) |
|---|---|---|
| `CLEANED` | modèle | le point paraît propre, sans déchets visibles restant à collecter |
| `NOT_CLEANED` | modèle | des déchets visibles paraissent rester au point |
| `OBSTRUCTED` | modèle | un obstacle visible empêche l'accès ou la collecte |
| `CANNOT_DETERMINE` | modèle | cadrage, qualité, visibilité ou incohérence des preuves (y compris : les deux images ne montrent pas le même point) |
| `ANALYSIS_UNAVAILABLE` | Java (`markUnavailable`, 136-146) | échec technique, avec `failureCategory` ∈ {`NOT_CONFIGURED`, `PROVIDER_HTTP_ERROR`, `PROVIDER_TIMEOUT`, `INVALID_PROVIDER_RESPONSE`, `INVALID_ANALYSIS_RESPONSE`, `EVIDENCE_UNAVAILABLE`, `RESPONSE_TRUNCATED`, `CONTENT_FILTER`, `PROVIDER_REFUSAL`, `EMPTY_RESPONSE`, `ANALYSIS_FAILED`, …} |

Ce ne sont pas des enums Java : ce sont des `String`, validées par des `Set` dupliqués à 4 endroits (`ImageAnalysisService.java:40`, `TourneeService.java:40`, `GroqClient.java:27`, `OpenRouterClient.java:27`), et dans le mobile (`active_tracking_page.dart:50-55`, `tour_progress.dart:91-97`) et le web (`TourneeDetailsModal.jsx:10-13`).

### 3.2.5 Prompt d'inspection (résumé, `ImageAnalysisService.java:148-164`, version `point-cleanliness-v1`)
- **Rôle** : aider à examiner des preuves visuelles d'un point de collecte municipal.
- **Ordre des images** : « La première image est AVANT ; la seconde est APRÈS », ou « Une seule image est fournie : APRÈS la collecte déclarée ».
- **Consignes** : évaluer **uniquement la photo APRÈS** (la paire sert à comparer), ne jamais affirmer qui a collecté ni qu'une tâche est prouvée, n'inventer aucun pourcentage de confiance. Si les images ne montrent pas le même point ou pas un point de collecte, répondre `CANNOT_DETERMINE`.
- **Schéma imposé** (`GroqClient.java:73-82`) : `{status: enum[CLEANED, NOT_CLEANED, OBSTRUCTED, CANNOT_DETERMINE], description: string}`, `required` sur les deux champs, `additionalProperties: false`, `strict: true`.

---

## 3.3 Qui consomme les statuts

### 3.3.1 Occurrences significatives

| fichier : ligne | rôle | consommation |
|---|---|---|
| `J\ai\vision\service\ImageAnalysisService.java:40, 60, 118, 173, 183` | logique métier et validation | Liste blanche des 4 libellés : réutilisation, validation de la réponse, correction du chauffeur, revue du superviseur. |
| `J\ai\common\GroqClient.java:27, 76, 84` ; `OpenRouterClient.java:27` | contrat du fournisseur | Enum du schéma JSON et validation. |
| `J\tournee\service\TourneeService.java:40, 161, 218` | validation | `HUMAN_LABELS` : `driverAssessment` (fin de visite) et `reviewLabel` (revue du superviseur). |
| `J\tournee\service\TourneeService.java:200-201, 211` | base de données | Copie de `status` dans `TourneeVisit.analysisStatus`. |
| `J\tournee\model\TourneeVisit.java:20-28` | base de données | `analysisId`, `analysisStatus`, `driverAssessment`, `reviewLabel`, `reviewNotes`, `reviewedBy`, `reviewedAt`. |
| `J\rapport\service\PdfRapportService.java:104-105` | rapport | Statut brut affiché dans le PDF de tournée. |
| `J\ai\assistant\service\tools\AssistantTools.java:72` | statistiques | `totalProblemesIa = aiProblemRepository.count()`, toujours 0 puisqu'aucun `AiProblem` n'est créé (DÉDUIT). |
| `M\lib\models\tour_progress.dart:91-100` | affichage | Libellés français des 5 valeurs. |
| `M\lib\pages\active_tracking_page.dart:50-55` | logique d'interface | `_hasSavedAnalysis` : si le statut est l'un des 4 libellés, l'appli affiche « Résultat IA enregistré » et **ne propose plus de relancer** ; sinon elle propose « Analyser » ou « Réessayer ». |
| `M\lib\pages\active_tracking_page.dart:779-780, 905-935` | affichage | Couleur verte pour CLEANED, rouge pour NOT_CLEANED, gris sinon ; icône ✓ pour CLEANED. |
| `M\lib\pages\active_tracking_page.dart:971-984` | saisie | Liste déroulante « Mon observation » (4 libellés). |
| `F\src\pages\tournees\TourneeDetailsModal.jsx:10-13, 84-98` | affichage et revue | Libellés et liste déroulante de la revue du superviseur. |
| `F\src\pages\analyses\AnalysePhotosPage.jsx:38-56, 114-120` | code mort | Attend `ANOMALIE` / `NORMAL`, `typeAnomalie`, `confidence` ; page non routée. |
| `F\src\services\aiService.js:42-56` | client d'API | `getPhotoAnalyses`, `getAiProblems`, `analyzePhoto`, `confirm` : utilisés seulement par la page morte (DÉDUIT de l'absence d'import ailleurs). |
| Tests : `T\ai\common\GroqClientTest.java:91-92`, `OpenRouterClientTest.java:92-93`, `T\ai\vision\ImageAnalysisServiceTest.java`, `T\tournee\service\TourReviewRegressionTest.java`, `TourneeWorkflowTest.java:80-83` ; `M\test\vision_quota_test.dart:158-166` | tests | Fixent les 4 libellés et `ANALYSIS_UNAVAILABLE`. |

PROPRE, DEBORDEMENT, NON_CONFORME, INACCESSIBLE, BAC_CASSE, ACCES : **aucune occurrence** dans le code applicatif. `POINT_INACCESSIBLE` apparaît seulement dans un commentaire (`ImageAnalysis.java:44`, champ `typeAnomalie` jamais renseigné).

### 3.3.2 Si `OBSTRUCTED` (l'équivalent réel d'« INACCESSIBLE ») n'était plus jamais produit par le modèle
- **Rien ne casse** (DÉDUIT) : aucune règle métier, statistique ni notification ne dépend d'`OBSTRUCTED` produit par l'IA. Le libellé reste utilisable par le chauffeur (`driverAssessment`) et par le superviseur (`reviewLabel`), qui passent par `HUMAN_LABELS`.
- **Changement de sens** : un point bloqué serait classé par le nouveau modèle en DEBORDEMENT, PROPRE ou CANNOT_DETERMINE. Le blocage d'accès n'est alors plus signalé que par le chauffeur, via l'issue `ISSUE` avec une note obligatoire (`TourneeService.java:157-159`), ce qui est déjà le chemin prévu.
- NON_CONFORME n'existe pas dans le code : rien à perdre.

### 3.3.3 Si la justification devenait un court texte de gabarit
- La seule contrainte technique est : texte non vide, 2000 caractères au plus (`ImageAnalysisService.java:116-118`).
- Elle n'est affichée dans aucune interface routée. Le changement est donc invisible pour les utilisateurs actuels. Seuls changent le contenu de `image_analyses.description` et la réponse de `GET /api/ai/vision/analyses/{id}`.
- Le message de `markUnavailable` (lignes 140-143) est déjà un texte de gabarit.

---

## 3.4 Photos avant et après

- **Nombre de photos** (LU) : la photo APRÈS est **obligatoire** pour analyser (`TourneeService.java:183`, `ImageAnalysisService.java:94-96`) et pour déclarer une collecte terminée (`TourneeService.java:155`, `133-134`). La photo AVANT est **facultative** (« Photo AVANT facultative », `active_tracking_page.dart:900` ; « AVANT (facultative) », `TourneeDetailsModal.jsx:79`).
- **Une seule analyse pour les deux images** : un seul appel au modèle avec [AVANT, APRÈS] dans cet ordre (`ImageAnalysisService.java:106-110`) ; le modèle décide seul, sur consigne d'évaluer la photo APRÈS (prompt, ligne 152). Il n'y a pas d'analyse par photo ni de règle de combinaison en Java.
- **Sens d'« avant » et « après »** : AVANT = état du point avant le vidage ; APRÈS = état « après la collecte déclarée » (prompt, ligne 163). Dans l'interface : « Collectez les déchets, puis prenez une photo APRÈS » (`active_tracking_page.dart:858-861`).
- **Ordre de prise** : le mobile n'autorise la photo AVANT que tant qu'il n'y a pas de photo APRÈS (`active_tracking_page.dart:895-899`). Le serveur, lui, accepte `stage=BEFORE` à tout moment pendant que la visite est courante (`TourneeService.java:167-176`) (DÉDUIT : pas de contrôle d'ordre).
- **Validation avec une seule photo** : oui. Le cas `AFTER_ONLY` est le cas nominal, testé par `afterOnlyIsSufficientAndHasNoInventedConfidence` (`T\ai\vision\ImageAnalysisServiceTest.java:46-55`). Il n'y a de toute façon pas de « validation » automatique (voir 3.5).
- **Photo de fin de tournée** (`TourneeService.saveFinPhoto`, ligne 178) : elle n'est jamais analysée.

---

## 3.5 Validation automatique du point

- `T\pointcollecte\PointValidationTest.java` **ne teste pas la vision** : il teste `PointCollecteService.update` et `create` (valeurs facultatives conservées, coordonnées et taux de remplissage invalides refusés). Voir l'écart n° 10.
- **Aucun état VALIDÉ** : `PointCollecte` (`J\pointcollecte\model\PointCollecte.java`) n'a pas de statut de propreté. Seule une visite (`TourneeVisit.Status`, `TourneeVisit.java:9`) a un état : `PENDING → ARRIVED → COMPLETED_REPORTED | ISSUE_REPORTED`, fixé **par la déclaration du chauffeur** (`completeVisit`, `TourneeService.java:149-165`), **indépendamment du résultat IA**. La fin de tournée n'exige pas d'analyse : `outageDoesNotBecomeCleanAndDoesNotBlockReportedCompletion` (`T\tournee\service\TourneeWorkflowTest.java:77-84`) le vérifie.
- **Conditions de « validation »** : aucune condition de statut, de confiance ou d'utilisateur, faute de mécanisme. Il n'existe **aucune confiance numérique** : le prompt l'interdit, et le test vérifie `confidence == null` (`ImageAnalysisServiceTest.java:51`).
- **Revue humaine** : oui.
  - Le superviseur enregistre un `reviewLabel` et des notes : `PATCH /api/tournees/{id}/points/{pointId}/review` (`TourneeService.reviewVisit`, 216-223), recopié dans l'`ImageAnalysis` (`review`, 182-192), depuis le web (`TourneeDetailsModal.jsx:55-98`).
  - Le chauffeur donne une observation (`driverAssessment`) à la fin de la visite.
  - Il n'existe pas d'état « à vérifier » explicite. Le PDF affiche « À examiner » si `reviewLabel` est vide (`PdfRapportService.java:105`).
- **Correction** : la prédiction d'origine n'est jamais écrasée (`ImageAnalysis.java:43`, commentaire « never overwritten by human correction » ; test `humanCorrectionAndReviewPreserveOriginalPrediction`, lignes 117-125). Le superviseur peut réviser à tout moment, sans contrôle de l'état de la tournée (DÉDUIT de `reviewVisit`). Remplacer une photo efface l'analyse et la revue précédentes (`TourneeService.java:174-175`).

---

## 3.6 Traitement des images et configuration

| aspect | constat (LU) |
|---|---|
| Format | Serveur : JPEG ou PNG selon l'en-tête `Content-Type` à l'upload (`TourneeService.java:227`), puis selon les octets magiques avant l'analyse (`ImageAnalysisService.java:214-216`). Mobile : vérification des octets (`photo_draft_service.dart:27-43`). |
| Taille | 10 Mo (serveur et mobile) ; multipart Spring 20 Mo / 22 Mo (`application.properties:39, 41`). |
| Décodage | `ImageIO.read(...) != null`, seulement pour vérifier que l'image est lisible (`TourneeService.java:229`, `ImageAnalysisService.java:211`). |
| Compression / redimensionnement | Côté client seulement : `imageQuality: 80`, `maxWidth: 1600` (`active_tracking_page.dart:241-242`). Le serveur stocke les octets tels quels. |
| Encodage vers le fournisseur | base64 dans une `data:` URL (`GroqClient.java:70-71`). |
| **Orientation EXIF** | **Appliquée nulle part** (aucune occurrence d'`exif` ni d'`orientation` dans BACK, MOBILE, FRONT). DÉDUIT, non vérifié : selon la plateforme, `image_picker` peut réécrire ou conserver la balise d'orientation lors du redimensionnement à 1600 px. Le comportement réel sur Android est à mesurer. |
| Stockage | Disque local : `ai.storage.photos-dir` (défaut `uploads/photos`, relatif au dossier de lancement ; variable `AI_STORAGE_PHOTOS_DIR`, `application.properties:74`). Nom `UUID.jpg|png`. La visite stocke le **nom de fichier** ; l'analyse stocke l'URL `/uploads/photos/<uuid>.jpg`. Les fichiers sont servis par `GET /api/uploads/{*path}` avec contrôle d'accès (`J\files\UploadController.java:29-82`). |
| Configuration | `AiProperties` (préfixe `ai`, `J\ai\config\AiProperties.java`) : `collection-provider` (`GROQ` ou `OPENROUTER`, ligne 17), `groq.{api-key, base-url, model-text, model-vision, timeout-seconds=60}` (33-39), `open-router.{…}` (49-56), `storage.photos-dir` (59-63). `AiConfig` (`J\ai\config\AiConfig.java:99-108`) : `OkHttpClient` partagé, réglé sur le délai Gemini ; `GroqClient` le dérive avec son propre délai, borné entre 1 et 120 s (`GroqClient.java:37-46`). |
| Fichier `.env` | `application.properties:5` : `spring.config.import=optional:file:./.env[.properties]`. **`BACK\.env` est absent** ; seul `R\BACK\.env.example` existe. Variables liées à la vision (noms seulement) : `COLLECTION_AI_PROVIDER`, `GROQ_API_KEY`, `GROQ_MODEL_VISION`, `GROQ_TIMEOUT_SECONDS`, `OPENROUTER_API_KEY`, `OPENROUTER_MODEL_VISION`, `OPENROUTER_TIMEOUT_SECONDS`, `AI_STORAGE_PHOTOS_DIR`. |
| Délais | Groq : `GROQ_TIMEOUT_SECONDS`, 60 s par défaut (call, read, write), connexion limitée à 10 s. Pas de nouvelle tentative (`retryOnConnectionFailure(false)`). |
| Erreurs et débit | Toute erreur devient `ANALYSIS_UNAVAILABLE` avec une `failureCategory` et un statut HTTP, enregistrés sans corps ni clé (`ImageAnalysisService.java:123-146`). HTTP 429 : message spécifique (« La limite temporaire du service IA est atteinte… »), **sans nouvelle tentative automatique** ; le chauffeur peut réessayer à la main (`active_tracking_page.dart:960-969`). Appels en double évités par le verrou et la réutilisation (`TourneeService.java:184-204`). |

---

## 3.7 Tests existants touchant la vision

| test (chemin) | ce qu'il vérifie | faux ou réel |
|---|---|---|
| `T\ai\vision\ImageAnalysisServiceTest.java` (14 tests, lignes 46-206) | cas AFTER_ONLY sans confiance ; paire en ordre AVANT puis APRÈS ; panne ou réponse invalide → jamais CLEANED ; diagnostics sûrs ; référence de photo malveillante ; correction et revue sans écraser la prédiction ; règles de réutilisation ; évaluation sans modifier la tournée | `CollectionAiClient` simulé avec Mockito (ligne 26) |
| `T\ai\vision\VisionEvaluationAccessTest.java` (3 tests) | droits sur `/vision/evaluation-config` et `/vision/evaluate` | `@WebMvcTest` avec `@MockBean ImageAnalysisService` |
| `T\ai\common\GroqClientTest.java` (≈ 15 tests, dont des tests paramétrés) | ordre des images, schéma strict, refus des modèles et URL non prévus, clé absente, erreurs HTTP, réponses tronquées, filtrées ou refusées, délai dépassé | `OkHttpClient` avec un **`Interceptor` factice** (lignes 303-314) : **aucun appel réseau** |
| `T\ai\common\OpenRouterClientTest.java` | mêmes contrôles pour OpenRouter | intercepteur factice |
| `T\tournee\service\TourneeWorkflowTest.java` (`outageDoesNotBecomeClean…`, lignes 77-84) | une panne IA ne bloque pas la fin de tournée | `ImageAnalysisService` simulé |
| `T\tournee\service\TourReviewRegressionTest.java` (6 tests, lignes 58-124) | une analyse tardive ne s'applique pas à une photo remplacée ; une nouvelle photo efface la revue ; réutilisation sans nouvel appel ; nouvelle tentative après panne ; un seul appel pour des requêtes simultanées | simulé |
| `M\test\vision_quota_test.dart` (5 tests et plus, lignes 95-177) | analyse manuelle par défaut, un seul appel par double tap, nouvelle tentative après panne, effacement à la reprise de photo, affichage d'un résultat enregistré | adaptateur Dio factice (`QuotaAdapter`) |
| `M\test\photo_cleanup_test.dart` | brouillons de photo, upload puis analyse | adaptateur factice |
| `R\evaluation\vision\test_metrics.py`, `R\scripts\evaluation\test_evaluate_vision.py` | métriques d'évaluation hors ligne | hors ligne |
| `R\scripts\diagnose-vision.py` | **appel réel** au fournisseur choisi, avec de vraies images (en-tête du script) | **réel** : ce n'est pas un test unitaire |

Commandes (non exécutées ; Java et Flutter sont absents du PATH de ce poste) :

```powershell
cd C:\Users\hassen\Desktop\mahdi\pfe_mahdi\BACK
.\mvnw.cmd test -Dtest="ImageAnalysisServiceTest,VisionEvaluationAccessTest,GroqClientTest,OpenRouterClientTest,TourneeWorkflowTest,TourReviewRegressionTest"
.\mvnw.cmd test                      # suite complète (comme la CI, cross-platform.yml:45)
cd ..\MOBILE
flutter test test\vision_quota_test.dart test\photo_cleanup_test.dart
cd ..
python -m unittest discover -s evaluation/vision -p "test_*.py"
python -m unittest discover -s scripts/evaluation -p "test_*.py"
```

---

## 3.8 Exécution et déploiement

| point | constat (LU) |
|---|---|
| Lancement du backend | `cd BACK` puis `.\mvnw.cmd spring-boot:run` (`R\README.md:101-104`). Aucun Dockerfile ni docker-compose dans le projet. MongoDB lancé à part : `docker run --name gesticollecte-mongo -d -p 27017:27017 … mongo:7` (`README.md:75`). |
| Java | 17 (`pom.xml:22`, `README.md:36`, CI `cross-platform.yml:23`). |
| Python | Pas utilisé à l'exécution de l'application. Utilisé pour les outils : `R\scripts\*.py` (diagnostic des fournisseurs, bancs de routage), `R\evaluation\**`, `R\generate_pdf_guide.py`, `R\computer_vision\` ; Python 3.12 en CI (`cross-platform.yml:34`), 3.10+ recommandé (`README.md:42`). |
| Ports | Backend **8080** (`application.properties:3`) ; web Vite **5173** (`README.md:120`) ; Flutter web via `run-mobile.bat` sur **8088** (`README.md:132`) ; MongoDB **27017**. |
| URL du backend (mobile) | `M\lib\core\constants.dart:6-10` : `String.fromEnvironment('API_BASE_URL')`, sinon `http://localhost:8080/api` (web) ou `http://10.0.2.2:8080/api` (émulateur Android). L'adresse peut être remplacée à l'écran de connexion et est enregistrée dans `SharedPreferences` (`api_service.dart:19-20`, `64`). |
| URL du backend (web) | `F\src\utils\constants.js:1` : `VITE_API_URL`, sinon `http://localhost:8080/api`. |
| CI | `R\.github\workflows\cross-platform.yml` : `mvnw test`, `npm ci && npm run lint && npm run build`, tests Python, `flutter analyze && flutter test && flutter build web`. |

---

## 3.9 Options d'intégration du nouveau modèle

**Contrainte commune, propre à ce code (LU)** : `ai.collection-provider` choisit le fournisseur **à la fois pour l'extraction de CV et pour les photos** (`CollectionAiClient.java:10`, `18-30`). Ajouter `LOCAL` à cet enum casserait `generateJson` (CV). Il faut donc un **nouveau réglage, propre à la vision**.

### Option A : petit service Python (onnxruntime) appelé en HTTP par Spring Boot

- **Principe** : un service local (par exemple `http://127.0.0.1:8090/predict`) reçoit les octets de la photo APRÈS et renvoie `{probabilites:[p_CD, p_DEB, p_PRO], qualite:{nettete, luminosite}, modele_sha256}`. Il réutilise **tel quel** `pretraiter()` de `exemple_pretraitement.py` (PIL, même interpolation, mêmes arrondis) et `proprete_mobilenetv3l.onnx`.
- **Fichiers à créer** :
  - `R\computer_vision\service\app.py` : serveur HTTP (bibliothèque standard `http.server`, ou FastAPI si l'ajout de dépendance est accepté) ;
  - `R\computer_vision\service\requirements.txt` : onnxruntime, pillow, numpy ;
  - `J\ai\common\LocalVisionClient.java` : client OkHttp, même style que `GroqClient` (délais, `AiServiceException` avec catégories) ;
  - `J\ai\vision\service\LocalCleanlinessMapper.java` : correspondance des classes et gabarit de justification.
- **Fichiers à modifier** :
  - `J\ai\config\AiProperties.java` : nouvel enum `VisionEngine {LLM, LOCAL}` et classe imbriquée `LocalVision {baseUrl, timeoutSeconds, modelId, …}` ;
  - `J\ai\vision\service\ImageAnalysisService.java` : aiguillage dans `analyzeStoredEvidence` (lignes 105-122) et dans `evaluationConfiguration()` (50-52) ;
  - `R\BACK\src\main\resources\application.properties` et `R\BACK\.env.example` : nouvelles variables ;
  - tests `T\ai\vision\ImageAnalysisServiceTest.java` et nouveau `T\ai\common\LocalVisionClientTest.java`.
- **Lancement** : un processus de plus, par exemple `python computer_vision\service\app.py`, à documenter dans `R\README.md` et éventuellement dans `R\scripts\` (il y a déjà des lanceurs `.bat` et `.sh`). Écoute en local seulement (127.0.0.1).
- **Effort** : (1) service Python et test de cohérence ; (2) client Java et propriétés ; (3) aiguillage, correspondance et gabarit ; (4) tests Java ; (5) documentation et lanceur. **Le plus court des trois.**
- **Risques** : un processus supplémentaire (s'il est arrêté, `ANALYSIS_UNAVAILABLE` / `PROVIDER_UNAVAILABLE`, chemin déjà géré) ; versions Python et onnxruntime à figer ; contrôle d'accès du port local.
- **Cohérence** : le prétraitement est le **même code** que l'entraînement. On compare `predict` sur 20 à 30 images avec `predictions.csv` (ONNX) ou avec `exemple_pretraitement.py`. L'écart attendu est nul, à l'arrondi flottant près.

### Option B : inférence dans le Java avec ONNX Runtime pour Java

- **Faisabilité** : Maven et Java 17 le permettent (dépendance `com.microsoft.onnxruntime:onnxruntime` à ajouter dans `R\BACK\pom.xml` ; DÉDUIT : la bibliothèque Java fournit les binaires natifs Windows et Linux x64).
- **Fichiers** :
  - à créer : `J\ai\vision\local\OnnxCleanlinessModel.java` (session chargée une seule fois, tenseur `[1,3,224,224]`), `J\ai\vision\local\CleanlinessPreprocessor.java`, `J\ai\vision\local\ImageQualityGate.java` ;
  - à modifier : `AiProperties`, `ImageAnalysisService`, `pom.xml`, `application.properties` ;
  - le fichier `.onnx` serait placé dans `R\BACK\models\` ou désigné par un chemin configurable.
- **Prétraitement à réécrire** :
  - lecture `ImageIO` → `BufferedImage`, conversion en RGB (`ImageIO` gère mal certains JPEG CMYK ou en niveaux de gris) ;
  - EXIF : `ImageIO` ne l'applique pas, il faudrait une bibliothèque de métadonnées et la rotation correspondante ;
  - redimensionnement à 224×224 ;
  - `/255`, normalisation, passage en NCHW.
- **Risque principal d'écart** :
  - le `resize(BILINEAR)` de PIL, **en réduction**, applique un filtre triangulaire dont le support est élargi selon le facteur de réduction (antialiasing) et arrondit le résultat sur 8 bits ;
  - l'interpolation bilinéaire de `Graphics2D` ou `AffineTransformOp` est **ponctuelle**, sans antialiasing. Sur une réduction de 1600 px à 224 px (facteur d'environ 7), les pixels obtenus diffèrent nettement ;
  - il faudrait **réimplémenter l'algorithme de PIL** (convolution séparable avec un filtre triangulaire de support égal au facteur) ;
  - les décodeurs JPEG (libjpeg contre ImageIO) peuvent aussi différer de ±1 niveau.
- **Effort** : moyen à élevé, surtout pour le portage fidèle du redimensionnement et son test.
- **Cohérence** : même jeu de 20 à 30 images, comparé à l'ONNX en Python. Tolérance plus large que pour A, et risque de devoir corriger le portage.

### Option C : inférence sur le téléphone (`tflite_flutter`)

- **Version** : Flutter 3.24.5 / Dart 3.5.4. Le paquet `tflite_flutter` et un paquet de traitement d'image (`image`) seraient à ajouter dans `M\pubspec.yaml` (compatibilité exacte des versions **non vérifiée**, faute d'accès réseau).
- **Fichiers** :
  - à modifier : `M\lib\pages\active_tracking_page.dart` (`_uploadDraft`, lignes 264-310 ; bloc « Analyse de la photo », lignes 901-970), `M\lib\services\tournee_service.dart` (envoi du résultat) ;
  - à créer : `M\lib\services\cleanliness_model.dart` ;
  - asset `M\assets\proprete_mobilenetv3l_float16.tflite` à déclarer dans `pubspec.yaml`.
  - Côté BACK : un nouvel endpoint, par exemple `POST /api/tournees/{id}/points/{pointId}/analyse-locale`, recevant `{probabilites, modele_sha256}`, et un champ dans `TourneeVisit` ou `ImageAnalysis`.
- **Points bloquants propres à ce projet** :
  - l'application chauffeur est **aussi une application web** (`flutter run -d chrome`, `flutter build web` en CI) : `tflite_flutter` ne fonctionne pas sur le web (DÉDUIT de la nature du paquet, qui repose sur une bibliothèque native), il faudrait **garder un chemin serveur** de toute façon ;
  - le redimensionnement en Dart (`image.copyResize`, interpolation linéaire) n'est pas celui de PIL : même risque d'écart qu'en B, sur chaque modèle de téléphone ;
  - **confiance** : le serveur recevrait une **étiquette décidée par le client**, que le chauffeur (la personne contrôlée) pourrait modifier ou rejouer. Aujourd'hui le serveur relit lui-même la photo stockée (`readStoredPhoto`), ce qui garantit que l'analyse porte sur la preuve archivée. Avec C, cette garantie disparaît, sauf à refaire l'inférence au serveur, et dans ce cas C n'apporte plus rien.
- **Effort** : le plus élevé (code natif, tailles d'APK, tests par appareil, double chemin web).

### Recommandation : **option A**

- **Fidélité** : c'est la seule option qui garantit le même prétraitement que l'entraînement, puisque c'est le même code PIL, et qui permet de vérifier la cohérence à l'identique.
- **Changements limités** : côté Java, un client et un aiguillage, dans le style de `GroqClient`. Le chemin d'échec `ANALYSIS_UNAVAILABLE`, la réutilisation, le verrou, la revue humaine et les deux clients (Android et web) restent inchangés. Mobile et web ne demandent que des changements d'affichage.
- **Retour en arrière** : un seul réglage de configuration. Le modèle reste mis à jour sans republier l'application.
- **Évolution possible** : B plus tard, si l'on veut supprimer le processus Python, une fois le redimensionnement PIL porté et validé avec le test de cohérence de A.

---

## 3.10 Proposition de conception (adaptée à ce code)

### a) Correspondance des 3 sorties avec les statuts existants

| sortie du modèle | statut écrit dans `ImageAnalysis.status` et `TourneeVisit.analysisStatus` |
|---|---|
| PROPRE | `CLEANED` |
| DEBORDEMENT | `NOT_CLEANED` (la définition du prompt, « des déchets visibles paraissent rester », englobe le débordement) |
| CANNOT_DETERMINE | `CANNOT_DETERMINE` |
| porte de qualité refusée (voir e) | `CANNOT_DETERMINE` avec `qualityIssue` renseigné |
| — | `OBSTRUCTED` : **jamais produit** par le modèle local ; reste un libellé **humain** (chauffeur et superviseur) |

- **Pourquoi aucune nouvelle valeur de statut** : le mobile (`_hasSavedAnalysis`, `active_tracking_page.dart:50-55`), le web, le PDF et 4 listes blanches Java dépendent des 4 libellés. Une nouvelle valeur ferait proposer « Réessayer l'analyse » en boucle sur le mobile.
- **Champs à ajouter dans `ImageAnalysis.java`** : `probabilities` (`Map<String, Double>` ou 3 champs `pCannotDetermine`, `pDebordement`, `pPropre`), `predictedClass` (sortie brute du modèle), `qualityIssue`, `sharpness`, `meanBrightness`, `decision` (voir d).
- **Champ existant** : `confidence` existe déjà, mais `afterOnlyIsSufficientAndHasNoInventedConfidence` (`ImageAnalysisServiceTest.java:51`) exige qu'il reste nul avec le moteur LLM. Il pourrait recevoir la probabilité de la classe prédite **pour le seul moteur LOCAL**, le test étant alors précisé par moteur.

### b) Interrupteur de moteur (retour en arrière sans supprimer le code)
- **Configuration** :
  - `AiProperties` : `private VisionEngine visionEngine = VisionEngine.LLM;` avec `enum VisionEngine { LLM, LOCAL }` ;
  - `application.properties` : `ai.vision-engine=${VISION_ENGINE:LLM}` ;
  - en mode `LLM`, le flux actuel continue de passer par `CollectionAiClient` et `collection-provider`. **Rien n'est supprimé.**
- **Code** :
  - `ImageAnalysisService.analyzeStoredEvidence` : selon `visionEngine`, `collectionAiClient.analyzeImages(...)` (lignes 109-122 actuelles) ou `localVisionClient.predict(after)`, puis le mapper ;
  - `evaluationConfiguration()` (50-52) renvoie en mode LOCAL `provider="LOCAL"`, `model=<identifiant du modèle + sha256>` (`model_card.json`), `promptVersion="local-cleanliness-v1"`.
- **Effet de bord voulu** : comme `reusableVisitAnalysis` (55-70) compare provider, modèle et version, **une analyse Groq n'est jamais réutilisée en mode LOCAL**, et inversement, sans code supplémentaire. `/vision/evaluate` (comparaison des moteurs par un superviseur) marche aussi tel quel.

### c) Justification générée par gabarit
- **Lieu** : `J\ai\vision\service\LocalCleanlinessMapper.java`, appelé par `ImageAnalysisService`, qui renseigne `description`. Aucun LLM.
- Exemple pour une photo APRÈS seule, nette :
  > « Analyse automatique locale (modèle proprete_mobilenetv3l) de la photo APRÈS : point probablement propre. Probabilités : propre 0,91 ; débordement 0,07 ; indéterminé 0,02. Aucune conclusion sur l'auteur de la collecte. À confirmer par un contrôle humain. »
- Variante pour un refus de qualité :
  > « Photo APRÈS trop sombre ou floue pour être analysée (netteté 12,4 ; luminosité 31). Reprenez la photo en cadrant le point. »
- Le texte reste en dessous de 2000 caractères et n'est jamais vide (contrainte de `ImageAnalysisService.java:118`).

### d) Règle pour 1 ou 2 photos
- **Décision sur la photo APRÈS seule**, comme le prompt actuel (« Évalue uniquement ce qui est visible sur la photo APRÈS »). Une photo suffit (cas nominal `AFTER_ONLY`).
- Si une photo AVANT existe : le modèle n'en a pas besoin, et une prédiction AVANT ne doit **pas** changer le statut.
  - Option recommandée : ne pas l'analyser, ce qui économise un appel.
  - Option de transparence : l'analyser et enregistrer ses probabilités dans un champ séparé (`beforeProbabilities`), à titre d'information pour le superviseur.
- `inputMode` reste `AFTER_ONLY` ou `BEFORE_AFTER`, pour la traçabilité.
- **Perte à signaler** : le LLM pouvait répondre `CANNOT_DETERMINE` si les deux photos ne montraient pas le même point. Le modèle local ne sait pas le faire.

### e) Garde sur la « validation » et statut « à vérifier »
- **Constat** : aujourd'hui rien n'est validé automatiquement (3.5). La garde porte donc sur un **nouveau champ de décision**, pas sur un état VALIDÉ qui n'existe pas.
- **Valeurs du champ `decision`** (dans `ImageAnalysis`, et copie `analysisDecision` dans `TourneeVisit.java`) :
  - `CONFIRMATION_AUTO` si `pPropre ≥ ai.local-vision.propre-threshold` et si la porte de qualité est passée ;
  - sinon `A_VERIFIER`.
- **Où configurer le seuil** : `AiProperties.LocalVision.propreThreshold`, alimenté par `application.properties` → `ai.local-vision.propre-threshold=${VISION_PROPRE_THRESHOLD:}`. **Aucune valeur proposée ici.** Il est recommandé qu'une absence de valeur donne `A_VERIFIER` pour tout ; le seuil se choisit avec `R\computer_vision\modeles\couverture_exactitude_valid.png`.
- **Calcul** : dans `ImageAnalysisService` (mode LOCAL), puis copie dans `TourneeService.analyzeVisit` (ligne 211) à côté d'`analysisStatus`.
- **Ce qui ne change pas** : l'état de la visite et la fin de tournée, conformément à `TourneeWorkflowTest.outageDoesNotBecomeClean…`. La décision ne sert qu'à **ordonner la revue humaine**.

### f) Porte de qualité d'image avant le modèle
- **Lieu** : dans le service Python (option A), avant `pretraiter`. Calculs :
  - netteté = variance du Laplacien, sur l'image en niveaux de gris redimensionnée à une taille fixe, pour que la mesure ne dépende pas de la résolution (choix à figer, voir 3.12) ;
  - luminosité = moyenne des niveaux de gris.
- **Implémentation** : numpy seul, pour éviter une dépendance à OpenCV. Si l'on veut reprendre exactement la mesure de l'étape 7, aligner sur `R\computer_vision\code\e7_qualite_image.py` ; tableaux de référence dans `R\computer_vision\resultats\qualite_image.json`.
- **Seuils** : `ai.local-vision.min-sharpness`, `ai.local-vision.min-brightness`, `ai.local-vision.max-brightness` (variables `VISION_MIN_SHARPNESS`, `VISION_MIN_BRIGHTNESS`, `VISION_MAX_BRIGHTNESS`), envoyés par Java dans la requête au service. Ainsi toute la configuration reste dans `application.properties`. Si un seuil est vide, la porte est désactivée.
- **Sortie en cas de refus** : `status=CANNOT_DETERMINE`, `qualityIssue=FLOUE|TROP_SOMBRE|SUREXPOSEE`, `decision=A_VERIFIER` ; le modèle n'est pas appelé, ou il est appelé et ses probabilités sont enregistrées sans servir à la décision (à choisir).

### g) Changements côté mobile et côté web
- **Mobile** (`M\lib\pages\active_tracking_page.dart`, bloc 901-970 ; `M\lib\models\tour_progress.dart`) :
  - afficher les 3 probabilités sous le libellé, en clair : « propre 91 % · débordement 7 % · indéterminé 2 % » ;
  - si `qualityIssue` est renseigné : message « Photo à refaire : trop sombre » et mise en avant du bouton existant « Reprendre la photo APRÈS » (lignes 886-893). Reprendre la photo efface déjà l'analyse précédente (`TourneeService.java:174`) ;
  - si `decision == A_VERIFIER` : pastille « À vérifier par le superviseur » ;
  - `_hasSavedAnalysis` reste inchangé, puisqu'il n'y a pas de nouveau statut.
  - Les champs viennent de `TourneeVisit` : y ajouter `analysisProbabilities`, `analysisDecision`, `analysisQualityIssue`, copiés par `TourneeService.analyzeVisit`, pour éviter un appel à `/vision/analyses/{id}`.
- **Web** (`F\src\pages\tournees\TourneeDetailsModal.jsx`, lignes 83-88) :
  - afficher les probabilités, la décision et la qualité ;
  - afficher enfin la `description` ;
  - filtre ou compteur « à vérifier » dans `F\src\pages\tournees\TourneePage.jsx`.
  - Supprimer ou corriger `AnalysePhotosPage.jsx` (code mort) relève d'une décision séparée.
- **PDF** (`PdfRapportService.java:104-105`) : ajouter la décision et la probabilité de PROPRE.

---

## 3.11 Plan d'intégration chiffré

| # | objectif | fichiers touchés | comment tester | critère de réussite |
|---|---|---|---|---|
| 1 | Service d'inférence Python | `R\computer_vision\service\app.py`, `requirements.txt` | test Python : appel `predict` sur 30 images connues (voir le plan de cohérence) | probabilités identiques à `exemple_pretraitement.py` avec l'ONNX (écart absolu max ≤ 1e-5), argmax identique à 100 % |
| 2 | Porte de qualité | `app.py` (+ test) | images floues, sombres, normales construites pour le test, et mêmes images que l'étape 7 | mesures identiques à `e7_qualite_image.py` à 1e-6 près, si l'on reprend sa définition |
| 3 | Configuration | `AiProperties.java`, `application.properties`, `R\BACK\.env.example` | test de liaison des propriétés (comme `T\ai\config\AiConfigTest.java`) | `VISION_ENGINE` absent → `LLM` ; seuils vides acceptés |
| 4 | Client Java | `J\ai\common\LocalVisionClient.java`, `T\ai\common\LocalVisionClientTest.java` | intercepteur OkHttp factice, comme `GroqClientTest:303-314` | délai, 5xx, JSON invalide, probabilités hors [0, 1] ou de somme ≠ 1 → `AiServiceException` avec une catégorie ; aucun réseau en test |
| 5 | Correspondance et gabarit | `LocalCleanlinessMapper.java`, test unitaire | tableau des cas (3 classes × qualité OK ou refusée × seuil défini ou vide) | statuts et décision conformes à 3.10 a et e ; description non vide, ≤ 2000 caractères |
| 6 | Aiguillage | `ImageAnalysisService.java`, `ImageAnalysis.java`, `ImageAnalysisServiceTest.java` | tests existants **inchangés** en mode LLM, plus de nouveaux tests en mode LOCAL | `.\mvnw.cmd test` entièrement vert ; en mode LOCAL, `provider="LOCAL"`, aucune réutilisation d'une analyse Groq |
| 7 | Copie dans la visite | `TourneeVisit.java`, `TourneeService.java:211` | `TourReviewRegressionTest` et `TourneeWorkflowTest` étendus | les règles de photo remplacée et de verrou restent vraies ; nouveaux champs copiés |
| 8 | Mobile | `active_tracking_page.dart`, `tour_progress.dart`, `M\test\vision_quota_test.dart` | `flutter test` avec réponses simulées (probabilités, photo à refaire, à vérifier) | affichage correct, aucun appel IA en double, `flutter analyze` sans erreur |
| 9 | Web et PDF | `TourneeDetailsModal.jsx`, `PdfRapportService.java` | `npm run lint && npm run build` ; vérification visuelle | probabilités, décision et description visibles |
| 10 | Bout en bout local | `R\README.md`, lanceur dans `R\scripts\` | lancer Mongo, le service Python, le backend, l'application ; faire 30 visites de test | statuts et probabilités en base égaux aux sorties de l'étape 1 |
| 11 | Mise en service | `.env` | `VISION_ENGINE=LOCAL` | analyses enregistrées avec `provider=LOCAL` ; retour en arrière testé (ligne ci-dessous) |

### Plan de test de cohérence (modèle d'entraînement contre version intégrée)
- **Jeu d'images** : 30 images figées, tirées avec la graine 42 de `R\computer_vision\resultats\predictions.csv` (split `valid`, et non `test`, pour ne pas réutiliser le test) : 10 PROPRE, 10 DEBORDEMENT, 10 CANNOT_DETERMINE. Ajouter 3 JPEG pris au téléphone avec une balise d'orientation EXIF ≠ 1, pour mesurer l'effet de l'orientation.
- **Référence** : probabilités de `exemple_pretraitement.py` + `proprete_mobilenetv3l.onnx` sur les fichiers d'origine, enregistrées dans un fichier de référence (`R\computer_vision\integration\reference_coherence.json`, à créer lors de l'implémentation).
- **Mesure** : écart absolu maximal par classe, et accord de l'argmax.
- **Tolérances** :
  - option A : écart ≤ 1e-5 et accord à 100 % (même code, même moteur) ;
  - option B ou C : écart ≤ 1e-2 et accord à 100 % sur les 30 images. Au-delà, le portage du redimensionnement est en cause et doit être corrigé avant la mise en service ;
  - à titre de comparaison, l'export TFLite float16 diffère déjà de PyTorch sur 6 images de valid (rapport d'entraînement).
- **Chemin complet** : refaire la mesure en passant par le backend (photo envoyée par l'API, puis relue sur le disque), pour vérifier que le stockage et `readStoredPhoto` ne modifient pas les octets.

### Procédure de retour en arrière
1. Dans `BACK\.env` (ou l'environnement) : `VISION_ENGINE=LLM`, puis redémarrer le backend. Le flux Groq ou OpenRouter reprend, puisque son code est intact.
2. Les analyses LOCAL déjà enregistrées restent en base (`provider="LOCAL"`). Elles ne sont pas réutilisées par le moteur LLM (comparaison provider, modèle et version dans `reusableVisitAnalysis`). Une visite peut être analysée à nouveau en relançant « Analyser la photo » si son `analysisStatus` n'est pas l'un des 4 libellés, ou après reprise de la photo.
3. Le service Python peut être arrêté. En mode LLM, il n'est jamais appelé.
4. Les nouveaux champs (probabilités, décision) restent nuls pour les analyses LLM. Le mobile et le web doivent donc les traiter comme facultatifs.

---

## 3.12 Points ouverts et décisions

| question | options | recommandation |
|---|---|---|
| Orientation EXIF : le modèle a été entraîné **sans** `exif_transpose` (`exemple_pretraitement.py:28-29`), alors que la consigne d'intégration l'exige | (1) l'appliquer à l'inférence ; (2) ne pas l'appliquer, comme à l'entraînement | Mesurer d'abord la proportion d'images du dataset dont la balise d'orientation est ≠ 1, et le comportement réel d'`image_picker` sur Android (introuvable dans le code). Si les photos du téléphone arrivent avec une balise de rotation, **l'appliquer** (une photo droite est plus proche des images d'entraînement), puis le consigner. |
| `DEBORDEMENT` → `NOT_CLEANED` : la correspondance est-elle acceptable pour la municipalité ? | conserver les 4 libellés ; ajouter un libellé d'affichage « Débordement » | Conserver `NOT_CLEANED`, qui ne demande aucun changement de contrat, et afficher « débordement » dans le texte de gabarit. |
| Le blocage d'accès (`OBSTRUCTED`) ne sera plus détecté par l'IA | accepter (le chauffeur le signale par `ISSUE`) ; garder le LLM pour ce seul cas | Accepter : le chemin humain existe déjà et sa note est obligatoire. |
| Valeur du seuil de PROPRE et des seuils de qualité | à choisir | À fixer avec la courbe couverture / exactitude et `qualite_image.json`. Un seuil absent doit signifier « tout à vérifier ». |
| Faut-il analyser la photo AVANT ? | non ; oui, à titre d'information | Non dans un premier temps. |
| Porte de qualité refusée : appeler quand même le modèle ? | non ; oui, en enregistrant les probabilités | Oui, avec enregistrement, utile pour régler les seuils, sans effet sur la décision. |
| Service Python : bibliothèque standard ou FastAPI ? | `http.server` (zéro dépendance) ; FastAPI + uvicorn | `http.server` suffit pour un seul point d'entrée local et réduit les dépendances. |
| Où tourne le service en démonstration ? | même machine que le backend ; machine séparée | Même machine, écoute sur 127.0.0.1, lanceur dans `scripts\`. |
| `confidence` : le renseigner en mode LOCAL ? | oui (probabilité de la classe prédite) ; non (nouveaux champs seulement) | Nouveaux champs seulement : cela respecte le test actuel et évite de confondre avec l'ancienne règle « pas de confiance inventée ». |
| Page web morte `AnalysePhotosPage.jsx` | la supprimer ; la corriger et la router | Hors du périmètre de l'intégration : à décider séparément. |
| Endpoint `/api/ai/vision/analyze` (une photo, non utilisé) | le laisser ; le retirer | Le laisser : il passe par le même `analyzeStoredEvidence` et bénéficiera de l'aiguillage. |
| Le serveur accepte une photo AVANT après la photo APRÈS (`TourneeService.java:167-176`) | ajouter un contrôle ; laisser | Sans effet si la décision ne porte que sur la photo APRÈS. À signaler seulement. |
| `BACK\.env` est absent alors que la consigne le mentionne | — | Vérifier où sont réellement définies les clés à l'exécution (introuvable dans le dépôt). |
| Java et Flutter sont absents du PATH de ce poste | installer JDK 17 et Flutter 3.24.5 | Nécessaire avant toute étape de test du plan. |
| Effet des étiquettes extrapolées et de la classe CANNOT_DETERMINE synthétique | — | Collecter 50 à 100 photos réelles de la municipalité, étiquetées à la main, avant de régler le seuil. Aucune donnée de terrain n'est disponible dans le dépôt. |
