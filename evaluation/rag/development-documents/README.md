# Corpus de démonstration RAG — développement

Ces trois fiches sont des **documents fictifs de simulation PFE**, rédigés pour essayer le parcours documentaire de GestiCollecte. Elles ne sont ni un règlement municipal ni des instructions professionnelles de terrain. Elles ne contiennent aucun CV, identité de candidat ou dossier personnel.

| Source | Fichier | Informations vérifiables |
|---|---|---|
| PFE-DEMO-01 | `PFE-DEMO-01-photos-et-verification.md` | Photo APRÈS requise pour la collecte normale, AVANT facultative, référence d'exercice `JASMIN-PHOTO-23`. |
| PFE-DEMO-02 | `PFE-DEMO-02-point-inaccessible-et-revue.md` | Problème avec raison et preuves disponibles, photographie impossible non exigée, suivi par signalement dans l'application, référence `PFE-REVUE-23`. |
| PFE-DEMO-03 | `PFE-DEMO-03-tournee-jasmin-et-retour.md` | Nom pédagogique `CIRCUIT-JASMIN`, dépôt `36.722857, 10.283535`, retour confirmé, estimation initiale de cinq minutes de service par point. |

Version des fiches : **1.0, 23 septembre 2026**. Les codes sont des repères pédagogiques explicitement fictifs, pas des identifiants de tournées ou de signalements enregistrés dans l'application.

## Importer et essayer

1. Connecter un superviseur et importer uniquement les trois fichiers `PFE-DEMO-*.md` dans la base documentaire. L'API `POST /api/ai/documents` accepte un champ multipart `file`.
2. Attendre la fin du traitement, puis approuver chaque fiche avec l'écran de gestion ou `PATCH /api/ai/documents/{id}/approval`, corps `{"approved":true}`.
3. Vérifier séparément `approved`, `textReady` et `semanticIndexReady`. `TEXT_READY` conserve la recherche textuelle même si l'index sémantique est indisponible.
4. Connecter un chauffeur à l'assistant documentaire. Poser une question, puis la relance dans la même conversation :
   - « Quelle photo est obligatoire pour confirmer une collecte normale ? »
   - « Et si ce point est inaccessible ? »
5. Essayer « Quelle est la référence pédagogique de l'exercice photo dans PFE-DEMO-01 ? », puis ouvrir la source citée. La valeur attendue est dans le fichier source.
6. Dans une nouvelle conversation, demander un salaire ou une prime de nuit. Aucun de ces montants n'est présent : l'assistant doit s'abstenir de les inventer.

Les identifiants MongoDB changent lors d'un nouvel import. Les identifiants réellement utilisés dans le test local sont enregistrés dans les rapports datés sous `../runs/`.

## Séparer attentes et observations

`expected-cases.json` contient les questions et attentes rédigées avant l'observation. Les réponses effectivement retournées, leurs états, les citations et les temps sont enregistrés séparément dans `../runs/ui-live-development-2026-09-23-*.json`.

Il s'agit de vérifications de développement sur un corpus rédigé pour le test. Elles ne constituent pas une mesure indépendante d'exactitude et ne doivent pas être agrégées avec un benchmark final annoté. Une réponse `SOURCE_EXCERPTS` prouve uniquement qu'un extrait a été proposé ; elle ne vaut pas réponse générée réussie.

Pour les pannes, les tests `RagServiceTest`, `DocumentProcessingServiceTest`, `GeminiEmbeddingServiceTest`, `MongoVectorStoreServiceTest` et `RagEvaluationServiceTest` simulent les cas indisponibles sans appeler de fournisseur. Le filtrage des extraits en mode dégradé exige une couverture lexicale minimale ; il peut s'abstenir pour une paraphrase pourtant pertinente. Ce garde-fou ne remplace pas une évaluation de la pertinence.
