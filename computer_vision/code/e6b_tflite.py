"""Étape 6b (exécuté avec .venv_tflite) : conversion TFLite et cohérence.

Ordre des tentatives : (a) PyTorch -> TFLite direct (litert-torch / ai-edge-torch) ; (b) ONNX -> TFLite (onnx2tf).
Si (b) réussit : float32 et float16 (onnx2tf), int8 (TFLiteConverter sur le SavedModel produit par onnx2tf,
quantification post-entraînement, 300 images de TRAIN, graine 42, entrée/sortie float32).
Lit donnees_derivees/export_tmp/coherence.npz (écrit par e6_export.py), écrit tflite_resultats.json.
"""
import hashlib
import json
import os
import shutil
import sys
import time
import traceback
from pathlib import Path

CV = Path(__file__).resolve().parents[1]
TMP = CV / "donnees_derivees" / "export_tmp"
EXPORT = CV / "export"
ONNX = EXPORT / "proprete_mobilenetv3l.onnx"
NOM = "proprete_mobilenetv3l"

import numpy as np


def sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def interprete(chemin):
    try:
        from ai_edge_litert.interpreter import Interpreter
        lib = "ai_edge_litert"
    except ImportError:
        from tensorflow.lite import Interpreter
        lib = "tensorflow.lite"
    it = Interpreter(model_path=str(chemin), num_threads=os.cpu_count())
    it.allocate_tensors()
    return it, lib


def executer(it, x):
    e, s = it.get_input_details()[0], it.get_output_details()[0]
    it.set_tensor(e["index"], x)
    it.invoke()
    return it.get_tensor(s["index"])


def evaluer(chemin, xv, p_ref):
    it, lib = interprete(chemin)
    e, s = it.get_input_details()[0], it.get_output_details()[0]
    nhwc = list(e["shape"]) == [1, 224, 224, 3]
    X = xv.transpose(0, 2, 3, 1) if nhwc else xv
    p = np.concatenate([executer(it, X[k:k + 1]) for k in range(len(X))]).astype(np.float32)
    for _ in range(10):
        executer(it, X[:1])
    ts = []
    for _ in range(100):
        t0 = time.perf_counter()
        executer(it, X[:1])
        ts.append(time.perf_counter() - t0)
    d = np.abs(p - p_ref)
    return {"fichier": str(Path(chemin).relative_to(CV)), "taille_mo": Path(chemin).stat().st_size / 1e6,
            "sha256": sha256(chemin), "interpreteur": lib,
            "entree": {"forme": [int(v) for v in e["shape"]], "type": str(np.dtype(e["dtype"])),
                       "disposition": "NHWC" if nhwc else "NCHW", "quantification": [float(v) for v in e["quantization"]]},
            "sortie": {"forme": [int(v) for v in s["shape"]], "type": str(np.dtype(s["dtype"])),
                       "quantification": [float(v) for v in s["quantization"]]},
            "latence_mediane_cpu_ms": 1000 * float(np.median(ts)),
            "concordance_classes_pct": 100 * float((p.argmax(1) == p_ref.argmax(1)).mean()),
            "diff_proba_max": float(d.max()), "diff_proba_moyenne": float(d.mean())}


def diagnostic_int8(tf, saved_model, xc, xv, p_ref):
    """Quantification refaite avec TFLiteConverter (même calibration) + QuantizationDebugger par couche."""
    import io
    import pandas as pd
    xc_nhwc = xc.transpose(0, 2, 3, 1).astype(np.float32)
    rep = lambda: ([xc_nhwc[k:k + 1]] for k in range(len(xc_nhwc)))
    conv = tf.lite.TFLiteConverter.from_saved_model(str(saved_model))
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.representative_dataset = rep
    conv.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    f = TMP / "int8_tfliteconverter.tflite"
    f.write_bytes(conv.convert())
    r_conv = evaluer(f, xv, p_ref)
    rep_dbg = lambda: ([xc_nhwc[k:k + 1]] for k in range(0, len(xc_nhwc), 3))
    conv.representative_dataset = rep_dbg
    dbg = tf.lite.experimental.QuantizationDebugger(converter=conv, debug_dataset=rep_dbg)
    dbg.run()
    buf = io.StringIO()
    dbg.layer_statistics_dump(buf)
    buf.seek(0)
    d = pd.read_csv(buf)
    d["rmse_sur_echelle"] = np.sqrt(d["mean_squared_error"]) / d["scale"]
    d.sort_values("rmse_sur_echelle", ascending=False).to_csv(CV / "resultats" / "int8_diagnostic_couches.csv",
                                                              index=False, encoding="utf-8")
    pires = d.sort_values("rmse_sur_echelle", ascending=False).head(6)
    return {"tfliteconverter_meme_calibration": {"concordance_classes_pct": r_conv["concordance_classes_pct"],
                                                 "diff_proba_max": r_conv["diff_proba_max"]},
            "n_couches": int(len(d)), "couches_rmse_sur_echelle_sup_0_7": int((d["rmse_sur_echelle"] > 0.7).sum()),
            "pires_couches": [{"op": o, "tenseur": t[:90], "rmse_sur_echelle": float(v)}
                              for o, t, v in zip(pires["op_name"], pires["tensor_name"], pires["rmse_sur_echelle"])],
            "lecture": "les couches les plus dégradées sont les convolutions 1x1 suivies de ReLU des blocs "
                       "squeeze-excitation de MobileNetV3 ; la même perte apparaît avec TFLiteConverter seul, "
                       "la calibration d'onnx2tf n'est donc pas en cause",
            "fichier": "resultats/int8_diagnostic_couches.csv"}


