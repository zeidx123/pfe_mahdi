"""Test de cohérence entre le service local en marche et les références d'entraînement (bibliothèque standard).

    python test_coherence.py [--url http://127.0.0.1:8090] [--jeu jeu_coherence] [--sortie coherence_service.csv]

Critères (étape 2 de l'intégration) :
  - 30 images droites : écart absolu maximal <= 1e-5 par classe ET même classe prédite (100 %) ;
  - 3 images tournées avec balise EXIF : écart <= 1e-5 avec la référence de l'image DROITE d'origine ;
  - qualité : |netteté - référence| <= 1e-6 et |luminosité - référence| <= 1e-6 (reference_qualite.json).
Code de sortie 0 si tout passe, 1 sinon.
"""
import argparse
import csv
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

ICI = Path(__file__).resolve().parent
CLASSES = ["CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE"]
TOL_PROBA, TOL_QUALITE = 1e-5, 1e-6


def predire(url, chemin, jeton):
    octets = chemin.read_bytes()
    type_ = "image/png" if chemin.suffix.lower() == ".png" else "image/jpeg"
    entetes = {"Content-Type": type_}
    if jeton:
        entetes["X-Service-Token"] = jeton
    req = urllib.request.Request(url.rstrip("/") + "/predict", data=octets, headers=entetes, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def main(argv=None):
    a = argparse.ArgumentParser()
    a.add_argument("--url", default="http://127.0.0.1:8090")
    a.add_argument("--jeu", default=str(ICI / "jeu_coherence"))
    a.add_argument("--sortie", default=str(ICI / "resultats_autre_pc" / "coherence_service.csv"))
    args = a.parse_args(argv)
    jeu = Path(args.jeu)
    ref = json.loads((jeu / "reference_coherence.json").read_text(encoding="utf-8"))["images"]
    qual = {q["fichier"]: q for q in json.loads((jeu / "reference_qualite.json").read_text(encoding="utf-8"))["images"]}
    jeton = os.environ.get("LOCAL_VISION_TOKEN", "")
    lignes, echecs = [], []
    for e in ref:
        try:
            r = predire(args.url, jeu / e["fichier"], jeton)
        except (urllib.error.URLError, OSError) as exc:
            print(f"Service injoignable ou erreur pour {e['fichier']} : {exc}")
            return 1
        ecarts = {c: abs(r["probabilities"][c] - e["probabilites_reference"][c]) for c in CLASSES}
        e_max = max(ecarts.values())
        accord = r["predicted_class"] == e["classe_reference"]
        q = qual[e["fichier"]]
        e_net = abs(r["quality"]["sharpness"] - q["nettete_224"])
        e_lum = abs(r["quality"]["mean_brightness"] - q["luminosite"])
        ok = e_max <= TOL_PROBA and accord and e_net <= TOL_QUALITE and e_lum <= TOL_QUALITE
        if e["type"] != "original":
            ok = ok and r["exif_orientation_applied"]
        lignes.append({"fichier": e["fichier"], "type": e["type"], "classe_vraie": e["classe_vraie"],
                       "classe_reference": e["classe_reference"], "classe_service": r["predicted_class"],
                       "accord_argmax": accord, **{f"ecart_{c}": f"{ecarts[c]:.3e}" for c in CLASSES},
                       "ecart_max": f"{e_max:.3e}", "ecart_nettete": f"{e_net:.3e}", "ecart_luminosite": f"{e_lum:.3e}",
                       "exif_applique": r["exif_orientation_applied"], "conforme": ok})
        if not ok:
            echecs.append(e["fichier"])
    sortie = Path(args.sortie)
    sortie.parent.mkdir(parents=True, exist_ok=True)
    with open(sortie, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(lignes[0]))
        w.writeheader()
        w.writerows(lignes)
    droites = [l for l in lignes if l["type"] == "original"]
    tournees = [l for l in lignes if l["type"] != "original"]
    print(f"Images droites : {len(droites)}, accord argmax {sum(l['accord_argmax'] for l in droites)}/{len(droites)}, "
          f"écart max {max(float(l['ecart_max']) for l in droites):.3e}")
    print(f"Images tournées (EXIF) : {len(tournees)}, écart max {max(float(l['ecart_max']) for l in tournees):.3e}")
    print(f"Qualité : écart max netteté {max(float(l['ecart_nettete']) for l in lignes):.3e}, "
          f"luminosité {max(float(l['ecart_luminosite']) for l in lignes):.3e}")
    print(f"Résultat : {'CONFORME' if not echecs else 'NON CONFORME : ' + ', '.join(echecs)} ; détail dans {sortie}")
    return 0 if not echecs else 1


if __name__ == "__main__":
    sys.exit(main())
