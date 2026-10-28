"""Fonctions partagées : embeddings existants, métriques, bootstrap par groupes."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
from sklearn.metrics import balanced_accuracy_score, confusion_matrix, f1_score, precision_recall_fscore_support

import config as C


def embeddings_existants():
    """{id: (sha256, vecteur brut float32)} depuis les trois caches existants (lecture seule)."""
    d = {}
    for f in C.EMB_EXISTANTS:
        z = np.load(f, allow_pickle=False)
        for i, s, e in zip(z["ids"], z["sha"], z["emb"]):
            d[str(i)] = (str(s), e.astype(np.float32), f.name)
    return d


def normaliser(E):
    E = np.asarray(E, dtype=np.float32)
    return E / np.linalg.norm(E, axis=1, keepdims=True)


def charger_embeddings(df):
    """Embeddings normalisés dans l'ordre de df (depuis donnees_derivees/embeddings.npz)."""
    z = np.load(C.EMBEDDINGS, allow_pickle=False)
    pos = {str(i): k for k, i in enumerate(z["ids"])}
    return z["emb"][[pos[i] for i in df["id"]]]


# ---------------------------------------------------------------------------
# Métriques. La macro-F1 et l'exactitude équilibrée portent sur les classes
# présentes dans les vraies étiquettes du sous-ensemble (ex. ia_probable n'a
# pas de CANNOT_DETERMINE : macro-F1 sur 2 classes ; une prédiction
# CANNOT_DETERMINE y compte quand même comme erreur dans le rappel).
# ---------------------------------------------------------------------------
def macro_f1(y, p):
    lab = np.unique(y)
    return float(f1_score(y, p, labels=lab, average="macro", zero_division=0))


def rappel_classe(y, p, c):
    m = y == c
    return float((p[m] == c).mean()) if m.any() else float("nan")


def metriques(y, p):
    y, p = np.asarray(y), np.asarray(p)
    if len(y) == 0:
        return {"n": 0}
    pr, rc, f1, sup = precision_recall_fscore_support(y, p, labels=[0, 1, 2], zero_division=0)
    return {
        "n": int(len(y)),
        "exactitude": float((y == p).mean()),
        "exactitude_equilibree": float(balanced_accuracy_score(y, p)),
        "macro_f1": macro_f1(y, p),
        "par_classe": {C.NOMS[k]: {"precision": float(pr[k]), "rappel": float(rc[k]), "f1": float(f1[k]),
                                   "support": int(sup[k])} for k in range(3)},
        "confusion": {"lignes=vrai, colonnes=prédit, ordre": C.NOMS,
                      "matrice": confusion_matrix(y, p, labels=[0, 1, 2]).tolist()},
    }


def metriques_2classes(y, proba):
    """PROPRE contre DEBORDEMENT : CANNOT_DETERMINE ignorée dans les vraies étiquettes,
    prédiction = argmax entre les probabilités PROPRE et DEBORDEMENT."""
    y = np.asarray(y)
    m = y != C.CD
    if not m.any():
        return {"n": 0}
    yy = y[m]
    pp = np.where(proba[m, C.PRO] >= proba[m, C.DEB], C.PRO, C.DEB)
    pr, rc, f1, sup = precision_recall_fscore_support(yy, pp, labels=[C.DEB, C.PRO], zero_division=0)
    return {"n": int(m.sum()), "exactitude_equilibree": float(balanced_accuracy_score(yy, pp)),
            "macro_f1": macro_f1(yy, pp),
            "par_classe": {C.NOMS[c]: {"precision": float(pr[k]), "rappel": float(rc[k]), "f1": float(f1[k]),
                                       "support": int(sup[k])} for k, c in enumerate([C.DEB, C.PRO])},
            "confusion": {"ordre": ["DEBORDEMENT", "PROPRE"],
                          "matrice": confusion_matrix(yy, pp, labels=[C.DEB, C.PRO]).tolist()}}


def _macro_f1_rapide(y, p):
    """Même définition que macro_f1 (classes présentes dans y), via la matrice de confusion."""
    cm = np.bincount(3 * y + p, minlength=9).reshape(3, 3).astype(float)
    tp = np.diag(cm)
    sup, prd = cm.sum(1), cm.sum(0)
    den = sup + prd
    f1 = np.where(den > 0, 2 * tp / np.where(den > 0, den, 1), 0.0)
    return float(f1[sup > 0].mean())


def bootstrap_groupes(y, p, groupes, n=1000, graine=C.GRAINE):
    """IC 95 % (percentiles) de la macro-F1 et du rappel PROPRE, rééchantillonnage des groupes de lieux."""
    y, p, g = np.asarray(y), np.asarray(p), np.asarray(groupes)
    ug, inv = np.unique(g, return_inverse=True)
    idx_par_g = [np.where(inv == k)[0] for k in range(len(ug))]
    rng = np.random.default_rng(graine)
    f1s, rps = [], []
    for _ in range(n):
        tir = rng.integers(0, len(ug), len(ug))
        idx = np.concatenate([idx_par_g[k] for k in tir])
        f1s.append(_macro_f1_rapide(y[idx], p[idx]))
        rps.append(rappel_classe(y[idx], p[idx], C.PRO))
    f1s, rps = np.array(f1s), np.array(rps)
    rps = rps[~np.isnan(rps)]
    ic = lambda a: [float(np.percentile(a, 2.5)), float(np.percentile(a, 97.5))] if len(a) else [None, None]
    return {"macro_f1": macro_f1(y, p), "macro_f1_ic95": ic(f1s),
            "rappel_propre": rappel_classe(y, p, C.PRO), "rappel_propre_ic95": ic(rps),
            "n_groupes": int(len(ug)), "n_reechantillons": n}


def ece(proba, y, n_bins=15):
    conf = proba.max(1)
    pred = proba.argmax(1)
    bords = np.linspace(0, 1, n_bins + 1)
    e = 0.0
    for a, b in zip(bords[:-1], bords[1:]):
        m = (conf > a) & (conf <= b)
        if m.any():
            e += m.mean() * abs((pred[m] == y[m]).mean() - conf[m].mean())
    return float(e)
