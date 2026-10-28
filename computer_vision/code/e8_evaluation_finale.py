"""Étape 8 : évaluation finale. Le TEST est évalué ici, UNE SEULE FOIS, tous les choix étant figés.

Modèles : CLIP_all, CLIP_reel, B1, B2, MobileNet PyTorch (avec température), ONNX, TFLite float32/float16/int8.
Sur valid et test, sous-ensembles : tout, sous_reel, ia_probable, sous_strict, gbs, hors_gbs, par origine,
par famille GBS, sous_flag_catalogue, sous_lot_2001_3000, sous_kaggle_sacs, sous_synthetique.
Métriques : exactitude équilibrée, macro-F1, P/R/F1 par classe, matrice de confusion, « 2 classes »
PROPRE contre DEBORDEMENT ; IC 95 % (bootstrap par groupe_lieu_v2, 1000 rééchantillons, graine 42) de la
macro-F1 et du rappel PROPRE.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import joblib
import numpy as np
import onnxruntime as ort
import pandas as pd
import torch
from torch.utils.data import DataLoader

import config as C
from commun import bootstrap_groupes, charger_embeddings, metriques, metriques_2classes
from donnees_image import JeuImages
from e3_references import MajoriteParOrigine, MetaB2  # noqa: F401  (nécessaires à joblib.load)
from e6_export import ONNX, PY_TFLITE, charger_final

sys.path.insert(0, str(C.EXPORT))
from exemple_pretraitement import pretraiter

TMP = C.DERIVEES / "export_tmp"
NOMS_TFLITE = {f"{C.NOM_MODELE}_float32": "TFLite_float32", f"{C.NOM_MODELE}_float16": "TFLite_float16",
               f"{C.NOM_MODELE}_int8": "TFLite_int8"}


def sous_ensembles(d, split):
    S = {"tout": np.ones(len(d), bool), "sous_reel": d["sous_reel"].to_numpy(),
         "ia_probable": d["ia_probable"].to_numpy(), "sous_strict": d["sous_strict"].to_numpy(),
         "gbs": (d["origine"] == "gbs").to_numpy(), "hors_gbs": (d["origine"] != "gbs").to_numpy(),
         "sous_reel_gbs": (d["sous_reel"] & (d["origine"] == "gbs")).to_numpy(),
         "sous_reel_hors_gbs": (d["sous_reel"] & (d["origine"] != "gbs")).to_numpy()}
    for o in sorted(d["origine"].unique()):
        S[f"origine={o}"] = (d["origine"] == o).to_numpy()
    for f in sorted(d.loc[d["origine"] == "gbs", "famille"].dropna().unique()):
        S[f"famille_gbs={f}"] = ((d["origine"] == "gbs") & (d["famille"] == f)).to_numpy()
    for c in ["sous_flag_catalogue", "sous_lot_2001_3000", "sous_kaggle_sacs", "sous_synthetique"]:
        S[c] = d[c].to_numpy()
    return S


def proba_mobilenet(d):
    model, T, _ = charger_final()
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = model.to(dev)
    dl = DataLoader(JeuImages(d), batch_size=128, shuffle=False, num_workers=6)
    out = []
    with torch.no_grad():
        for x, _ in dl:
            out.append(model(x.to(dev)).cpu().numpy())
    return np.concatenate(out)


def proba_onnx(d):
    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count()
    s = ort.InferenceSession(str(ONNX), so, providers=["CPUExecutionProvider"])
    n = s.get_inputs()[0].name
    return np.concatenate([s.run(None, {n: pretraiter(C.DATASET / c)})[0] for c in d["chemin_image"]])


def proba_tflite(d):
    liste = TMP / "liste_eval.csv"
    pd.DataFrame({"id": d["id"], "chemin": [str(C.DATASET / c) for c in d["chemin_image"]]}).to_csv(
        liste, index=False, encoding="utf-8")
    sortie = TMP / "probas_tflite.npz"
    r = subprocess.run([str(PY_TFLITE), str(C.CODE / "e8b_inference_tflite.py"), str(liste), str(sortie)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(r.stderr[-3000:])
    z = np.load(sortie)
    assert (z["ids"] == d["id"].to_numpy().astype(str)).all()
    return {NOMS_TFLITE[k]: z[k] for k in z.files if k in NOMS_TFLITE}


def main():
    C.fixer_graine()
    df = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    d = df[df["split"].isin(["valid", "test"])].reset_index(drop=True)
    E = charger_embeddings(d)

    P = {}
    for nom in ["CLIP_all", "CLIP_reel"]:
        P[nom] = joblib.load(C.MODELES / f"{nom}.joblib").predict_proba(E)
    for nom in ["B1", "B2"]:
        P[nom] = joblib.load(C.MODELES / f"{nom}.joblib").predict_proba(d)
    P["MobileNet_PyTorch"] = proba_mobilenet(d)
    P["ONNX"] = proba_onnx(d)
    P.update(proba_tflite(d))

    # predictions.csv
    lignes = []
    for nom, pr in P.items():
        lignes.append(pd.DataFrame({"id": d["id"], "split": d["split"], "classe_vraie": d["classe"],
                                    "classe_predite": [C.NOMS[k] for k in pr.argmax(1)],
                                    **{f"p_{c}": pr[:, k].round(6) for k, c in enumerate(C.NOMS)}, "modele": nom}))
    pd.concat(lignes).to_csv(C.RESULTATS / "predictions.csv", index=False, encoding="utf-8")

    y = d["y"].to_numpy()
    g = d["groupe_lieu_v2"].to_numpy()
    R = {"note": "test évalué une seule fois, à cette étape ; macro-F1 sur les classes présentes dans le sous-ensemble",
         "bootstrap": "par groupe_lieu_v2, 1000 rééchantillons, graine 42, IC percentiles 2,5-97,5",
         "resultats": {}}
    for split in ["valid", "test"]:
        ms = (d["split"] == split).to_numpy()
        S = sous_ensembles(d[ms].reset_index(drop=True), split)
        R["resultats"][split] = {}
        for nom, pr in P.items():
            pr_s, y_s, g_s = pr[ms], y[ms], g[ms]
            R["resultats"][split][nom] = {}
            for sous, m in S.items():
                if not m.any():
                    continue
                p = pr_s[m].argmax(1)
                r = metriques(y_s[m], p)
                r["ic95"] = bootstrap_groupes(y_s[m], p, g_s[m])
                r["deux_classes"] = metriques_2classes(y_s[m], pr_s[m])
                R["resultats"][split][nom][sous] = r
        print(split, {n: round(R["resultats"][split][n]["sous_reel"]["macro_f1"], 4) for n in P}, flush=True)
    C.ecrire_json(C.RESULTATS / "metriques.json", R)
    t = R["resultats"]["test"]
    for nom in P:
        r = t[nom]["sous_reel"]["ic95"]
        print(f"{nom:20s} test∩sous_reel macro-F1 {r['macro_f1']:.3f} {np.round(r['macro_f1_ic95'], 3)} "
              f"rappel PROPRE {r['rappel_propre']:.3f} {np.round(r['rappel_propre_ic95'], 3)} | sous_strict "
              f"{t[nom]['sous_strict']['macro_f1']:.3f}")


if __name__ == "__main__":
    main()
