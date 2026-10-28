"""Étape 6 : export pour Flutter (ONNX, puis TFLite via e6b_tflite.py dans .venv_tflite), cohérence, fiche.

Graphe exporté : probabilités = softmax(logits / T), température INCLUSE dans le graphe.
Entrée [1, 3, 224, 224] float32 déjà normalisée (normalisation hors du modèle).
Cohérence : 300 images de VALID (graine 42). Calibration int8 : 300 images de TRAIN (jeu retenu, graine 42).
"""
import hashlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "export"))
import numpy as np
import onnx
import onnxruntime as ort
import pandas as pd
import torch
import torch.nn as nn

import config as C
from donnees_image import TF_EVAL, charger_rgb
from e5_entrainement import construire
from exemple_pretraitement import pretraiter

TMP = C.DERIVEES / "export_tmp"
ONNX = C.EXPORT / f"{C.NOM_MODELE}.onnx"
PY_TFLITE = C.CV / ".venv_tflite" / "Scripts" / "python.exe"


class AvecSoftmax(nn.Module):
    def __init__(self, m, t):
        super().__init__()
        self.m = m
        self.register_buffer("inv_t", torch.tensor(1.0 / t, dtype=torch.float32))

    def forward(self, x):
        return torch.softmax(self.m(x) * self.inv_t, dim=1)


def charger_final():
    ck = torch.load(C.MODELES / "mobilenet_final.pt", map_location="cpu")
    m = construire()
    m.load_state_dict(ck["state_dict"])
    t = json.loads((C.MODELES / "temperature.json").read_text())["temperature"]
    return AvecSoftmax(m.eval(), t).eval(), t, ck


def sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def latence(f, x, n=100):
    for _ in range(10):
        f(x)
    ts = []
    for _ in range(n):
        t0 = time.perf_counter()
        f(x)
        ts.append(time.perf_counter() - t0)
    return 1000 * float(np.median(ts))


def coherence(p, p_ref):
    d = np.abs(p - p_ref)
    return {"concordance_classes_pct": 100 * float((p.argmax(1) == p_ref.argmax(1)).mean()),
            "diff_proba_max": float(d.max()), "diff_proba_moyenne": float(d.mean())}


