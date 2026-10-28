"""Configuration unique : chemins, graine, constantes partagées par tous les scripts."""
from pathlib import Path
import json
import os
import random

import numpy as np

GRAINE = 42

# --- Chemins -----------------------------------------------------------------
MAHDI = Path(r"C:\Users\hassen\Desktop\mahdi")
DATASET = MAHDI / "dataset_entrainement_v2"            # LECTURE SEULE
CSV = DATASET / "dataset_entrainement_v2.csv"
SPLITS_FIGES = DATASET / "splits_figes.csv"
CLASSES_JSON = DATASET / "classes.json"

EMB_EXISTANTS = [
    MAHDI / "dataset_entrainement" / "_cache" / "clip_kaggle.npz",
    MAHDI / "dataset_entrainement" / "_cache" / "clip_gbs.npz",
    MAHDI / "dataset_final" / "_diagnostic" / "clip_embeddings_gd.npz",
]

CV = MAHDI / "pfe_mahdi" / "computer_vision"           # seul dossier d'écriture
CODE = CV / "code"
DERIVEES = CV / "donnees_derivees"
MODELES = CV / "modeles"
EXPORT = CV / "export"
RESULTATS = CV / "resultats"
PLANCHES = RESULTATS / "planches"
RAPPORT = CV / "RAPPORT_ENTRAINEMENT.md"

EMBEDDINGS = DERIVEES / "embeddings.npz"
SOUS_ENSEMBLES = DERIVEES / "sous_ensembles.csv"
DECISION = DERIVEES / "decision_jeu_entrainement.json"
VERIF_DEPART = DERIVEES / "verification_depart.json"

for _d in (CODE, DERIVEES, MODELES, EXPORT, RESULTATS, PLANCHES):
    _d.mkdir(parents=True, exist_ok=True)

# --- Classes -----------------------------------------------------------------
CLASSES = json.loads(CLASSES_JSON.read_text(encoding="utf-8"))   # {"CANNOT_DETERMINE":0,"DEBORDEMENT":1,"PROPRE":2}
NOMS = [c for c, _ in sorted(CLASSES.items(), key=lambda t: t[1])]
CD, DEB, PRO = CLASSES["CANNOT_DETERMINE"], CLASSES["DEBORDEMENT"], CLASSES["PROPRE"]

# --- Effectifs attendus (étape 0) --------------------------------------------
ATTENDU = {
    "tout": {"train": {"PROPRE": 960, "DEBORDEMENT": 5295, "CANNOT_DETERMINE": 381},
             "valid": {"PROPRE": 207, "DEBORDEMENT": 1135, "CANNOT_DETERMINE": 82},
             "test": {"PROPRE": 207, "DEBORDEMENT": 1135, "CANNOT_DETERMINE": 81}},
    "sous_reel": {"train": {"PROPRE": 352, "DEBORDEMENT": 2655, "CANNOT_DETERMINE": 381},
                  "valid": {"PROPRE": 76, "DEBORDEMENT": 569, "CANNOT_DETERMINE": 82},
                  "test": {"PROPRE": 76, "DEBORDEMENT": 569, "CANNOT_DETERMINE": 81}},
}

# --- CLIP ----------------------------------------------------------------------
CLIP_MODELE = "openai/clip-vit-base-patch32"
SEUIL_STRICT = 0.90

# --- Modèle mobile --------------------------------------------------------------
TAILLE = 224
MOYENNE = [0.485, 0.456, 0.406]
ECART_TYPE = [0.229, 0.224, 0.225]
LOT = 64
EPOQUES_TETE = 2
LR_TETE = 1e-3
EPOQUES_MAX = 12
PATIENCE = 3
WEIGHT_DECAY = 1e-4
LABEL_SMOOTHING = 0.1
GRILLE_LR = [3e-5, 1e-4]
BUDGET_CPU_MIN = 90
NOM_MODELE = "proprete_mobilenetv3l"
VERSION_MODELE = "1.0.0"


def fixer_graine(g=GRAINE):
    random.seed(g)
    np.random.seed(g)
    os.environ["PYTHONHASHSEED"] = str(g)
    try:
        import torch
        torch.manual_seed(g)
        torch.cuda.manual_seed_all(g)
    except ImportError:
        pass


def charger_csv():
    import pandas as pd
    df = pd.read_csv(CSV, encoding="utf-8-sig", dtype={"famille": str, "parent_id": str})
    for c in ["ia_probable", "sous_reel", "sous_gbs", "sous_kaggle_sacs", "sous_lot_2001_3000",
              "sous_synthetique", "sous_flag_catalogue"]:
        df[c] = df[c].astype(str).str.lower().eq("true")
    df["y"] = df["classe"].map(CLASSES).astype(int)
    df["chemin_abs"] = [str(DATASET / p) for p in df["chemin_image"]]
    return df


def ecrire_json(chemin, obj):
    Path(chemin).write_text(json.dumps(obj, ensure_ascii=False, indent=2, default=_json_def), encoding="utf-8")


def _json_def(o):
    if isinstance(o, (np.integer,)):
        return int(o)
    if isinstance(o, (np.floating,)):
        return float(o)
    if isinstance(o, np.ndarray):
        return o.tolist()
    if isinstance(o, (np.bool_,)):
        return bool(o)
    if isinstance(o, Path):
        return str(o)
    raise TypeError(type(o))
