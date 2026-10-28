# Rapport : rédaction facultative par modèle de langage du verdict du modèle local

Poste A, 2026-10-28.

**Préfixes de chemins**

| préfixe | chemin |
|---|---|
| R | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\` |
| J | `R\BACK\src\main\java\com\municipalite\gestiontournees\` |
| T | `R\BACK\src\test\java\com\municipalite\gestiontournees\` |
| F | `R\FRONT\` |
| CV | `R\computer_vision\` |

Tout ce qui n'a pas été exécuté est marqué **NON TESTÉ**. Le modèle de langage ne voit jamais la photo.

## 1. État de départ et écarts constatés

**Outils du poste A**
- Java, Maven et Flutter : absents du PATH ;
- JDK 17 portable : présent dans `CV\integration\outils\jdk-17.0.20.1+1` ;
- Node v24.19.0, npm 11.17.0, Python 3.13.15.

**Intégration précédente : complète** (vérifiée dans le code avant toute modification)

| élément | constat |
|---|---|
| `J\ai\common\LocalVisionClient.java`, `J\ai\vision\service\LocalCleanlinessMapper.java` | présents |
| Champs d'`ImageAnalysis` | `probabilities`, `predictedClass`, `qualityIssue`, `sharpness`, `meanBrightness`, `decision`, `modelSha256` (lignes 54-60) |
| Aiguillage | `ImageAnalysisService` : `VisionEngine.LOCAL` (l. 62 et 122), `applyLocalPrediction` (l. 157), `LOCAL_PROMPT_VERSION` (l. 42) |
| Réglages | `ai.vision-engine` et `ai.local-vision.*` (`application.properties` l. 71-80), `AiProperties.LocalVision` |
| Visite, web | `TourneeVisit.analysis*`, `copyLocalAnalysis` dans `TourneeService` ; `getPhotoAnalysis` dans `aiService.js` |
| Service Python | présent (`CV\service`) |

**Écarts entre documents et code**

| écart | détail |
|---|---|
| `CvExtractionService` (cité dans la mission) | n'appelle pas de modèle de langage. L'usage réel de `CollectionAiClient.generateJson` se trouve dans `J\ai\cv\service\CvAnalysisService.java:78` et `:176` (champ `qwen` de type `CollectionAiClient`). `RagGenerationClient.generateJson` existe aussi, mais il dépend du réglage RAG (Gemini ou Groq). |
| `RAPPORT_INTEGRATION.md` | la copie placée dans l'ancien zip n'avait pas sa section 7, comme déjà signalé ; aucun autre écart. |
| Gabarit existant | il serait **rejeté par la règle V2** s'il passait par le validateur, à cause du « 3 » de « mobilenetv3l » (constaté en Python). Sans conséquence : le gabarit est le texte de repli et n'est pas validé. Cela montre toutefois que V2 est stricte. |

## 2. Fichiers créés ou modifiés

Détail (rôle, date, compilé, testé) : **`CV\integration\JOURNAL_NARRATION.md`**. Originaux : **`CV\integration\sauvegardes_narration\`** (10 fichiers).

**Modifiés**, par ajouts seulement :
- `AiProperties.java`, `application.properties`, `.env.example`, `ImageAnalysis.java` ;
- `aiService.js` ;
- `TourneeDetailsModal.jsx` : 30 lignes ajoutées, 2 remplacées par une forme équivalente ;
- `README.md` ;
- kit : `01_service_python.ps1`, `02_tests_backend.ps1` (liste ciblée), `LISEZMOI_AUTRE_PC.md`.

**Créés**
- **Code Java** : 6 classes dans `J\ai\vision\narrative\` (5) et `J\ai\vision\controller\VisionNarrativeController.java` ;
- **Ressources** : 2 dans `R\BACK\src\main\resources\prompts\`, plus 1 vecteur partagé dans `R\BACK\src\test\resources\vision\` ;
- **Tests Java** : 4 classes ;
- **Python** : 4 fichiers dans `R\scripts\evaluation\` ;
- **Rapports et journaux**.

**Non touchés** : la description et le gabarit (`LocalCleanlinessMapper`), `CollectionAiClient`, `GroqClient`, `OpenRouterClient`, `RagGenerationClient`, `VisionController`, le mobile, le PDF, les seuils, la décision et les statuts.

## 3. Exécuté ici, et non testé

| contrôle | commande | résultat |
|---|---|---|
| Tests Python de `scripts\evaluation` (suite existante + 6 nouveaux) | `python -m unittest discover -s scripts/evaluation -p "test_*.py"` | **38 tests, OK** |
| Vecteurs partagés, validateur Python | idem (`test_narrative_validator`) | **49/49 conformes** (17 acceptés, 32 rejetés), règles V1 à V7 toutes couvertes |
| Script d'évaluation hors ligne | `python scripts/evaluation/evaluate_narration.py` (`--dry-run` par défaut) | 40 prompts écrits dans `CV\integration\execution_narration\dry_run\prompts_dry_run.jsonl`, **aucun accès réseau** (un test vérifie qu'`urlopen` n'est jamais appelé) |
| Kit, script 01 | `powershell -File kit_tests_autre_pc\01_service_python.ps1` | réussi : 14 tests du service, 6 tests du validateur, cohérence CONFORME (journaux dans `execution_narration\kit_01\`) |
| Compilation Java | `mvnw -q -B -DskipTests test-compile` (JDK portable, JAVA_HOME limité à la session) | **compilé**, journal vide |
| Web | `npm ci`, `npm run lint`, `npm run build` | codes 0 ; 5 avertissements de lint, tous dans des fichiers non modifiés |
| Analyse syntaxique des `.ps1` du kit | analyseur PowerShell | 0 erreur |

| NON TESTÉ | où, et commande |
|---|---|
| `NarrativeValidatorTest` (mêmes 49 vecteurs, en Java), `NarrativeServiceTest`, `VisionNarrativeAccessTest`, `VisionNarrationPropertiesTest` et non-régression complète | poste B : `kit_tests_autre_pc\02_tests_backend.ps1`, ou `cd BACK ; .\mvnw.cmd test "-Dtest=NarrativeValidatorTest,NarrativeServiceTest,VisionNarrativeAccessTest,VisionNarrationPropertiesTest"` puis `.\mvnw.cmd test` |
| Flutter | inchangé par cette mission ; `03_tests_mobile_web.ps1` reste à exécuter pour l'intégration précédente |
| Appel réel à l'API (`--live`) | poste disposant d'une clé : voir § 5 |
| Endpoint et bouton web dans l'application lancée | poste B, à la main, après `04_e2e.ps1` ou avec un backend de démonstration |

## 4. Écarts par rapport aux décisions N1 à N11

| décision | écart ou précision | raison |
|---|---|---|
| N3 | Endpoint placé dans un **nouveau contrôleur** `VisionNarrativeController`, même chemin `/api/ai`, et non dans `VisionController`. | Ajouter une dépendance à `VisionController` aurait fait échouer `VisionEvaluationAccessTest` (`@WebMvcTest` sans `@MockBean` pour le nouveau service). Le contrôle d'accès est identique : rôle SUPERVISEUR, puis `requireTourAccess` de la tournée de l'analyse. |
| N3 (verrou) | Une seconde requête simultanée reçoit **409** (« Une rédaction est déjà en cours… ») au lieu d'attendre. | Même principe que le verrou de `TourneeService` ; garantit un seul appel. |
| N3 (cache, mode TEMPLATE) | Une narrative TEMPLATE déjà présente est renvoyée telle quelle en mode TEMPLATE. En mode LLM, elle autorise une nouvelle tentative. | Conforme ; précision d'implémentation. |
| N2 | En mode TEMPLATE, `narrativePromptVersion` et `narrativeModel` restent nuls, car aucun prompt n'est utilisé. Lors d'un repli après une tentative LLM, ils sont renseignés (`vision-narrative-v1`, modèle tenté). | Traçabilité exacte. |
| N4 | Interface `NarrativeGenerator` et adaptateur `CollectionAiNarrativeGenerator`. L'adaptateur appelle `CollectionAiClient.generateJson(prompt, schéma {"texte"})`, le chemin déjà utilisé pour les CV, **sans modifier les clients**. | C'est le chemin qui demande le moins de changements. Il partage le fournisseur (`COLLECTION_AI_PROVIDER`), le modèle de texte et le quota avec l'extraction de CV et les photos. |
| N1 (délai) | `VISION_NARRATION_TIMEOUT_SECONDS` est appliqué par `NarrativeService` autour de l'appel (`Future.get`). Le client HTTP garde son propre délai (`GROQ_TIMEOUT_SECONDS`) : après un dépassement, l'appel peut se terminer en arrière-plan. | Les clients ne devaient pas être modifiés. |
| N7 | Une réponse sans champ `texte` textuel ou illisible donne `INVALID_PROVIDER_RESPONSE`, **sans** nouvelle tentative. `VALIDATION_<code>` reprend le **premier** code violé à la seconde tentative. | Catégorie existante d'`AiServiceException`. Un code unique reste lisible. |
| N7 (V3) | Comparaison sur des **mots entiers** (bornes `[a-z0-9]` après normalisation), chaque entrée acceptant les terminaisons `e`, `s`, `es` ou `x`. Ajouts à la liste minimale : « on aperçoit », « la photographie montre », « visiblement », « poubelle », « prouvé », « certainement », « certitude ». | Sans bornes, « confirmé » rejetait « confirmer », pourtant exigé par V4, et « sac » rejetait « sachant ». Le choix est documenté dans la ressource. |
| N7 (V5) | Le mot du motif est exigé **pour le problème signalé** : « sombre » pour TROP_SOMBRE, « flou » pour FLOUE, « surexpos » pour SUREXPOSEE. | Lecture la plus stricte de « un mot du motif ». |
| N7 (V1) | Un texte vide renvoie seulement V1. Sinon, toutes les règles violées sont listées (triées). Les longueurs sont comptées en points de code ; seuls les espaces, tabulations et retours à la ligne sont retirés aux extrémités. | Comportement identique en Java et en Python, vérifié par les vecteurs partagés côté Python. |
| N8 | Les vecteurs listent **tous** les codes violés (`motifs`), pas un seul. | Comparaison plus forte entre les deux implémentations. |
| N10 | Le bouton reste visible quand la narrative en cache est TEMPLATE, pour permettre une nouvelle tentative. Il disparaît pour une narrative LLM. Le web est déjà réservé aux superviseurs (`AuthContext`) et le serveur vérifie le rôle. | Conforme à N3. |
| Prompt (N6) | Ajouts : « aucun autre nombre (date, heure, numéro…) », « pas de séparateur de milliers », « écris le pourcentage suivi de % », « texte sur une seule ligne ». | Pour limiter les rejets V2 et V5 attendus. |

N5, N9 et N11 sont respectés sans écart. `NarrativeServiceTest.promptContainsOnlyStructuredFacts` vérifie que le prompt ne contient ni identifiant, ni chemin de photo, ni chauffeur, ni note libre, ni correction ; ce test est **NON TESTÉ** à l'exécution. Les journaux ne contiennent que la source, le nombre de tentatives, la catégorie et la latence.

**Données envoyées au modèle** (liste exacte) :
- `classe_predite` (propre, débordement ou indéterminé) ;
- `pourcentages` : propre, débordement et indéterminé, en entiers arrondis comme dans le gabarit ;
- `decision` (« à vérifier » ou « confirmation automatique ») ;
- `probleme_qualite` (trop sombre, floue, surexposée, ou null) ;
- `nettete` et `luminosite`, à 1 décimale ;
- `photos` (« photo APRÈS seule », ou « photos AVANT et APRÈS fournies, seule la photo APRÈS est analysée »).

## 5. Vérification à la main par l'utilisateur

1. Sur le poste B, dans l'ordre : `00_verifier_prerequis.ps1`, `01_service_python.ps1`, `02_tests_backend.ps1` (comprend les 4 nouvelles classes), `03_tests_mobile_web.ps1`, `04_e2e.ps1`.
2. Hors ligne : `python scripts\evaluation\evaluate_narration.py` relit les prompts dans `evaluation\narration\prompts_dry_run.jsonl`.
3. Avec **votre** clé, dans votre session :
   - `$env:GROQ_API_KEY = '…'`, puis `python scripts\evaluation\evaluate_narration.py --live --provider GROQ --sortie evaluation\narration` ;
   - ou `--provider OPENROUTER` avec `OPENROUTER_API_KEY` ;
   - valeurs par défaut : `https://api.groq.com/openai/v1` avec `qwen/qwen3.8-27b`, ou `https://openrouter.ai/api/v1` avec `qwen/qwen3.8-27b:free` ; options `--model`, `--base-url`, `--timeout` (10 s), `--pause` (2 s).
