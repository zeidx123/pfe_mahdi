"""Étape 1 : sous-ensembles d'évaluation, dont sous_strict.

sous_strict (valid et test seulement) : image réelle NON ia_probable dont la similarité cosinus CLIP
maximale avec n'importe quelle image réelle (= non synthétique, ia_probable comprises) de TRAIN est < 0,90.
Les synthétiques héritent du statut de leur parent. Aucune étiquette n'est utilisée dans le calcul.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import pandas as pd

import config as C
from commun import embeddings_existants, normaliser


def main():
    df = C.charger_csv()
    ex = embeddings_existants()
    reel = (df["origine"] != "synthetique").to_numpy()
    ids_reel = df.loc[reel, "id"].tolist()
    E = normaliser(np.stack([ex[i][1] for i in ids_reel])).astype(np.float64)
    sp = df.loc[reel, "split"].to_numpy()
    Etr = E[sp == "train"]

    sim_max = pd.Series(np.nan, index=df["id"])
    for s in ["valid", "test"]:
        Es = E[sp == s]
        sim_max.loc[np.array(ids_reel)[sp == s]] = (Es @ Etr.T).max(1)
    df["sim_max_train"] = sim_max.to_numpy()

    strict = pd.Series(False, index=df["id"])
    m = reel & df["split"].isin(["valid", "test"]).to_numpy() & ~df["ia_probable"].to_numpy() \
        & (df["sim_max_train"].to_numpy() < C.SEUIL_STRICT)
    strict.loc[df.loc[m, "id"]] = True
    synth = df["origine"] == "synthetique"
    df.loc[synth, "sim_max_train"] = sim_max.loc[df.loc[synth, "parent_id"]].to_numpy()
    df["sous_strict"] = strict.to_numpy()
    df.loc[synth, "sous_strict"] = strict.loc[df.loc[synth, "parent_id"]].to_numpy()
    df.loc[df["split"] == "train", "sous_strict"] = False

    cols = ["id", "chemin_image", "classe", "y", "split", "origine", "famille", "parent_id", "groupe_lieu_v2",
            "strate", "ia_probable", "sous_reel", "sous_gbs", "sous_kaggle_sacs", "sous_lot_2001_3000",
            "sous_synthetique", "sous_flag_catalogue", "nettete", "luminosite", "largeur_origine",
            "hauteur_origine", "fraction_fond_blanc", "flag_collage", "plage_2001_3000", "md5", "phash",
            "sim_max_train", "sous_strict"]
    df[cols].to_csv(C.SOUS_ENSEMBLES, index=False, encoding="utf-8")

    eff = {}
    for nom in ["sous_reel", "ia_probable", "sous_strict"]:
        t = df[df[nom]].groupby(["split", "classe"]).size().unstack(fill_value=0)
        eff[nom] = {s: t.loc[s].to_dict() for s in t.index}
    # proportion des réelles non ia de valid/test retenues dans sous_strict
    base = df[df["sous_reel"] & ~synth & df["split"].isin(["valid", "test"])]
    eff["part_reelles_non_ia_retenues"] = {s: float(base[base.split == s]["sous_strict"].mean()) for s in ["valid", "test"]}
    C.ecrire_json(C.DERIVEES / "effectifs_sous_ensembles.json", eff)
    print(eff)


if __name__ == "__main__":
    main()
