"""Étape 2 : embeddings CLIP (openai/clip-vit-base-patch32) des 9 483 images.

Réutilise les caches existants quand id et sha256 correspondent ; calcule le reste (les 544 synthétiques)
avec le même calcul que les caches (CLIPProcessor puis visual_projection(pooler_output)) sur CPU.
Contrôle : 20 images réelles tirées au hasard sont recalculées et comparées au cache (cosinus).
Sortie : donnees_derivees/embeddings.npz (ids dans l'ordre du CSV, sha256, emb normalisés L2, source).
"""
import hashlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import torch
from PIL import Image
from transformers import CLIPModel, CLIPProcessor

import config as C
from commun import embeddings_existants, normaliser


def calculer(chemins, m, p):
    out = []
    with torch.no_grad():
        for b in range(0, len(chemins), 32):
            imgs = []
            for c in chemins[b:b + 32]:
                with Image.open(c) as im:
                    imgs.append(im.convert("RGB"))
            px = p(images=imgs, return_tensors="pt")["pixel_values"]
            out.append(m.visual_projection(m.vision_model(pixel_values=px).pooler_output).numpy())
    return np.concatenate(out).astype(np.float32)


def main():
    C.fixer_graine()
    torch.set_num_threads(4)
    torch.use_deterministic_algorithms(True)
    df = C.charger_csv()
    ex = embeddings_existants()
    sha = [hashlib.sha256(Path(c).read_bytes()).hexdigest() for c in df["chemin_abs"]]

    emb, source = {}, {}
    a_calc = []
    for i, s, c in zip(df["id"], sha, df["chemin_abs"]):
        if i in ex and ex[i][0] == s:
            emb[i], source[i] = ex[i][1], ex[i][2]
        else:
            a_calc.append((i, c))
    print("réutilisés", len(emb), "à calculer", len(a_calc))

    m = CLIPModel.from_pretrained(C.CLIP_MODELE).eval()
    p = CLIPProcessor.from_pretrained(C.CLIP_MODELE)
    if a_calc:
        E = calculer([c for _, c in a_calc], m, p)
        for (i, _), e in zip(a_calc, E):
            emb[i], source[i] = e, "calcule_e2"

    # contrôle de cohérence avec le cache
    rng = np.random.default_rng(C.GRAINE)
    reels = df.loc[df["origine"] != "synthetique"].reset_index(drop=True)
    k = rng.choice(len(reels), 20, replace=False)
    Ec = normaliser(calculer(reels.loc[k, "chemin_abs"].tolist(), m, p))
    Er = normaliser(np.stack([emb[i] for i in reels.loc[k, "id"]]))
    cos = (Ec * Er).sum(1)
    controle = {"n": 20, "cos_min": float(cos.min()), "cos_moyen": float(cos.mean())}

    ids = df["id"].to_numpy().astype(str)
    np.savez(C.EMBEDDINGS, ids=ids, sha=np.array(sha), emb=normaliser(np.stack([emb[i] for i in ids])),
             source=np.array([source[i] for i in ids]))
    res = {"total": len(ids), "reutilises": len(ids) - len(a_calc), "calcules": len(a_calc),
           "controle_recalcul_vs_cache": controle, "modele": C.CLIP_MODELE,
           "versions": {"torch": torch.__version__, "transformers": __import__("transformers").__version__}}
    C.ecrire_json(C.DERIVEES / "embeddings_info.json", res)
    print(res)


if __name__ == "__main__":
    main()
