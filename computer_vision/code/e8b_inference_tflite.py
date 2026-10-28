"""Étape 8b (exécuté avec .venv_tflite) : probabilités des modèles TFLite sur une liste d'images.

Usage : python e8b_inference_tflite.py liste.csv sortie.npz  (liste.csv : colonnes id, chemin)
Prétraitement : export/exemple_pretraitement.py (référence de l'application), disposition NHWC.
"""
import csv
import os
import sys
from pathlib import Path

CV = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CV / "export"))
import numpy as np
from exemple_pretraitement import pretraiter

from e6b_tflite import interprete, executer


def main():
    liste, sortie = sys.argv[1], sys.argv[2]
    with open(liste, encoding="utf-8") as f:
        lignes = list(csv.DictReader(f))
    modeles = sorted((CV / "export").glob("*.tflite"))
    its = {m.stem: interprete(m)[0] for m in modeles}
    res = {k: [] for k in its}
    for l in lignes:
        x_nhwc = pretraiter(l["chemin"], "NHWC")
        for k, it in its.items():
            shape = list(it.get_input_details()[0]["shape"])
            x = x_nhwc if shape == [1, 224, 224, 3] else x_nhwc.transpose(0, 3, 1, 2)
            res[k].append(executer(it, x)[0])
    np.savez(sortie, ids=np.array([l["id"] for l in lignes]), **{k: np.array(v, dtype=np.float32) for k, v in res.items()})
    print({k: len(v) for k, v in res.items()})


if __name__ == "__main__":
    sys.path.insert(0, str(CV / "code"))
    main()
