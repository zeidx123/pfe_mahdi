"""Étape 2 de l'intégration : construit le jeu de cohérence portable et ses références.

À lancer avec l'environnement d'entraînement (computer_vision\\.venv), depuis n'importe quel dossier :
    .venv\\Scripts\\python integration\\construire_jeu_coherence.py

- 30 images du split VALID (10 par vraie classe), tirées avec la graine 42 dans resultats/predictions.csv ;
- 3 images tournées de 90 degrés (PNG sans perte + balise EXIF d'orientation 6 ou 8) : après
  ImageOps.exif_transpose, elles sont identiques pixel pour pixel à l'image droite d'origine ;
- reference_coherence.json : probabilités de référence = export/exemple_pretraitement.py + export ONNX,
  calculées sur les fichiers ORIGINAUX droits ; SHA-256 de chaque fichier copié ;
- reference_qualite.json : netteté 224 et luminosité de code/e7_qualite_image.mesures (fichiers originaux) ;
- service/exemples : 3 images (une par classe) et leurs probabilités attendues, pour test_service.py ;
- exif_dataset.json : proportion d'images du dataset dont la balise d'orientation EXIF est différente de 1 ;
- exif_sans_transpose.json : pour information, écart si exif_transpose n'était PAS appliqué.
Le dataset est lu sans être modifié.
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import pandas as pd
from PIL import Image, ImageOps

CV = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CV))
sys.path.insert(0, str(CV / "code"))
sys.path.insert(0, str(CV / "export"))
sys.path.insert(0, str(CV / "service"))
import config as C  # noqa: E402
from e7_qualite_image import mesures  # noqa: E402
from exemple_pretraitement import CLASSES, pretraiter  # noqa: E402
import app  # noqa: E402

KIT = CV / "integration" / "kit_tests_autre_pc"
JEU = KIT / "jeu_coherence"
EXEMPLES = CV / "service" / "exemples"
ROTATIONS = [(6, Image.ROTATE_90), (8, Image.ROTATE_270), (6, Image.ROTATE_90)]  # exif_transpose(6) = ROTATE_270


def sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def ecrire(p, obj):
    Path(p).write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    for d in (JEU, EXEMPLES):
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True)
    pr = pd.read_csv(C.RESULTATS / "predictions.csv", encoding="utf-8")
    pr = pr[(pr["modele"] == "ONNX") & (pr["split"] == "valid")]
    ds = pd.read_csv(C.CSV, encoding="utf-8-sig").set_index("id")
    choix = pd.concat([pr[pr["classe_vraie"] == c].sample(10, random_state=C.GRAINE) for c in
                       ["PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE"]])
    sess = ort.InferenceSession(str(C.EXPORT / "proprete_mobilenetv3l.onnx"), providers=["CPUExecutionProvider"])
    entree = sess.get_inputs()[0].name

    ref, qual, origines = [], [], {}
    for _, r in choix.iterrows():
        src = C.DATASET / ds.loc[r["id"], "chemin_image"]
        dst = JEU / f"{r['id']}{src.suffix.lower()}"
        shutil.copyfile(src, dst)
        p = sess.run(None, {entree: pretraiter(src)})[0][0]
        n224, _, lum = mesures(src)
        e = {"id": r["id"], "fichier": dst.name, "classe_vraie": r["classe_vraie"], "type": "original",
             "probabilites_reference": {c: float(v) for c, v in zip(CLASSES, p)},
             "classe_reference": CLASSES[int(np.argmax(p))], "sha256": sha256(dst)}
        ref.append(e)
        qual.append({"id": r["id"], "fichier": dst.name, "nettete_224": float(n224), "luminosite": float(lum)})
        origines[r["id"]] = (src, e, qual[-1])

    # 3 images tournées : une par classe (la première tirée de chaque classe)
    sans_exif = []
    for k, c in enumerate(["PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE"]):
        rid = choix[choix["classe_vraie"] == c].iloc[0]["id"]
        src, e, q = origines[rid]
        orientation, transformation = ROTATIONS[k]
        with Image.open(src) as im:
            droite = im.convert("RGB")
        tournee = droite.transpose(transformation)
        exif = Image.Exif()
        exif[app.ORIENTATION_EXIF] = orientation
        dst = JEU / f"{rid}_tourne_exif{orientation}.png"
        tournee.save(dst, format="PNG", exif=exif.tobytes())
        with Image.open(dst) as im:
            assert im.getexif().get(app.ORIENTATION_EXIF) == orientation
            assert np.array_equal(np.asarray(ImageOps.exif_transpose(im).convert("RGB")), np.asarray(droite)), dst
            x_sans, _, _, _ = app.pretraiter_image(im, appliquer_exif=False)
        p_sans = sess.run(None, {entree: x_sans})[0][0]
        p_ref = np.array([e["probabilites_reference"][c2] for c2 in CLASSES])
        sans_exif.append({"fichier": dst.name, "orientation": orientation,
                          "ecart_max_sans_exif_transpose": float(np.abs(p_sans - p_ref).max()),
                          "classe_sans_exif": CLASSES[int(np.argmax(p_sans))], "classe_reference": e["classe_reference"]})
        ref.append({**e, "fichier": dst.name, "type": f"tourne_exif{orientation}", "original": e["fichier"],
                    "sha256": sha256(dst)})
        qual.append({**q, "fichier": dst.name})

    meta = {"note": "Références calculées sur les fichiers ORIGINAUX droits avec export/exemple_pretraitement.py "
                    "et export/proprete_mobilenetv3l.onnx (onnxruntime CPU). Pour les images tournées, la référence "
                    "est celle de l'image droite d'origine.",
            "onnxruntime": ort.__version__, "modele_sha256": sha256(C.EXPORT / "proprete_mobilenetv3l.onnx"),
            "graine": C.GRAINE, "source": "resultats/predictions.csv, modele=ONNX, split=valid"}
    ecrire(JEU / "reference_coherence.json", {**meta, "images": ref})
    ecrire(JEU / "reference_qualite.json", {"note": "code/e7_qualite_image.mesures sur les fichiers originaux "
                                                    "(netteté sur 224x224 étiré, luminosité sur l'image entière)",
                                            "images": qual})
    ecrire(CV / "integration" / "exif_sans_transpose.json", sans_exif)

    # exemples du service (une image par classe, originaux droits)
    attendus = []
    for c in ["PROPRE", "DEBORDEMENT", "CANNOT_DETERMINE"]:
        e = next(x for x in ref if x["classe_vraie"] == c and x["type"] == "original")
        shutil.copyfile(JEU / e["fichier"], EXEMPLES / e["fichier"])
        attendus.append({"fichier": e["fichier"], "probabilites": e["probabilites_reference"]})
    ecrire(EXEMPLES / "attendus.json", attendus)

    # proportion d'images à balise d'orientation EXIF différente de 1 (lecture seule)
    n, n_diff, par_valeur = 0, 0, {}
    for f in sorted((C.DATASET / "images").rglob("*")):
        if not f.is_file():
            continue
        try:
            with Image.open(f) as im:
                o = im.getexif().get(app.ORIENTATION_EXIF, 1)
        except Exception:  # noqa: BLE001
            o = "illisible"
        n += 1
        if o != 1:
            n_diff += 1
            par_valeur[str(o)] = par_valeur.get(str(o), 0) + 1
    ecrire(CV / "integration" / "exif_dataset.json",
           {"n_fichiers": n, "n_orientation_differente_de_1": n_diff, "proportion": n_diff / max(n, 1),
            "par_valeur": par_valeur})
    print(f"{len(ref)} images ; EXIF ≠ 1 : {n_diff}/{n} ; sans exif_transpose : {sans_exif}")


if __name__ == "__main__":
    main()
