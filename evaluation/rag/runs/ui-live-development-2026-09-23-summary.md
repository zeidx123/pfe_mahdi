# Vérification RAG locale — 23 septembre 2026

Périmètre : cinq requêtes de développement, sur trois fiches fictives rédigées pour la simulation PFE. Les attentes sont séparées des réponses dans `../development-documents/expected-cases.json`. Ces essais ne mesurent pas une exactitude indépendante.

## Corpus réellement importé

| Fichier | Identifiant local | État vérifié |
|---|---|---|
| PFE-DEMO-01-photos-et-verification.md | `6ab3f5c972e8c50402ed66fb` | Approuvé, texte disponible, INDEXED, index sémantique compatible. |
| PFE-DEMO-02-point-inaccessible-et-revue.md | `6ab3f5c972e8c50402ed66fc` | Approuvé, texte disponible, INDEXED, index sémantique compatible. |
| PFE-DEMO-03-tournee-jasmin-et-retour.md | `6ab3f5c972e8c50402ed66fd` | Approuvé, texte disponible, INDEXED, index sémantique compatible. |

Le corpus contient six fragments. Configuration observée : `gemini-embedding-2`, 768 dimensions, version `text-v1` ; génération `gemini-3.6-flash`, prompt `grounded-rag-v1`. Les trois téléchargements authentifiés par le compte chauffeur ont retourné HTTP 200 et les octets des fichiers importés.

## Résultats après correction du filtrage des extraits

| Cas | État API | Sources proposées | Durée de la requête |
|---|---|---:|---:|
| Photo APRÈS obligatoire / AVANT facultative | SOURCE_EXCERPTS | 2 | 1,795 s |
| Relance : « Et si ce point est inaccessible ? » | SOURCE_EXCERPTS | 2 | 1,058 s |
| Référence pédagogique de l'exercice photo | SOURCE_EXCERPTS | 4 | 0,958 s |
| Retour au dépôt après le dernier point | SOURCE_EXCERPTS | 2 | 1,387 s |
| Salaire annuel et prime de nuit absents du corpus | INSUFFICIENT_INFORMATION | 0 | 1,210 s |

La relance conserve le même identifiant de conversation. Les extraits proposés sont des passages littéraux des fichiers approuvés. La recherche était hybride ; aucun de ces cinq appels n'a produit une réponse générée `ANSWERED`.

Un diagnostic indépendant avec le véritable contexte RAG et la configuration JSON du client a observé HTTP **503 / UNAVAILABLE**, motif fournisseur : forte demande temporaire sur le modèle. Aucune instruction fictive de remplacement n'a été générée. Ce constat décrit cet appel précis ; il ne signifie pas que tous les appels au modèle échouent.

Comparaison du code des requêtes : RAG et extraction CV utilisent le même `GeminiClient.generateJson`, le même modèle texte configuré, la même clé serveur, la même enveloppe `contents.parts.text`, `responseMimeType=application/json`, `temperature=0` et le même délai maximal. L'analyse d'image partage cette configuration JSON. Le diagnostic RAG comportait cinq passages, 4 028 caractères de contexte et environ 5 189 caractères de prompt, avec un historique vide ; le plafond de texte CV est de 30 000 caractères. Aucun défaut concret de forme de requête n'a été identifié. Le HTTP 503 a précédé toute validation locale des citations. L'audit n'a déclenché aucun nouvel appel fournisseur.

## Défaut reproduit et corrigé

Avant correction, la question sur le salaire affichait cinq extraits de collecte parce que les résultats de recherche partageaient des termes génériques, notamment « chauffeur ». Le mode dégradé les présentait tous comme pertinents.

La correction exige désormais une couverture lexicale minimale avant d'afficher des extraits en mode dégradé, avec au moins deux termes en commun pour une question comportant plusieurs termes. Elle conserve les passages littéraux pertinents et retourne `INSUFFICIENT_INFORMATION` quand ils ne soutiennent pas suffisamment la question. La récupération sémantique et les réponses générées restent inchangées. Cette heuristique conservatrice peut s'abstenir sur certaines paraphrases ; elle ne prouve pas la pertinence sémantique d'un passage.

Vérifications automatisées : 26 tests ciblés de services RAG, indexation et pannes ont passé ; après le dernier ajustement, les 17 tests des services RAG et d'évaluation ont repassé. Ils couvrent notamment les citations inventées, les conversations étrangères, les fragments non approuvés, les embeddings incompatibles et les indisponibilités. Les fournisseurs sont simulés dans ces tests.

## Fichier de preuve conservé

- `ui-live-development-2026-09-23-results.json` : exécution après correction, réponses complètes et vérifications des sources.

Les captures avant correction et les séries brutes de diagnostic/réessai ont été archivées hors du dépôt lors de la préparation de la livraison. Les observations ci-dessus restent celles du 23 septembre, avec les modèles configurés à cette date ; elles ne décrivent pas les paramètres actuels de tous les fournisseurs.

Pour la vérification visuelle mobile, ouvrir la source de la réponse puis essayer les deux questions de photo/inaccessibilité dans la même conversation. Une réponse générée réussie reste à vérifier lors d'un appel fournisseur disponible. Une relance manuelle bornée, dans une nouvelle conversation, peut utiliser « Dans PFE-DEMO-01, quel est le code de l'exercice photo ? » ; la réponse attendue est `JASMIN-PHOTO-23`, avec une citation de PFE-DEMO-01.