4. Lire `narration_live_resume.json` :
   - `taux_acceptation_1re_tentative`, `taux_llm_final`, `taux_repli_gabarit` ;
   - `motifs_rejet_derniere_tentative` ;
   - `latence_mediane_ms`.
5. Lire `narration_live.csv` :
   - `texte` : relire chaque texte accepté, car le validateur ne détecte pas tout ;
   - `motifs` : V1 longueur, V2 nombre inventé, V3 mot interdit, V4 mention automatique ou contrôle humain absente, V5 pourcentage ou problème de qualité absent, V6 balisage, V7 lien ;
   - `categorie_repli`, `tentatives`, `latence_ms`.
6. **Critère à surveiller** : n'activer `VISION_NARRATION=LLM` que si le taux final LLM est élevé et qu'aucun motif ne domine. Un V2 ou V3 fréquent indique de revoir le prompt et de passer à une version `vision-narrative-v2`. Aucun seuil n'a été fixé ici. Le lot s'arrête au premier 429.
7. Dans l'application, en mode `VISION_NARRATION=LLM` :
   - pour une analyse LOCAL, bouton « Générer un résumé rédigé », puis vérifier la mention de source ;
   - sans clé, on doit obtenir le texte standard, avec `narrativeFailureCategory = NOT_CONFIGURED`.