def main():
    C.fixer_graine()
    TMP.mkdir(exist_ok=True)
    torch.set_num_threads(os.cpu_count())
    df = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    decision = json.loads(C.DECISION.read_text(encoding="utf-8"))
    rng = np.random.default_rng(C.GRAINE)
    va = df[df["split"] == "valid"].reset_index(drop=True)
    tr = df[(df["split"] == "train") & (df["sous_reel"] if decision["decision"] == "train_sous_reel" else True)]
    tr = tr.reset_index(drop=True)
    iv = np.sort(rng.choice(len(va), 300, replace=False))
    it = np.sort(rng.choice(len(tr), 300, replace=False))
    xv = np.concatenate([pretraiter(C.DATASET / c) for c in va.loc[iv, "chemin_image"]])
    xc = np.concatenate([pretraiter(C.DATASET / c) for c in tr.loc[it, "chemin_image"]])
    # le prétraitement NumPy de référence = celui de l'entraînement (torchvision) ?
    ref = torch.stack([TF_EVAL(charger_rgb(C.DATASET / c)) for c in va.loc[iv[:50], "chemin_image"]]).numpy()
    ecart_pretraitement = float(np.abs(ref - xv[:50]).max())

    model, T, ck = charger_final()
    with torch.no_grad():
        p_torch = np.concatenate([model(torch.from_numpy(xv[k:k + 50])).numpy() for k in range(0, 300, 50)])
    np.savez(TMP / "coherence.npz", x_valid=xv, x_calib=xc, p_torch=p_torch,
             ids_valid=va.loc[iv, "id"].to_numpy().astype(str), ids_calib=tr.loc[it, "id"].to_numpy().astype(str))

    # ---- ONNX
    x0 = torch.from_numpy(xv[:1])
    erreur_onnx = None
    try:
        torch.onnx.export(model, (x0,), str(ONNX), input_names=["input"], output_names=["probabilites"],
                          opset_version=18, dynamo=False)
        mode_onnx = "torch.onnx.export (TorchScript, dynamo=False), opset 18"
    except Exception as e:  # noqa: BLE001
        erreur_onnx = repr(e)
        torch.onnx.export(model, (x0,), str(ONNX), input_names=["input"], output_names=["probabilites"],
                          opset_version=18, dynamo=True, external_data=False)
        mode_onnx = "torch.onnx.export (dynamo=True), opset 18"
    onnx.checker.check_model(onnx.load(str(ONNX)))
    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count()
    sess = ort.InferenceSession(str(ONNX), so, providers=["CPUExecutionProvider"])
    nom_e = sess.get_inputs()[0].name
    p_onnx = np.concatenate([sess.run(None, {nom_e: xv[k:k + 1]})[0] for k in range(300)])

    formats = {
        "pytorch": {"fichier": str((C.MODELES / "mobilenet_final.pt").relative_to(C.CV)),
                    "taille_mo": (C.MODELES / "mobilenet_final.pt").stat().st_size / 1e6,
                    "latence_mediane_cpu_ms": None,
                    "concordance_classes_pct": 100.0, "diff_proba_max": 0.0, "diff_proba_moyenne": 0.0},
        "onnx": {"fichier": str(ONNX.relative_to(C.CV)), "taille_mo": ONNX.stat().st_size / 1e6,
                 "sha256": sha256(ONNX), "outil": mode_onnx, "erreur_tentative_precedente": erreur_onnx,
                 "entree": {"nom": nom_e, "forme": sess.get_inputs()[0].shape, "type": sess.get_inputs()[0].type,
                            "disposition": "NCHW"},
                 "sortie": {"nom": sess.get_outputs()[0].name, "forme": sess.get_outputs()[0].shape,
                            "type": sess.get_outputs()[0].type},
                 "latence_mediane_cpu_ms": latence(lambda a: sess.run(None, {nom_e: a}), xv[:1]),
                 **coherence(p_onnx, p_torch)},
    }
    with torch.no_grad():
        formats["pytorch"]["latence_mediane_cpu_ms"] = latence(lambda a: model(a), x0)

    # ---- TFLite (environnement séparé)
    r = subprocess.run([str(PY_TFLITE), str(C.CODE / "e6b_tflite.py")], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    (C.EXPORT / "log_tflite.txt").write_text(r.stdout + "\n--- stderr ---\n" + r.stderr, encoding="utf-8")
    res_tfl = json.loads((TMP / "tflite_resultats.json").read_text(encoding="utf-8"))
    formats.update(res_tfl.get("formats", {}))

    journal = json.loads((C.MODELES / "journal_entrainement.json").read_text(encoding="utf-8"))
    card = {
        "nom": C.NOM_MODELE, "version": C.VERSION_MODELE,
        "tache": "classification d'image, une étiquette par photo de point de collecte",
        "architecture": "torchvision MobileNetV3-Large, tête remplacée par 3 sorties",
        "classes": C.CLASSES, "ordre_sorties": C.NOMS,
        "entree": {"taille": [C.TAILLE, C.TAILLE], "redimensionnement": "étirement direct en 224x224, SANS recadrage, "
                   "proportions non conservées, interpolation bilinéaire",
                   "ordre_canaux": "RGB", "valeurs": "float32, (pixel/255 - moyenne) / écart-type",
                   "normalisation": {"moyenne": C.MOYENNE, "ecart_type": C.ECART_TYPE,
                                     "faite_dans_le_modele": False},
                   "disposition_entree": {"onnx": "NCHW [1, 3, 224, 224]",
                                          **{k: v.get("entree", {}).get("disposition") for k, v in res_tfl.get("formats", {}).items()}},
                   "reference_pretraitement": "export/exemple_pretraitement.py",
                   "ecart_max_reference_numpy_vs_entrainement": ecart_pretraitement},
        "sortie": {"contenu": "3 probabilités (softmax) dans l'ordre de classes.json",
                   "temperature": T, "temperature_incluse_dans_le_graphe": True,
                   "note": "sortie = softmax(logits / T) ; ne pas réappliquer la température ni le softmax"},
        "formats": formats,
        "coherence": {"images": "300 images de VALID tirées avec la graine 42", "reference": "modèle PyTorch (avec T)",
                      "latence": "médiane sur le CPU de l'ordinateur d'entraînement, lot de 1 ; ce n'est PAS la latence d'un téléphone"},
        "conversion_tflite": res_tfl.get("tentatives"),
        "quantification_int8": res_tfl.get("int8_info"),
        "entrainement": {"jeu": decision["decision"], "n_images": journal["n_train"],
                         "pourquoi": f"règle fixée à l'avance : macro-F1 valid ∩ sous_reel CLIP_all − CLIP_reel = "
                                     f"{decision['ecart_all_moins_reel']:.4f} ≤ 0,02 → train ∩ sous_reel "
                                     "(seuil 0,02 = proposition de départ, non démontrée)",
                         "lr_retenu": ck["lr"], "epoque_retenue": ck["epoque"], "graine": C.GRAINE,
                         "poids_pretraines": "torchvision MobileNet_V3_Large_Weights.IMAGENET1K_V2 "
                                             "(download.pytorch.org ; code torchvision BSD-3-Clause ; poids appris sur ImageNet-1k, "
                                             "dont les conditions d'utilisation sont réservées à la recherche non commerciale)"},
        "donnees": {"sources": ["Kaggle / Roboflow (CC BY 4.0, attribution requise)", "kabam / garbage_detection (CC BY 4.0, attribution requise)",
                                "GBS (CC BY 4.0, attribution requise)", "images synthétiques CANNOT_DETERMINE dérivées des précédentes"],
                    "licence": "CC BY 4.0 : attribution requise pour Kaggle/Roboflow, kabam et GBS"},
        "avertissement": "Étiquettes en partie extrapolées à partir de boîtes englobantes, classe CANNOT_DETERMINE "
                         "synthétique, images surtout issues du web chinois ; non validé sur le terrain.",
    }
    C.ecrire_json(C.EXPORT / "model_card.json", card)
    print(json.dumps({k: {kk: vv for kk, vv in v.items() if kk in ("taille_mo", "concordance_classes_pct",
                     "diff_proba_max", "latence_mediane_cpu_ms")} for k, v in formats.items()}, indent=1))
    print("tentatives", res_tfl.get("tentatives"))


if __name__ == "__main__":
    main()
