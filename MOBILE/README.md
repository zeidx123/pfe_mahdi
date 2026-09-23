# GestiCollecte — application chauffeur

Application Flutter existante, centrée sur les tournées affectées au chauffeur : départ/reprise, navigation vers le point suivant, arrivée, collecte, photo APRÈS, observation IA et humaine, retour au dépôt et fin de tournée. La revue et la planification restent dans l’application web du superviseur.

## Démarrer

Utiliser Flutter 3.24.5 / Dart 3.5.4 ou une version compatible avec `pubspec.yaml`.

```sh
flutter pub get
flutter run --dart-define=API_BASE_URL=http://ADRESSE_LAN_DU_SERVEUR:8080/api
```

Sur un téléphone, `localhost` désigne le téléphone. Utiliser une adresse du serveur accessible depuis le réseau du téléphone. Le bouton **Configurer le serveur** de l’écran de connexion permet aussi d’enregistrer cette adresse ; cette préférence prend priorité sur la valeur de compilation. Le défaut Android `10.0.2.2` convient à l’émulateur Android standard.

Se connecter avec un compte `CHAUFFEUR` actif. Les comptes superviseurs utilisent le web. Les jetons restent dans les requêtes authentifiées, jamais dans les liens Google Maps ou les URL des photos. Les clés Groq, Gemini, OpenRouter et OpenRouteService restent côté serveur dans `BACK/.env`.

Le manifeste autorise HTTP pour la démonstration sur réseau local. Android SDK 35 et les licences/outils Android correspondants sont nécessaires pour construire un APK. Aucun parcours complet sur téléphone physique n’est revendiqué par les tests automatiques.

## Ouvrir l’application chauffeur dans le navigateur

Le même projet Flutter fonctionne dans un navigateur. Cela reste l’espace chauffeur ; React est l’espace superviseur.

Windows PowerShell, depuis la racine du projet :

```powershell
.\scripts\run-mobile.bat
```

Linux Bash, depuis la racine du projet :

```bash
./scripts/run-mobile.sh
```

Pour servir une compilation statique sous Linux, depuis `MOBILE` :

```bash
flutter build web --no-web-resources-cdn --dart-define=API_BASE_URL=http://127.0.0.1:8080/api
python3 -m http.server 8088 --bind 127.0.0.1 --directory build/web
```

Ouvrir `http://127.0.0.1:8088` après avoir démarré le backend sur le port `8080`. Adapter le port API si nécessaire. Autoriser cette origine dans la configuration CORS du backend. Une ancienne adresse enregistrée dans **Configurer le serveur** prend priorité sur la valeur de compilation : la corriger si nécessaire. `--no-web-resources-cdn` fournit CanvasKit depuis ce serveur local et évite un écran blanc si son CDN est inaccessible.

Dans le navigateur, **Choisir la photo APRÈS** ouvre un sélecteur de fichier JPEG/PNG (10 Mo maximum). **Photo AVANT facultative** utilise le même sélecteur. Sur Android, les boutons continuent d’utiliser la caméra. Le brouillon unique est conservé dans IndexedDB, par chauffeur, pour permettre le réessai et la reprise après rechargement ; une erreur de stockage est affichée avant de déclarer la photo enregistrée. Les anciens brouillons du navigateur restent lisibles. Supprimer les données du site supprime aussi les brouillons et la connexion locale.

Après rechargement de la page, l’application restaure la session puis affiche **Mes tournées**. Rouvrir la tournée reprend son état confirmé et le brouillon local éventuel ; les arguments de navigation en mémoire ne sont pas nécessaires à cette reprise.

Google Maps s’ouvre dans un nouvel onglet ; autoriser les fenêtres contextuelles si le navigateur le bloque. Pour la localisation, utiliser `localhost`/`127.0.0.1` ou HTTPS et autoriser la position. Un navigateur peut suspendre un onglet en arrière-plan : le parcours web ne démontre pas le suivi Android pendant Maps ni l’utilisation de la caméra d’un téléphone.

## Parcours de démonstration

