# Test local des fonctions IA

Ces essais utilisent des documents pédagogiques et CV fictifs, ainsi que des photographies publiques avec attribution. Vos CV privés peuvent rester dans `local-data/cvs/`, dossier ignoré par Git. Ces essais ne constituent pas un benchmark indépendant ni une preuve de collecte réelle.

## Ouvrir les applications

Lorsque les services locaux sont démarrés selon le README :

- Superviseur React : <http://127.0.0.1:5173>.
- Chauffeur Flutter dans Chrome : <http://127.0.0.1:8088>.
- Backend : <http://127.0.0.1:8080>.

Les comptes de démonstration créés sur cette base locale ne sont pas des comptes universels d'installation. Utilisez les identifiants fournis avec votre instance. Aucune clé Gemini, Groq, OpenRouter ou ORS ne doit être saisie dans les applications web ou mobile ; elles restent dans le fichier ignoré `BACK/.env`.

## Configurer les photos et CV avec Groq gratuit

Créez une [clé GroqCloud](https://console.groq.com/keys) et conservez le compte sur le plan gratuit. Aucun modèle local ni activation de la facturation n'est nécessaire pour cette configuration. Groq est le fournisseur sélectionné par défaut pour les photos et les CV. Dans le fichier existant `BACK/.env`, remplacez le placeholder par votre clé :

```dotenv
COLLECTION_AI_PROVIDER=GROQ
GROQ_API_KEY=VOTRE_CLE_GROQ_ICI
GROQ_MODEL_TEXT=qwen/qwen3.8-27b
GROQ_MODEL_VISION=qwen/qwen3.8-27b
GROQ_TIMEOUT_SECONDS=60
```

Le délai partagé est facultatif, borné entre 1 et 120 secondes. Les identifiants Groq ne portent pas le suffixe OpenRouter `:free` : le plan du compte Groq définit son quota. Qwen analyse les photos et extrait les CV. Gemini reste réservé au chatbot RAG et à ses embeddings. Le score des candidatures reste calculé par le barème du backend ; la décision d'embauche reste humaine. Ne copiez pas cette clé dans React, Flutter ou un fichier suivi par Git.

Les requêtes désactivent le raisonnement prolongé et limitent la sortie à 1 024 tokens pour une photo ou 4 096 pour un CV. Ces réglages conviennent aux réponses structurées courtes ; ils ne garantissent pas la disponibilité du fournisseur. Une réponse tronquée ou hors délai reste indisponible.

Après modification, arrêtez puis relancez le backend depuis `BACK` avec `.\mvnw.cmd spring-boot:run` sous Windows PowerShell ou `./mvnw spring-boot:run` sous Linux. Le [README](../README.md) décrit l'installation complète, Windows puis Linux.

Les [limites gratuites Groq](https://console.groq.com/docs/rate-limits) publiées pour ce modèle sont actuellement :

| Requêtes/minute | Requêtes/jour | Tokens/minute | Tokens/jour |
| ---: | ---: | ---: | ---: |
| 30 | 1 000 | 8 000 | 200 000 |

Les quatre plafonds s'appliquent simultanément ; ils ne garantissent pas un nombre de photos par jour. [Chaque image représente 2 048 tokens d'entrée](https://console.groq.com/docs/vision), auxquels s'ajoutent le texte de la demande et la réponse. Une analyse APRÈS seule utilise une requête et une image ; une paire AVANT/APRÈS utilise une requête et deux images. Les CV et les réessais partagent ce quota. Compresser une image réduit sa taille d'envoi, pas son coût fixe en tokens ni le nombre de requêtes. Consultez les limites de votre compte et respectez `Retry-After` lorsqu'il est présent.

### Alternative OpenRouter explicite

Pour utiliser l'ancien fournisseur, créez une [clé OpenRouter](https://openrouter.ai/settings/keys), modifiez ces paramètres dans `BACK/.env`, puis redémarrez le backend :

```dotenv
COLLECTION_AI_PROVIDER=OPENROUTER
OPENROUTER_API_KEY=VOTRE_CLE_OPENROUTER_ICI
OPENROUTER_MODEL_TEXT=qwen/qwen3.8-27b:free
OPENROUTER_MODEL_VISION=qwen/qwen3.8-27b:free
OPENROUTER_TIMEOUT_SECONDS=60
```

Conservez les suffixes `:free`. Sans crédits achetés, les [limites OpenRouter](https://openrouter.ai/docs/api/reference/limits) indiquent actuellement **50 requêtes gratuites par jour au total et jusqu'à 20 par minute**, tous modèles gratuits confondus. L'extraction des CV, les analyses photo et les réessais partagent ce quota ; la capacité du fournisseur peut limiter davantage le débit. Une consommation monétaire nulle ne prouve pas qu'il reste 50 requêtes.

Aucun basculement automatique vers l'autre fournisseur, Gemini ou un modèle payant n'est prévu. Une analyse photo impossible doit afficher `ANALYSIS_UNAVAILABLE` ; une extraction CV impossible doit rester explicitement indisponible et permettre la saisie manuelle.

Vérifiez les réponses réelles de Qwen avec votre clé sur votre installation. Une configuration enregistrée ou un test d'intégration réussi ne démontre ni une réponse réelle du fournisseur ni la justesse de l'analyse. Utilisez les parcours CV et photo ci-dessous et vérifiez les propositions, leurs éléments justificatifs et le modèle enregistré.

## Chatbot RAG

Trois fiches structurées se trouvent dans `evaluation/rag/development-documents/`. Elles décrivent les photos, les points inaccessibles et le retour au dépôt. Le fichier `expected-cases.json` contient des attentes séparées ; ne l'importez pas dans la base documentaire.

Importez les trois fichiers Markdown depuis **Base documentaire**, attendez la disponibilité du texte, puis approuvez-les. Vérifiez d’abord la liste pour éviter de les importer à nouveau dans une base de démonstration existante. L'index sémantique doit correspondre au modèle d'embedding configuré. Un texte approuvé reste utilisable par la recherche lexicale si l'indexation échoue.

Dans Flutter, ouvrez **Assistant** et essayez :

1. « Quelle photo est obligatoire pour confirmer une collecte normale ? » — la source prévoit APRÈS obligatoire et AVANT facultative.
2. Dans la même conversation : « Et si ce point est inaccessible ? » — retrouver la fiche de signalement, sans inventer de coordonnées de contact.
3. Dans un nouveau chat : « Dans PFE-DEMO-01, quel est le code de l’exercice photo ? » — la référence est `JASMIN-PHOTO-23`.
4. « Quel est le salaire annuel du chauffeur et la prime de nuit ? » — les fiches ne contiennent pas cette information ; aucune rémunération ne doit être inventée.

Vérifiez l'état affiché et les passages cités. `SOURCE_EXCERPTS` signifie que des extraits littéraux sont disponibles mais que la réponse IA n'a pas pu être produite. Ce n'est pas une réponse générée réussie. Une panne sans extrait utile doit rester explicite. Vérifiez également l'historique après rechargement.

Dans chaque carte source, **Extrait cité** développe ou replie le passage. **Télécharger la source** récupère le document original dans le navigateur ; **Ouvrir le document** utilise une application compatible sur Android. L'accès passe par l'API authentifiée. **Préciser ma question** remet la question dans le champ de saisie ; **Réessayer la réponse** réessaie la dernière réponse indisponible dans la même discussion. Le bouton d'historique permet de rechercher puis rouvrir une discussion enregistrée.

Pour reproduire le rendu HTML vérifié avec Flutter 3.24.5, lancez depuis `MOBILE` :

```text
flutter build web --no-web-resources-cdn --web-renderer html --pwa-strategy=none --dart-define=API_BASE_URL=http://127.0.0.1:8080/api
```

Servez ensuite `build/web` avec `py -3 -m http.server 8088 --directory build/web` sous Windows, ou `python3 -m http.server 8088 --directory build/web` sous Linux. Le navigateur ne remplace pas la vérification du GPS et de la caméra sur téléphone. Ce rendu HTML est une option du SDK Flutter épinglé ; des SDK plus récents peuvent ne plus le proposer.

Résultats détaillés : [essai RAG de développement](../evaluation/rag/runs/ui-live-development-2026-09-23-summary.md).

## CV et matching

Pour un test partageable, importez les deux CV fictifs de [evaluation/candidates/fixtures/cv-development](../evaluation/candidates/fixtures/cv-development/) dans **Candidatures**. Ils ne décrivent aucune personne réelle. Le [guide de matching](evaluation/CANDIDATE_MATCHING.md) donne leurs champs de référence et leur barème de développement.

Pour vos propres documents, créez `local-data/cvs/` et placez-y les PDF/DOCX autorisés pour cet usage. Les treize PDF initialement présents à la racine ont été déplacés dans ce dossier local ; ils ne font pas partie d’une nouvelle installation. Les mots `ACCEPTE` ou `REFUSE` dans un nom de fichier ne sont ni des décisions ni des labels de référence. L’envoi d’un CV au fournisseur utilise le quota d’extraction ; relire les champs et recalculer le score n’appelle pas le modèle.

Ouvrez **Examiner** pour lire les propositions et les citations. Si le fournisseur était indisponible, **Réessayer l’extraction IA** réutilise le PDF enregistré. Les informations restent à vérifier ; un score provisoire de zéro ne signifie pas que le candidat est inadapté. Vérifiez les champs avant d'interpréter les points, puis comparez le détail des critères au profil du poste. Aucune acceptation ou création de chauffeur n'a été exécutée pendant ces imports.

Résultats retenus et limites : [P1 — matching](evaluation/P1_RUN_RESULTS.md#candidate-matching).

## Photo APRÈS sans caméra

Les cinq JPEG dans `evaluation/vision/internet-development/` permettent de tester le sélecteur de fichiers de Flutter web. Les métadonnées de source et de licence sont dans ce même dossier.

1. Utilisez une tournée de démonstration affectée au chauffeur et optimisée.
2. Démarrez-la, ouvrez le point courant puis **Je suis arrivé**.
3. **Choisir la photo APRÈS** ouvre un sélecteur de fichiers dans le navigateur. Sur Android, le parcours conserve la caméra.
4. Essayez `street-litter.jpg` pour des déchets visibles, ou `clear-bin-surroundings.jpg` pour un entourage de poubelle visiblement plus dégagé. L'évaluation peut rester incertaine : le contenu de la poubelle et le hors-champ ne sont pas démontrés.
5. L'analyse automatique est désactivée par défaut : l'envoi conserve la photo sans requête IA. Cliquez sur **Analyser la photo** pour un essai réel, ou activez **Analyse IA automatique** pour les prochains envois de cette page. Vérifiez la prédiction originale, puis renseignez une observation humaine si nécessaire. Ajoutez une note explicite indiquant qu'il s'agit d'une simulation avec une photo internet.
6. Si l'envoi échoue, le brouillon reste disponible. Actualisez pour vérifier le serveur, puis utilisez le réessai si nécessaire.
7. Terminez ou signalez chaque point, confirmez le retour au dépôt dans la simulation, puis consultez les preuves depuis le détail de la tournée web.

Ces images montrent des scènes différentes. Ne les associez pas comme une paire AVANT/APRÈS. Une image ne prouve ni l'identité de l'intervenant ni la réalisation du travail. La vérification sur téléphone physique reste nécessaire pour la caméra, le GPS et le passage en arrière-plan.

Un résultat IA enregistré pour les preuves courantes est conservé sans nouvelle requête ; cela inclut `CANNOT_DETERMINE`, qui reste une incertitude à examiner. Une photo remplacée, un modèle ou un prompt modifié invalident la réutilisation côté serveur. Un résultat indisponible peut être réessayé manuellement. Les évaluations superviseur restent des appels frais afin de ne pas fausser les expériences. Pour tester uniquement l'interface, laissez l'option automatique désactivée et ne cliquez pas sur le bouton d'analyse : aucune requête de vision ne sera envoyée. Cela ne valide pas la précision du modèle.
