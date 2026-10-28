"""Étape 9 : planches d'erreurs sur test ∩ sous_reel, modèle final (MobileNet PyTorch, probabilités étalonnées).

Grilles 5 colonnes x 4 lignes, miniatures 160 px, avec id, vrai, prédit, probabilité de la classe prédite :
  1. les 20 erreurs les plus confiantes PROPRE prédit DEBORDEMENT ;
  2. les 20 erreurs les plus confiantes DEBORDEMENT prédit PROPRE ;
  3. 20 PROPRE bien classées tirées au hasard (graine 42).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pandas as pd
from PIL import Image, ImageDraw, ImageFont

import config as C

MODELE = "MobileNet_PyTorch"
COTE, BANDE, COLS, LIGNES = 160, 46, 5, 4


def police(t):
    for f in ["arial.ttf", "DejaVuSans.ttf"]:
        try:
            return ImageFont.truetype(f, t)
        except OSError:
            pass
    return ImageFont.load_default()


def planche(d, titre, fichier):
    f, ft = police(11), police(14)
    W, H = COLS * COTE, LIGNES * (COTE + BANDE) + 26
    img = Image.new("RGB", (W, H), "white")
    dr = ImageDraw.Draw(img)
    dr.text((6, 5), f"{titre} (n = {len(d)})", fill="black", font=ft)
    for k, (_, r) in enumerate(d.iterrows()):
        x, y = (k % COLS) * COTE, 26 + (k // COLS) * (COTE + BANDE)
        with Image.open(C.DATASET / r["chemin_image"]) as im:
            img.paste(im.convert("RGB").resize((COTE, COTE), Image.BILINEAR), (x, y))
        p = r[f"p_{r['classe_predite']}"]
        dr.text((x + 3, y + COTE + 2), str(r["id"]), fill="black", font=f)
        dr.text((x + 3, y + COTE + 16), f"vrai {r['classe_vraie'][:5]}  préd {r['classe_predite'][:5]}", fill="black", font=f)
        dr.text((x + 3, y + COTE + 30), f"p = {p:.3f}", fill="firebrick" if r["classe_vraie"] != r["classe_predite"] else "darkgreen", font=f)
    img.save(C.PLANCHES / fichier)


def main():
    pr = pd.read_csv(C.RESULTATS / "predictions.csv", encoding="utf-8")
    se = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    d = pr[(pr["modele"] == MODELE) & (pr["split"] == "test")].merge(se[["id", "chemin_image", "sous_reel"]], on="id")
    d = d[d["sous_reel"]]
    pd_ = d[(d.classe_vraie == "PROPRE") & (d.classe_predite == "DEBORDEMENT")].sort_values("p_DEBORDEMENT", ascending=False)
    dp = d[(d.classe_vraie == "DEBORDEMENT") & (d.classe_predite == "PROPRE")].sort_values("p_PROPRE", ascending=False)
    ok = d[(d.classe_vraie == "PROPRE") & (d.classe_predite == "PROPRE")]
    ok = ok.sample(min(20, len(ok)), random_state=C.GRAINE)
    planche(pd_.head(20), "test ∩ sous_reel : PROPRE prédit DEBORDEMENT, les plus confiantes", "01_propre_predit_debordement.png")
    planche(dp.head(20), "test ∩ sous_reel : DEBORDEMENT prédit PROPRE, les plus confiantes", "02_debordement_predit_propre.png")
    planche(ok, "test ∩ sous_reel : PROPRE bien classées (tirage graine 42)", "03_propre_bien_classees.png")
    resume = {"erreurs_propre_predit_debordement": len(pd_), "erreurs_debordement_predit_propre": len(dp),
              "ids_01": pd_.head(20)["id"].tolist(), "ids_02": dp.head(20)["id"].tolist(), "ids_03": ok["id"].tolist()}
    C.ecrire_json(C.PLANCHES / "planches.json", resume)
    print(resume)


if __name__ == "__main__":
    main()