def main():
    z = np.load(TMP / "coherence.npz")
    xv, xc, p_ref = z["x_valid"], z["x_calib"], z["p_torch"]
    R = {"tentatives": [], "formats": {}}

    # (a) conversion directe
    ok_a = False
    for mod in ["litert_torch", "ai_edge_torch"]:
        try:
            __import__(mod)
            R["tentatives"].append({"outil": mod, "resultat": "importable (non utilisé : voir b)"})
        except Exception as e:  # noqa: BLE001
            R["tentatives"].append({"outil": mod, "resultat": "échec", "erreur": repr(e),
                                    "cause_installation": "pip install litert-torch / ai-edge-torch : ResolutionImpossible ; "
                                    "litert-converter (litert-torch 0.9.x/0.10.0) et ai-edge-tensorflow (0.8.0) n'ont "
                                    "aucune distribution pour Windows / Python 3.13 (voir export/log_install_litert.txt "
                                    "et export/log_install_aiedgetorch.txt)"})
    # (b) onnx2tf
    if not ok_a:
        import onnx2tf
        import tensorflow as tf
        version = f"onnx2tf {onnx2tf.__version__} (TensorFlow {tf.__version__})"
        np.save(TMP / "calib_nhwc.npy", xc.transpose(0, 2, 3, 1).astype(np.float32))
        # b1 : backend par défaut (flatbuffer_direct) -- diagnostic
        s1 = TMP / "onnx2tf_flatbuffer_direct"
        try:
            if s1.exists():
                shutil.rmtree(s1)
            onnx2tf.convert(input_onnx_file_path=str(ONNX), output_folder_path=str(s1),
                            copy_onnx_input_output_names_to_tflite=True, non_verbose=True)
            charge = {}
            for f in sorted(s1.glob("*.tflite")):
                try:
                    interprete(f)
                    charge[f.name] = "s'exécute"
                except Exception as e:  # noqa: BLE001
                    charge[f.name] = f"ne s'exécute pas : {e!r}"
            R["tentatives"].append({"outil": version + ", backend flatbuffer_direct (défaut)", "resultat":
                                    "conversion faite, mais float16 à entrée/activations float16 non exécutable sur CPU "
                                    "et aucun SavedModel produit (int8 calibré impossible) : non livré",
                                    "fichiers": charge})
        except Exception as e:  # noqa: BLE001
            R["tentatives"].append({"outil": version + ", backend flatbuffer_direct", "resultat": "échec",
                                    "erreur": repr(e)})
        # b2 : backend tf_converter (convertisseur TFLite officiel), float32 / float16 / int8 calibré
        sortie = TMP / "onnx2tf"
        try:
            if sortie.exists():
                shutil.rmtree(sortie)
            onnx2tf.convert(input_onnx_file_path=str(ONNX), output_folder_path=str(sortie),
                            copy_onnx_input_output_names_to_tflite=True, non_verbose=True,
                            tflite_backend="tf_converter", output_signaturedefs=True,
                            output_integer_quantized_tflite=True, quant_type="per-channel",
                            custom_input_op_name_np_data_path=[["input", str(TMP / "calib_nhwc.npy"),
                                                                np.zeros((1, 1, 1, 3), np.float32),
                                                                np.ones((1, 1, 1, 3), np.float32)]])
            R["tentatives"].append({"outil": version + ", backend tf_converter", "resultat": "réussi",
                                    "fichiers_produits": sorted(f.name for f in sortie.glob("*.tflite"))})
            fichiers = {}
            for suf, cle in [("float32", "tflite_float32"), ("float16", "tflite_float16"),
                             ("integer_quant", "tflite_int8")]:
                src = sortie / f"{NOM}_{suf}.tflite"
                dst = EXPORT / f"{NOM}_{cle.split('_')[1]}.tflite"
                shutil.copyfile(src, dst)
                fichiers[cle] = dst
            R["int8_info"] = {"mode": "onnx2tf integer_quant (TFLiteConverter, quantification post-entraînement "
                                      "par canal, poids et activations int8, entrée/sortie float32)",
                              "calibration": "300 images de TRAIN (jeu d'entraînement retenu : train ∩ sous_reel), "
                                             "graine 42, déjà normalisées (moyenne 0 / écart-type 1 passés à onnx2tf)",
                              "ids_calibration_10_premiers": z["ids_calib"].tolist()[:10]}
            R["float16_info"] = "onnx2tf float16 (backend tf_converter) : poids float16, entrée/sortie float32"
            for k, f in fichiers.items():
                R["formats"][k] = evaluer(f, xv, p_ref)
            if R["formats"]["tflite_int8"]["concordance_classes_pct"] < 95:
                R["int8_info"]["avertissement"] = ("NON UTILISABLE EN L'ÉTAT : concordance des classes avec PyTorch "
                                                   f"{R['formats']['tflite_int8']['concordance_classes_pct']:.1f} %. "
                                                   "Utiliser float16 ou float32.")
                R["int8_info"]["diagnostic"] = diagnostic_int8(tf, sortie, xc, xv, p_ref)
        except Exception as e:  # noqa: BLE001
            R["tentatives"].append({"outil": version + ", backend tf_converter", "resultat": "échec",
                                    "erreur": repr(e), "trace": traceback.format_exc()[-3000:]})
    (TMP / "tflite_resultats.json").write_text(json.dumps(R, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(R, ensure_ascii=False, indent=1)[:3000])


if __name__ == "__main__":
    main()