1. Le superviseur crée la tournée, définit son ordre manuel et calcule le parcours routier avec dépôt.
2. Le chauffeur ouvre sa tournée et appuie sur **Démarrer**. Sans parcours routier, le départ reste bloqué.
3. **Naviguer avec Google Maps** ouvre uniquement la destination suivante. Maps peut choisir d’autres routes, sans changer l’ordre des visites dans GestiCollecte.
4. Sur place, **Je suis arrivé**, puis collecte et **Prendre la photo APRÈS**. La photo AVANT est facultative et se prend avant la photo APRÈS.
5. La preuve est enregistrée sans appel IA par défaut. **Analyser la photo** lance une analyse manuelle ; **Analyse IA automatique** permet de l’activer pour cet écran. Un résultat valide est réutilisé tant que la preuve et la configuration restent identiques. Une erreur d’analyse ne devient jamais une validation de propreté. Le chauffeur peut ajouter sa propre observation et continuer.
6. Pour une obstruction, utiliser **Signaler un problème**, avec un motif. Cette action est disponible avant l’arrivée si le point est inaccessible : aucune arrivée ni photo ne doit être inventée. Si photographier est impossible ou dangereux, expliquer pourquoi.
7. Après chaque visite, le point suivant provient de l’état enregistré par le serveur. Après fermeture/reconnexion, rouvrir la tournée reprend cet état.
8. Après tous les points, naviguer vers le dépôt et confirmer le retour. Le superviseur consulte ensuite les preuves, les observations et les durées.

Une seule photo non envoyée est conservée en brouillon privé sur cet appareil, par utilisateur. Réessayer manuellement l’envoi ou supprimer explicitement le brouillon. Il n’existe pas de file de synchronisation hors ligne ; les actions de collecte nécessitent le serveur.

Si la suppression locale échoue après un envoi confirmé, la photo enregistrée et l’analyse restent utilisables : l’application signale la copie locale restante et permet de continuer. Après reprise, un brouillon correspondant exactement à la preuve enregistrée ne demande pas un nouvel envoi ; une photo de remplacement différente reste conservée pour réessai.

Le suivi GPS utilise un seul flux pendant la tournée, avec service de premier plan Android lorsqu’on ouvre Maps. Il s’arrête à la fin ou à la déconnexion. Une fermeture forcée peut l’interrompre : reprendre la tournée relance le suivi. L’écran signale les positions anciennes et erreurs d’envoi ; aucune valeur de carburant n’est simulée.

L’assistant affiche `ANSWERED`, `SOURCE_EXCERPTS`, `INSUFFICIENT_INFORMATION` ou `PROVIDER_UNAVAILABLE` en français, avec les passages sources complets et leurs références. Les scores de recherche ne sont pas présentés comme des probabilités.

## Vérifier

```sh
flutter analyze --no-pub
flutter test
CHROME_EXECUTABLE=/usr/bin/chromium flutter test --platform chrome
flutter build web --no-pub --no-web-resources-cdn
flutter build apk --debug --no-pub
```

Adapter `CHROME_EXECUTABLE` à l’emplacement de Chrome/Chromium sur votre machine. Les tests couvrent le contrat de connexion plat Spring, la reprise de session, le refus des comptes superviseurs, les erreurs 401/403, les fichiers authentifiés, les actions de visite, l’ordre de reprise et le retour au dépôt, ainsi que l’absence de validation normale sans photo APRÈS. Ils vérifient aussi le type JPEG/PNG lors des réessais et le refus des formats non pris en charge ou des fichiers trop volumineux. La suite Chrome ajoute la persistance IndexedDB d’un brouillon de 6 Mo, l’isolation entre chauffeurs et la lecture des anciens brouillons. Le sélecteur de fichier et le parcours complet restent à exercer dans l’application lancée.

À vérifier sur un téléphone Android réel : autorisations GPS/caméra, photo et réessai réseau, navigation externe, suivi pendant Maps, fermeture/reprise, puis arrêt du suivi à la fin/déconnexion. Un build web ou des tests simulés ne remplacent pas cette validation matérielle.