## 6. Retour en arrière

- **Désactiver** : `VISION_NARRATION=TEMPLATE`, ou ne rien définir (valeur par défaut), puis redémarrer le backend.
  - L'endpoint reste disponible mais n'appelle plus aucun modèle : il renvoie le texte standard (`narrativeSource=TEMPLATE`).
  - Les narratives LLM déjà enregistrées restent en base (champ `narrative`, source LLM) et restent affichées avec leur mention. Elles ne modifient ni le statut, ni la décision, ni la description.
- **Retour complet au code d'avant** : recopier `CV\integration\sauvegardes_narration\` à sa place, puis supprimer les fichiers créés listés dans le journal.

## 7. Paquet de transfert

`CV\integration\preparer_transfert.ps1` a été relancé.

| élément | valeur |
|---|---|
| Zip | `CV\integration\transfert\pfe_mahdi_transfert.zip` (remplace le précédent) |
| Taille | 34,7 Mo, 502 fichiers (BACK 204, MOBILE 108, FRONT 52, scripts 22, service 11, export 3, code 1, integration 100) |
| SHA-256 | `09c91cbd1fcd002054455051a16a40701aa53dcb3d8e94289a20cc6c9d2db579` (`transfert\SHA256.txt`) |
| Contrôles | 0 fichier `.env` ; 0 dossier exclu ; 0 motif de clé (Groq, OpenRouter, Google, JWT) ; liste dans `transfert\liste_contenu.txt` |
| Contenu ajouté | les 6 classes Java et les 4 tests, les 2 ressources de prompt, les vecteurs partagés, les 4 scripts Python, `JOURNAL_NARRATION.md`, ce rapport, `sauvegardes_narration\`, `execution_narration\` |

La copie de ce rapport placée dans le zip a été écrite avant le zip : elle ne contient pas ces valeurs.

## 8. Points d'attention

- **Le validateur** borne la longueur, les nombres, le vocabulaire et la forme. Il **ne détecte pas** toutes les formulations trompeuses : par exemple « le point est propre », sans conditionnel, passe si la mention automatique et le contrôle humain sont présents. Un texte accepté n'est pas une information nouvelle : il reformule les probabilités.
- **V2 est stricte** : tout nombre non fourni est rejeté, y compris dans un nom de modèle ou un nombre écrit avec séparateur de milliers, alors que la netteté peut dépasser 1 000. Des rejets légitimes sont donc possibles ; à mesurer en `--live`.
- **Quota** : le mode LLM consomme le même quota que l'extraction de CV et l'analyse des photos (même fournisseur, même clé). Un 429 fait retomber immédiatement sur le texte standard.
- **Délai** : après un dépassement de `VISION_NARRATION_TIMEOUT_SECONDS`, l'appel HTTP peut se poursuivre en arrière-plan jusqu'au délai du client (60 s par défaut). Au plus 2 rédactions simultanées (fil dédié).
- **Exactitude Java / Python** : l'égalité est établie par les vecteurs partagés côté Python (49/49). Le même test Java est **NON TESTÉ** ; les deux normalisations (NFD, minuscules, apostrophes, espaces) ont été écrites pour être identiques, mais les tables Unicode de Java et de Python peuvent différer sur des caractères rares.
- **Limites du modèle de vision**, inchangées : étiquettes d'entraînement en partie extrapolées, classe CANNOT_DETERMINE apprise sur des images synthétiques, précision de PROPRE d'environ 0,71, images surtout issues du web chinois, rien validé sur le terrain. Le texte rédigé ne doit pas laisser croire le contraire.
- **Fichiers non testés** : `NarrativeService` (verrou, délai, repli), le contrôleur, le bouton web en conditions réelles, l'appel `--live`.
