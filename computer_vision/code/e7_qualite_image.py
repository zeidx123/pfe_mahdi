"""Étape 7 : règle de qualité d'image, indépendante du modèle. Aucun seuil n'est choisi.

Netteté = variance du Laplacien (OpenCV, CV_64F) sur le niveau de gris, calculée sur l'image 224x224 étirée
(même redimensionnement que le modèle : PIL bilinéaire) ET sur l'image 640x640.
Luminosité = moyenne du niveau de gris (0-255), sur 640x640.
Seuils de netteté : percentiles 1 à 20 (pas de 1) des PROPRE et DEBORDEMENT réels (sous_reel) de TRAIN.
Seuils de luminosité : 20, 30, 40, 50, 60.
Sur valid : % des CANNOT_DETERMINE synthétiques détectées, % des PROPRE / DEBORDEMENT de sous_reel
faussement détectés. Optimiste : les synthétiques sont fabriquées par flou, nuit et recadrage.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import cv2
import numpy as np
import pandas as pd
from PIL import Image

import config as C


def mesures(chemin):
    with Image.open(chemin) as im:
        im = im.convert("RGB")
        g640 = cv2.cvtColor(np.asarray(im), cv2.COLOR_RGB2GRAY)
        g224 = cv2.cvtColor(np.asarray(im.resize((C.TAILLE, C.TAILLE), Image.BILINEAR)), cv2.COLOR_RGB2GRAY)
    return (cv2.Laplacian(g224, cv2.CV_64F).var(), cv2.Laplacian(g640, cv2.CV_64F).var(), float(g640.mean()))


def main():
    df = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    op = C.charger_csv().set_index("id")["operation"]
    df["operation"] = df["id"].map(op)
    synth = df["origine"] == "synthetique"
    reel_pd = df["sous_reel"] & ~synth & df["classe"].isin(["PROPRE", "DEBORDEMENT"])
    utile = ((df["split"] == "train") & reel_pd) | (df["split"] == "valid")
    d = df[utile].copy()
    v = np.array([mesures(C.DATASET / c) for c in d["chemin_image"]])
    d["nettete_224"], d["nettete_640"], d["luminosite"] = v[:, 0], v[:, 1], v[:, 2]
    d[["id", "split", "classe", "origine", "operation", "nettete_224", "nettete_640", "luminosite"]].to_csv(
        C.DERIVEES / "qualite_image_mesures.csv", index=False, encoding="utf-8")

    tr_ref = d[(d["split"] == "train") & reel_pd.loc[d.index]]
    va = d[d["split"] == "valid"]
    cd = va[va["origine"] == "synthetique"]
    fp = va[reel_pd.loc[va.index]]
    R = {"n_reference_train": len(tr_ref), "n_cd_synth_valid": len(cd), "n_reel_pd_valid": len(fp),
         "n_cd_par_operation_valid": cd["operation"].value_counts().to_dict(),
         "note": "Test optimiste : les CANNOT_DETERMINE sont fabriquées par flou, assombrissement et recadrage."}

    for res in ["224", "640"]:
        col = f"nettete_{res}"
        lignes = []
        for q in range(1, 21):
            s = float(np.percentile(tr_ref[col], q))
            ligne = {"percentile": q, "seuil": s,
                     "cd_detectees_pct": 100 * float((cd[col] < s).mean()),
                     "reel_faux_pct": 100 * float((fp[col] < s).mean())}
            for o, g in cd.groupby("operation"):
                ligne[f"cd_{o}_pct"] = 100 * float((g[col] < s).mean())
            lignes.append(ligne)
        R[f"nettete_{res}"] = lignes
    R["luminosite"] = []
    for s in [20, 30, 40, 50, 60]:
        ligne = {"seuil": s, "cd_detectees_pct": 100 * float((cd["luminosite"] < s).mean()),
                 "reel_faux_pct": 100 * float((fp["luminosite"] < s).mean())}
        for o, g in cd.groupby("operation"):
            ligne[f"cd_{o}_pct"] = 100 * float((g["luminosite"] < s).mean())
        R["luminosite"].append(ligne)
    # Combinaison (netteté 224 < seuil OU luminosité < seuil), pour information
    R["combinaison_nettete224_ou_luminosite"] = []
    for q in [1, 5, 10, 15, 20]:
        s = float(np.percentile(tr_ref["nettete_224"], q))
        for l in [20, 30, 40, 50, 60]:
            dc = (cd["nettete_224"] < s) | (cd["luminosite"] < l)
            df_ = (fp["nettete_224"] < s) | (fp["luminosite"] < l)
            R["combinaison_nettete224_ou_luminosite"].append(
                {"percentile_nettete": q, "seuil_nettete": s, "seuil_luminosite": l,
                 "cd_detectees_pct": 100 * float(dc.mean()), "reel_faux_pct": 100 * float(df_.mean())})
    C.ecrire_json(C.RESULTATS / "qualite_image.json", R)
    print(pd.DataFrame(R["nettete_224"]).round(1).to_string())
    print(pd.DataFrame(R["nettete_640"]).round(1).to_string())
    print(pd.DataFrame(R["luminosite"]).round(1).to_string())


if __name__ == "__main__":
    main()
