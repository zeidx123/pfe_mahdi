# Modèle mobile de statut de propreté (PFE)

Classification d'une photo de point de collecte en 3 classes (une étiquette par photo) :
`CANNOT_DETERMINE` (0), `DEBORDEMENT` (1), `PROPRE` (2), dans l'ordre de `classes.json`.

Résultats et lecture critique : **`RAPPORT_ENTRAINEMENT.md`**.

## Environnements

Deux environnements virtuels, tous deux dans ce dossier :

| venv | rôle | contenu principal |
|---|---|---|
| `.venv` | étapes 0 à 9 | créé avec `--system-site-packages` (réutilise numpy, pandas, opencv, transformers du Python 3.13 système) + torch 2.14.1+cu126, torchvision 0.29.1+cu126, scikit-learn, onnx, onnxruntime, onnxscript |
| `.venv_tflite` | conversion et inférence TFLite (appelé automatiquement par les étapes 6 et 8) | tensorflow 2.21, onnx2tf 2.6.9, ai-edge-litert, pillow, pandas |

Recréer :

```powershell
python -m venv --system-site-packages .venv
.venv\Scripts\python -m pip install --ignore-installed --no-deps torch==2.14.1+cu126 torchvision --index-url https://download.pytorch.org/whl/cu126
.venv\Scripts\python -m pip install scikit-learn joblib onnx onnxruntime onnxscript
python -m venv .venv_tflite
.venv_tflite\Scripts\python -m pip install tensorflow tf-keras onnx2tf onnx onnxruntime onnx-graphsurgeon sng4onnx psutil ai-edge-litert pillow pandas
```

`config.py` contient tous les chemins, la graine (42) et les hyperparamètres. Le dataset
`dataset_entrainement_v2\` est lu sans jamais être modifié ; tout est écrit dans ce dossier.

## Relancer chaque étape

Depuis ce dossier, avec `set PYTHONIOENCODING=utf-8` (PowerShell : `$env:PYTHONIOENCODING="utf-8"`) :

| étape | commande | sorties |
|---|---|---|
| 0 vérification | `.venv\Scripts\python code\e0_verification.py` | `donnees_derivees\verification_depart.json` |
| 1 sous-ensembles | `.venv\Scripts\python code\e1_sous_ensembles.py` | `donnees_derivees\sous_ensembles.csv`, `effectifs_sous_ensembles.json` |
| 2 embeddings CLIP | `.venv\Scripts\python code\e2_embeddings.py` | `donnees_derivees\embeddings.npz`, `embeddings_info.json` |
| 3 références | `.venv\Scripts\python code\e3_references.py` | `modeles\CLIP_all.joblib`, `CLIP_reel.joblib`, `B1.joblib`, `B2.joblib`, `resultats\references_valid.json` |
| 4 décision | `.venv\Scripts\python code\e4_decision.py` | `donnees_derivees\decision_jeu_entrainement.json` |
| 5 entraînement | `.venv\Scripts\python code\e5_entrainement.py` | `modeles\mobilenet_final.pt`, `temperature.json`, `journal_entrainement.json`, courbes |
| 6 export | `.venv\Scripts\python code\e6_export.py` (appelle `code\e6b_tflite.py` dans `.venv_tflite`) | `export\*.onnx`, `export\*.tflite`, `export\model_card.json` |
| 7 qualité d'image | `.venv\Scripts\python code\e7_qualite_image.py` | `resultats\qualite_image.json`, `donnees_derivees\qualite_image_mesures.csv` |
| 8 évaluation finale (test) | `.venv\Scripts\python code\e8_evaluation_finale.py` (appelle `code\e8b_inference_tflite.py`) | `resultats\metriques.json`, `resultats\predictions.csv` |
| 9 planches | `.venv\Scripts\python code\e9_planches.py` | `resultats\planches\*.png` |

Les étapes 1 à 7 ne calculent aucune métrique sur test. **Relancer l'étape 8 réévalue le test** :
à ne faire que si tout est figé, et à signaler.

Reproductibilité : les étapes 0 à 4, 7 et 9 sont déterministes. L'étape 2 est faite sur CPU, en mode
déterministe. L'étape 5 se fait sur GPU avec cuDNN déterministe, mais certains noyaux CUDA restent
non déterministes (`use_deterministic_algorithms(warn_only=True)`). Le résultat est donc identique
à la tolérance numérique près, pas bit à bit. L'ordre des lots et les augmentations sont fixés par
la graine, à nombre de workers constant (6).

## Utiliser le modèle exporté (Flutter)

Fichiers dans `export\` (détails, SHA-256 et cohérence dans `model_card.json`) :

| fichier | entrée | sortie | recommandation |
|---|---|---|---|
| `proprete_mobilenetv3l_float16.tflite` (8,5 Mo) | `[1, 224, 224, 3]` float32, **NHWC** | `[1, 3]` float32 | **à privilégier pour l'application** |
| `proprete_mobilenetv3l_float32.tflite` (16,8 Mo) | `[1, 224, 224, 3]` float32, NHWC | `[1, 3]` float32 | référence exacte |
| `proprete_mobilenetv3l_int8.tflite` (4,7 Mo) | `[1, 224, 224, 3]` float32, NHWC | `[1, 3]` float32 | **NON UTILISABLE** (38 % de concordance avec PyTorch) |
| `proprete_mobilenetv3l.onnx` (16,8 Mo) | `[1, 3, 224, 224]` float32, **NCHW** | `[1, 3]` float32 | serveur, tests |

Prétraitement, à reproduire exactement en Dart (référence : `export\exemple_pretraitement.py`) :

1. décoder l'image en RGB ;
2. la redimensionner **par étirement** en 224 x 224, sans recadrage, en interpolation bilinéaire ;
3. diviser par 255 ;
4. normaliser chaque canal : `(x - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]` ;
5. ranger les valeurs en NHWC (TFLite) ou en NCHW (ONNX).

La sortie contient déjà les **probabilités** : `softmax(logits / T)`, avec T = 0,2984 inclus dans le
graphe. Il ne faut appliquer ni softmax ni température une seconde fois. Ordre des sorties :
`[CANNOT_DETERMINE, DEBORDEMENT, PROPRE]`.

Exemple : `python export\exemple_pretraitement.py photo.jpg export\proprete_mobilenetv3l_float16.tflite`

Aucun seuil de refus n'est fixé. La courbe couverture / exactitude (`modeles\couverture_exactitude_valid.png`)
et les tableaux de qualité d'image (`resultats\qualite_image.json`) servent à en choisir un.

**Avertissement** : étiquettes en partie extrapolées à partir de boîtes englobantes, classe
CANNOT_DETERMINE synthétique, images surtout issues du web chinois ; non validé sur le terrain.
Licences des données : CC BY 4.0, attribution requise pour Kaggle/Roboflow, kabam et GBS.
