"""Étape 3 : références (mesures, pas le modèle de l'application). Scores sur VALID uniquement.

- CLIP_all  : régression logistique multinomiale sur les embeddings CLIP de tout train.
- CLIP_reel : idem sur train ∩ sous_reel.
  C ∈ {0.01, 0.1, 1, 10, 100} choisi sur la macro-F1 de valid ∩ sous_reel.
- B1 : majorité par origine (classe la plus fréquente de train pour chaque origine).
- B2 : HistGradientBoostingClassifier sur les métadonnées seules.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression

import config as C
from commun import charger_embeddings, macro_f1, metriques

GRILLE_C = [0.01, 0.1, 1, 10, 100]
CAT_B2 = ["origine", "famille", "flag_collage", "plage_2001_3000"]
NUM_B2 = ["largeur_origine", "hauteur_origine", "nettete", "luminosite", "fraction_fond_blanc"]
MANQUANT_NUM = -1.0
MANQUANT_CAT = "__manquant__"


class MajoriteParOrigine:
    def fit(self, df):
        self.table = df.groupby("origine")["y"].agg(lambda s: int(s.value_counts().sort_index().idxmax())).to_dict()
        self.defaut = int(df["y"].value_counts().idxmax())
        return self

    def predict(self, df):
        return np.array([self.table.get(o, self.defaut) for o in df["origine"]])

    def predict_proba(self, df):
        P = np.zeros((len(df), 3))
        P[np.arange(len(df)), self.predict(df)] = 1.0
        return P


class MetaB2:
    """One-hot des catégories (manquant = modalité dédiée), numériques manquants = -1."""

    def _X(self, df):
        X = df[NUM_B2].astype(float).fillna(MANQUANT_NUM)
        for c in CAT_B2:
            v = df[c].astype(object).where(df[c].notna(), MANQUANT_CAT).astype(str)
            for mod in self.modalites[c]:
                X[f"{c}={mod}"] = (v == mod).astype(float)
        return X.to_numpy()

    def fit(self, df):
        self.modalites = {c: sorted(df[c].astype(object).where(df[c].notna(), MANQUANT_CAT).astype(str).unique())
                          for c in CAT_B2}
        self.clf = HistGradientBoostingClassifier(class_weight="balanced", random_state=C.GRAINE)
        self.clf.fit(self._X(df), df["y"].to_numpy())
        return self

    def predict_proba(self, df):
        return self.clf.predict_proba(self._X(df))

    def predict(self, df):
        return self.predict_proba(df).argmax(1)


def entrainer_clip(E, y, masque_tr, E_va, y_va):
    scores = {}
    for c in GRILLE_C:
        clf = LogisticRegression(C=c, class_weight="balanced", max_iter=10000, random_state=C.GRAINE)
        clf.fit(E[masque_tr], y[masque_tr])
        scores[c] = macro_f1(y_va, clf.predict(E_va))
    best = max(GRILLE_C, key=lambda c: (scores[c], -c))   # égalité : le C le plus petit (plus régularisé)
    clf = LogisticRegression(C=best, class_weight="balanced", max_iter=10000, random_state=C.GRAINE)
    clf.fit(E[masque_tr], y[masque_tr])
    return clf, best, scores


def main():
    C.fixer_graine()
    df = pd.read_csv(C.SOUS_ENSEMBLES, encoding="utf-8", dtype={"famille": str, "parent_id": str})
    E = charger_embeddings(df)
    y = df["y"].to_numpy()
    tr = (df["split"] == "train").to_numpy()
    va = (df["split"] == "valid").to_numpy()
    reel = df["sous_reel"].to_numpy()
    va_reel = va & reel

    R = {"selection": "macro-F1 sur valid ∩ sous_reel", "grille_C": GRILLE_C}
    modeles = {}
    for nom, m_tr in [("CLIP_all", tr), ("CLIP_reel", tr & reel)]:
        clf, best, sc = entrainer_clip(E, y, m_tr, E[va_reel], y[va_reel])
        joblib.dump(clf, C.MODELES / f"{nom}.joblib")
        modeles[nom] = clf
        R[nom] = {"n_train": int(m_tr.sum()), "C_retenu": best, "macro_f1_valid_reel_par_C": {str(k): v for k, v in sc.items()}}

    b1 = MajoriteParOrigine().fit(df[tr])
    b2 = MetaB2().fit(df[tr])
    joblib.dump(b1, C.MODELES / "B1.joblib")
    joblib.dump(b2, C.MODELES / "B2.joblib")
    R["B1"] = {"table_origine_vers_classe": {k: C.NOMS[v] for k, v in b1.table.items()}}
    R["B2"] = {"variables_num": NUM_B2, "variables_cat": CAT_B2, "manquant_num": MANQUANT_NUM, "manquant_cat": MANQUANT_CAT}

    R["valid"] = {}
    for nom, pred in [("CLIP_all", lambda d, e: modeles["CLIP_all"].predict(e)),
                      ("CLIP_reel", lambda d, e: modeles["CLIP_reel"].predict(e)),
                      ("B1", lambda d, e: b1.predict(d)), ("B2", lambda d, e: b2.predict(d))]:
        R["valid"][nom] = {}
        for sous, m in [("tout", va), ("sous_reel", va_reel), ("ia_probable", va & df["ia_probable"].to_numpy()),
                        ("sous_strict", va & df["sous_strict"].to_numpy())]:
            R["valid"][nom][sous] = metriques(y[m], pred(df[m], E[m]))
    C.ecrire_json(C.RESULTATS / "references_valid.json", R)
    for nom in R["valid"]:
        print(nom, {s: round(v["macro_f1"], 4) for s, v in R["valid"][nom].items()})
    print({k: R[k]["C_retenu"] for k in ["CLIP_all", "CLIP_reel"]})


if __name__ == "__main__":
    main()
