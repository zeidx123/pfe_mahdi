"""Étape 0 : vérification de départ (effectifs, fichiers image, ids et sha256 des embeddings existants)."""
import hashlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
from PIL import Image

import config as C


def main():
    df = C.charger_csv()
    R = {"lignes_csv": len(df), "inclus_1": int((df["inclus"] == 1).sum())}

    # Effectifs
    ecarts = []
    obs = {}
    for nom, masque in [("tout", np.ones(len(df), bool)), ("sous_reel", df["sous_reel"].to_numpy())]:
        obs[nom] = {}
        for sp in ["train", "valid", "test"]:
            obs[nom][sp] = {}
            for cl in ["PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE"]:
                n = int(((df["split"] == sp) & (df["classe"] == cl) & masque).sum())
                obs[nom][sp][cl] = n
                if n != C.ATTENDU[nom][sp][cl]:
                    ecarts.append(f"{nom}/{sp}/{cl}: attendu {C.ATTENDU[nom][sp][cl]}, observé {n}")
    R["effectifs"] = obs
    R["ecarts_effectifs"] = ecarts

    # splits_figes cohérent avec le CSV
    import pandas as pd
    sf = pd.read_csv(C.SPLITS_FIGES, encoding="utf-8-sig").set_index("id")
    R["splits_figes_differents"] = int((sf.loc[df["id"], "split"].to_numpy() != df["split"].to_numpy()).sum())

    # Fichiers image : existence, ouverture, RGB 640x640, md5, sha256
    problemes, md5_diff = [], []
    sha = {}
    for i, p, m in zip(df["id"], df["chemin_abs"], df["md5"]):
        fp = Path(p)
        if not fp.exists():
            problemes.append(f"{i}: absent")
            continue
        b = fp.read_bytes()
        sha[i] = hashlib.sha256(b).hexdigest()
        if hashlib.md5(b).hexdigest() != m:
            md5_diff.append(i)
        try:
            with Image.open(fp) as im:
                im.load()
                if im.mode != "RGB" or im.size != (640, 640):
                    problemes.append(f"{i}: {im.mode} {im.size}")
        except Exception as e:  # noqa: BLE001
            problemes.append(f"{i}: {e!r}")
    R["images_problemes"] = problemes
    R["md5_differents"] = md5_diff

    # Embeddings existants
    ids_csv = set(df["id"])
    reel = set(df.loc[df["origine"] != "synthetique", "id"])
    vus, sha_diff, en_trop = {}, [], []
    for f in C.EMB_EXISTANTS:
        z = np.load(f, allow_pickle=False)
        for i, s in zip(z["ids"], z["sha"]):
            i = str(i)
            if i not in ids_csv:
                en_trop.append(i)
            elif sha[i] != str(s):
                sha_diff.append(i)
            else:
                vus[i] = f.name
    manquants = sorted(ids_csv - set(vus) - set(sha_diff))
    R["embeddings"] = {
        "fichiers": [str(f) for f in C.EMB_EXISTANTS],
        "ids_valides_reutilisables": len(vus),
        "ids_sha256_differents": len(sha_diff), "exemples_sha_diff": sha_diff[:10],
        "ids_en_trop_hors_csv": len(en_trop), "exemples_en_trop": en_trop[:10],
        "ids_manquants": len(manquants),
        "manquants_reels": len([m for m in manquants if m in reel]),
        "manquants_synthetiques": len([m for m in manquants if m not in reel]),
    }
    R["statut"] = "OK" if not ecarts and not problemes and not md5_diff else "ECHEC"
    C.ecrire_json(C.VERIF_DEPART, R)
    print({k: v for k, v in R.items() if k != "effectifs"})
    if ecarts:
        print("ARRÊT : effectifs différents", ecarts)
        sys.exit(1)


if __name__ == "__main__":
    main()
