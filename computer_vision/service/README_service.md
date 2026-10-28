# Service local du modèle de propreté (`proprete_mobilenetv3l`)

Petit service HTTP (bibliothèque standard Python + ONNX Runtime) appelé par le backend Spring Boot quand
`VISION_ENGINE=LOCAL`. Il est autonome : ce dossier suffit, il ne dépend pas du dossier d'entraînement.

| fichier | rôle |
|---|---|
| `app.py` | le service |
| `requirements.txt` | versions figées : onnxruntime 1.31.1, pillow 13.0.0, numpy 2.5.4, opencv-python-headless 5.0.0.93 |
| `modele/proprete_mobilenetv3l.onnx` | le modèle (entrée `[1,3,224,224]` NCHW, sortie 3 probabilités, température incluse) |
| `modele/model_card.json` | fiche du modèle ; son SHA-256 ONNX est vérifié au démarrage |
| `modele/SHA256SUMS.txt` | empreintes des deux fichiers ci-dessus |
| `test_service.py`, `exemples/` | tests unittest et 3 images de référence |

## Installation sur un poste vierge

Prérequis : Python 3.10 ou plus récent (testé avec 3.13).

```powershell
cd computer_vision\service
python -m venv .venv_service
.venv_service\Scripts\python -m pip install -r requirements.txt
.venv_service\Scripts\python -m unittest -v test_service
.venv_service\Scripts\python app.py
```

Linux : `python3 -m venv .venv_service`, puis `.venv_service/bin/python …`. Le lanceur `scripts\run-local-vision.bat`
(ou `.sh`) fait la même chose.

Réglages (variables d'environnement ou options) :

| variable | option | défaut |
|---|---|---|
| `LOCAL_VISION_HOST` | `--host` | `127.0.0.1` (ne pas exposer sur le réseau) |
| `LOCAL_VISION_PORT` | `--port` | `8090` |
| `LOCAL_VISION_MODEL`, `LOCAL_VISION_MODEL_CARD` | `--modele`, `--fiche` | fichiers de `modele/` |
| `LOCAL_VISION_TOKEN` | — | vide : jeton non exigé ; sinon en-tête `X-Service-Token` obligatoire (même valeur côté backend) |

Le service **refuse de démarrer** (code 2) si le SHA-256 du `.onnx` diffère de `formats.onnx.sha256` dans la fiche.

## Contrat

- `GET /health` → `{status, model_name, model_sha256, onnxruntime_version}`
- `POST /predict` : corps = octets de l'image, `Content-Type: image/jpeg` ou `image/png`, 12 Mo au plus.
  En-têtes facultatifs : `X-Min-Sharpness`, `X-Min-Brightness`, `X-Max-Brightness` (porte de qualité désactivée s'ils
  sont absents ou vides), `X-Service-Token`.
  Réponse : `{classes, probabilities:{CANNOT_DETERMINE, DEBORDEMENT, PROPRE}, predicted_class,
  quality:{sharpness, mean_brightness, gate_enabled, issue}, model:{name, sha256}, exif_orientation_applied, latency_ms}`.
  `issue` vaut `TROP_SOMBRE`, `SUREXPOSEE`, `FLOUE` (testés dans cet ordre) ou `null`. Le modèle est appelé même
  quand la porte refuse la photo.
- Erreurs, toujours `{error, message}` : 400 `IMAGE_ILLISIBLE` / `SEUIL_INVALIDE` / `LONGUEUR_REQUISE`,
  401 `JETON_INVALIDE`, 404 `INTROUVABLE`, 413 `TROP_GROS`, 415 `TYPE_REFUSE`, 500 `ERREUR_INTERNE` (sans trace de pile).

Aucune image n'est écrite sur disque ni journalisée ; le jeton n'est jamais journalisé.

## Prétraitement et mesures

- Orientation EXIF appliquée (`ImageOps.exif_transpose`), **ce que l'entraînement ne faisait pas** : les 9 483 images du
  dataset n'ont aucune balise d'orientation ≠ 1, donc l'effet est nul sur elles ; sur une photo de téléphone tournée,
  ne pas l'appliquer change les probabilités (jusqu'à 0,018 mesuré sur 3 images tournées de 90°).
- Puis exactement `export/exemple_pretraitement.py` : RGB, étirement direct en 224×224 sans recadrage (PIL bilinéaire),
  /255, normalisation ImageNet, NCHW. Le test `test_pretraitement_identique_bit_a_bit_a_la_reference` le vérifie.
- Netteté = variance du Laplacien (OpenCV `CV_64F`) sur le gris de l'image 224×224 étirée ; luminosité = moyenne du gris
  de l'image entière (après orientation). Mêmes calculs que `code/e7_qualite_image.py`.

## Limites du modèle

Étiquettes d'entraînement en partie extrapolées ; classe CANNOT_DETERMINE apprise sur des images **synthétiques**
(flou, nuit, recadrage) ; précision de PROPRE d'environ 0,71 sur le test (30 DEBORDEMENT parmi les images prédites
PROPRE) ; images surtout issues du web chinois ; **rien n'est validé sur le terrain**. Les seuils de qualité proposés
dans `BACK/.env.example` ont été mesurés sur des images synthétiques : optimistes, à recalibrer sur des photos réelles.

Note : `modele/model_card.json` est une copie de `export/model_card.json` dont le SHA-256 ONNX a été corrigé
(la fiche d'export indiquait une empreinte qui ne correspond pas au fichier livré ; voir `note_sha256` dans la fiche).
