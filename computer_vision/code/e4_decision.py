"""Étape 4 : choix du jeu d'entraînement du modèle mobile (règle fixée à l'avance).

Si macro-F1(CLIP_all) − macro-F1(CLIP_reel) > 0,02 sur valid ∩ sous_reel : tout train ; sinon train ∩ sous_reel.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import config as C

SEUIL = 0.02


def main():
    R = json.loads((C.RESULTATS / "references_valid.json").read_text(encoding="utf-8"))
    a = R["valid"]["CLIP_all"]["sous_reel"]["macro_f1"]
    r = R["valid"]["CLIP_reel"]["sous_reel"]["macro_f1"]
    ecart = a - r
    jeu = "train" if ecart > SEUIL else "train_sous_reel"
    D = {"macro_f1_valid_sous_reel": {"CLIP_all": a, "CLIP_reel": r}, "ecart_all_moins_reel": ecart,
         "seuil": SEUIL, "regle": "si écart > 0,02 : tout train ; sinon (égalité comprise) : train ∩ sous_reel",
         "decision": jeu,
         "note": "Le seuil de 0,02 est une proposition de départ, pas une valeur démontrée."}
    C.ecrire_json(C.DECISION, D)
    print(D)


if __name__ == "__main__":
    main()
