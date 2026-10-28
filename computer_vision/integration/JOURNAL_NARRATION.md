# Journal de la rédaction facultative par modèle de langage

Date de toutes les opérations : 2026-10-28 (poste A).

**Préfixes**

| préfixe | chemin |
|---|---|
| R | `C:\Users\hassen\Desktop\mahdi\pfe_mahdi\` |
| J | `R\BACK\src\main\java\com\municipalite\gestiontournees\` |
| T | `R\BACK\src\test\java\com\municipalite\gestiontournees\` |
| CV | `R\computer_vision\` |

**Légende**
- « compilé : oui » : `mvnw -q -B -DskipTests test-compile` avec le JDK portable de `CV\integration\outils\`, code principal et tests, sans erreur ni avertissement.
- « testé : non » : aucun test Java ni Flutter exécuté sur ce poste.

Les originaux des fichiers modifiés sont dans `CV\integration\sauvegardes_narration\<chemin relatif à R>`.

## Fichiers existants modifiés

| fichier | rôle | compilé | testé |
|---|---|---|---|
| `J\ai\config\AiProperties.java` | enum `VisionNarration {TEMPLATE, LLM}`, `visionNarration`, `visionNarrationTimeoutSeconds` | oui | non |
| `R\BACK\src\main\resources\application.properties` | `ai.vision-narration`, `ai.vision-narration-timeout-seconds` | oui (ressource) | non |
| `R\BACK\.env.example` | `VISION_NARRATION`, `VISION_NARRATION_TIMEOUT_SECONDS` (noms, valeurs par défaut sans secret) | sans objet | non |
| `J\ai\vision\model\ImageAnalysis.java` | champs `narrative`, `narrativeSource`, `narrativeModel`, `narrativeFailureCategory`, `narrativePromptVersion`, `narrativeGeneratedAt` | oui | non |
| `R\FRONT\src\services\aiService.js` | `generatePhotoNarrative(id)` | build : oui | lint et build : oui (aucun test web) |
| `R\FRONT\src\pages\tournees\TourneeDetailsModal.jsx` | bouton « Générer un résumé rédigé », résumé et mention de sa source | build : oui | lint et build : oui |
| `R\README.md` | section « Rédaction facultative par modèle de langage » | sans objet | sans objet |
| `CV\integration\kit_tests_autre_pc\01_service_python.ps1` | tests hors ligne du validateur et du script d'évaluation | sans objet | **exécuté ici, réussi** |
| `CV\integration\kit_tests_autre_pc\02_tests_backend.ps1` | 4 classes de test ajoutées aux tests ciblés ; avertissement si `VISION_NARRATION` est défini | sans objet | non (poste B) |
| `CV\integration\kit_tests_autre_pc\LISEZMOI_AUTRE_PC.md` | section « Vérification manuelle de la rédaction », compteurs du tableau | sans objet | sans objet |

## Fichiers créés

| fichier | rôle | compilé | testé |
|---|---|---|---|
| `J\ai\vision\narrative\NarrativeFacts.java` | données transmises au modèle (N5) | oui | non |
| `J\ai\vision\narrative\NarrativeValidator.java` | règles V1 à V7 (N7) | oui | non |
| `J\ai\vision\narrative\NarrativeGenerator.java` | interface de génération | oui | non |
| `J\ai\vision\narrative\CollectionAiNarrativeGenerator.java` | adaptateur vers `CollectionAiClient.generateJson`, sans modifier ce client | oui | non |
| `J\ai\vision\narrative\NarrativeService.java` | verrou, cache, délai, nouvelle tentative, repli, journalisation N9 | oui | non |
| `J\ai\vision\controller\VisionNarrativeController.java` | `POST /api/ai/vision/analyses/{id}/narrative` | oui | non |
| `R\BACK\src\main\resources\prompts\vision-narrative-v1.txt` | prompt versionné (N6) | ressource copiée | lu par le dry-run Python |
| `R\BACK\src\main\resources\prompts\vision-narrative-forbidden.txt` | mots interdits (V3) | ressource copiée | lu par les tests Python |
| `R\BACK\src\test\resources\vision\narrative_validator_cases.json` | 49 vecteurs partagés (N8) | ressource de test | **validateur Python : 49/49** |
| `T\ai\vision\narrative\NarrativeValidatorTest.java` | lit les vecteurs partagés | oui | non |
| `T\ai\vision\narrative\NarrativeServiceTest.java` | générateur simulé, 14 cas | oui | non |
| `T\ai\vision\VisionNarrativeAccessTest.java` | accès SUPERVISEUR / CHAUFFEUR / anonyme / tournée | oui | non |
| `T\ai\config\VisionNarrationPropertiesTest.java` | liaison : `VISION_NARRATION` absent → TEMPLATE | oui | non |
| `R\scripts\evaluation\narrative_validator.py` | validateur Python (mêmes règles, même liste) | sans objet | **oui, poste A** |
| `R\scripts\evaluation\test_narrative_validator.py` | lit les vecteurs partagés | sans objet | **oui, poste A** |
| `R\scripts\evaluation\evaluate_narration.py` | 40 cas, `--dry-run` par défaut, `--live` | sans objet | dry-run **exécuté** ; `--live` **non exécuté** |
| `R\scripts\evaluation\test_evaluate_narration.py` | cas déterministes, prompt, dry-run sans réseau, refus sans clé | sans objet | **oui, poste A** |
| `CV\integration\execution_narration\` | journaux du poste A (compilation, tests Python, dry-run, npm, kit 01) | sans objet | sans objet |
| `CV\integration\RAPPORT_NARRATION.md`, ce journal | rapports | sans objet | sans objet |

## Effets de bord

- `R\BACK\target\` est mis à jour par la compilation.
- `R\FRONT\node_modules\` et `R\FRONT\dist\` sont recréés par npm.
- Aucun fichier `.env` n'a été créé.
- Aucun appel réseau n'a été fait vers un fournisseur d'IA.
